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
        RoleCode role,
        AccountStatus accountStatus
) {

    public static RegistrationResponse from(User user) {
        return new RegistrationResponse(
                user.getId(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getMiddleName(),
                user.getRole().getCode(),
                user.getAccountStatus()
        );
    }
}
