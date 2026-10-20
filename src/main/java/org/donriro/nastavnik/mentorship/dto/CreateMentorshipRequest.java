package org.donriro.nastavnik.mentorship.dto;

import jakarta.validation.constraints.NotNull;

public record CreateMentorshipRequest(
        @NotNull Long menteeId,
        @NotNull Long mentorId
) {
}
