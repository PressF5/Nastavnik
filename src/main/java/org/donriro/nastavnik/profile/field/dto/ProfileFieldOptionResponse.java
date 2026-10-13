package org.donriro.nastavnik.profile.field.dto;

public record ProfileFieldOptionResponse(
        Long id,
        Long profileFieldId,
        String value,
        String label,
        boolean active,
        int sortOrder
) {
}
