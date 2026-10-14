package org.donriro.nastavnik.profile.options.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.donriro.nastavnik.profile.options.dto.ProfileFieldOptionRequest;
import org.donriro.nastavnik.profile.options.dto.ProfileFieldOptionResponse;
import org.donriro.nastavnik.profile.options.service.ProfileFieldOptionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/profile-fields/{fieldId}/options")
@RequiredArgsConstructor
public class ProfileFieldOptionController {

    private final ProfileFieldOptionService optionService;

    @GetMapping
    public ResponseEntity<List<ProfileFieldOptionResponse>> getAll(@PathVariable Long fieldId) {
        return ResponseEntity.ok(optionService.getAll(fieldId));
    }

    @PostMapping
    public ResponseEntity<ProfileFieldOptionResponse> create(@PathVariable Long fieldId, @Valid @RequestBody ProfileFieldOptionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(optionService.create(fieldId, request));
    }

    @PutMapping("/{optionId}")
    public ResponseEntity<ProfileFieldOptionResponse> update(@PathVariable Long fieldId, @PathVariable Long optionId, @Valid @RequestBody ProfileFieldOptionRequest request) {
        return ResponseEntity.ok(optionService.update(fieldId, optionId, request));
    }

    @PatchMapping("/{optionId}/deactivate")
    public ResponseEntity<Void> deactivate(@PathVariable Long fieldId, @PathVariable Long optionId) {
        optionService.deactivate(fieldId, optionId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/active")
    public ResponseEntity<List<ProfileFieldOptionResponse>> getActiveOptions(@PathVariable Long fieldId) {
        return ResponseEntity.ok(optionService.getActiveOptions(fieldId));
    }

    @PatchMapping("/{optionId}/activate")
    public ResponseEntity<Void> activate(@PathVariable Long fieldId, @PathVariable Long optionId) {
        optionService.activate(fieldId, optionId);
        return ResponseEntity.noContent().build();
    }
}