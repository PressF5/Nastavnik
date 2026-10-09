package org.donriro.nastavnik.security.config;

public record RefreshTokenRotation(
        Long userId,
        String refreshToken
) {}
