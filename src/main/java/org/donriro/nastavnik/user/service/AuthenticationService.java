package org.donriro.nastavnik.user.service;

import lombok.RequiredArgsConstructor;
import org.donriro.nastavnik.security.config.RefreshTokenRotation;
import org.donriro.nastavnik.security.exception.InvalidRefreshTokenException;
import org.donriro.nastavnik.security.service.RefreshTokenService;
import org.donriro.nastavnik.user.dto.request.LoginRequest;
import org.donriro.nastavnik.user.entity.User;
import org.donriro.nastavnik.user.exception.InvalidCredentialsException;
import org.donriro.nastavnik.security.principal.AuthenticationResult;
import org.donriro.nastavnik.security.principal.CustomUserDetails;
import org.donriro.nastavnik.security.jwt.JwtService;
import org.donriro.nastavnik.user.repository.UserRepository;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthenticationService {
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final UserRepository userRepository;

    @Transactional
    public AuthenticationResult authenticate(LoginRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);

        try {
            var authentication = authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(email, request.password()));

            CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
            User user = userDetails.getUser();

            String accessToken = jwtService.generateAccessToken(userDetails);
            String refreshToken = refreshTokenService.createToken(user);

            return new AuthenticationResult(user, accessToken, refreshToken);
        } catch (AuthenticationException exception) {
            throw new InvalidCredentialsException("Неверный email или пароль.");
        }
    }

    @Transactional
    public AuthenticationResult refresh(String rawRefreshToken) {
        RefreshTokenRotation rotation = refreshTokenService.rotateToken(rawRefreshToken);

        User user = userRepository.findById(rotation.userId()).orElseThrow(() -> new InvalidRefreshTokenException("Пользователь не найден."));
        CustomUserDetails userDetails = new CustomUserDetails(user);

        String accessToken = jwtService.generateAccessToken(userDetails);

        return new AuthenticationResult(user, accessToken, rotation.refreshToken());
    }

    public void logout(String rawRefreshToken) {
        refreshTokenService.revokeByRawToken(rawRefreshToken);
    }
}
