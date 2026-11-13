package org.donriro.nastavnik.mentorship.dto;

import org.donriro.nastavnik.profile.value.dto.ProfileValueResponse;
import java.util.List;

public record MentorProfileResponse(
        Long id,
        String firstName,
        String lastName,
        String middleName,
        String regionName,
        String msuName,
        String educationalOrganizationName,
        MentorshipResponse mentorship,
        List<ProfileValueResponse> profileValues
) {
}
