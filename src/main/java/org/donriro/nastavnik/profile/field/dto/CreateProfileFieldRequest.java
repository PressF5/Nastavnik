package org.donriro.nastavnik.profile.field.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.donriro.nastavnik.profile.field.type.ProfileFieldType;

import java.util.List;

public record CreateProfileFieldRequest(

        @NotBlank
        @Size(max = 255)
        String name,

        @NotNull
        ProfileFieldType fieldType,

        boolean searchable,

        @NotNull
        @Size(min = 1, max = 2)
        List<@Valid ProfileFieldRoleRequest> roles,

        boolean matchingEnabled,

        @Valid
        List<@Valid ProfileFieldOptionRequest> options

) {
}
