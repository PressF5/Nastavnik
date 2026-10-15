package org.donriro.nastavnik.matching.dto;

import org.donriro.nastavnik.matching.type.ComparisonType;

import java.math.BigDecimal;

public record ProfileMatchingRuleResponse(
        Long id,
        Long profileFieldId,
        String profileFieldName,
        ComparisonType comparisonType,
        BigDecimal weight,
        boolean active
) {
}
