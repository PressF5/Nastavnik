package org.donriro.nastavnik.organization.educationalorganization.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.donriro.nastavnik.organization.educationalorganization.dto.request.CreateEducationalOrganizationRequest;
import org.donriro.nastavnik.organization.educationalorganization.dto.request.UpdateEducationalOrganizationRequest;
import org.donriro.nastavnik.organization.educationalorganization.dto.response.EducationalOrganizationResponse;
import org.donriro.nastavnik.organization.educationalorganization.entity.EducationalOrganization;
import org.donriro.nastavnik.organization.educationalorganization.service.EducationalOrganizationService;
import org.donriro.nastavnik.security.principal.CustomUserDetails;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/educational-organizations")
@RequiredArgsConstructor
public class EducationalOrganizationController {

    private final EducationalOrganizationService educationalOrganizationService;

    @PostMapping
    public ResponseEntity<EducationalOrganizationResponse> create(@AuthenticationPrincipal CustomUserDetails principal,
                                                                  @Valid @RequestBody CreateEducationalOrganizationRequest request) {
        EducationalOrganization organization = educationalOrganizationService.create(principal.getUser().getId(), request);
        EducationalOrganizationResponse response = new EducationalOrganizationResponse(organization.getId(),
                organization.getCode(), organization.getName(), organization.getMsu().getId(), organization.getMsu().getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<EducationalOrganizationResponse> update(@AuthenticationPrincipal CustomUserDetails principal, @PathVariable Long id, @Valid @RequestBody UpdateEducationalOrganizationRequest request) {
        EducationalOrganization organization = educationalOrganizationService.update(principal.getUser().getId(), id, request);
        return ResponseEntity.ok(
                new EducationalOrganizationResponse(
                        organization.getId(),
                        organization.getCode(),
                        organization.getName(),
                        organization.getMsu().getId(),
                        organization.getMsu().getName()
                )
        );
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal CustomUserDetails principal, @PathVariable Long id) {
        educationalOrganizationService.delete(principal.getUser().getId(), id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public ResponseEntity<Page<EducationalOrganizationResponse>> getAllForRegion(
            @AuthenticationPrincipal CustomUserDetails principal,
            @RequestParam(defaultValue = "") String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Page<EducationalOrganizationResponse> response = educationalOrganizationService.getPageForRegionAdmin(principal.getUser().getId(), search, page, size)
                .map(organization -> new EducationalOrganizationResponse(
                        organization.getId(),
                        organization.getCode(),
                        organization.getName(),
                        organization.getMsu().getId(),
                        organization.getMsu().getName()
                ));
        return ResponseEntity.ok(response);
    }
}