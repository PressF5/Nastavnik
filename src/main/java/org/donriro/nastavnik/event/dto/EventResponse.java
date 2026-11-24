package org.donriro.nastavnik.event.dto;

import org.donriro.nastavnik.event.status.EventFormat;
import org.donriro.nastavnik.event.status.EventLevel;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

public record EventResponse(

        Long id,
        String name,
        String description,
        EventLevel level,
        EventFormat format,
        LocalDateTime startAt,
        LocalDateTime endAt,
        String location,
        String organizerPersonName,
        String organizerOrganizationName,
        String url,
        boolean active,
        Long regionId,
        Long msuId,
        List<SpeakerResponse> speakers,
        List<MaterialLinkResponse> materialLinks,
        Instant createdAt,
        Instant updatedAt
) {

    public record SpeakerResponse(
            Long id,
            String fullName,
            String position,
            int sortOrder
    ) {
    }

    public record MaterialLinkResponse(
            Long id,
            String name,
            String url,
            int sortOrder
    ) {
    }
}