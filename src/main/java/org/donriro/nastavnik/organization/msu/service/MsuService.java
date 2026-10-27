package org.donriro.nastavnik.organization.msu.service;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.donriro.nastavnik.organization.educationalorganization.entity.EducationalOrganization;
import org.donriro.nastavnik.organization.educationalorganization.repository.EducationalOrganizationRepository;
import org.donriro.nastavnik.organization.msu.dto.request.CreateMsuRequest;
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
public class MsuService {

    private final MsuRepository msuRepository;
    private final UserRepository userRepository;
    private final EducationalOrganizationRepository educationalOrganizationRepository;

    @Transactional
    @PreAuthorize("@roleAccess.isRegionAdmin(authentication)")
    public Msu create(Long actorId, CreateMsuRequest request) {
        User actor = getUser(actorId);
        checkRegionAdmin(actor);

        if (msuRepository.existsByRegionIdAndCode(actor.getRegion().getId(), request.code())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "МСУ с таким кодом уже существует в регионе.");
        }

        Msu msu = new Msu();
        msu.setCode(request.code());
        msu.setName(request.name().trim());

        // МСУ автоматически относится к региону текущего администратора.
        msu.setRegion(actor.getRegion());

        return msuRepository.save(msu);
    }

    /**
     * Получает МСУ текущего администратора.
     */
    @PreAuthorize("@roleAccess.isMsuAdmin(authentication)")
    public Msu getCurrentMsuForMsuAdmin(Long actorId) {
        User actor = getUser(actorId);
        checkMsuAdmin(actor);

        return actor.getMsu();
    }

    /**
     * Получает страницу ОО собственного МСУ администратора.
     * Чужой msuId клиент передать не может: МСУ определяется
     * по авторизованному пользователю.
     */
    @PreAuthorize("@roleAccess.isMsuAdmin(authentication)")
    public Page<EducationalOrganization> getEducationalOrganizationsForMsuAdmin(
            Long actorId,
            String search,
            int page,
            int size) {
        User actor = getUser(actorId);
        checkMsuAdmin(actor);
        validatePagination(page, size);

        return searchEducationalOrganizations(
                actor.getMsu().getId(),
                actor.getRegion().getId(),
                search,
                page,
                size
        );
    }

    /**
     * Проверяет состояние учётной записи и корректность привязки МСУ.
     */
    private void checkMsuAdmin(User actor) {
        if (actor.getAccountStatus() != AccountStatus.ACCEPTED
                || actor.isBlocked()
                || actor.getRole() == null
                || actor.getRole().getCode() != RoleCode.MSU_ADMIN
                || actor.getRegion() == null
                || actor.getMsu() == null
                || actor.getMsu().getRegion() == null
                || !actor.getRegion().getId().equals(
                actor.getMsu().getRegion().getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Доступно только действующему администратору своего МСУ.");
        }
    }

    @PreAuthorize("@roleAccess.isRegionAdmin(authentication)")
    public List<Msu> getAllForRegionAdmin(Long actorId) {
        User actor = getUser(actorId);
        checkRegionAdmin(actor);

        return msuRepository.findAllByRegionIdOrderByNameAsc(actor.getRegion().getId());
    }

    @Transactional
    @PreAuthorize("@roleAccess.isRegionAdmin(authentication)")
    public Msu update(Long actorId, Long msuId, CreateMsuRequest request) {
        User actor = getUser(actorId);
        checkRegionAdmin(actor);

        Msu msu = msuRepository.findById(msuId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "МСУ не найдено."));

        // Администратор может изменять только МСУ своего региона.
        if (!msu.getRegion().getId().equals(actor.getRegion().getId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "МСУ не найдено.");
        }

        if (msuRepository.existsByRegionIdAndCodeAndIdNot(actor.getRegion().getId(), request.code(), msuId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "МСУ с таким кодом уже существует в регионе.");
        }

        msu.setCode(request.code());
        msu.setName(request.name().trim());

        return msuRepository.save(msu);
    }

    @Transactional
    @PreAuthorize("@roleAccess.isRegionAdmin(authentication)")
    public void delete(Long actorId, Long msuId) {
        User actor = getUser(actorId);
        checkRegionAdmin(actor);

        Msu msu = msuRepository.findById(msuId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "МСУ не найдено."));

        if (!msu.getRegion().getId().equals(actor.getRegion().getId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "МСУ не найдено.");
        }

        if (educationalOrganizationRepository.existsByMsuId(msuId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Нельзя удалить МСУ: к нему прикреплены образовательные организации.");
        }

        if (userRepository.existsByRoleCodeAndMsuId(RoleCode.MSU_ADMIN, msuId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Нельзя удалить МСУ: к нему прикреплены администраторы.");
        }

        msuRepository.delete(msu);
    }

    /**
     * Постраничный список МСУ текущего региона.
     * Поиск выполняется по названию и, если введён числовой запрос, по коду.
     */
    @PreAuthorize("@roleAccess.isRegionAdmin(authentication)")
    public Page<Msu> getPageForRegionAdmin(Long actorId, String search, int page, int size) {
        User actor = getUser(actorId);
        checkRegionAdmin(actor);
        validatePagination(page, size);

        Long regionId = actor.getRegion().getId();
        String normalizedSearch = search == null ? "" : search.trim();

        Specification<Msu> specification = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Всегда ограничиваем результат регионом текущего администратора.
            predicates.add(cb.equal(root.get("region").get("id"), regionId));

            if (!normalizedSearch.isEmpty()) {
                String pattern = "%" + normalizedSearch.toLowerCase(Locale.ROOT) + "%";
                Predicate nameMatch = cb.like(cb.lower(root.get("name")), pattern);

                try {
                    Integer code = Integer.valueOf(normalizedSearch);
                    Predicate codeMatch = cb.equal(root.get("code"), code);
                    predicates.add(cb.or(nameMatch, codeMatch));
                } catch (NumberFormatException ignored) {
                    predicates.add(nameMatch);
                }
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };

        return msuRepository.findAll(specification, PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "name")));
    }

    /**
     * Получает одно МСУ, доступное региональному администратору.
     */
    @PreAuthorize("@roleAccess.isRegionAdmin(authentication)")
    public Msu getByIdForRegionAdmin(Long actorId, Long msuId) {
        User actor = getUser(actorId);
        checkRegionAdmin(actor);

        return findMsuInRegion(msuId, actor.getRegion().getId());
    }

    /**
     * Получает страницу образовательных организаций выбранного МСУ.
     * Поиск выполняется по названию и коду ОО.
     */
    @PreAuthorize("@roleAccess.isRegionAdmin(authentication)")
    public Page<EducationalOrganization> getEducationalOrganizationsForRegionAdmin(Long actorId, Long msuId, String search, int page, int size) {
        User actor = getUser(actorId);
        checkRegionAdmin(actor);
        validatePagination(page, size);

        Long regionId = actor.getRegion().getId();

        // Проверяем, что МСУ относится к региону текущего администратора.
        findMsuInRegion(msuId, regionId);

        return searchEducationalOrganizations(
                msuId,
                regionId,
                search,
                page,
                size
        );
    }

    private Page<EducationalOrganization> searchEducationalOrganizations(Long msuId, Long regionId, String search, int page, int size) {
        String normalizedSearch = search == null ? "" : search.trim();

        Specification<EducationalOrganization> specification =
                (root, query, cb) -> {
                    List<Predicate> predicates = new ArrayList<>();
                    predicates.add(cb.equal(root.get("msu").get("id"), msuId));
                    predicates.add(cb.equal(root.get("msu").get("region").get("id"), regionId));

                    if (!normalizedSearch.isEmpty()) {
                        String pattern = "%" + normalizedSearch.toLowerCase(Locale.ROOT) + "%";
                        Predicate nameMatch = cb.like(cb.lower(root.get("name")), pattern);

                        try {
                            Integer code = Integer.valueOf(normalizedSearch);
                            Predicate codeMatch = cb.equal(root.get("code"), code);
                            predicates.add(cb.or(nameMatch, codeMatch));
                        } catch (NumberFormatException ignored) {
                            predicates.add(nameMatch);
                        }
                    }

                    return cb.and(predicates.toArray(Predicate[]::new));
                };

        return educationalOrganizationRepository.findAll(
                specification,
                PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "name")));
    }

    private Msu findMsuInRegion(Long msuId, Long regionId) {
        Msu msu = msuRepository.findById(msuId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "МСУ не найдено."));

        if (!msu.getRegion().getId().equals(regionId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "МСУ не найдено.");
        }

        return msu;
    }

    private void validatePagination(int page, int size) {
        if (page < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Номер страницы не может быть отрицательным.");
        }

        if (size != 10 && size != 20 && size != 50) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Размер страницы должен быть 10, 20 или 50.");
        }
    }

    private void checkRegionAdmin(User actor) {
        if (actor.getAccountStatus() != AccountStatus.ACCEPTED
                || actor.isBlocked()
                || actor.getRole() == null
                || actor.getRole().getCode() != RoleCode.REGION_ADMIN
                || actor.getRegion() == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Создавать МСУ может только действующий администратор региона.");
        }
    }

    private User getUser(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Пользователь не найден: " + id));
    }
}
