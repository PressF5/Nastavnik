package org.donriro.nastavnik.mentorship.dto;

/**
 * Данные наставника для ручного назначения.
 */
public record AvailableMentorResponse(
        Long mentorId,
        String firstName,
        String lastName,
        String middleName,
        String educationalOrganizationName
) {
}
