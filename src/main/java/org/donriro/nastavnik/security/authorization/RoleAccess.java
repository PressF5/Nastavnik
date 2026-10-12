package org.donriro.nastavnik.security.authorization;

import lombok.RequiredArgsConstructor;
import org.donriro.nastavnik.mentorship.entity.Mentorship;
import org.donriro.nastavnik.mentorship.repository.MentorshipRepository;
import org.donriro.nastavnik.organization.educationalorganization.repository.EducationalOrganizationRepository;
import org.donriro.nastavnik.organization.msu.repository.MsuRepository;
import org.donriro.nastavnik.security.principal.CustomUserDetails;
import org.donriro.nastavnik.user.entity.User;
import org.donriro.nastavnik.user.role.RoleCode;
import org.donriro.nastavnik.user.status.AccountStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class RoleAccess {

    private final MsuRepository msuRepository;
    private final EducationalOrganizationRepository educationalOrganizationRepository;
    private final MentorshipRepository mentorshipRepository;

    public boolean isRegionAdmin(Authentication authentication) {
        return hasRole(authentication, RoleCode.REGION_ADMIN);
    }

    public boolean isMsuAdmin(Authentication authentication) {
        return hasRole(authentication, RoleCode.MSU_ADMIN);
    }

    public boolean isOoAdmin(Authentication authentication) {
        return hasRole(authentication, RoleCode.OO_ADMIN);
    }

    public boolean isMentor(Authentication authentication) {
        return hasRole(authentication, RoleCode.MENTOR);
    }

    public boolean isMentee(Authentication authentication) {
        return hasRole(authentication, RoleCode.MENTEE);
    }

    public boolean hasRole(Authentication authentication, RoleCode roleCode) {
        User user = getActiveUser(authentication);
        return user != null && user.getRole() != null && user.getRole().getCode() == roleCode;
    }

    /**
     * REGION_ADMIN может получать доступ к MSU в пределах своего региона.
     * MSU_ADMIN может получать доступ только к своему собственному MSU.
     * Другие роли не получают общего доступа на уровне MSU посредством этого метода.
     */
    public boolean canAccessMsu(Authentication authentication, Long msuId) {
        User user = getActiveUser(authentication);
        if (user == null || msuId == null || user.getRole() == null) {
            return false;
        }

        RoleCode role = user.getRole().getCode();

        if (role == RoleCode.MSU_ADMIN) {
            return user.getMsu() != null && msuId.equals(user.getMsu().getId());
        }

        if (role == RoleCode.REGION_ADMIN) {
            return user.getRegion() != null && msuRepository.existsByIdAndRegionId(msuId, user.getRegion().getId());
        }

        return false;
    }

    /**
     * Доступ на уровне организации:
     * REGION_ADMIN — организации в своем регионе;
     * MSU_ADMIN — организации в своем MSU;
     * OO_ADMIN, MENTOR и MENTEE — только собственная организация.
     * Доступ к записи об организации не предоставляет автоматически доступ
     * к каждому пользователю или каждой программе наставничества, относящимся к этой организации.
     */
    public boolean canAccessOrganization(Authentication authentication, Long organizationId) {

        User user = getActiveUser(authentication);
        if (user == null || organizationId == null || user.getRole() == null) {
            return false;
        }

        RoleCode role = user.getRole().getCode();

        if (role == RoleCode.REGION_ADMIN) {
            return user.getRegion() != null && educationalOrganizationRepository.existsByIdAndMsuRegionId(organizationId, user.getRegion().getId());
        }

        if (role == RoleCode.MSU_ADMIN) {
            return user.getMsu() != null && educationalOrganizationRepository.existsByIdAndMsuId(organizationId, user.getMsu().getId());
        }

        if (role == RoleCode.OO_ADMIN || role == RoleCode.MENTOR || role == RoleCode.MENTEE) {
            return user.getEducationalOrganization() != null && organizationId.equals(user.getEducationalOrganization().getId());
        }

        return false;
    }

    /**
     * Доступ в рамках наставничества более ограничен, чем доступ на уровне организации:
     * возможности администраторов ограничены пределами их административных полномочий,
     * наставники имеют доступ только к собственным записям о наставничестве,
     * подопечные имеют доступ только к собственным записям о наставничестве.
     */
    @Transactional(readOnly = true)
    public boolean canAccessMentorship(Authentication authentication, Long mentorshipId) {

        User user = getActiveUser(authentication);
        if (user == null || mentorshipId == null || user.getRole() == null) {
            return false;
        }

        Mentorship mentorship = mentorshipRepository.findById(mentorshipId).orElse(null);
        if (mentorship == null || mentorship.getMsu() == null) {
            return false;
        }

        return switch (user.getRole().getCode()) {
            case REGION_ADMIN -> user.getRegion() != null
                    && mentorship.getMsu().getRegion() != null
                    && user.getRegion().getId().equals(
                    mentorship.getMsu().getRegion().getId());

            case MSU_ADMIN -> user.getMsu() != null
                    && user.getMsu().getId().equals(
                    mentorship.getMsu().getId());

            case OO_ADMIN -> isInSameOrganization(
                    user, mentorship.getMentor())
                    && isInSameOrganization(user, mentorship.getMentee());

            case MENTOR -> mentorship.getMentor() != null
                    && user.getId().equals(mentorship.getMentor().getId());

            case MENTEE -> mentorship.getMentee() != null
                    && user.getId().equals(mentorship.getMentee().getId());
        };
    }

    private boolean isInSameOrganization(User actor, User participant) {
        return actor.getEducationalOrganization() != null
                && participant != null
                && participant.getEducationalOrganization() != null
                && actor.getEducationalOrganization().getId().equals(
                participant.getEducationalOrganization().getId());
    }

    private User getActiveUser(Authentication authentication) {
        if (authentication == null
                || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof CustomUserDetails userDetails)) {
            return null;
        }

        User user = userDetails.getUser();
        if (user == null
                || user.isBlocked()
                || user.getAccountStatus() != AccountStatus.ACCEPTED
                || user.getRole() == null
                || user.getRole().getCode() == null) {
            return null;
        }

        return user;
    }
}
