package org.donriro.nastavnik.user.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.donriro.nastavnik.user.dto.request.RegistrationRequest;
import org.donriro.nastavnik.user.dto.response.RegistrationResponse;
import org.donriro.nastavnik.user.entity.User;
import org.donriro.nastavnik.user.service.RegistrationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/registration")
@RequiredArgsConstructor
public class RegistrationController {

    private final RegistrationService registrationService;

    @PostMapping
    public ResponseEntity<RegistrationResponse> register(@Valid @RequestBody RegistrationRequest request) {
        User user = registrationService.register(request);
        RegistrationResponse response = RegistrationResponse.from(user);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
