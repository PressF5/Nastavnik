package org.donriro.nastavnik.user.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.donriro.nastavnik.user.role.RoleCode;

import java.time.LocalDate;

public record RegistrationRequest(

        @NotBlank
        @Size(max = 100)
        String firstName,

        @NotBlank
        @Size(max = 100)
        String lastName,

        @Size(max = 100)
        String middleName,

        @NotBlank
        @Email
        @Size(max = 250)
        String email,

        @NotBlank
        @Size(min = 8, max = 100)
        String password,

        @NotNull
        LocalDate birthDate,

        @NotNull
        RoleCode role,

        Long msuId,
        Long educationalOrganizationId
) {
}
