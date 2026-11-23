package org.donriro.nastavnik.matching.service;

import lombok.RequiredArgsConstructor;
import org.donriro.nastavnik.matching.dto.MentorMatchingResponse;
import org.donriro.nastavnik.mentorship.entity.AcademicYear;
import org.donriro.nastavnik.mentorship.repository.AcademicYearRepository;
import org.donriro.nastavnik.mentorship.repository.MentorshipRepository;
import org.donriro.nastavnik.mentorship.status.MentorshipStatus;
import org.donriro.nastavnik.profile.value.repository.ProfileValueRepository;
import org.donriro.nastavnik.profile.value.service.ProfileValueService;
import org.donriro.nastavnik.security.principal.CustomUserDetails;
import org.donriro.nastavnik.user.dto.response.UserProfileResponse;
import org.donriro.nastavnik.user.entity.User;
import org.donriro.nastavnik.user.repository.UserRepository;
import org.donriro.nastavnik.user.role.RoleCode;
import org.donriro.nastavnik.user.status.AccountStatus;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MentorMatchingService {

    private static final String MENTOR_READINESS_FIELD_NAME = "Готовность к наставничеству";
    private static final int MIN_COMPARABLE_FIELDS = 3;

    private final Clock clock;

    private final UserRepository userRepository;
    private final ProfileCompatibilityCalculator compatibilityCalculator;
    private final MentorshipRepository mentorshipRepository;
    private final ProfileValueRepository profileValueRepository;
    private final ProfileValueService profileValueService;
    private final AcademicYearRepository academicYearRepository;

    public List<MentorMatchingResponse> findMentorsFor(User mentee) {
        if (mentee == null || mentee.getId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Необходимо указать наставляемого.");
        }

        if (mentee.getRole().getCode() != RoleCode.MENTEE) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Подбор наставников доступен только наставляемому.");
        }

        if (mentee.getMsu() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Для наставляемого не указано МСУ.");
        }

        if (mentee.isBlocked() || mentee.getAccountStatus() != AccountStatus.ACCEPTED) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Наставляемый должен иметь подтверждённую и активную учётную запись.");
        }

        LocalDate today = LocalDate.now(clock);

        if (mentee.getBirthDate() == null || mentee.getBirthDate().isAfter(today) || !mentee.getBirthDate().isAfter(today.minusYears(36))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Наставляемый должен быть не старше 35 лет.");
        }

        AcademicYear academicYear = academicYearRepository
                .findByDate(today)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "На текущую дату не найден действующий учебный год."));

        Long msuId = mentee.getMsu().getId();
        List<User> mentors = userRepository.findEligibleMentors(RoleCode.MENTOR, msuId, AccountStatus.ACCEPTED, MentorshipStatus.ACCEPTED, academicYear.getId());

        // Получаем ID наставников, подтвердивших готовность к наставничеству.
        Set<Long> mentorIds = mentors.stream()
                .map(User::getId)
                .collect(java.util.stream.Collectors.toSet());

        if (mentorIds.isEmpty()) {
            return List.of();
        }

        Set<Long> readyMentorIds = new HashSet<>(
                profileValueRepository.findUserIdsWithTrueBooleanValue(mentorIds, MENTOR_READINESS_FIELD_NAME));

        return mentors.stream()
                .filter(mentor -> readyMentorIds.contains(mentor.getId()))
                .map(mentor -> {
                    var compatibility = compatibilityCalculator.calculate(mentor.getId(), mentee.getId());
                    return new MentorMatchingResponse(
                            mentor.getId(),
                            mentor.getFirstName(),
                            mentor.getLastName(),
                            mentor.getMiddleName(),
                            mentor.getEducationalOrganization() != null
                                    ? mentor.getEducationalOrganization().getName()
                                    : null,
                            compatibility.compatibilityPercentage(),
                            compatibility.comparableFieldCount()
                    );
                })
                .filter(result -> result.comparableFieldCount() >= MIN_COMPARABLE_FIELDS)
                .filter(result -> result.compatibilityPercentage() != null)
                .filter(result -> result.compatibilityPercentage().compareTo(BigDecimal.valueOf(50)) > 0)
                .sorted(Comparator.comparing(MentorMatchingResponse::compatibilityPercentage).reversed())
                .toList();
    }

    public List<MentorMatchingResponse> findMentorsForMentee(Long menteeId) {
        User admin = getCurrentUser();

        if (admin.getRole() == null || admin.getRole().getCode() != RoleCode.OO_ADMIN || admin.getEducationalOrganization() == null || admin.getMsu() == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Подбор наставников доступен администратору ОО с настроенной организационной принадлежностью.");
        }

        if (admin.isBlocked() || admin.getAccountStatus() != AccountStatus.ACCEPTED) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Учётная запись администратора должна быть подтверждена и не заблокирована.");
        }

        User mentee = userRepository.findById(menteeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Наставляемый не найден."));

        if (mentee.getRole().getCode() != RoleCode.MENTEE || mentee.getEducationalOrganization() == null || !admin.getEducationalOrganization().getId().equals(mentee.getEducationalOrganization().getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Наставляемый не относится к вашей образовательной организации."
            );
        }

        if (mentee.isBlocked() || mentee.getAccountStatus() != AccountStatus.ACCEPTED) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Наставляемый должен иметь подтверждённую активную учётную запись.");
        }

        LocalDate today = LocalDate.now(clock);

        AcademicYear academicYear = academicYearRepository
                .findByDate(today)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "На текущую дату не найден действующий учебный год."));

        if (mentorshipRepository.existsByMentee_IdAndAcademicYear_IdAndStatusIn(
                mentee.getId(),
                academicYear.getId(),
                List.of(MentorshipStatus.PENDING, MentorshipStatus.ACCEPTED))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Для наставляемого уже создана заявка или назначен наставник в текущем учебном году.");
        }

        return findMentorsFor(mentee);
    }

    /**
     * Возвращает профиль наставника, рекомендованного для конкретного наставляемого.
     * Администратор ОО может просматривать профили рекомендованных наставников
     * только в пределах своего МСУ.
     */
    public UserProfileResponse getMentorProfileForMentee(Long menteeId, Long mentorId) {
        // Проверяем, что наставник действительно входит в список рекомендаций
        // для наставляемого, доступного текущему администратору.
        boolean isCandidate = findMentorsForMentee(menteeId)
                .stream()
                .anyMatch(candidate -> candidate.mentorId().equals(mentorId));

        if (!isCandidate) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Нет доступа к профилю этого наставника.");
        }

        User mentor = userRepository.findByIdWithOrganizationContext(mentorId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Наставник не найден."));

        return UserProfileResponse.from(mentor, profileValueService.getAllForUser(mentorId));
    }

    private User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !(authentication.getPrincipal() instanceof CustomUserDetails userDetails)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Необходимо авторизоваться.");
        }

        return userDetails.getUser();
    }

    public List<MentorMatchingResponse> findMentorsForCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !(authentication.getPrincipal() instanceof CustomUserDetails userDetails)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Необходимо авторизоваться.");
        }
        return findMentorsFor(userDetails.getUser());
    }
}
