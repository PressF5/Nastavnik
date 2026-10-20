package org.donriro.nastavnik.mentorship.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EndMentorshipRequest(
        @NotBlank(message = "Необходимо указать причину завершения наставничества.")
        @Size(max = 2000, message = "Причина не должна превышать 2000 символов.")
        String reason
) {
}
