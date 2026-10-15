package org.donriro.nastavnik.matching.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import org.donriro.nastavnik.matching.type.ComparisonType;

import java.math.BigDecimal;

public record UpdateProfileMatchingRuleRequest(

        @NotNull
        ComparisonType comparisonType,

        @NotNull
        @DecimalMin("0.01")
        @DecimalMax("100.00")
        BigDecimal weight,

        @NotNull
        Boolean active

) {
}
