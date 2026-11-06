package org.donriro.nastavnik.matching.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import org.donriro.nastavnik.matching.type.ComparisonType;

import java.math.BigDecimal;

public record UpdateProfileMatchingRuleRequest(

        @NotNull
        ComparisonType comparisonType,

        @NotNull
        @DecimalMin("1")
        @DecimalMax("10")
        @Digits(integer = 2, fraction = 0)
        BigDecimal weight,

        @NotNull
        Boolean active

) {
}
