package org.donriro.nastavnik.event.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.donriro.nastavnik.event.status.EventLevel;

import java.time.LocalDate;

public record CreateEventRequest(

        @NotBlank(message = "Название мероприятия обязательно.")
        @Size(max = 200)
        String name,

        String description,

        @NotNull(message = "Уровень мероприятия обязателен.")
        EventLevel level,

        @NotNull(message = "Дата начала мероприятия обязательна.")
        LocalDate startDate,

        LocalDate endDate,

        @Size(max = 300)
        String location,

        @Size(max = 200)
        String organizer,

        @Size(max = 1000)
        String url
) {
}
