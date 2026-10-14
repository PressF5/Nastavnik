package org.donriro.nastavnik.profile.options.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record ProfileFieldOptionRequest(

        @NotBlank
        @Size(max = 255)
        String value,

        @NotBlank
        @Size(max = 255)
        String label,

        @PositiveOrZero
        int sortOrder
) {
}
