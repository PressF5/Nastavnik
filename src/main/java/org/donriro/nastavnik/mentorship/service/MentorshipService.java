package org.donriro.nastavnik.mentorship.service;

import lombok.RequiredArgsConstructor;
import org.donriro.nastavnik.mentorship.dto.CreateMentorshipRequest;
import org.donriro.nastavnik.mentorship.dto.MentorshipDecision;
import org.donriro.nastavnik.mentorship.dto.MentorshipDecisionRequest;
import org.donriro.nastavnik.mentorship.dto.MentorshipResponse;
import org.donriro.nastavnik.mentorship.entity.AcademicYear;
import org.donriro.nastavnik.mentorship.entity.Mentorship;
import org.donriro.nastavnik.mentorship.entity.MentorshipAuditLog;
import org.donriro.nastavnik.mentorship.repository.AcademicYearRepository;
import org.donriro.nastavnik.mentorship.repository.MentorshipAuditLogRepository;
import org.donriro.nastavnik.mentorship.repository.MentorshipRepository;
import org.donriro.nastavnik.mentorship.status.MentorshipStatus;
import org.donriro.nastavnik.security.principal.CustomUserDetails;
import org.donriro.nastavnik.user.entity.User;
import org.donriro.nastavnik.user.repository.UserRepository;
import org.donriro.nastavnik.user.role.RoleCode;
import org.donriro.nastavnik.user.status.AccountStatus;
import org.donriro.nastavnik.user.status.AuditSource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MentorshipService {

    private static final List<MentorshipStatus> OPEN_STATUSES =
            List.of(MentorshipStatus.PENDING, MentorshipStatus.ACCEPTED);

    private final MentorshipRepository mentorshipRepository;
    private final MentorshipAuditLogRepository auditLogRepository;
    private final AcademicYearRepository academicYearRepository;
    private final UserRepository userRepository;

    @Transactional
    public MentorshipResponse createRequest(CreateMentorshipRequest request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Необходимо передать данные заявки.");
        }

        User admin = getCurrentUser();
        requireRole(admin, RoleCode.OO_ADMIN, "Создавать заявки может только администратор ОО.");
        if (admin.getEducationalOrganization() == null || admin.getMsu() == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Для администратора должны быть указаны образовательная организация и МСУ.");
        }

        User mentee = userRepository.findById(request.menteeId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Наставляемый не найден."));
        User mentor = userRepository.findById(request.mentorId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Наставник не найден."));

        if (mentee.getRole() == null || mentee.getRole().getCode() != RoleCode.MENTEE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Указанный пользователь не является наставляемым.");
        }
        if (mentor.getRole() == null || mentor.getRole().getCode() != RoleCode.MENTOR) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Указанный пользователь не является наставником.");
        }

        if (mentee.getEducationalOrganization() == null
                || !Objects.equals(admin.getEducationalOrganization().getId(),
                                   mentee.getEducationalOrganization().getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Можно создавать заявки только для наставляемых своей образовательной организации.");
        }

        if (mentee.getMsu() == null || mentor.getMsu() == null
                || !Objects.equals(mentee.getMsu().getId(), mentor.getMsu().getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Наставник и наставляемый должны относиться к одному МСУ.");
        }

        requireActiveUser(mentee, "Наставляемый");
        requireActiveUser(mentor, "Наставник");

        if (mentee.getId().equals(mentor.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Наставник и наставляемый должны быть разными пользователями.");
        }

        AcademicYear academicYear = academicYearRepository.findByActiveTrue()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                        "В системе не настроен активный учебный год."));

        if (mentorshipRepository.existsByMentee_IdAndStatusIn(mentee.getId(), OPEN_STATUSES)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "У наставляемого уже есть ожидающая решения заявка или действующая связь.");
        }

        if (mentorshipRepository.existsByMentor_IdAndMentee_IdAndAcademicYear_IdAndStatusIn(
                mentor.getId(), mentee.getId(), academicYear.getId(), OPEN_STATUSES)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Для этой пары уже существует ожидающая решения заявка или действующая связь.");
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

    public List<MentorshipResponse> getCurrentMentorRequests() {
        User mentor = getCurrentUser();
        requireRole(mentor, RoleCode.MENTOR, "Просматривать заявки может только наставник.");

        return mentorshipRepository
                .findAllByMentor_IdAndStatusOrderByCreatedAtDesc(mentor.getId(), MentorshipStatus.PENDING)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public MentorshipResponse decide(Long mentorshipId, MentorshipDecisionRequest request) {
        if (mentorshipId == null || request == null || request.decision() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Необходимо указать заявку и решение наставника.");
        }

        User mentor = getCurrentUser();
        requireRole(mentor, RoleCode.MENTOR, "Решение по заявке может принимать только наставник.");

        Mentorship mentorship = mentorshipRepository.findById(mentorshipId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Заявка не найдена."));

        if (!Objects.equals(mentorship.getMentor().getId(), mentor.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Нельзя принимать решение по заявке, адресованной другому наставнику.");
        }

        if (mentorship.getStatus() != MentorshipStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Рассмотреть можно только заявку со статусом PENDING.");
        }

        MentorshipStatus oldStatus = mentorship.getStatus();
        if (request.decision() == MentorshipDecision.ACCEPT) {
            boolean otherAccepted = mentorshipRepository.existsByMentee_IdAndStatusIn(
                    mentorship.getMentee().getId(), List.of(MentorshipStatus.ACCEPTED));
            if (otherAccepted) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "У наставляемого уже есть действующая связь с наставником.");
            }
            mentorship.setStatus(MentorshipStatus.ACCEPTED);
            mentorship.setStartedAt(Instant.now());
            mentorship.setEndedAt(null);
            writeAudit(mentorship, mentor, "REQUEST_ACCEPTED", oldStatus,
                    MentorshipStatus.ACCEPTED, request.comment());
        } else if (request.decision() == MentorshipDecision.REJECT) {
            mentorship.setStatus(MentorshipStatus.REJECTED);
            mentorship.setStartedAt(null);
            mentorship.setEndedAt(null);
            writeAudit(mentorship, mentor, "REQUEST_REJECTED", oldStatus,
                    MentorshipStatus.REJECTED, request.comment());
        } else {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Неизвестное решение по заявке.");
        }

        return toResponse(mentorshipRepository.save(mentorship));
    }

    private User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !(authentication.getPrincipal() instanceof CustomUserDetails userDetails)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Необходимо авторизоваться.");
        }
        return userDetails.getUser();
    }

    private void requireRole(User user, RoleCode roleCode, String message) {
        if (user.getRole() == null || user.getRole().getCode() != roleCode) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, message);
        }
    }

    private void requireActiveUser(User user, String label) {
        if (user.isBlocked() || user.getAccountStatus() != AccountStatus.ACCEPTED) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    label + " должен иметь подтверждённую и незаблокированную учётную запись.");
        }
    }

    private void writeAudit(
            Mentorship mentorship,
            User actor,
            String action,
            MentorshipStatus oldStatus,
            MentorshipStatus newStatus,
            String comment
    ) {
        MentorshipAuditLog audit = new MentorshipAuditLog();
        audit.setMentorship(mentorship);
        audit.setActor(actor);
        audit.setMsuId(mentorship.getMsu().getId());
        audit.setAction(action);
        audit.setOldStatus(oldStatus);
        audit.setNewStatus(newStatus);
        audit.setComment(comment);
        audit.setSource(AuditSource.USER);
        auditLogRepository.save(audit);
    }

    private MentorshipResponse toResponse(Mentorship mentorship) {
        User mentor = mentorship.getMentor();
        User mentee = mentorship.getMentee();
        AcademicYear year = mentorship.getAcademicYear();

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
                year.getId(),
                year.getName(),
                mentorship.getStatus(),
                mentorship.getCreatedAt(),
                mentorship.getStartedAt(),
                mentorship.getEndedAt()
        );
    }
}
