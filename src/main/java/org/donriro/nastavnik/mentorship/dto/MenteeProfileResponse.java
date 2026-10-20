package org.donriro.nastavnik.mentorship.dto;

import org.donriro.nastavnik.profile.value.dto.ProfileValueResponse;

import java.time.LocalDate;
import java.util.List;

public record MenteeProfileResponse(
        Long id,
        String firstName,
        String lastName,
        String middleName,
        LocalDate birthDate,
        String msuName,
        String educationalOrganizationName,
        MentorshipResponse mentorship,
        List<ProfileValueResponse> profileValues
) {
}
