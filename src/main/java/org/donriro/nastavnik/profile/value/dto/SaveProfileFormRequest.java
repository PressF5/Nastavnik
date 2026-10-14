package org.donriro.nastavnik.profile.value.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record SaveProfileFormRequest(
        @NotEmpty(message = "Анкета не должна быть пустой.")
        List<@Valid SaveProfileValueRequest> values
) {
}
