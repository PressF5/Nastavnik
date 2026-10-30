package org.donriro.nastavnik.user.dto.response;

import org.donriro.nastavnik.user.entity.User;
import org.donriro.nastavnik.user.role.RoleCode;
import org.donriro.nastavnik.user.status.AccountStatus;

public record RegistrationResponse(
        Long id,
        String email,
        String firstName,
        String lastName,
        String middleName,
        boolean isBlock,
        RoleCode role,
        AccountStatus accountStatus,
        String educationalOrganizationName,
        String msuName
) {

    public static RegistrationResponse from(User user) {
        String educationalOrganizationName = user.getEducationalOrganization() != null
                ? user.getEducationalOrganization().getName()
                : null;

        // Для пользователей, связанных с ОО, получаем МСУ через ОО.
        // Для остальных ролей используем прямую связь User.msu.
        String msuName = user.getEducationalOrganization() != null
                && user.getEducationalOrganization().getMsu() != null
                ? user.getEducationalOrganization().getMsu().getName()
                : user.getMsu() != null
                ? user.getMsu().getName()
                : null;

        return new RegistrationResponse(
                user.getId(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getMiddleName(),
                user.isBlocked(),
                user.getRole().getCode(),
                user.getAccountStatus(),
                educationalOrganizationName,
                msuName
        );
    }
}
