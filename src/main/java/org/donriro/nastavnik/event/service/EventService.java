package org.donriro.nastavnik.event.service;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.donriro.nastavnik.event.entity.Event;
import org.donriro.nastavnik.event.repository.EventRepository;
import org.donriro.nastavnik.event.status.EventLevel;
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
public class EventService {

    private final EventRepository eventRepository;
    private final UserRepository userRepository;

    /**
     * Получает страницу мероприятий в пределах доступной администратору области.
     *
     * REGION_ADMIN видит все мероприятия своего региона.
     * MSU_ADMIN видит федеральные и региональные мероприятия своего региона,
     * а также муниципальные мероприятия своего МСУ.
     */
    @PreAuthorize("@roleAccess.isRegionAdmin(authentication) or @roleAccess.isMsuAdmin(authentication)")
    public Page<Event> getPage(Long actorId, String search, EventLevel level, Boolean active, int page, int size) {
        User actor = getUser(actorId);
        checkAdministrator(actor);
        validatePagination(page, size);

        Long regionId = actor.getRegion().getId();
        Long msuId = actor.getRole().getCode() == RoleCode.MSU_ADMIN ? actor.getMsu().getId() : null;

        String normalizedSearch = search == null ? "" : search.trim();

        Specification<Event> specification = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Все мероприятия должны относиться к региону администратора.
            predicates.add(cb.equal(root.get("region").get("id"), regionId));

            if (actor.getRole().getCode() == RoleCode.MSU_ADMIN) {
                // Администратор МСУ видит муниципальные мероприятия только своего МСУ.
                Predicate regionalEvents = root.get("level").in(EventLevel.FEDERAL, EventLevel.REGIONAL);
                Predicate municipalEvents = cb.and(cb.equal(root.get("level"), EventLevel.MUNICIPAL), cb.equal(root.get("msu").get("id"), msuId));
                predicates.add(cb.or(regionalEvents, municipalEvents));
            }

            if (!normalizedSearch.isEmpty()) {
                String pattern = "%" + normalizedSearch.toLowerCase(Locale.ROOT) + "%";
                predicates.add(cb.like(cb.lower(root.get("name")), pattern));
            }

            if (level != null) {
                predicates.add(cb.equal(root.get("level"), level));
            }

            if (active != null) {
                predicates.add(cb.equal(root.get("active"), active));
            }

            return cb.and(predicates.toArray(Predicate[]::new));
        };

        return eventRepository.findAll(specification, PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "startDate")));
    }

    private User getUser(Long id) {
        return userRepository.findByIdWithOrganizationContext(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Пользователь не найден."));
    }

    /**
     * Проверяет роль, состояние учётной записи и организационную принадлежность.
     */
    private void checkAdministrator(User actor) {
        if (actor.getAccountStatus() != AccountStatus.ACCEPTED
                || actor.isBlocked()
                || actor.getRole() == null
                || actor.getRole().getCode() == null
                || actor.getRegion() == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Недостаточно прав для работы с мероприятиями.");
        }

        RoleCode role = actor.getRole().getCode();

        if (role == RoleCode.REGION_ADMIN) {
            return;
        }

        if (role == RoleCode.MSU_ADMIN
                && actor.getMsu() != null
                && actor.getMsu().getRegion() != null
                && actor.getRegion().getId().equals(
                actor.getMsu().getRegion().getId())) {
            return;
        }

        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Недостаточно прав для работы с мероприятиями.");
    }

    private void validatePagination(int page, int size) {
        if (page < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Номер страницы не может быть отрицательным.");
        }

        if (size != 10 && size != 20 && size != 50) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Размер страницы должен быть 10, 20 или 50.");
        }
    }
}