package org.donriro.nastavnik.user.controller;

import org.donriro.nastavnik.security.principal.CustomUserDetails;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/test")
public class SecurityTestController {

    @GetMapping("/me")
    public AuthenticationTestResponse me(Authentication authentication) {
        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        return AuthenticationTestResponse.from(userDetails);
    }

    public record AuthenticationTestResponse(Long userId, String email, String role, boolean authenticated) {
        public static AuthenticationTestResponse from(CustomUserDetails userDetails) {
            return new AuthenticationTestResponse(userDetails.getUser().getId(),
                    userDetails.getUser().getEmail(),
                    userDetails.getUser().getRole().getCode().name(),
                    true);
        }
    }
}
