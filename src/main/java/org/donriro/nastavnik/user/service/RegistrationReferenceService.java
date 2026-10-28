package org.donriro.nastavnik.user.service;

import lombok.RequiredArgsConstructor;
import org.donriro.nastavnik.organization.educationalorganization.repository.EducationalOrganizationRepository;
import org.donriro.nastavnik.organization.msu.repository.MsuRepository;
import org.donriro.nastavnik.user.dto.response.RegistrationOptionResponse;
import org.donriro.nastavnik.user.exception.InvalidRegistrationRoleException;
import org.donriro.nastavnik.user.role.RoleCode;
import org.donriro.nastavnik.user.status.AccountStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RegistrationReferenceService {

    private static final List<AccountStatus> ACTIVE_ADMIN_STATUSES = List.of(AccountStatus.PENDING, AccountStatus.ACCEPTED);
    private final MsuRepository msuRepository;
    private final EducationalOrganizationRepository educationalOrganizationRepository;

    /**
     * Возвращает МСУ в зависимости от выбранной роли.
     */
    public List<RegistrationOptionResponse> getMsus(RoleCode role) {
        return switch (role) {
            case MSU_ADMIN -> msuRepository
                    .findAvailableForMsuAdmin(RoleCode.MSU_ADMIN, ACTIVE_ADMIN_STATUSES)
                    .stream()
                    .map(msu -> new RegistrationOptionResponse(msu.getId(), msu.getName()))
                    .toList();

            case OO_ADMIN -> msuRepository
                    .findWithAvailableEducationalOrganizations(RoleCode.OO_ADMIN, ACTIVE_ADMIN_STATUSES)
                    .stream()
                    .map(msu -> new RegistrationOptionResponse(msu.getId(), msu.getName()))
                    .toList();

            case MENTOR, MENTEE -> msuRepository
                    .findAllByOrderByNameAsc()
                    .stream()
                    .map(msu -> new RegistrationOptionResponse(msu.getId(), msu.getName()))
                    .toList();

            case REGION_ADMIN -> throw new InvalidRegistrationRoleException("Регистрация REGION_ADMIN через обычную регистрацию запрещена.");
        };
    }

    /**
     * Возвращает ОО выбранного МСУ.
     */
    public List<RegistrationOptionResponse> getEducationalOrganizations(Long msuId, RoleCode role) {
        return switch (role) {
            case OO_ADMIN -> educationalOrganizationRepository
                    .findAvailableForOoAdmin(msuId, RoleCode.OO_ADMIN, ACTIVE_ADMIN_STATUSES)
                    .stream()
                    .map(organization -> new RegistrationOptionResponse(organization.getId(), organization.getName()))
                    .toList();

            case MENTOR, MENTEE -> educationalOrganizationRepository
                    .findAllByMsuIdOrderByNameAsc(msuId)
                    .stream()
                    .map(organization -> new RegistrationOptionResponse(organization.getId(), organization.getName()))
                    .toList();

            case MSU_ADMIN -> throw new InvalidRegistrationRoleException("Для MSU_ADMIN выбор образовательной организации не предусмотрен.");

            case REGION_ADMIN -> throw new InvalidRegistrationRoleException("Регистрация REGION_ADMIN через обычную регистрацию запрещена.");
        };
    }
}
