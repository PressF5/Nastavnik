package org.donriro.nastavnik.organization.educationalorganization.service;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.donriro.nastavnik.organization.educationalorganization.dto.request.CreateEducationalOrganizationRequest;
import org.donriro.nastavnik.organization.educationalorganization.dto.request.UpdateEducationalOrganizationRequest;
import org.donriro.nastavnik.organization.educationalorganization.entity.EducationalOrganization;
import org.donriro.nastavnik.organization.educationalorganization.repository.EducationalOrganizationRepository;
import org.donriro.nastavnik.organization.msu.entity.Msu;
import org.donriro.nastavnik.organization.msu.repository.MsuRepository;
import org.donriro.nastavnik.user.entity.User;
import org.donriro.nastavnik.user.repository.UserRepository;
import org.donriro.nastavnik.user.role.RoleCode;
import org.donriro.nastavnik.user.status.AccountStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

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
     * Редактирует код и название образовательной организации.
     * МСУ остаётся прежним.
     */
    @Transactional
    @PreAuthorize("@roleAccess.isRegionAdmin(authentication) or @roleAccess.isMsuAdmin(authentication)")
    public EducationalOrganization update(Long actorId, Long organizationId, UpdateEducationalOrganizationRequest request) {
        User actor = getUser(actorId);
        checkAdministrator(actor);

        EducationalOrganization organization = getOrganization(organizationId);

        checkOrganizationAccess(actor, organization);

        Long msuId = organization.getMsu().getId();

        if (educationalOrganizationRepository.existsByMsuIdAndCodeAndIdNot(msuId, request.code(), organizationId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "ОО с таким кодом уже существует в данном МСУ.");
        }

        organization.setCode(request.code());
        organization.setName(request.name().trim());

        return educationalOrganizationRepository.save(organization);
    }

    /**
     * Удаляет ОО, если к ней не прикреплены пользователи.
     */
    @Transactional
    @PreAuthorize("@roleAccess.isRegionAdmin(authentication) " + "or @roleAccess.isMsuAdmin(authentication)")
    public void delete(Long actorId, Long organizationId) {
        User actor = getUser(actorId);
        checkAdministrator(actor);

        EducationalOrganization organization = getOrganization(organizationId);

        checkOrganizationAccess(actor, organization);

        if (userRepository.existsByEducationalOrganizationId(organizationId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Нельзя удалить ОО: к ней прикреплены пользователи.");
        }

        educationalOrganizationRepository.delete(organization);
    }

    /**
     * Возвращает страницу всех образовательных организаций
     * региона текущего регионального администратора.
     *
     * Поиск выполняется по названию и коду ОО.
     */
    @PreAuthorize("@roleAccess.isRegionAdmin(authentication)")
    public Page<EducationalOrganization> getPageForRegionAdmin(Long actorId, String search, int page, int size) {
        User actor = getUser(actorId);
        checkRegionAdmin(actor);

        if (page < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Номер страницы не может быть отрицательным.");
        }

        if (size != 10 && size != 20 && size != 50) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Размер страницы должен быть 10, 20 или 50.");
        }

        Long regionId = actor.getRegion().getId();
        String normalizedSearch = search == null ? "" : search.trim();

        Specification<EducationalOrganization> specification =
                (root, query, cb) -> {
                    List<Predicate> predicates = new ArrayList<>();
                    // Только ОО региона текущего администратора.
                    predicates.add(cb.equal(root.get("msu").get("region").get("id"), regionId));

                    if (!normalizedSearch.isEmpty()) {
                        String pattern = "%" + normalizedSearch.toLowerCase(Locale.ROOT) + "%";
                        Predicate nameMatch = cb.like(cb.lower(root.get("name")), pattern);
                        try {
                            Integer code = Integer.valueOf(normalizedSearch);
                            predicates.add(cb.or(nameMatch, cb.equal(root.get("code"), code)));
                        } catch (NumberFormatException ignored) {
                            predicates.add(nameMatch);
                        }
                    }
                    return cb.and(predicates.toArray(Predicate[]::new));
                };

        return educationalOrganizationRepository.findAll(specification, PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "name")));
    }

    private void checkRegionAdmin(User actor) {
        if (actor.getAccountStatus() != AccountStatus.ACCEPTED
                || actor.isBlocked()
                || actor.getRole() == null
                || actor.getRole().getCode() != RoleCode.REGION_ADMIN
                || actor.getRegion() == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Доступно только действующему администратору региона.");
        }
    }

    private EducationalOrganization getOrganization(Long organizationId) {
        return educationalOrganizationRepository.findById(organizationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Образовательная организация не найдена."));
    }

    /**
     * Проверяет, относится ли ОО к области полномочий администратора.
     */
    private void checkOrganizationAccess(User actor, EducationalOrganization organization) {
        RoleCode role = actor.getRole().getCode();

        if (role == RoleCode.REGION_ADMIN) {
            if (actor.getRegion() == null
                    || organization.getMsu().getRegion() == null
                    || !actor.getRegion().getId().equals(
                    organization.getMsu().getRegion().getId()
            )) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Образовательная организация не найдена.");
            }

            return;
        }

        if (role == RoleCode.MSU_ADMIN) {
            if (actor.getMsu() == null || !actor.getMsu().getId().equals(organization.getMsu().getId())) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Образовательная организация не найдена.");
            }

            return;
        }

        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Недостаточно прав для управления ОО.");
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