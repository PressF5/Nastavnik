package org.donriro.nastavnik.matching.service;

import lombok.RequiredArgsConstructor;
import org.donriro.nastavnik.matching.dto.MentorMatchingResponse;
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

import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MentorMatchingService {

    private final UserRepository userRepository;
    private final ProfileCompatibilityCalculator compatibilityCalculator;

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
                .sorted(Comparator.comparing(MentorMatchingResponse::compatibilityPercentage).reversed())
                .toList();
    }

    public List<MentorMatchingResponse> findMentorsForCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !(authentication.getPrincipal() instanceof CustomUserDetails userDetails)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Необходимо авторизоваться.");
        }
        return findMentorsFor(userDetails.getUser());
    }
}
