package org.donriro.nastavnik.mentorship.service;

import lombok.RequiredArgsConstructor;
import org.donriro.nastavnik.matching.service.ProfileCompatibilityCalculator;
import org.donriro.nastavnik.mentorship.dto.*;
import org.donriro.nastavnik.mentorship.entity.AcademicYear;
import org.donriro.nastavnik.mentorship.entity.Mentorship;
import org.donriro.nastavnik.mentorship.entity.MentorshipAuditLog;
import org.donriro.nastavnik.mentorship.repository.AcademicYearRepository;
import org.donriro.nastavnik.mentorship.repository.MentorParticipationRepository;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MentorshipService {

    private static final int MAX_MENTEE_AGE = 35;

    private final Clock clock;

    private final MentorshipRepository mentorshipRepository;
    private final AcademicYearRepository academicYearRepository;
    private final MentorshipAuditLogRepository auditLogRepository;
    private final UserRepository userRepository;
    private final ProfileCompatibilityCalculator compatibilityCalculator;
    private final ProfileValueService profileValueService;
    private final MentorParticipationRepository mentorParticipationRepository;

    /**
     * Администратор ОО создаёт предварительную заявку
     * на назначение наставника наставляемому.
     */
    @Transactional
    public MentorshipResponse createRequest(CreateMentorshipRequest request) {
        User admin = getCurrentUser();

        requireRole(admin, RoleCode.OO_ADMIN);
        requireActiveAccount(admin);

        if (admin.getEducationalOrganization() == null || admin.getMsu() == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "У администратора не настроена организационная принадлежность.");
        }

        // Сначала блокируем наставника.
        // Создание и принятие заявок одного наставника должны выполняться последовательно.
        User mentor = userRepository
                .findByIdForUpdate(request.mentorId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Наставник не найден."));

        // Затем блокируем наставляемого.
        // Это предотвращает одновременное создание нескольких активных назначений.
        User mentee = userRepository
                .findByIdForUpdate(request.menteeId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Наставляемый не найден."));

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
        requireAllowedMenteeAge(mentee);

        LocalDate today = LocalDate.now(clock);

        AcademicYear academicYear = academicYearRepository
                .findByDate(today)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "На текущую дату не найден действующий учебный год."));

        // Наставник должен подтвердить участие в текущем учебном году.
        if (!mentorParticipationRepository.existsByMentor_IdAndAcademicYear_IdAndParticipatedTrue(mentor.getId(), academicYear.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Наставник не подтвердил участие в текущем учебном году.");
        }

        // Не создаём новые заявки наставнику, который уже достиг лимита.
        long activeMentees = mentorshipRepository
                .countByMentor_IdAndAcademicYear_IdAndStatus(mentor.getId(), academicYear.getId(), MentorshipStatus.ACCEPTED);
        if (activeMentees >= 3) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Наставник уже имеет трёх действующих наставляемых.");
        }

        // Предварительно проверяем совместимость наставника
        // и наставляемого. Допускается только результат строго выше 50%.
        var compatibility = compatibilityCalculator.calculate(mentor.getId(), mentee.getId());
        BigDecimal compatibilityPercentage = compatibility.compatibilityPercentage();

        if (compatibilityPercentage == null || compatibilityPercentage.compareTo(BigDecimal.valueOf(50)) <= 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Совместимость наставника и наставляемого должна быть выше 50%.");
        }

        List<MentorshipStatus> occupiedStatuses = List.of(MentorshipStatus.PENDING, MentorshipStatus.ACCEPTED);

        // Не допускаем несколько ожидающих заявок
        // или действующих наставников у одного наставляемого.
        if (mentorshipRepository.existsByMentee_IdAndAcademicYear_IdAndStatusIn(mentee.getId(), academicYear.getId(), occupiedStatuses)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "У наставляемого уже есть ожидающая заявка или действующий наставник в текущем учебном году.");
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

    public List<MentorshipResponse> getCurrentMentorRequests(Long academicYearId) {
        User mentor = getCurrentUser();

        requireRole(mentor, RoleCode.MENTOR);
        requireActiveAccount(mentor);

        AcademicYear academicYear = resolveAcademicYear(academicYearId);

        return mentorshipRepository
                .findAllByMentor_IdAndAcademicYear_IdAndStatusOrderByCreatedAtDesc(
                        mentor.getId(),
                        academicYear.getId(),
                        MentorshipStatus.PENDING)
                .stream()
                .map(this::toResponse)
                .toList();
    }


    /**
     * Возвращает список действующих наставляемых
     * текущего наставника.
     */

    public List<MentorshipResponse> getCurrentMentorMentees(Long academicYearId) {
        User mentor = getCurrentUser();

        requireRole(mentor, RoleCode.MENTOR);
        requireActiveAccount(mentor);

        AcademicYear academicYear = resolveAcademicYear(academicYearId);

        return mentorshipRepository
                .findAllByMentor_IdAndAcademicYear_IdAndStatusInOrderByStartedAtDesc(
                        mentor.getId(),
                        academicYear.getId(),
                        List.of(MentorshipStatus.ACCEPTED, MentorshipStatus.ENDED)
                )
                .stream()
                .map(this::toResponse)
                .toList();
    }


    /**
     * Возвращает назначение наставничества
     * авторизованного наставляемого за выбранный учебный год.
     */
    public Optional<MentorshipResponse> getCurrentMenteeMentorship(Long academicYearId) {
        User mentee = getCurrentUser();

        requireRole(mentee, RoleCode.MENTEE);
        requireActiveAccount(mentee);

        AcademicYear academicYear = resolveAcademicYear(academicYearId);

        return mentorshipRepository
                .findFirstByMentee_IdAndAcademicYear_IdAndStatusInOrderByCreatedAtDesc(
                        mentee.getId(),
                        academicYear.getId(),
                        List.of(MentorshipStatus.PENDING, MentorshipStatus.ACCEPTED, MentorshipStatus.ENDED)
                )
                .map(this::toResponse);
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

        LocalDate today = LocalDate.now(clock);

        AcademicYear currentAcademicYear = academicYearRepository
                .findByDate(today)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "На текущую дату не найден действующий учебный год."));

        if (request.decision() == MentorshipDecision.ACCEPT
                && !mentorship.getAcademicYear().getId().equals(currentAcademicYear.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Нельзя принять заявку, созданную в другом учебном году.");
        }

        if (request.decision() == MentorshipDecision.ACCEPT
                && !mentorParticipationRepository.existsByMentor_IdAndAcademicYear_IdAndParticipatedTrue(mentor.getId(), currentAcademicYear.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Вы не подтвердили участие в текущем учебном году.");
        }

        MentorshipStatus oldStatus = mentorship.getStatus();

        if (request.decision() == MentorshipDecision.ACCEPT) {

            // Блокируем наставляемого до завершения транзакции.
            // Если другой наставник одновременно пытается принять
            // другую заявку этого же пользователя, он будет ждать.
            User mentee = userRepository
                    .findByIdForUpdate(mentorship.getMentee().getId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Наставляемый не найден."));

            requireRole(mentee, RoleCode.MENTEE);
            requireActiveAccount(mentee);
            requireAllowedMenteeAge(mentee);

            if (!sameMsu(mentor, mentee)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Наставник и наставляемый должны относиться к одному МСУ.");
            }

            var compatibility = compatibilityCalculator.calculate(mentor.getId(), mentee.getId());
            BigDecimal compatibilityPercentage = compatibility.compatibilityPercentage();

            if (compatibilityPercentage == null || compatibilityPercentage.compareTo(BigDecimal.valueOf(50)) <= 0) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Совместимость наставника и наставляемого должна быть выше 50%. Предложение больше нельзя принять.");
            }

            // После получения блокировки проверяем актуальное состояние.
            // У наставляемого может быть только один действующий наставник.
            boolean alreadyHasMentor = mentorshipRepository
                    .existsByMentee_IdAndAcademicYear_IdAndStatusIn(mentee.getId(), mentorship.getAcademicYear().getId(), List.of(MentorshipStatus.ACCEPTED));

            if (alreadyHasMentor) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "У наставляемого уже есть действующий наставник.");
            }

            // Считаем только действующие назначения.
            // Заявки PENDING в лимит не входят.
            long activeMentees = mentorshipRepository
                    .countByMentor_IdAndAcademicYear_IdAndStatus(mentor.getId(), mentorship.getAcademicYear().getId(), MentorshipStatus.ACCEPTED);

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

        writeAudit(
                saved,
                mentor,
                request.decision() == MentorshipDecision.ACCEPT
                        ? "REQUEST_ACCEPTED"
                        : "REQUEST_REJECTED",
                oldStatus,
                saved.getStatus(),
                request.comment()
        );

        // После принятия третьего наставляемого автоматически отклоняем остальные заявки.
        if (request.decision() == MentorshipDecision.ACCEPT) {
            long activeMentees = mentorshipRepository
                    .countByMentor_IdAndAcademicYear_IdAndStatus(mentor.getId(), mentorship.getAcademicYear().getId(), MentorshipStatus.ACCEPTED);

            if (activeMentees >= 3) {
                List<Mentorship> pendingRequests = mentorshipRepository
                        .findAllByMentor_IdAndAcademicYear_IdAndStatusOrderByCreatedAtDesc(mentor.getId(), mentorship.getAcademicYear().getId(), MentorshipStatus.PENDING);
                String rejectionReason = "Наставник достиг максимального количества действующих наставляемых";

                for (Mentorship pendingRequest : pendingRequests) {
                    MentorshipStatus previousStatus = pendingRequest.getStatus();

                    pendingRequest.setStatus(MentorshipStatus.REJECTED);
                    pendingRequest.setStartedAt(null);
                    pendingRequest.setEndedAt(null);

                    mentorshipRepository.save(pendingRequest);

                    // Автоматическое отклонение фиксируется от имени системы.
                    writeAudit(
                            pendingRequest,
                            null,
                            "AUTO_REJECTED_LIMIT_REACHED",
                            previousStatus,
                            MentorshipStatus.REJECTED,
                            rejectionReason,
                            AuditSource.SYSTEM
                    );
                }
            }
        }

        return toResponse(saved);
    }


    /**
     * Возвращает анкету наставляемого из ожидающей заявки.
     * Наставник может просматривать только заявки, адресованные ему.
     */
    public MenteeProfileResponse getPendingMenteeProfile(Long mentorshipId) {
        User mentor = getCurrentUser();

        requireRole(mentor, RoleCode.MENTOR);
        requireActiveAccount(mentor);

        Mentorship mentorship = mentorshipRepository
                .findById(mentorshipId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Заявка на наставничество не найдена."
                ));

        // Запрещаем просматривать заявки других наставников.
        if (!mentorship.getMentor().getId().equals(mentor.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Эта заявка адресована другому наставнику.");
        }

        // Просмотр доступен только для ожидающей заявки.
        if (mentorship.getStatus() != MentorshipStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Заявка больше не ожидает решения.");
        }

        User mentee = mentorship.getMentee();

        return new MenteeProfileResponse(
                mentee.getId(),
                mentee.getFirstName(),
                mentee.getLastName(),
                mentee.getMiddleName(),
                mentee.getBirthDate(),
                mentee.getRegion() != null
                        ? mentee.getRegion().getName()
                        : null,
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
     * Возвращает карточку наставляемого,
     * с которым у текущего наставника есть действующее назначение
     * в выбранном учебном году.
     */
    public MenteeProfileResponse getMenteeProfile(Long menteeId, Long academicYearId) {
        User mentor = getCurrentUser();

        requireRole(mentor, RoleCode.MENTOR);
        requireActiveAccount(mentor);

        AcademicYear academicYear = resolveAcademicYear(academicYearId);

        Mentorship mentorship = mentorshipRepository
                .findFirstByMentor_IdAndMentee_IdAndAcademicYear_IdAndStatusInOrderByStartedAtDesc(
                        mentor.getId(),
                        menteeId,
                        academicYear.getId(),
                        List.of(MentorshipStatus.ACCEPTED, MentorshipStatus.ENDED)
                )
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Наставляемый не найден в выбранном учебном году."));

        User mentee = mentorship.getMentee();

        return new MenteeProfileResponse(
                mentee.getId(),
                mentee.getFirstName(),
                mentee.getLastName(),
                mentee.getMiddleName(),
                mentee.getBirthDate(),
                mentee.getRegion() != null ? mentee.getRegion().getName() : null,
                mentee.getMsu() != null ? mentee.getMsu().getName() : null,
                mentee.getEducationalOrganization() != null
                        ? mentee.getEducationalOrganization().getName()
                        : null,
                toResponse(mentorship),
                profileValueService.getAllForUser(mentee.getId())
        );
    }

    /**
     * Возвращает анкету наставника для выбранного учебного года.
     */
    public MentorProfileResponse getCurrentMenteeMentorProfile(Long academicYearId) {
        User mentee = getCurrentUser();

        requireRole(mentee, RoleCode.MENTEE);
        requireActiveAccount(mentee);

        AcademicYear academicYear = resolveAcademicYear(academicYearId);

        Mentorship mentorship = mentorshipRepository
                .findFirstByMentee_IdAndAcademicYear_IdAndStatusInOrderByCreatedAtDesc(
                        mentee.getId(),
                        academicYear.getId(),
                        List.of(MentorshipStatus.PENDING, MentorshipStatus.ACCEPTED, MentorshipStatus.ENDED)
                )
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Назначение наставника не найдено в выбранном учебном году."));

        User mentor = mentorship.getMentor();

        return new MentorProfileResponse(
                mentor.getId(),
                mentor.getFirstName(),
                mentor.getLastName(),
                mentor.getMiddleName(),
                mentor.getRegion() != null ? mentor.getRegion().getName() : null,
                mentor.getMsu() != null ? mentor.getMsu().getName() : null,
                mentor.getEducationalOrganization() != null
                        ? mentor.getEducationalOrganization().getName()
                        : null,
                toResponse(mentorship),
                profileValueService.getAllForUser(mentor.getId())
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

        requireRole(mentor, RoleCode.MENTOR);
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

        LocalDate today = LocalDate.now(clock);
        LocalDate oldestAllowedBirthDateExclusive = today.minusYears(MAX_MENTEE_AGE + 1L);
        AcademicYear academicYear = academicYearRepository
                .findByDate(today)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "На текущую дату не найден действующий учебный год."));

        return userRepository.findAvailableMentees(
                        RoleCode.MENTEE,
                        admin.getEducationalOrganization().getId(),
                        AccountStatus.ACCEPTED,
                        List.of(MentorshipStatus.PENDING, MentorshipStatus.ACCEPTED),
                        today,
                        oldestAllowedBirthDateExclusive,
                        academicYear.getId())
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


    /**
     * Возвращает назначения наставничества наставляемых
     * образовательной организации текущего администратора
     * за выбранный учебный год.
     */
    public Page<MentorshipResponse> getOrganizationMentorships(Long academicYearId, MentorshipStatus status, String search, Pageable pageable) {
        User admin = getCurrentUser();

        requireRole(admin, RoleCode.OO_ADMIN);
        requireActiveAccount(admin);

        if (admin.getEducationalOrganization() == null || admin.getMsu() == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "У администратора не настроена организационная принадлежность.");
        }

        // Если год не передан, используем текущий учебный год.
        AcademicYear academicYear;

        if (academicYearId == null) {
            LocalDate today = LocalDate.now(clock);

            academicYear = academicYearRepository.findByDate(today)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "На текущую дату не найден действующий учебный год."));
        } else {
            academicYear = academicYearRepository.findById(academicYearId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Учебный год не найден."));
        }

        String normalizedSearch = search == null || search.isBlank() ? "" : search.trim().toLowerCase();

        return mentorshipRepository.findAllForOrganization(
                        admin.getEducationalOrganization().getId(),
                        academicYear.getId(),
                        status,
                        normalizedSearch,
                        pageable
                )
                .map(this::toResponse);
    }

    /**
     * Возвращает историю изменений назначения наставничества
     * для администратора образовательной организации.
     */
    public List<MentorshipAuditLogResponse> getOrganizationMentorshipHistory(Long mentorshipId) {
        User admin = getCurrentUser();

        requireRole(admin, RoleCode.OO_ADMIN);
        requireActiveAccount(admin);

        if (admin.getEducationalOrganization() == null || admin.getMsu() == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "У администратора не настроена организационная принадлежность.");
        }

        Mentorship mentorship = mentorshipRepository.findById(mentorshipId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Назначение наставничества не найдено."));

        // Администратор может просматривать историю только своей ОО.
        if (!sameOrganization(admin, mentorship.getMentee()) || !admin.getMsu().getId().equals(mentorship.getMsu().getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Нет доступа к истории этого назначения.");
        }

        return auditLogRepository
                .findAllByMentorship_IdOrderByCreatedAtAsc(mentorshipId)
                .stream()
                .map(log -> {
                    User actor = log.getActor();

                    String actorName = actor == null
                            ? "Система"
                            : String.join(
                            " ",
                            java.util.stream.Stream.of(
                                            actor.getLastName(),
                                            actor.getFirstName(),
                                            actor.getMiddleName()
                                    )
                                    .filter(value -> value != null && !value.isBlank())
                                    .toList()
                    );

                    return new MentorshipAuditLogResponse(
                            log.getId(),
                            log.getAction(),
                            log.getOldStatus(),
                            log.getNewStatus(),
                            log.getComment(),
                            actorName.isBlank() ? "Система" : actorName,
                            log.getSource(),
                            log.getCreatedAt());
                })
                .toList();
    }

    /**
     * Возвращает учебный год для просмотра наставником или наставляемым.
     * Если идентификатор не передан, используется текущий год.
     * Будущие учебные годы недоступны для просмотра.
     */
    private AcademicYear resolveAcademicYear(Long academicYearId) {
        LocalDate today = LocalDate.now(clock);

        AcademicYear academicYear;

        if (academicYearId != null) {
            academicYear = academicYearRepository.findById(academicYearId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Учебный год не найден."));
        } else {
            academicYear = academicYearRepository.findByDate(today)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "На текущую дату не найден действующий учебный год."));
        }

        if (academicYear.getStartDate().isAfter(today)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Просмотр будущих учебных годов недоступен.");
        }

        return academicYear;
    }

    /**
     * Проверяет, соответствует ли наставляемый возрастному ограничению.
     */
    private void requireAllowedMenteeAge(User mentee) {
        LocalDate today = LocalDate.now(clock);

        if (mentee.getBirthDate() == null
                || mentee.getBirthDate().isAfter(today)
                || !mentee.getBirthDate().isAfter(today.minusYears(MAX_MENTEE_AGE + 1L))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Наставляемый должен быть не старше 35 лет.");
        }
    }

    private void writeAudit(Mentorship mentorship, User actor, String action, MentorshipStatus oldStatus, MentorshipStatus newStatus, String comment) {
        writeAudit(mentorship, actor, action, oldStatus, newStatus, comment, AuditSource.USER);
    }

    private void writeAudit(Mentorship mentorship, User actor, String action, MentorshipStatus oldStatus, MentorshipStatus newStatus, String comment, AuditSource source) {
        MentorshipAuditLog auditLog = new MentorshipAuditLog();

        auditLog.setMentorship(mentorship);
        auditLog.setActor(actor);
        auditLog.setMsuId(mentorship.getMsu().getId());
        auditLog.setAction(action);
        auditLog.setOldStatus(oldStatus);
        auditLog.setNewStatus(newStatus);
        auditLog.setComment(comment);
        auditLog.setSource(source);

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
                mentorship.getEndedAt(),

                mentor.getEducationalOrganization() != null
                        ? mentor.getEducationalOrganization().getName()
                        : null,

                mentee.getEducationalOrganization() != null
                        ? mentee.getEducationalOrganization().getName()
                        : null
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
