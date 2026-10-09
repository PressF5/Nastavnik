package org.donriro.nastavnik.organization.msu.dto.response;

public record MsuResponse(
        Long id,
        Integer code,
        String name,
        Long regionId
) {
}
