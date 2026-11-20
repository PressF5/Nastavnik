package org.donriro.nastavnik.mentorship.dto;

import java.time.LocalDate;

public record MentorParticipationResponse(
        Long academicYearId,
        String academicYearName,
        LocalDate startDate,
        LocalDate endDate,
        boolean participated
) {
}