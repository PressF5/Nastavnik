package org.donriro.nastavnik.mentorship.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.donriro.nastavnik.mentorship.status.MentorshipDecision;

public record MentorshipDecisionRequest(
        @NotNull
        MentorshipDecision decision,

        @Size(max = 2000)
        String comment
) {
}
