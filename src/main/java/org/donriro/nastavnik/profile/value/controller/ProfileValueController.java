package org.donriro.nastavnik.profile.value.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.donriro.nastavnik.profile.value.dto.ProfileFormFieldResponse;
import org.donriro.nastavnik.profile.value.dto.ProfileValueResponse;
import org.donriro.nastavnik.profile.value.dto.SaveProfileFormRequest;
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

    @GetMapping("/my-form")
    public ResponseEntity<List<ProfileFormFieldResponse>> getMyFormWithValues() {
        return ResponseEntity.ok(profileValueService.getMyFormWithValues());
    }

    @PutMapping("/my-form")
    public ResponseEntity<List<ProfileValueResponse>> saveMyForm(@Valid @RequestBody SaveProfileFormRequest request) {
        return ResponseEntity.ok(profileValueService.saveFormForCurrentUser(request));
    }
}
