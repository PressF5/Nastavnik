package org.donriro.nastavnik.organization.context.dto.response;

public record OrganizationContextResponse(
        OrganizationUnitResponse region,
        OrganizationUnitResponse msu,
        OrganizationUnitResponse educationalOrganization
) {
}
