package org.donriro.nastavnik.event.dto;

import jakarta.validation.constraints.NotNull;

public record ChangeEventStatusRequest(
        @NotNull(message = "Статус активности обязателен.")
        Boolean active
) {
}