package org.donriro.nastavnik.user.dto.response;

/**
 * Краткая информация для выпадающих списков регистрации.
 */
public record RegistrationOptionResponse(
        Long id,
        String name
) {
}
