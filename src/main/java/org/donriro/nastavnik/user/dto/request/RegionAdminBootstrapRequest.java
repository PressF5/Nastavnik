package org.donriro.nastavnik.user.dto.request;

import jakarta.validation.constraints.*;

import java.time.LocalDate;

public record RegionAdminBootstrapRequest(

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

        @PastOrPresent
        @NotNull
        LocalDate birthDate,

        @NotNull
        @Positive
        Integer regionCode,

        @NotBlank
        @Size(max = 500)
        String regionName
) {
}
