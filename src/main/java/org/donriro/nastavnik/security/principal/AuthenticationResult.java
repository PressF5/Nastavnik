package org.donriro.nastavnik.security.principal;

import org.donriro.nastavnik.user.entity.User;

public record AuthenticationResult(
        User user,
        String accessToken,
        String refreshToken
) {}
