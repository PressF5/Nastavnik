package org.donriro.nastavnik.mentorship.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.donriro.nastavnik.mentorship.dto.*;
import org.donriro.nastavnik.mentorship.service.MentorshipService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class MentorshipController {

    private final MentorshipService mentorshipService;

    @PostMapping("/api/admin/mentorship-assignments")
    @PreAuthorize("hasRole('OO_ADMIN')")
    public ResponseEntity<MentorshipResponse> createRequest(@Valid @RequestBody CreateMentorshipRequest request) {
        MentorshipResponse response = mentorshipService.createRequest(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/api/mentor/mentorship-assignments")
    @PreAuthorize("hasRole('MENTOR')")
    public ResponseEntity<List<MentorshipResponse>> getCurrentMentorRequests() {
        return ResponseEntity.ok(mentorshipService.getCurrentMentorRequests());
    }

    @PatchMapping("/api/mentor/mentorship-assignments/{id}/decision")
    @PreAuthorize("hasRole('MENTOR')")
    public ResponseEntity<MentorshipResponse> decide(@PathVariable("id") Long mentorshipId, @Valid @RequestBody MentorshipDecisionRequest request) {
        return ResponseEntity.ok(mentorshipService.decide(mentorshipId, request));
    }

    @GetMapping("/api/mentor/mentees")
    @PreAuthorize("hasRole('MENTOR')")
    public ResponseEntity<List<MentorshipResponse>> getCurrentMentorMentees() {
        return ResponseEntity.ok(mentorshipService.getCurrentMentorMentees());
    }

    @GetMapping("/api/mentor/mentees/{menteeId}")
    @PreAuthorize("hasRole('MENTOR')")
    public ResponseEntity<MenteeProfileResponse> getMenteeProfile(@PathVariable Long menteeId) {
        return ResponseEntity.ok(mentorshipService.getMenteeProfile(menteeId));
    }

    @PatchMapping("/api/mentor/mentorship-assignments/{id}/end")
    @PreAuthorize("hasRole('MENTOR')")
    public ResponseEntity<MentorshipResponse> endMentorship(@PathVariable("id") Long mentorshipId, @Valid @RequestBody EndMentorshipRequest request) {
        return ResponseEntity.ok(mentorshipService.endMentorship(mentorshipId, request));
    }
}
