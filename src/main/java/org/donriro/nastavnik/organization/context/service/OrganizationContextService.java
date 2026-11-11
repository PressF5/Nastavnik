package org.donriro.nastavnik.organization.context.service;

import lombok.RequiredArgsConstructor;
import org.donriro.nastavnik.organization.context.dto.response.OrganizationContextResponse;
import org.donriro.nastavnik.organization.context.dto.response.OrganizationUnitResponse;
import org.donriro.nastavnik.organization.educationalorganization.entity.EducationalOrganization;
import org.donriro.nastavnik.organization.msu.entity.Msu;
import org.donriro.nastavnik.organization.region.entity.Region;
import org.donriro.nastavnik.user.entity.User;
import org.donriro.nastavnik.user.repository.UserRepository;
import org.donriro.nastavnik.user.role.RoleCode;
import org.donriro.nastavnik.user.status.AccountStatus;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrganizationContextService {

    private final UserRepository userRepository;

    @PreAuthorize("@roleAccess.isRegionAdmin(authentication) or @roleAccess.isMsuAdmin(authentication) or @roleAccess.isOoAdmin(authentication)")
    public OrganizationContextResponse getCurrent(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Пользователь не найден."));

        checkActiveAccount(user);

        Region region = user.getRegion();

        if (region == null) {
            throw forbidden();
        }

        OrganizationUnitResponse regionResponse = toResponse(region);

        RoleCode role = user.getRole().getCode();

        return switch (role) {
            case REGION_ADMIN -> new OrganizationContextResponse(regionResponse, null, null);
            case MSU_ADMIN -> {
                Msu msu = user.getMsu();
                if (msu == null || msu.getRegion() == null || !region.getId().equals(msu.getRegion().getId())) {
                    throw forbidden();
                }

                yield new OrganizationContextResponse(regionResponse, toResponse(msu), null);
            }
            case OO_ADMIN -> {
                EducationalOrganization organization = user.getEducationalOrganization();
                if (organization == null || organization.getMsu() == null) {
                    throw forbidden();
                }

                Msu msu = organization.getMsu();
                if (msu.getRegion() == null
                        || user.getMsu() == null
                        || !user.getMsu().getId().equals(msu.getId())
                        || !region.getId().equals(msu.getRegion().getId())) {
                    throw forbidden();
                }

                yield new OrganizationContextResponse(regionResponse, toResponse(msu), toResponse(organization));
            }
            default -> throw forbidden();
        };
    }

    private void checkActiveAccount(User user) {
        if (user.getAccountStatus() != AccountStatus.ACCEPTED || user.isBlocked() || user.getRole() == null) {
            throw forbidden();
        }
    }

    private OrganizationUnitResponse toResponse(Region region) {
        return new OrganizationUnitResponse(region.getId(), region.getCode(), region.getName());
    }

    private OrganizationUnitResponse toResponse(Msu msu) {
        return new OrganizationUnitResponse(msu.getId(), msu.getCode(), msu.getName());
    }

    private OrganizationUnitResponse toResponse(EducationalOrganization organization) {
        return new OrganizationUnitResponse(organization.getId(), organization.getCode(), organization.getName());
    }

    private ResponseStatusException forbidden() {
        return new ResponseStatusException(HttpStatus.FORBIDDEN, "Недостаточно прав или некорректно указана организационная структура.");
    }
}