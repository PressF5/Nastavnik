package org.donriro.nastavnik.organization.educationalorganization.service;

import lombok.RequiredArgsConstructor;
import org.donriro.nastavnik.organization.educationalorganization.dto.request.CreateEducationalOrganizationRequest;
import org.donriro.nastavnik.organization.educationalorganization.entity.EducationalOrganization;
import org.donriro.nastavnik.organization.educationalorganization.repository.EducationalOrganizationRepository;
import org.donriro.nastavnik.user.entity.User;
import org.donriro.nastavnik.user.repository.UserRepository;
import org.donriro.nastavnik.user.role.RoleCode;
import org.donriro.nastavnik.user.status.AccountStatus;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EducationalOrganizationService {

    private final EducationalOrganizationRepository educationalOrganizationRepository;
    private final UserRepository userRepository;

    @Transactional
    public EducationalOrganization create(Long actorId, CreateEducationalOrganizationRequest request) {
        User actor = getUser(actorId);

        checkMsuAdmin(actor);

        Long msuId = actor.getMsu().getId();

        if (educationalOrganizationRepository.existsByMsuIdAndCode(msuId, request.code())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "ОО с таким кодом уже существует в данном МСУ.");
        }

        EducationalOrganization organization = new EducationalOrganization();

        organization.setCode(request.code());
        organization.setName(request.name().trim());

        // ОО относится к МСУ авторизованного администратора.
        organization.setMsu(actor.getMsu());

        return educationalOrganizationRepository.save(organization);
    }

    private void checkMsuAdmin(User actor) {
        if (actor.getAccountStatus() != AccountStatus.ACCEPTED
                || actor.isBlocked()
                || actor.getRole().getCode() != RoleCode.MSU_ADMIN
                || actor.getMsu() == null
                || actor.getRegion() == null
                || actor.getMsu().getRegion() == null
                || !actor.getRegion().getId().equals(actor.getMsu().getRegion().getId())) {

            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Создавать ОО может только действующий администратор своего МСУ.");
        }
    }

    private User getUser(Long id) {
        return userRepository.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Пользователь не найден: " + id));
    }
}
