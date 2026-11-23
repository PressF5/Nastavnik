package org.donriro.nastavnik.mentorship.dto;

import org.donriro.nastavnik.mentorship.status.AcademicYearStatus;

import java.time.LocalDate;

/**
 * Информация об учебном годе.
 */
public record AcademicYearResponse(

        Long id,
        String name,
        boolean published,
        LocalDate startDate,
        LocalDate endDate,
        AcademicYearStatus status
) {
}
