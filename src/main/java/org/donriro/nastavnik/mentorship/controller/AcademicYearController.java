package org.donriro.nastavnik.mentorship.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.donriro.nastavnik.mentorship.dto.AcademicYearResponse;
import org.donriro.nastavnik.mentorship.dto.CreateAcademicYearRequest;
import org.donriro.nastavnik.mentorship.dto.UpdateAcademicYearRequest;
import org.donriro.nastavnik.mentorship.service.AcademicYearService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class AcademicYearController {

    private final AcademicYearService academicYearService;

    /**
     * Создаёт учебный год.
     */
    @PostMapping("/api/admin/academic-years")
    @PreAuthorize("hasRole('REGION_ADMIN')")
    public ResponseEntity<AcademicYearResponse> create(@Valid @RequestBody CreateAcademicYearRequest request) {
        AcademicYearResponse response = academicYearService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Возвращает список всех учебных годов.
     */
    @GetMapping("/api/admin/academic-years")
    @PreAuthorize("hasRole('REGION_ADMIN')")
    public ResponseEntity<List<AcademicYearResponse>> getAll() {
        return ResponseEntity.ok(academicYearService.getAll());
    }

    /**
     * Возвращает текущий и завершённые учебные годы
     * для выбора в личном кабинете.
     */
    @GetMapping("/api/academic-years/available")
    @PreAuthorize("hasAnyRole('MSU_ADMIN', 'OO_ADMIN', 'MENTOR', 'MENTEE')")
    public ResponseEntity<List<AcademicYearResponse>> getAvailable() {
        return ResponseEntity.ok(academicYearService.getAvailableForCurrentUser());
    }

    /**
     * Возвращает учебный год, действующий на текущую дату.
     */
    @GetMapping("/api/academic-years/current")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<AcademicYearResponse> getCurrent() {
        return ResponseEntity.ok(academicYearService.getCurrent());
    }

    /**
     * Возвращает учебный год по идентификатору.
     */
    @GetMapping("/api/academic-years/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<AcademicYearResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(academicYearService.getById(id));
    }

    /**
     * Изменяет запланированный учебный год.
     */
    @PutMapping("/api/admin/academic-years/{id}")
    @PreAuthorize("hasRole('REGION_ADMIN')")
    public ResponseEntity<AcademicYearResponse> update(@PathVariable Long id, @Valid @RequestBody UpdateAcademicYearRequest request) {
        return ResponseEntity.ok(academicYearService.update(id, request));
    }

    /**
     * Удаляет запланированный учебный год, если он не связан с другими данными.
     */
    @DeleteMapping("/api/admin/academic-years/{id}")
    @PreAuthorize("hasRole('REGION_ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        academicYearService.delete(id);
        return ResponseEntity.noContent().build();
    }
}