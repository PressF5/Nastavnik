package org.donriro.nastavnik.mentorship.dto;

import org.donriro.nastavnik.mentorship.status.MentorshipStatus;
import org.donriro.nastavnik.user.status.AuditSource;

import java.time.Instant;

public record MentorshipAuditLogResponse(
        Long id,
        String action,
        MentorshipStatus oldStatus,
        MentorshipStatus newStatus,
        String comment,
        String actorName,
        AuditSource source,
        Instant createdAt
) {
}
