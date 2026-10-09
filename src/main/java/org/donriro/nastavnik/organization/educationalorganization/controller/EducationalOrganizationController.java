package org.donriro.nastavnik.organization.educationalorganization.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.donriro.nastavnik.organization.educationalorganization.dto.request.CreateEducationalOrganizationRequest;
import org.donriro.nastavnik.organization.educationalorganization.dto.response.EducationalOrganizationResponse;
import org.donriro.nastavnik.organization.educationalorganization.entity.EducationalOrganization;
import org.donriro.nastavnik.organization.educationalorganization.service.EducationalOrganizationService;
import org.donriro.nastavnik.security.principal.CustomUserDetails;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
                organization.getCode(), organization.getName(), organization.getMsu().getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}