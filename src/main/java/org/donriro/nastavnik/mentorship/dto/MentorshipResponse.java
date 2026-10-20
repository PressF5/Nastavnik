package org.donriro.nastavnik.mentorship.dto;

import org.donriro.nastavnik.mentorship.status.MentorshipStatus;

import java.time.Instant;

public record MentorshipResponse(
        Long id,

        Long mentorId,
        String mentorFirstName,
        String mentorLastName,
        String mentorMiddleName,

        Long menteeId,
        String menteeFirstName,
        String menteeLastName,
        String menteeMiddleName,

        Long academicYearId,
        String academicYearName,

        MentorshipStatus status,

        Instant createdAt,
        Instant startedAt,
        Instant endedAt
) {
}
