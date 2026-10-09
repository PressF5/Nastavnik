package org.donriro.nastavnik.security.service;

import lombok.RequiredArgsConstructor;
import org.donriro.nastavnik.security.config.RefreshTokenRotation;
import org.donriro.nastavnik.security.entity.RefreshToken;
import org.donriro.nastavnik.security.exception.InvalidRefreshTokenException;
import org.donriro.nastavnik.security.repository.RefreshTokenRepository;
import org.donriro.nastavnik.user.entity.User;
import org.donriro.nastavnik.user.status.AccountStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {
    private static final int TOKEN_BYTES = 32;
    private final RefreshTokenRepository refreshTokenRepository;
    private final SecureRandom secureRandom = new SecureRandom();
    @Value("${spring.security.jwt.refresh-token-expiration}")
    private long refreshTokenExpiration;

    @Transactional
    public String createToken(User user) {
        String rawToken = generateRawToken();

        RefreshToken refreshToken = new RefreshToken();

        refreshToken.setUser(user);
        refreshToken.setTokenHash(hashToken(rawToken));
        refreshToken.setExpiresAt(Instant.now().plusMillis(refreshTokenExpiration));

        refreshTokenRepository.save(refreshToken);

        return rawToken;
    }

    public RefreshToken findValidToken(String rawToken) {
        String tokenHash = hashToken(rawToken);

        RefreshToken refreshToken = refreshTokenRepository
                .findByTokenHash(tokenHash)
                .orElseThrow(() -> new InvalidRefreshTokenException("Недействительный refresh token."));

        if (refreshToken.getRevokedAt() != null) {
            throw new InvalidRefreshTokenException("Недействительный refresh token.");
        }

        if (refreshToken.getExpiresAt().isBefore(Instant.now())) {
            throw new InvalidRefreshTokenException("Недействительный refresh token.");
        }

        return refreshToken;
    }

    @Transactional
    public RefreshTokenRotation rotateToken(String rawToken) {
        String tokenHash = hashToken(rawToken);

        RefreshToken currentToken = refreshTokenRepository
                .findByTokenHashForUpdate(tokenHash)
                .orElseThrow(() -> new InvalidRefreshTokenException("Недействительный refresh token."));

        Instant now = Instant.now();

        if (currentToken.getRevokedAt() != null || !currentToken.getExpiresAt().isAfter(now)) {
            throw new InvalidRefreshTokenException("Недействительный refresh token.");
        }

        User user = currentToken.getUser();

        if (user.isBlocked() || user.getAccountStatus() != AccountStatus.ACCEPTED) {
            throw new InvalidRefreshTokenException("Недействительный refresh token.");
        }

        // Отзываем старый токен под блокировкой строки.
        currentToken.setRevokedAt(now);

        // Создаём новый токен в той же транзакции.
        String newRawToken = generateRawToken();

        RefreshToken newToken = new RefreshToken();
        newToken.setUser(user);
        newToken.setTokenHash(hashToken(newRawToken));
        newToken.setExpiresAt(now.plusMillis(refreshTokenExpiration));

        refreshTokenRepository.save(newToken);

        return new RefreshTokenRotation(user.getId(), newRawToken);
    }

    @Transactional
    public void revokeByRawToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }

        String tokenHash = hashToken(rawToken);
        refreshTokenRepository.findByTokenHash(tokenHash)
                .filter(token -> token.getRevokedAt() == null)
                .ifPresent(token -> token.setRevokedAt(Instant.now()));
    }

    private String generateRawToken() {
        byte[] bytes = new byte[TOKEN_BYTES];

        secureRandom.nextBytes(bytes);

        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));

            return bytesToHex(hash);

        } catch (NoSuchAlgorithmException exception) {

            throw new IllegalStateException("SHA-256 недоступен.", exception);
        }
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);

        for (byte b : bytes) {
            result.append(String.format("%02x", b));
        }

        return result.toString();
    }
}
