package org.donriro.nastavnik.profile.value.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.donriro.nastavnik.profile.value.dto.ProfileValueResponse;
import org.donriro.nastavnik.profile.value.dto.SaveProfileValueRequest;
import org.donriro.nastavnik.profile.value.service.ProfileValueService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/profile-values")
@RequiredArgsConstructor
public class ProfileValueController {

    private final ProfileValueService profileValueService;

    @GetMapping
    public ResponseEntity<List<ProfileValueResponse>> getAllForCurrentUser() {
        return ResponseEntity.ok(profileValueService.getAllForCurrentUser());
    }

    @PutMapping
    public ResponseEntity<ProfileValueResponse> saveForCurrentUser(@Valid @RequestBody SaveProfileValueRequest request) {
        return ResponseEntity.ok(profileValueService.saveForCurrentUser(request));
    }
}
