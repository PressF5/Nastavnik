package org.donriro.nastavnik.profile.field.dto;

import org.donriro.nastavnik.profile.field.type.ProfileFieldType;
import org.donriro.nastavnik.user.role.RoleCode;

import java.util.List;

public record ProfileFieldResponse(
        Long id,
        Long version,
        String name,
        ProfileFieldType fieldType,
        boolean searchable,
        boolean active,
        boolean matchingEnabled,
        List<ProfileFieldRoleResponse> roles,
        List<ProfileFieldOptionResponse> options
) {
    public record ProfileFieldRoleResponse(
            RoleCode roleCode,
            boolean required,
            int sortOrder,
            boolean matchingEnabled,
            List<ProfileFieldRoleResponse> roles,
            List<ProfileFieldOptionResponse> options
    ) {
    }
}
