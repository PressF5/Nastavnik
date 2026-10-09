package org.donriro.nastavnik.organization.msu.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.donriro.nastavnik.organization.msu.dto.request.CreateMsuRequest;
import org.donriro.nastavnik.organization.msu.dto.response.MsuResponse;
import org.donriro.nastavnik.organization.msu.entity.Msu;
import org.donriro.nastavnik.organization.msu.service.MsuService;
import org.donriro.nastavnik.security.principal.CustomUserDetails;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
}
