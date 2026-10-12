package org.donriro.nastavnik.user.controller;

import lombok.RequiredArgsConstructor;
import org.donriro.nastavnik.security.principal.CustomUserDetails;
import org.donriro.nastavnik.user.dto.response.RegistrationResponse;
import org.donriro.nastavnik.user.entity.User;
import org.donriro.nastavnik.user.role.RoleCode;
import org.donriro.nastavnik.user.service.UserApprovalService;
import org.donriro.nastavnik.user.status.AccountStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class UserApprovalController {
    private final UserApprovalService userApprovalService;

    @GetMapping
    public Page<RegistrationResponse> getUsers(
            @RequestParam(required = false) RoleCode role,
            @RequestParam(required = false) AccountStatus status,
            @RequestParam(required = false) Boolean blocked,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {
        if (page < 0) {
            throw new IllegalArgumentException("Номер страницы не может быть отрицательным.");
        }

        if (size < 1 || size > 100) {
            throw new IllegalArgumentException("Размер страницы должен быть от 1 до 100.");
        }

        CustomUserDetails principal = (CustomUserDetails) authentication.getPrincipal();
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "id"));
        return userApprovalService.getUsers(principal.getUser().getId(), role, status, blocked, pageable).map(RegistrationResponse::from);
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

    @PatchMapping("/{id}/restore-approval")
    public ResponseEntity<RegistrationResponse> restoreApproval(@AuthenticationPrincipal CustomUserDetails principal, @PathVariable Long id) {
        User user = userApprovalService.restoreApproval(principal.getUser().getId(), id);
        return ResponseEntity.ok(RegistrationResponse.from(user));
    }

    @PatchMapping("/{id}/block")
    public ResponseEntity<RegistrationResponse> block(@AuthenticationPrincipal CustomUserDetails principal, @PathVariable Long id) {
        User user = userApprovalService.blockUser(principal.getUser().getId(), id);
        return ResponseEntity.ok(RegistrationResponse.from(user));
    }

    @PatchMapping("/{id}/unblock")
    public ResponseEntity<RegistrationResponse> unblock(@AuthenticationPrincipal CustomUserDetails principal, @PathVariable Long id) {
        User user = userApprovalService.unblockUser(principal.getUser().getId(), id);
        return ResponseEntity.ok(RegistrationResponse.from(user));
    }
}
