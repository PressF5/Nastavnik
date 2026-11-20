package org.donriro.nastavnik.mentorship.dto;

import jakarta.validation.constraints.NotNull;

public record UpdateMentorParticipationRequest(
        @NotNull Boolean participated
) {
}