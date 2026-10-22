package org.donriro.nastavnik.user.controller;

import lombok.RequiredArgsConstructor;
import org.donriro.nastavnik.user.dto.response.RegistrationOptionResponse;
import org.donriro.nastavnik.user.service.RegistrationReferenceService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/registration")
@RequiredArgsConstructor
public class RegistrationReferenceController {

    private final RegistrationReferenceService registrationReferenceService;

    /**
     * GET /api/registration/msus
     *
     * Список МСУ для формы регистрации.
     */
    @GetMapping("/msus")
    public List<RegistrationOptionResponse> getMsus() {
        return registrationReferenceService.getMsus();
    }

    /**
     * GET /api/registration/msus/{msuId}/educational-organizations
     *
     * Список ОО, принадлежащих выбранному МСУ.
     */
    @GetMapping("/msus/{msuId}/educational-organizations")
    public List<RegistrationOptionResponse> getEducationalOrganizations(@PathVariable Long msuId) {
        return registrationReferenceService.getEducationalOrganizations(msuId);
    }
}
