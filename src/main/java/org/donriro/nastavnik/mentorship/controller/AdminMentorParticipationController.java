package org.donriro.nastavnik.mentorship.controller;

import lombok.RequiredArgsConstructor;
import org.donriro.nastavnik.mentorship.dto.MentorParticipationAdminResponse;
import org.donriro.nastavnik.mentorship.service.AdminMentorParticipationService;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/academic-years/{academicYearId}/mentor-participations")
@PreAuthorize("hasRole('REGION_ADMIN')")
public class AdminMentorParticipationController {

    private final AdminMentorParticipationService service;

    /**
     * Возвращает подтверждения участия наставников
     * за выбранный учебный год.
     */
    @GetMapping
    public ResponseEntity<Page<MentorParticipationAdminResponse>> getAll(
            @PathVariable Long academicYearId,
            @RequestParam(required = false) Boolean participated,
            @RequestParam(defaultValue = "") String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(service.getAll(academicYearId, participated, search, page, size));
    }
}