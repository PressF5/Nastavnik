package org.donriro.nastavnik.mentorship.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.donriro.nastavnik.matching.dto.CreateProfileMatchingRuleRequest;
import org.donriro.nastavnik.matching.dto.ProfileMatchingRuleResponse;
import org.donriro.nastavnik.matching.dto.UpdateProfileMatchingRuleRequest;
import org.donriro.nastavnik.matching.service.ProfileMatchingRuleService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/profile-matching-rules")
@RequiredArgsConstructor
@PreAuthorize("hasRole('REGION_ADMIN')")
public class ProfileMatchingRuleController {

    private final ProfileMatchingRuleService matchingRuleService;

    @PostMapping
    public ResponseEntity<ProfileMatchingRuleResponse> create(@Valid @RequestBody CreateProfileMatchingRuleRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(matchingRuleService.create(request));
    }

    @GetMapping
    public ResponseEntity<List<ProfileMatchingRuleResponse>> getAll() {
        return ResponseEntity.ok(matchingRuleService.getAll());
    }

    @GetMapping("/active")
    public ResponseEntity<List<ProfileMatchingRuleResponse>> getAllActive() {
        return ResponseEntity.ok(matchingRuleService.getAllActive());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProfileMatchingRuleResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(matchingRuleService.getById(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ProfileMatchingRuleResponse> update(@PathVariable Long id, @Valid @RequestBody UpdateProfileMatchingRuleRequest request) {
        return ResponseEntity.ok(matchingRuleService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deactivate(@PathVariable Long id) {
        matchingRuleService.deactivate(id);
        return ResponseEntity.noContent().build();
    }
}
