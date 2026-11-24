package org.donriro.nastavnik.event.service;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.donriro.nastavnik.event.dto.CreateEventRequest;
import org.donriro.nastavnik.event.dto.EventResponse;
import org.donriro.nastavnik.event.dto.UpdateEventRequest;
import org.donriro.nastavnik.event.entity.Event;
import org.donriro.nastavnik.event.entity.EventMaterialLink;
import org.donriro.nastavnik.event.entity.EventSpeaker;
import org.donriro.nastavnik.event.repository.EventRepository;
import org.donriro.nastavnik.event.status.EventFormat;
import org.donriro.nastavnik.event.status.EventLevel;
import org.donriro.nastavnik.event.status.EventStatus;
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

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EventService {

    private final EventRepository eventRepository;
    private final UserRepository userRepository;
    private final Clock platformClock;

    /**
     * Создаёт мероприятие в пределах полномочий текущего администратора.
     */
    @Transactional
    @PreAuthorize("@roleAccess.isRegionAdmin(authentication) or @roleAccess.isMsuAdmin(authentication)")
    public EventResponse create(Long actorId, CreateEventRequest request) {
        User actor = getUser(actorId);
        checkAdministrator(actor);

        RoleCode role = actor.getRole().getCode();
        EventLevel level = request.level();

        checkEventLevel(role, level);
        validateEventData(request.format(), request.startAt(), request.endAt(), request.location());

        Event event = new Event();
        event.setName(request.name().trim());
        event.setDescription(request.description());
        event.setLevel(level);
        event.setFormat(request.format());
        event.setStartAt(request.startAt());
        event.setEndAt(request.endAt());
        event.setLocation(normalizeLocation(request.location()));
        event.setUrl(request.url());

        // Сохраняем данные организатора на момент создания мероприятия.
        event.setOrganizerPersonName(getFullName(actor));
        event.setOrganizerOrganizationName(getOrganizationName(actor));

        event.setRegion(actor.getRegion());

        if (level == EventLevel.MUNICIPAL) {
            event.setMsu(actor.getMsu());
        }

        event.setCreatedBy(actor);
        event.setActive(true);

        updateSpeakers(event, request.speakers());
        updateMaterialLinks(event, request.materialLinks());

        return toResponse(eventRepository.save(event));
    }

    /**
     * Получает страницу мероприятий в пределах доступной администратору области.
     */
    @PreAuthorize("@roleAccess.isRegionAdmin(authentication) or @roleAccess.isMsuAdmin(authentication)")
    public Page<EventResponse> getPage(
            Long actorId,
            String search,
            EventLevel level,
            Boolean active,
            int page,
            int size
    ) {
        User actor = getUser(actorId);
        checkAdministrator(actor);
        validatePagination(page, size);

        Long regionId = actor.getRegion().getId();
        Long msuId = actor.getRole().getCode() == RoleCode.MSU_ADMIN ? actor.getMsu().getId() : null;

        String normalizedSearch = search == null ? "" : search.trim();

        Specification<Event> specification = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            predicates.add(cb.equal(root.get("region").get("id"), regionId));

            if (actor.getRole().getCode() == RoleCode.MSU_ADMIN) {
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

        return eventRepository.findAll(specification, PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "startAt"))).map(this::toResponse);
    }

    /**
     * Обновляет мероприятие в пределах полномочий текущего администратора.
     */
    @Transactional
    @PreAuthorize("@roleAccess.isRegionAdmin(authentication) or @roleAccess.isMsuAdmin(authentication)")
    public EventResponse update(Long actorId, Long eventId, UpdateEventRequest request) {
        User actor = getUser(actorId);
        checkAdministrator(actor);

        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Мероприятие не найдено."));

        checkEventAccess(actor, event);

        RoleCode role = actor.getRole().getCode();
        EventLevel requestedLevel = request.level();

        if (role == RoleCode.MSU_ADMIN && requestedLevel != event.getLevel()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Администратор МСУ не может изменять уровень мероприятия.");
        }

        checkEventLevel(role, requestedLevel);
        validateEventData(request.format(), request.startAt(), request.endAt(), request.location());

        event.setName(request.name().trim());
        event.setDescription(request.description());
        event.setLevel(requestedLevel);
        event.setFormat(request.format());
        event.setStartAt(request.startAt());
        event.setEndAt(request.endAt());
        event.setLocation(normalizeLocation(request.location()));
        event.setUrl(request.url());

        // При изменении уровня региональным администратором отвязываем мероприятие от МСУ.
        if (role == RoleCode.REGION_ADMIN) {
            event.setMsu(null);
        }

        updateSpeakers(event, request.speakers());
        updateMaterialLinks(event, request.materialLinks());

        return toResponse(event);
    }

    /**
     * Изменяет активность мероприятия.
     */
    @Transactional
    @PreAuthorize("@roleAccess.isRegionAdmin(authentication) or @roleAccess.isMsuAdmin(authentication)")
    public EventResponse changeStatus(Long actorId, Long eventId, boolean active) {
        User actor = getUser(actorId);
        checkAdministrator(actor);

        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Мероприятие не найдено."));

        checkEventAccess(actor, event);

        event.setActive(active);

        return toResponse(event);
    }

    /**
     * Проверяет допустимый уровень мероприятия для роли администратора.
     */
    private void checkEventLevel(RoleCode role, EventLevel level) {
        if (level == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Уровень мероприятия обязателен.");
        }

        if (role == RoleCode.REGION_ADMIN && level != EventLevel.FEDERAL && level != EventLevel.REGIONAL) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Региональный администратор может создавать и изменять только федеральные и региональные мероприятия.");
        }

        if (role == RoleCode.MSU_ADMIN && level != EventLevel.MUNICIPAL) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Администратор МСУ может создавать только муниципальные мероприятия.");
        }
    }

    /**
     * Проверяет даты и соответствие места проведения формату мероприятия.
     */
    private void validateEventData(EventFormat format, LocalDateTime startAt, LocalDateTime endAt, String location) {
        if (startAt == null || endAt == null || !endAt.isAfter(startAt)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Дата и время окончания должны быть позже даты и времени начала.");
        }

        if (format == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Формат мероприятия обязателен.");
        }

        boolean hasLocation = location != null && !location.isBlank();

        if (format == EventFormat.ONLINE && hasLocation) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Для онлайн-мероприятия место проведения не указывается.");
        }

        if ((format == EventFormat.OFFLINE || format == EventFormat.HYBRID) && !hasLocation) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Для выбранного формата необходимо указать место проведения.");
        }
    }

    private String normalizeLocation(String location) {
        return location == null || location.isBlank() ? null : location.trim();
    }

    /**
     * Формирует ФИО организатора из данных учётной записи.
     */
    private String getFullName(User user) {
        return Stream.of(
                        user.getLastName(),
                        user.getFirstName(),
                        user.getMiddleName() == null ? "" : user.getMiddleName())
                .filter(value -> !value.isBlank())
                .reduce((first, second) -> first + " " + second)
                .orElse("");
    }

    /**
     * Возвращает название организации, от имени которой создано мероприятие.
     */
    private String getOrganizationName(User actor) {
        return switch (actor.getRole().getCode()) {
            case REGION_ADMIN -> actor.getRegion().getName();
            case MSU_ADMIN -> actor.getMsu().getName();
            default -> throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Недостаточно прав для создания мероприятия.");
        };
    }

    /**
     * Полностью заменяет список спикеров данными из запроса.
     */
    private void updateSpeakers(Event event, List<CreateEventRequest.SpeakerRequest> requests) {
        event.getSpeakers().clear();

        if (requests == null) {
            return;
        }

        for (int i = 0; i < requests.size(); i++) {
            CreateEventRequest.SpeakerRequest request = requests.get(i);

            EventSpeaker speaker = new EventSpeaker();
            speaker.setEvent(event);
            speaker.setFullName(request.fullName().trim());
            speaker.setPosition(request.position() == null || request.position().isBlank() ? null : request.position().trim());
            speaker.setSortOrder(i);

            event.getSpeakers().add(speaker);
        }
    }

    /**
     * Полностью заменяет список материалов данными из запроса.
     */
    private void updateMaterialLinks(Event event, List<CreateEventRequest.MaterialLinkRequest> requests) {
        event.getMaterialLinks().clear();

        if (requests == null) {
            return;
        }

        for (int i = 0; i < requests.size(); i++) {
            CreateEventRequest.MaterialLinkRequest request = requests.get(i);

            EventMaterialLink materialLink = new EventMaterialLink();
            materialLink.setEvent(event);
            materialLink.setName(request.name().trim());
            materialLink.setUrl(request.url().trim());
            materialLink.setSortOrder(i);

            event.getMaterialLinks().add(materialLink);
        }
    }

    /**
     * Проверяет, что мероприятие находится в области доступа администратора.
     */
    private void checkEventAccess(User actor, Event event) {
        if (!event.getRegion().getId().equals(actor.getRegion().getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Нет доступа к этому мероприятию.");
        }

        if (actor.getRole().getCode() == RoleCode.MSU_ADMIN
                && (event.getLevel() != EventLevel.MUNICIPAL
                || event.getMsu() == null
                || !event.getMsu().getId().equals(actor.getMsu().getId()))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Нет доступа к этому мероприятию.");
        }
    }

    /**
     * Преобразует мероприятие в DTO и вычисляет его временной статус.
     */
    private EventResponse toResponse(Event event) {
        LocalDateTime now = LocalDateTime.now(platformClock);

        EventStatus status;
        LocalDateTime nextChange;

        if (now.isBefore(event.getStartAt())) {
            status = EventStatus.PLANNED;
            nextChange = event.getStartAt();
        } else if (now.isBefore(event.getEndAt())) {
            status = EventStatus.ONGOING;
            nextChange = event.getEndAt();
        } else {
            status = EventStatus.COMPLETED;
            nextChange = null;
        }

        Instant nextStatusChangeAt = nextChange == null ? null : nextChange.atZone(platformClock.getZone()).toInstant();

        return new EventResponse(
                event.getId(),
                event.getName(),
                event.getDescription(),
                event.getLevel(),
                event.getFormat(),
                status,
                event.getStartAt(),
                event.getEndAt(),
                nextStatusChangeAt,
                event.getLocation(),
                event.getOrganizerPersonName(),
                event.getOrganizerOrganizationName(),
                event.getUrl(),
                event.isActive(),
                event.getRegion().getId(),
                event.getMsu() != null ? event.getMsu().getId() : null,
                event.getSpeakers().stream()
                        .map(speaker -> new EventResponse.SpeakerResponse(
                                speaker.getId(),
                                speaker.getFullName(),
                                speaker.getPosition(),
                                speaker.getSortOrder()
                        ))
                        .toList(),
                event.getMaterialLinks().stream()
                        .map(link -> new EventResponse.MaterialLinkResponse(
                                link.getId(),
                                link.getName(),
                                link.getUrl(),
                                link.getSortOrder()
                        ))
                        .toList(),
                event.getCreatedAt(),
                event.getUpdatedAt()
        );
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
                && actor.getRegion().getId().equals(actor.getMsu().getRegion().getId())) {
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
