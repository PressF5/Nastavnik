package org.donriro.nastavnik.profile.field.dto;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record SaveProfileValueRequest(

        @NotNull
        Long profileFieldId,

        String valueText,

        BigDecimal valueNumber,

        Boolean valueBoolean,

        LocalDate valueDate,

        List<@NotNull Long> optionIds

) {
}
