package org.donriro.nastavnik.profile.value.dto;

import org.donriro.nastavnik.profile.field.dto.ProfileFieldResponse;

public record ProfileFormFieldResponse(
        ProfileFieldResponse field,
        ProfileValueResponse savedValue
) {
}
