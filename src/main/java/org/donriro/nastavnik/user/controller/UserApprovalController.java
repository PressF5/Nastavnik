package org.donriro.nastavnik.user.controller;

import lombok.RequiredArgsConstructor;
import org.donriro.nastavnik.security.principal.CustomUserDetails;
import org.donriro.nastavnik.user.dto.response.RegistrationResponse;
import org.donriro.nastavnik.user.entity.User;
import org.donriro.nastavnik.user.service.UserApprovalService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class UserApprovalController {
    private final UserApprovalService userApprovalService;

    @GetMapping("/pending")
    public ResponseEntity<List<RegistrationResponse>> getPendingUsers(@AuthenticationPrincipal CustomUserDetails principal) {
        List<RegistrationResponse> response = userApprovalService
                .getPendingUsers(principal.getUser().getId())
                .stream()
                .map(RegistrationResponse::from)
                .toList();
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{id}/approve")
    public ResponseEntity<RegistrationResponse> approve(@AuthenticationPrincipal CustomUserDetails principal, @PathVariable Long id) {
        User user = userApprovalService.approve(principal.getUser().getId(), id);
        return ResponseEntity.ok(RegistrationResponse.from(user));
    }

    @PatchMapping("/{id}/reject")
    public ResponseEntity<RegistrationResponse> reject(@AuthenticationPrincipal CustomUserDetails principal, @PathVariable Long id) {
        User user = userApprovalService.reject(principal.getUser().getId(), id);
        return ResponseEntity.ok(RegistrationResponse.from(user));
    }
}
