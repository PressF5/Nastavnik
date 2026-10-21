package org.donriro.nastavnik.mentorship.service;

import lombok.RequiredArgsConstructor;
import org.donriro.nastavnik.matching.service.ProfileCompatibilityCalculator;
import org.donriro.nastavnik.mentorship.dto.*;
import org.donriro.nastavnik.mentorship.entity.AcademicYear;
import org.donriro.nastavnik.mentorship.entity.Mentorship;
import org.donriro.nastavnik.mentorship.entity.MentorshipAuditLog;
import org.donriro.nastavnik.mentorship.repository.AcademicYearRepository;
import org.donriro.nastavnik.mentorship.repository.MentorshipAuditLogRepository;
import org.donriro.nastavnik.mentorship.repository.MentorshipRepository;
import org.donriro.nastavnik.mentorship.status.MentorshipDecision;
import org.donriro.nastavnik.mentorship.status.MentorshipStatus;
import org.donriro.nastavnik.profile.value.service.ProfileValueService;
import org.donriro.nastavnik.security.principal.CustomUserDetails;
import org.donriro.nastavnik.user.entity.User;
import org.donriro.nastavnik.user.repository.UserRepository;
import org.donriro.nastavnik.user.role.RoleCode;
import org.donriro.nastavnik.user.status.AccountStatus;
import org.donriro.nastavnik.user.status.AuditSource;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MentorshipService {

    private final MentorshipRepository mentorshipRepository;
    private final AcademicYearRepository academicYearRepository;
    private final MentorshipAuditLogRepository auditLogRepository;
    private final UserRepository userRepository;
    private final ProfileCompatibilityCalculator compatibilityCalculator;
    private final ProfileValueService profileValueService;

    /**
     * Администратор ОО создаёт предварительную заявку
     * на назначение наставника наставляемому.
     */
    @Transactional
    public MentorshipResponse createRequest(CreateMentorshipRequest request) {
        User admin = getCurrentUser();

        requireRole(admin, RoleCode.OO_ADMIN);

        if (admin.getEducationalOrganization() == null || admin.getMsu() == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "У администратора не настроена организационная принадлежность.");
        }

        // Блокируем запись наставляемого до завершения транзакции.
        // Параллельные запросы на создание заявок для этого пользователя
        // будут выполняться последовательно.
        User mentee = userRepository
                .findByIdForUpdate(request.menteeId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Наставляемый не найден."));
        User mentor = getUser(request.mentorId());

        requireRole(mentee, RoleCode.MENTEE);
        requireRole(mentor, RoleCode.MENTOR);

        // Администратор работает только со своей ОО.
        if (!sameOrganization(admin, mentee)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Наставляемый не относится к вашей образовательной организации.");
        }

        // Наставник может быть из другой ОО, но только своего МСУ.
        if (!sameMsu(mentee, mentor)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Наставник и наставляемый должны относиться к одному МСУ.");
        }

        requireActiveAccount(mentee);
        requireActiveAccount(mentor);

        // Предварительно проверяем совместимость наставника
        // и наставляемого. Допускается только результат строго выше 50%.
        var compatibility = compatibilityCalculator.calculate(mentor.getId(), mentee.getId());
        BigDecimal compatibilityPercentage = compatibility.compatibilityPercentage();

        if (compatibilityPercentage == null || compatibilityPercentage.compareTo(BigDecimal.valueOf(50)) <= 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Совместимость наставника и наставляемого должна быть выше 50%.");
        }

        AcademicYear academicYear = academicYearRepository
                .findByActiveTrue()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Не найден активный учебный год."));

        List<MentorshipStatus> occupiedStatuses = List.of(MentorshipStatus.PENDING, MentorshipStatus.ACCEPTED);

        // Не допускаем несколько ожидающих заявок
        // или действующих наставников у одного наставляемого.
        if (mentorshipRepository.existsByMentee_IdAndStatusIn(mentee.getId(), occupiedStatuses)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "У наставляемого уже есть ожидающая заявка или действующий наставник.");
        }

        Mentorship mentorship = new Mentorship();
        mentorship.setMentor(mentor);
        mentorship.setMentee(mentee);
        mentorship.setCreatedBy(admin);
        mentorship.setAcademicYear(academicYear);
        mentorship.setMsu(mentee.getMsu());
        mentorship.setStatus(MentorshipStatus.PENDING);

        Mentorship saved = mentorshipRepository.save(mentorship);

        writeAudit(saved, admin, "REQUEST_CREATED", null, MentorshipStatus.PENDING, null);
        return toResponse(saved);
    }

    /**
     * Наставник видит только ожидающие заявки,
     * которые адресованы именно ему.
     */
    public List<MentorshipResponse> getCurrentMentorRequests() {
        User mentor = getCurrentUser();

        requireRole(mentor, RoleCode.MENTOR);

        return mentorshipRepository
                .findAllByMentor_IdAndStatusOrderByCreatedAtDesc(mentor.getId(), MentorshipStatus.PENDING)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * Возвращает список действующих наставляемых
     * текущего наставника.
     */
    public List<MentorshipResponse> getCurrentMentorMentees() {
        User mentor = getCurrentUser();

        requireRole(mentor, RoleCode.MENTOR);
        requireActiveAccount(mentor);

        return mentorshipRepository
                .findAllByMentor_IdAndStatusOrderByStartedAtDesc(mentor.getId(), MentorshipStatus.ACCEPTED)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * Наставник принимает или отклоняет заявку.
     */
    @Transactional
    public MentorshipResponse decide(Long mentorshipId, MentorshipDecisionRequest request) {
        User currentMentor = getCurrentUser();

        requireRole(currentMentor, RoleCode.MENTOR);
        requireActiveAccount(currentMentor);

        Long mentorId = currentMentor.getId();

        // Блокируем запись наставника до завершения транзакции.
        // Все решения по его заявкам будут последовательно проверять
        // актуальное состояние связей наставничества.
        User mentor = userRepository
                .findByIdForUpdate(mentorId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Наставник не найден."));

        requireRole(mentor, RoleCode.MENTOR);
        requireActiveAccount(mentor);

        Mentorship mentorship = mentorshipRepository
                .findById(mentorshipId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Заявка на наставничество не найдена."));

        // Наставник может обрабатывать только адресованные ему заявки.
        if (!mentorship.getMentor().getId().equals(mentor.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Эта заявка адресована другому наставнику.");
        }

        // После получения блокировки проверяем актуальный статус заявки.
        if (mentorship.getStatus() != MentorshipStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Заявка уже обработана.");
        }

        MentorshipStatus oldStatus = mentorship.getStatus();

        if (request.decision() == MentorshipDecision.ACCEPT) {
            // У наставляемого может быть только один действующий наставник.
            if (mentorshipRepository.existsByMentee_IdAndStatusIn(mentorship.getMentee().getId(), List.of(MentorshipStatus.ACCEPTED))) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "У наставляемого уже есть действующий наставник.");
            }

            // Ограничиваем только подтверждённые назначения.
            // Ожидающие заявки в лимит не входят.
            long activeMentees =
                    mentorshipRepository.countByMentor_IdAndStatus(mentor.getId(), MentorshipStatus.ACCEPTED);

            if (activeMentees >= 3) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Наставник уже имеет трёх действующих наставляемых.");
            }

            mentorship.setStatus(MentorshipStatus.ACCEPTED);
            mentorship.setStartedAt(Instant.now());
            mentorship.setEndedAt(null);

        } else if (request.decision() == MentorshipDecision.REJECT) {

            mentorship.setStatus(MentorshipStatus.REJECTED);
            mentorship.setStartedAt(null);
            mentorship.setEndedAt(null);

        } else {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Неизвестное решение по заявке.");
        }

        Mentorship saved = mentorshipRepository.save(mentorship);
        writeAudit(saved, mentor, request.decision() == MentorshipDecision.ACCEPT ? "REQUEST_ACCEPTED" : "REQUEST_REJECTED", oldStatus, saved.getStatus(), request.comment());
        return toResponse(saved);
    }

    /**
     * Возвращает карточку наставляемого,
     * с которым у текущего наставника есть действующее назначение.
     */
    public MenteeProfileResponse getMenteeProfile(Long menteeId) {
        User mentor = getCurrentUser();

        requireRole(mentor, RoleCode.MENTOR);
        requireActiveAccount(mentor);

        Mentorship mentorship = mentorshipRepository
                .findAllByMentor_IdAndStatusOrderByStartedAtDesc(mentor.getId(), MentorshipStatus.ACCEPTED)
                .stream()
                .filter(item -> item.getMentee().getId().equals(menteeId))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Действующий наставляемый не найден."));

        User mentee = mentorship.getMentee();

        return new MenteeProfileResponse(
                mentee.getId(),
                mentee.getFirstName(),
                mentee.getLastName(),
                mentee.getMiddleName(),
                mentee.getBirthDate(),
                mentee.getMsu() != null
                        ? mentee.getMsu().getName()
                        : null,
                mentee.getEducationalOrganization() != null
                        ? mentee.getEducationalOrganization().getName()
                        : null,
                toResponse(mentorship),
                profileValueService.getAllForUser(mentee.getId())
        );
    }

    /**
     * Завершает действующее наставничество.
     */
    @Transactional
    public MentorshipResponse endMentorship(Long mentorshipId, EndMentorshipRequest request) {
        User currentMentor = getCurrentUser();

        requireRole(currentMentor, RoleCode.MENTOR);
        requireActiveAccount(currentMentor);

        // Блокируем запись наставника на время операции.
        User mentor = userRepository
                .findByIdForUpdate(currentMentor.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Наставник не найден."));

        requireActiveAccount(mentor);

        Mentorship mentorship = mentorshipRepository
                .findByIdAndMentor_Id(mentorshipId, mentor.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Назначение наставничества не найдено."));

        if (mentorship.getStatus() != MentorshipStatus.ACCEPTED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Завершить можно только действующее наставничество.");
        }

        MentorshipStatus oldStatus = mentorship.getStatus();

        mentorship.setStatus(MentorshipStatus.ENDED);
        mentorship.setEndedAt(Instant.now());

        Mentorship saved = mentorshipRepository.save(mentorship);

        writeAudit(
                saved,
                mentor,
                "MENTORSHIP_ENDED",
                oldStatus,
                saved.getStatus(),
                request.reason().trim()
        );

        return toResponse(saved);
    }

    /**
     * Возвращает наставляемых текущей ОО,
     * которым можно назначить наставника.
     */
    public List<AvailableMenteeResponse> getAvailableMentees() {
        User admin = getCurrentUser();

        requireRole(admin, RoleCode.OO_ADMIN);
        requireActiveAccount(admin);

        if (admin.getEducationalOrganization() == null || admin.getMsu() == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "У администратора не настроена организационная принадлежность.");
        }

        return userRepository.findAvailableMentees(
                        RoleCode.MENTEE,
                        admin.getEducationalOrganization().getId(),
                        AccountStatus.ACCEPTED,
                        List.of(MentorshipStatus.PENDING, MentorshipStatus.ACCEPTED))
                .stream()
                .map(mentee -> new AvailableMenteeResponse(
                        mentee.getId(),
                        mentee.getFirstName(),
                        mentee.getLastName(),
                        mentee.getMiddleName(),
                        mentee.getBirthDate()
                ))
                .toList();
    }

    private void writeAudit(Mentorship mentorship, User actor, String action, MentorshipStatus oldStatus, MentorshipStatus newStatus, String comment) {
        MentorshipAuditLog auditLog = new MentorshipAuditLog();

        auditLog.setMentorship(mentorship);
        auditLog.setActor(actor);
        auditLog.setMsuId(mentorship.getMsu().getId());
        auditLog.setAction(action);
        auditLog.setOldStatus(oldStatus);
        auditLog.setNewStatus(newStatus);
        auditLog.setComment(comment);
        auditLog.setSource(AuditSource.USER);

        auditLogRepository.save(auditLog);
    }

    private MentorshipResponse toResponse(Mentorship mentorship) {
        User mentor = mentorship.getMentor();
        User mentee = mentorship.getMentee();
        AcademicYear academicYear = mentorship.getAcademicYear();

        return new MentorshipResponse(
                mentorship.getId(),

                mentor.getId(),
                mentor.getFirstName(),
                mentor.getLastName(),
                mentor.getMiddleName(),

                mentee.getId(),
                mentee.getFirstName(),
                mentee.getLastName(),
                mentee.getMiddleName(),

                academicYear.getId(),
                academicYear.getName(),

                mentorship.getStatus(),

                mentorship.getCreatedAt(),
                mentorship.getStartedAt(),
                mentorship.getEndedAt()
        );
    }

    private User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof CustomUserDetails userDetails)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Необходимо авторизоваться.");
        }

        return userDetails.getUser();
    }

    private User getUser(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Пользователь не найден: " + id));
    }

    private void requireRole(User user, RoleCode expectedRole) {
        if (user.getRole() == null || user.getRole().getCode() != expectedRole) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Недостаточно прав для выполнения операции.");
        }
    }

    private void requireActiveAccount(User user) {
        if (user.isBlocked() || user.getAccountStatus() != AccountStatus.ACCEPTED) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Учётная запись должна быть подтверждена и не заблокирована.");
        }
    }

    private boolean sameOrganization(User first, User second) {
        return first.getEducationalOrganization() != null
                && second.getEducationalOrganization() != null
                && first.getEducationalOrganization().getId()
                .equals(second.getEducationalOrganization().getId());
    }

    private boolean sameMsu(User first, User second) {
        return first.getMsu() != null
                && second.getMsu() != null
                && first.getMsu().getId()
                .equals(second.getMsu().getId());
    }
}
