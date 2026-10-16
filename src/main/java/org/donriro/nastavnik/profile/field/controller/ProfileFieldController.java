package org.donriro.nastavnik.profile.field.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.donriro.nastavnik.profile.field.dto.CreateProfileFieldRequest;
import org.donriro.nastavnik.profile.field.dto.ProfileFieldResponse;
import org.donriro.nastavnik.profile.field.dto.UpdateProfileFieldRequest;
import org.donriro.nastavnik.profile.field.service.ProfileFieldService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/profile-fields")
@RequiredArgsConstructor
public class ProfileFieldController {
    private final ProfileFieldService profileFieldService;

    @GetMapping
    public ResponseEntity<List<ProfileFieldResponse>> getAll() {
        return ResponseEntity.ok(profileFieldService.getAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProfileFieldResponse> getById(@PathVariable Long id) {
        ProfileFieldResponse response = profileFieldService.getById(id);
        return ResponseEntity.ok(response);
    }

    @PreAuthorize("hasRole('REGION_ADMIN')")
    @PostMapping
    public ResponseEntity<ProfileFieldResponse> create(@Valid @RequestBody CreateProfileFieldRequest request) {
        ProfileFieldResponse response = profileFieldService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PreAuthorize("hasRole('REGION_ADMIN')")
    @PutMapping("/{id}")
    public ResponseEntity<ProfileFieldResponse> update(@PathVariable Long id, @Valid @RequestBody UpdateProfileFieldRequest request) {
        ProfileFieldResponse response = profileFieldService.update(id, request);
        return ResponseEntity.ok(response);
    }

    @PreAuthorize("hasRole('REGION_ADMIN')")
    @PatchMapping("/{id}/activate")
    public ResponseEntity<Void> activate(@PathVariable Long id) {
        profileFieldService.activate(id);
        return ResponseEntity.noContent().build();
    }

    @PreAuthorize("hasRole('REGION_ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deactivate(@PathVariable Long id) {
        profileFieldService.deactivate(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/my-form")
    public ResponseEntity<List<ProfileFieldResponse>> getMyForm() {
        return ResponseEntity.ok(profileFieldService.getMyForm());
    }
}
