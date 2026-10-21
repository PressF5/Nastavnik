package org.donriro.nastavnik.matching.service;

import lombok.RequiredArgsConstructor;
import org.donriro.nastavnik.matching.dto.MentorMatchingResponse;
import org.donriro.nastavnik.mentorship.repository.MentorshipRepository;
import org.donriro.nastavnik.mentorship.status.MentorshipStatus;
import org.donriro.nastavnik.security.principal.CustomUserDetails;
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
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MentorMatchingService {

    private final UserRepository userRepository;
    private final ProfileCompatibilityCalculator compatibilityCalculator;
    private final MentorshipRepository mentorshipRepository;

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

        Long msuId = mentee.getMsu().getId();
        List<User> mentors = userRepository.findEligibleMentors(RoleCode.MENTOR, msuId, AccountStatus.ACCEPTED);

        return mentors.stream()
                .map(mentor -> {
                    var compatibility = compatibilityCalculator.calculate(mentor.getId(), mentee.getId());
                    return new MentorMatchingResponse(
                            mentor.getId(),
                            mentor.getFirstName(),
                            mentor.getLastName(),
                            mentor.getMiddleName(),
                            mentor.getEducationalOrganization() != null ? mentor.getEducationalOrganization().getName() : null,
                            compatibility.compatibilityPercentage(),
                            compatibility.comparableFieldCount()
                    );
                })
                .filter(result -> result.compatibilityPercentage() != null)
                .filter(result -> result.compatibilityPercentage().compareTo(BigDecimal.valueOf(50)) > 0)
                .sorted(Comparator.comparing(MentorMatchingResponse::compatibilityPercentage).reversed())
                .toList();
    }

    public List<MentorMatchingResponse> findMentorsForMentee(Long menteeId) {
        User admin = getCurrentUser();

        if (admin.getRole().getCode() != RoleCode.OO_ADMIN || admin.getEducationalOrganization() == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Подбор наставников доступен администратору ОО.");
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

        if (mentorshipRepository.existsByMentee_IdAndStatusIn(mentee.getId(), List.of(MentorshipStatus.PENDING, MentorshipStatus.ACCEPTED))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Для наставляемого уже создана заявка или назначен наставник.");
        }

        return findMentorsFor(mentee);
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
