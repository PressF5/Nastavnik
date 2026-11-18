package org.donriro.nastavnik.user.dto.response;

import org.donriro.nastavnik.profile.value.dto.ProfileValueResponse;
import org.donriro.nastavnik.user.entity.User;
import org.donriro.nastavnik.user.role.RoleCode;
import org.donriro.nastavnik.user.status.AccountStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record UserProfileResponse(
        Long id,
        String email,
        String firstName,
        String lastName,
        String middleName,
        LocalDate birthDate,
        RoleCode role,
        AccountStatus accountStatus,
        boolean isBlock,
        String regionName,
        String msuName,
        String educationalOrganizationName,
        Instant createdAt,
        Instant updatedAt,
        List<ProfileValueResponse> profileValues
) {
    public static UserProfileResponse from(User user, List<ProfileValueResponse> profileValues) {
        String msuName = user.getEducationalOrganization() != null
                && user.getEducationalOrganization().getMsu() != null
                ? user.getEducationalOrganization().getMsu().getName()
                : user.getMsu() != null
                ? user.getMsu().getName()
                : null;

        return new UserProfileResponse(
                user.getId(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getMiddleName(),
                user.getBirthDate(),
                user.getRole().getCode(),
                user.getAccountStatus(),
                user.isBlocked(),
                user.getRegion() != null ? user.getRegion().getName() : null,
                msuName,
                user.getEducationalOrganization() != null
                        ? user.getEducationalOrganization().getName()
                        : null,
                user.getCreatedAt(),
                user.getUpdatedAt(),
                profileValues
        );
    }
}
