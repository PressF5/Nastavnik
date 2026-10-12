package org.donriro.nastavnik.profile.field.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import org.donriro.nastavnik.user.role.RoleCode;

public record ProfileFieldRoleRequest(

        @NotNull
        RoleCode roleCode,

        boolean required,

        @PositiveOrZero
        int sortOrder

) {
}
