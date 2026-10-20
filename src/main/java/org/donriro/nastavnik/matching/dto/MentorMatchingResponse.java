package org.donriro.nastavnik.matching.dto;

import java.math.BigDecimal;

public record MentorMatchingResponse(
        Long mentorId,
        String firstName,
        String lastName,
        String middleName,
        String educationalOrganizationName,
        BigDecimal compatibilityPercentage,
        int comparableFieldCount
) {
}
