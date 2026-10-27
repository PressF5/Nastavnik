package org.donriro.nastavnik.organization.msu.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.donriro.nastavnik.organization.educationalorganization.dto.response.EducationalOrganizationResponse;
import org.donriro.nastavnik.organization.educationalorganization.entity.EducationalOrganization;
import org.donriro.nastavnik.organization.msu.dto.request.CreateMsuRequest;
import org.donriro.nastavnik.organization.msu.dto.response.MsuResponse;
import org.donriro.nastavnik.organization.msu.entity.Msu;
import org.donriro.nastavnik.organization.msu.service.MsuService;
import org.donriro.nastavnik.security.principal.CustomUserDetails;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/msus")
@RequiredArgsConstructor
public class MsuController {

    private final MsuService msuService;

    @PostMapping
    public ResponseEntity<MsuResponse> create(@AuthenticationPrincipal CustomUserDetails principal, @Valid @RequestBody CreateMsuRequest request) {
        Msu msu = msuService.create(principal.getUser().getId(), request);
        MsuResponse response = new MsuResponse(msu.getId(), msu.getCode(), msu.getName(), msu.getRegion().getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<Page<MsuResponse>> getAll(
            @AuthenticationPrincipal CustomUserDetails principal,
            @RequestParam(defaultValue = "") String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Page<MsuResponse> response = msuService
                .getPageForRegionAdmin(
                        principal.getUser().getId(),
                        search,
                        page,
                        size)
                .map(this::toResponse);

        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}")
    public MsuResponse update(@AuthenticationPrincipal CustomUserDetails principal, @PathVariable Long id, @Valid @RequestBody CreateMsuRequest request) {
        Msu msu = msuService.update(principal.getUser().getId(), id, request);
        return new MsuResponse(msu.getId(), msu.getCode(), msu.getName(), msu.getRegion().getId());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal CustomUserDetails principal, @PathVariable Long id) {
        msuService.delete(principal.getUser().getId(), id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/current")
    public ResponseEntity<MsuResponse> getCurrentMsu(@AuthenticationPrincipal CustomUserDetails principal) {
        Msu msu = msuService.getCurrentMsuForMsuAdmin(principal.getUser().getId());

        return ResponseEntity.ok(toResponse(msu));
    }

    @GetMapping("/current/educational-organizations")
    public ResponseEntity<Page<EducationalOrganizationResponse>>
    getCurrentMsuEducationalOrganizations(
            @AuthenticationPrincipal CustomUserDetails principal,
            @RequestParam(defaultValue = "") String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        Page<EducationalOrganizationResponse> response = msuService
                .getEducationalOrganizationsForMsuAdmin(principal.getUser().getId(), search, page, size)
                .map(this::toEducationalOrganizationResponse);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<MsuResponse> getById(
            @AuthenticationPrincipal CustomUserDetails principal,
            @PathVariable Long id) {
        Msu msu = msuService.getByIdForRegionAdmin(principal.getUser().getId(), id);

        return ResponseEntity.ok(toResponse(msu));
    }

    @GetMapping("/{id}/educational-organizations")
    public ResponseEntity<Page<EducationalOrganizationResponse>>
    getEducationalOrganizations(
            @AuthenticationPrincipal CustomUserDetails principal,
            @PathVariable Long id,
            @RequestParam(defaultValue = "") String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        Page<EducationalOrganizationResponse> response = msuService
                .getEducationalOrganizationsForRegionAdmin(
                        principal.getUser().getId(),
                        id,
                        search,
                        page,
                        size
                )
                .map(this::toEducationalOrganizationResponse);

        return ResponseEntity.ok(response);
    }

    private MsuResponse toResponse(Msu msu) {
        return new MsuResponse(
                msu.getId(),
                msu.getCode(),
                msu.getName(),
                msu.getRegion().getId()
        );
    }

    private EducationalOrganizationResponse
    toEducationalOrganizationResponse(EducationalOrganization organization) {
        return new EducationalOrganizationResponse(
                organization.getId(),
                organization.getCode(),
                organization.getName(),
                organization.getMsu().getId()
        );
    }
}
