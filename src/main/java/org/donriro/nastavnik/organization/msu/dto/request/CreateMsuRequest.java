package org.donriro.nastavnik.organization.msu.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CreateMsuRequest(

        @NotNull
        @Positive
        Integer code,

        @NotBlank
        @Size(max = 500)
        String name
) {
}
