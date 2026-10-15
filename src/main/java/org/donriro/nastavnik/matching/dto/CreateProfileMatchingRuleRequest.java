package org.donriro.nastavnik.matching.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import org.donriro.nastavnik.matching.type.ComparisonType;

import java.math.BigDecimal;

public record CreateProfileMatchingRuleRequest(

        @NotNull
        Long profileFieldId,

        @NotNull
        ComparisonType comparisonType,

        @NotNull
        @DecimalMin("0.01")
        @DecimalMax("100.00")
        @Digits(integer = 3, fraction = 2)
        BigDecimal weight

) {
}
