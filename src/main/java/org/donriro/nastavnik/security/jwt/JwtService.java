package org.donriro.nastavnik.security.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.donriro.nastavnik.security.principal.CustomUserDetails;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Service
public class JwtService {

    private final SecretKey secretKey;
    private final long accessTokenExpiration;

    public JwtService(@Value("${spring.security.jwt.secret}") String secret, @Value("${spring.security.jwt.access-token-expiration}") long accessTokenExpiration) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenExpiration = accessTokenExpiration;
    }

    public String generateAccessToken(CustomUserDetails userDetails) {
        var user = userDetails.getUser();

        Date issuedAt = new Date();
        Date expiration = new Date(issuedAt.getTime() + accessTokenExpiration);

        var builder = Jwts.builder()
                .subject(user.getEmail())
                .claim("uid", user.getId())
                .claim("role", user.getRole().getCode().name())
                .claim("regionId", user.getRegion().getId())
                .issuedAt(issuedAt)
                .expiration(expiration);

        if (user.getMsu() != null) {
            builder.claim("msuId", user.getMsu().getId());
        }

        if (user.getEducationalOrganization() != null) {
            builder.claim("educationalOrganizationId", user.getEducationalOrganization().getId());
        }

        return builder.signWith(secretKey).compact();
    }

    public Claims parseToken(String token) {
        return Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(token).getPayload();
    }
}