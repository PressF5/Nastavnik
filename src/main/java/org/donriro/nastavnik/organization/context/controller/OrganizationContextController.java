package org.donriro.nastavnik.organization.context.controller;

import lombok.RequiredArgsConstructor;
import org.donriro.nastavnik.organization.context.dto.response.OrganizationContextResponse;
import org.donriro.nastavnik.organization.context.service.OrganizationContextService;
import org.donriro.nastavnik.security.principal.CustomUserDetails;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/organization-context")
@RequiredArgsConstructor
public class OrganizationContextController {

    private final OrganizationContextService organizationContextService;

    @GetMapping("/current")
    public ResponseEntity<OrganizationContextResponse> getCurrent(@AuthenticationPrincipal CustomUserDetails principal) {
        return ResponseEntity.ok(organizationContextService.getCurrent(principal.getUser().getId()));
    }
}
