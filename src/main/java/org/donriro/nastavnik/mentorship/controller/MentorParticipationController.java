package org.donriro.nastavnik.mentorship.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.donriro.nastavnik.mentorship.dto.MentorParticipationResponse;
import org.donriro.nastavnik.mentorship.dto.UpdateMentorParticipationRequest;
import org.donriro.nastavnik.mentorship.service.MentorParticipationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/mentor/participation")
@PreAuthorize("hasRole('MENTOR')")
public class MentorParticipationController {

    private final MentorParticipationService mentorParticipationService;

    /**
     * Возвращает подтверждение участия в текущем учебном году.
     */
    @GetMapping("/current")
    public ResponseEntity<MentorParticipationResponse> getCurrentParticipation() {
        return ResponseEntity.ok(mentorParticipationService.getCurrentParticipation());
    }

    /**
     * Изменяет подтверждение участия в текущем учебном году.
     */
    @PutMapping("/current")
    public ResponseEntity<MentorParticipationResponse> updateCurrentParticipation(@Valid @RequestBody UpdateMentorParticipationRequest request) {
        return ResponseEntity.ok(mentorParticipationService.updateCurrentParticipation(request));
    }
}