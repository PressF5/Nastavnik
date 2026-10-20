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
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MentorMatchingService {

    private final UserRepository userRepository;
    private final ProfileCompatibilityCalculator compatibilityCalculator;

    /**
     * Возвращает кандидатов для наставляемого, доступного текущему администратору ОО.
     * Наставники могут принадлежать любым ОО в том же МСУ.
     */
    public List<MentorMatchingResponse> findMentorsForMentee(Long menteeId) {
        if (menteeId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Необходимо указать ID наставляемого.");
        }

        User admin = getCurrentUser();
        if (admin.getRole() == null || admin.getRole().getCode() != RoleCode.OO_ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Подбор наставников доступен администратору ОО.");
        }
        if (admin.getEducationalOrganization() == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Для администратора не указана образовательная организация.");
        }

        User mentee = userRepository.findById(menteeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Наставляемый не найден."));

        if (mentee.getRole() == null || mentee.getRole().getCode() != RoleCode.MENTEE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Указанный пользователь не является наставляемым.");
        }

        if (mentee.getEducationalOrganization() == null
                || !Objects.equals(
                        admin.getEducationalOrganization().getId(),
                        mentee.getEducationalOrganization().getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Можно подбирать наставников только для наставляемых своей образовательной организации.");
        }

        if (mentee.getMsu() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Для наставляемого не указано МСУ.");
        }

        if (mentee.isBlocked() || mentee.getAccountStatus() != AccountStatus.ACCEPTED) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Наставляемый должен иметь подтверждённую и незаблокированную учётную запись.");
        }

        List<User> mentors = userRepository.findEligibleMentors(
                RoleCode.MENTOR,
                mentee.getMsu().getId(),
                AccountStatus.ACCEPTED);

        return mentors.stream()
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
                .filter(result -> result.compatibilityPercentage() != null)
                .sorted(Comparator.comparing(MentorMatchingResponse::compatibilityPercentage).reversed())
                .toList();
    }

    private User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null
                || !(authentication.getPrincipal() instanceof CustomUserDetails userDetails)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Необходимо авторизоваться.");
        }

        return userDetails.getUser();
    }
}
