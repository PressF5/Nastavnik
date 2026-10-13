package org.donriro.nastavnik.profile.value.dto;

import org.donriro.nastavnik.profile.field.type.ProfileFieldType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record ProfileValueResponse(
        Long id,
        Long profileFieldId,
        String profileFieldName,
        ProfileFieldType fieldType,
        String valueText,
        BigDecimal valueNumber,
        Boolean valueBoolean,
        LocalDate valueDate,
        List<SelectedOptionResponse> selectedOptions,
        Instant createdAt,
        Instant updatedAt

) {
    public record SelectedOptionResponse(
            Long id,
            String value,
            String label
    ) {
    }
}
