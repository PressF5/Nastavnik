package org.donriro.nastavnik.user.service;

import lombok.RequiredArgsConstructor;
import org.donriro.nastavnik.organization.educationalorganization.repository.EducationalOrganizationRepository;
import org.donriro.nastavnik.organization.msu.repository.MsuRepository;
import org.donriro.nastavnik.user.dto.response.RegistrationOptionResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RegistrationReferenceService {

    private final MsuRepository msuRepository;
    private final EducationalOrganizationRepository educationalOrganizationRepository;

    /**
     * Возвращает список МСУ для регистрации.
     */
    public List<RegistrationOptionResponse> getMsus() {
        return msuRepository.findAllByOrderByNameAsc()
                .stream()
                .map(msu -> new RegistrationOptionResponse(msu.getId(), msu.getName()))
                .toList();
    }

    /**
     * Возвращает образовательные организации выбранного МСУ.
     */
    public List<RegistrationOptionResponse> getEducationalOrganizations(Long msuId) {
        if (!msuRepository.existsById(msuId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "МСУ не найдено.");
        }

        return educationalOrganizationRepository
                .findAllByMsuIdOrderByNameAsc(msuId)
                .stream()
                .map(organization -> new RegistrationOptionResponse(organization.getId(), organization.getName()))
                .toList();
    }
}
