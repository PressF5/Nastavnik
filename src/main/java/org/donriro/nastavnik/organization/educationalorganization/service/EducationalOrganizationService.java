package org.donriro.nastavnik.organization.educationalorganization.service;

import lombok.RequiredArgsConstructor;
import org.donriro.nastavnik.organization.educationalorganization.dto.request.CreateEducationalOrganizationRequest;
import org.donriro.nastavnik.organization.educationalorganization.entity.EducationalOrganization;
import org.donriro.nastavnik.organization.educationalorganization.repository.EducationalOrganizationRepository;
import org.donriro.nastavnik.organization.msu.entity.Msu;
import org.donriro.nastavnik.organization.msu.repository.MsuRepository;
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
public class EducationalOrganizationService {

    private final EducationalOrganizationRepository educationalOrganizationRepository;
    private final MsuRepository msuRepository;
    private final UserRepository userRepository;

    /**
     * Создаёт образовательную организацию.
     * REGION_ADMIN:
     * может создать ОО в любом МСУ своего региона;
     * msuId обязательно передаётся в запросе.
     * MSU_ADMIN:
     * может создать ОО только в собственном МСУ;
     * МСУ определяется по учётной записи администратора.
     */
    @Transactional
    @PreAuthorize("@roleAccess.isRegionAdmin(authentication) " + "or @roleAccess.isMsuAdmin(authentication)")
    public EducationalOrganization create(Long actorId, CreateEducationalOrganizationRequest request) {
        User actor = getUser(actorId);
        checkAdministrator(actor);

        Msu msu = resolveMsu(actor, request.msuId());

        // Код ОО должен быть уникальным в пределах одного МСУ.
        if (educationalOrganizationRepository.existsByMsuIdAndCode(msu.getId(), request.code())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "ОО с таким кодом уже существует в данном МСУ.");
        }

        EducationalOrganization organization = new EducationalOrganization();

        organization.setCode(request.code());
        organization.setName(request.name().trim());
        organization.setMsu(msu);

        return educationalOrganizationRepository.save(organization);
    }

    /**
     * Определяет МСУ, в котором создаётся ОО,
     * с учётом роли текущего администратора.
     */
    private Msu resolveMsu(User actor, Long requestedMsuId) {
        RoleCode roleCode = actor.getRole().getCode();

        if (roleCode == RoleCode.MSU_ADMIN) {
            if (actor.getMsu() == null
                    || actor.getRegion() == null
                    || actor.getMsu().getRegion() == null
                    || !actor.getRegion().getId().equals(
                    actor.getMsu().getRegion().getId())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Не удалось определить МСУ администратора.");
            }

            // Администратор МСУ не может выбрать чужое МСУ.
            if (requestedMsuId != null && !requestedMsuId.equals(actor.getMsu().getId())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Создавать ОО можно только в своём МСУ.");
            }

            return actor.getMsu();
        }

        if (roleCode == RoleCode.REGION_ADMIN) {
            if (actor.getRegion() == null) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "У администратора региона не указан регион.");
            }

            if (requestedMsuId == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Для создания ОО необходимо выбрать МСУ.");
            }

            Msu msu = msuRepository.findById(requestedMsuId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "МСУ не найдено."));

            // Региональный администратор работает
            // только с МСУ собственного региона.
            if (msu.getRegion() == null || !actor.getRegion().getId().equals(msu.getRegion().getId())) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "МСУ не найдено.");
            }

            return msu;
        }

        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Недостаточно прав для создания ОО.");
    }

    /**
     * Дополнительная проверка состояния учётной записи.
     */
    private void checkAdministrator(User actor) {
        if (actor.getAccountStatus() != AccountStatus.ACCEPTED
                || actor.isBlocked()
                || actor.getRole() == null
                || actor.getRole().getCode() == null
                || (actor.getRole().getCode() != RoleCode.REGION_ADMIN
                && actor.getRole().getCode() != RoleCode.MSU_ADMIN)) {

            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Создавать ОО может только действующий администратор региона или МСУ.");
        }
    }

    private User getUser(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Пользователь не найден: " + id));
    }
}