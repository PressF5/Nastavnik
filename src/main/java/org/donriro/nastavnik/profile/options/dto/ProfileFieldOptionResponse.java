package org.donriro.nastavnik.profile.options.dto;

public record ProfileFieldOptionResponse(
        Long id,
        Long profileFieldId,
        String value,
        String label,
        boolean active,
        int sortOrder
) {
}
