package org.donriro.nastavnik.user.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.donriro.nastavnik.user.dto.request.RegionAdminBootstrapRequest;
import org.donriro.nastavnik.user.dto.response.BootstrapStatusResponse;
import org.donriro.nastavnik.user.dto.response.RegistrationResponse;
import org.donriro.nastavnik.user.entity.User;
import org.donriro.nastavnik.user.service.BootstrapService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/bootstrap")
@RequiredArgsConstructor
public class BootstrapController {

    private final BootstrapService bootstrapService;

    @GetMapping("/status")
    public ResponseEntity<BootstrapStatusResponse> getStatus() {
        BootstrapStatusResponse response = bootstrapService.getStatus();
        return ResponseEntity.ok(response);
    }

    @PostMapping("/region-admin")
    public ResponseEntity<RegistrationResponse> createRegionAdmin(@Valid @RequestBody RegionAdminBootstrapRequest request) {
        User user = bootstrapService.createRegionAdmin(request);
        RegistrationResponse response = RegistrationResponse.from(user);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
