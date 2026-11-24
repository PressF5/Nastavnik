package org.donriro.nastavnik.event.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.donriro.nastavnik.event.dto.ChangeEventStatusRequest;
import org.donriro.nastavnik.event.dto.CreateEventRequest;
import org.donriro.nastavnik.event.dto.EventResponse;
import org.donriro.nastavnik.event.dto.UpdateEventRequest;
import org.donriro.nastavnik.event.service.EventService;
import org.donriro.nastavnik.event.status.EventLevel;
import org.donriro.nastavnik.security.principal.CustomUserDetails;
import org.springframework.data.domain.Page;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/events")
@RequiredArgsConstructor
public class EventController {

    private final EventService eventService;

    /**
     * Создаёт мероприятие.
     */
    @PostMapping
    public ResponseEntity<EventResponse> create(@AuthenticationPrincipal CustomUserDetails principal, @Valid @RequestBody CreateEventRequest request) {
        EventResponse response = eventService.create(principal.getUser().getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Возвращает страницу мероприятий с учётом полномочий администратора.
     */
    @GetMapping
    public ResponseEntity<PagedModel<EventResponse>> getAll(
            @AuthenticationPrincipal CustomUserDetails principal,
            @RequestParam(defaultValue = "") String search,
            @RequestParam(required = false) EventLevel level,
            @RequestParam(required = false) Boolean active,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        Page<EventResponse> response = eventService.getPage(
                principal.getUser().getId(),
                search,
                level,
                active,
                page,
                size
        );

        return ResponseEntity.ok(new PagedModel<>(response));
    }

    /**
     * Обновляет мероприятие.
     */
    @PutMapping("/{id}")
    public ResponseEntity<EventResponse> update(@AuthenticationPrincipal CustomUserDetails principal, @PathVariable Long id, @Valid @RequestBody UpdateEventRequest request) {
        EventResponse response = eventService.update(principal.getUser().getId(), id, request);
        return ResponseEntity.ok(response);
    }

    /**
     * Изменяет активность мероприятия.
     */
    @PatchMapping("/{id}/status")
    public ResponseEntity<EventResponse> changeStatus(@AuthenticationPrincipal CustomUserDetails principal, @PathVariable Long id, @Valid @RequestBody ChangeEventStatusRequest request) {
        EventResponse response = eventService.changeStatus(principal.getUser().getId(), id, request.active());
        return ResponseEntity.ok(response);
    }
}
