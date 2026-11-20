package org.donriro.nastavnik.mentorship.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Запрос на создание учебного года.
 */
public record CreateAcademicYearRequest(

        @NotBlank(message = "Название учебного года обязательно.")
        @Size(max = 20, message = "Название учебного года не должно превышать 20 символов.")
        String name,

        @NotNull(message = "Дата начала учебного года обязательна.")
        LocalDate startDate,

        @NotNull(message = "Дата окончания учебного года обязательна.")
        LocalDate endDate
) {
}
