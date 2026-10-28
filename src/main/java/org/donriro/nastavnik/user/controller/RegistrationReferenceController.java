package org.donriro.nastavnik.user.controller;

import lombok.RequiredArgsConstructor;
import org.donriro.nastavnik.user.dto.response.RegistrationOptionResponse;
import org.donriro.nastavnik.user.role.RoleCode;
import org.donriro.nastavnik.user.service.RegistrationReferenceService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/registration")
@RequiredArgsConstructor
public class RegistrationReferenceController {

    private final RegistrationReferenceService registrationReferenceService;

    /**
     * GET /api/registration/msus?role=MSU_ADMIN
     */
    @GetMapping("/msus")
    public List<RegistrationOptionResponse> getMsus(@RequestParam RoleCode role) {
        return registrationReferenceService.getMsus(role);
    }

    /**
     * GET /api/registration/msus/{msuId}/educational-organizations?role=OO_ADMIN
     */
    @GetMapping("/msus/{msuId}/educational-organizations")
    public List<RegistrationOptionResponse> getEducationalOrganizations(@PathVariable Long msuId, @RequestParam RoleCode role) {
        return registrationReferenceService.getEducationalOrganizations(msuId, role);
    }
}
