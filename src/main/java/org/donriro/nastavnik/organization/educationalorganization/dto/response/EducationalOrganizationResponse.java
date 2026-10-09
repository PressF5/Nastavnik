package org.donriro.nastavnik.organization.educationalorganization.dto.response;

public record EducationalOrganizationResponse(
        Long id,
        Integer code,
        String name,
        Long msuId
) {
}
