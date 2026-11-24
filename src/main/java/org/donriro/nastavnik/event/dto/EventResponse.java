package org.donriro.nastavnik.event.dto;

import org.donriro.nastavnik.event.status.EventLevel;

import java.time.Instant;
import java.time.LocalDate;

public record EventResponse(
        Long id,
        String name,
        String description,
        EventLevel level,
        LocalDate startDate,
        LocalDate endDate,
        String location,
        String organizer,
        String url,
        boolean active,
        Long regionId,
        Long msuId,
        Instant createdAt,
        Instant updatedAt
) {
}
