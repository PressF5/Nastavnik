package org.donriro.nastavnik.organization.educationalorganization.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CreateEducationalOrganizationRequest(
        @NotNull
        @Positive
        Integer code,

        @NotBlank
        @Size(max = 500)
        String name
) {
}
