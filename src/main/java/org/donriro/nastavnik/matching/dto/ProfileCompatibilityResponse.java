package org.donriro.nastavnik.matching.dto;

import java.math.BigDecimal;
import java.util.List;

public record ProfileCompatibilityResponse(
        Long mentorId,
        Long menteeId,
        BigDecimal compatibilityPercentage,
        int comparableFieldCount,
        int skippedFieldCount,
        List<FieldCompatibility> details
) {
    public record FieldCompatibility(
            Long profileFieldId,
            String profileFieldName,
            String comparisonType,
            BigDecimal weight,
            BigDecimal similarityPercentage
    ) {
    }
}
