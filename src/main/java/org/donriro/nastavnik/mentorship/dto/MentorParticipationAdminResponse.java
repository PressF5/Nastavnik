package org.donriro.nastavnik.mentorship.dto;

import org.donriro.nastavnik.user.status.AccountStatus;

public record MentorParticipationAdminResponse(
        Long mentorId,
        String fullName,
        String msuName,
        String educationalOrganizationName,
        boolean participated,
        AccountStatus accountStatus,
        boolean blocked
) {
}
