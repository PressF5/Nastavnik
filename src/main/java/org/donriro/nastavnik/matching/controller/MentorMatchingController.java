package org.donriro.nastavnik.matching.controller;

import lombok.RequiredArgsConstructor;
import org.donriro.nastavnik.matching.dto.MentorMatchingResponse;
import org.donriro.nastavnik.matching.service.MentorMatchingService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/mentee/mentors")
@RequiredArgsConstructor
@PreAuthorize("hasRole('MENTEE')")
public class MentorMatchingController {

    private final MentorMatchingService mentorMatchingService;

    @GetMapping
    public ResponseEntity<List<MentorMatchingResponse>> findMentors() {
        return ResponseEntity.ok(mentorMatchingService.findMentorsForCurrentUser()
        );
    }
}
