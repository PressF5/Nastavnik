package org.donriro.nastavnik.event.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.donriro.nastavnik.event.status.EventFormat;
import org.donriro.nastavnik.event.status.EventLevel;

import java.time.LocalDateTime;
import java.util.List;

public record CreateEventRequest(

        @NotBlank(message = "Название мероприятия обязательно.")
        @Size(max = 200)
        String name,

        String description,

        @NotNull(message = "Уровень мероприятия обязателен.")
        EventLevel level,

        @NotNull(message = "Формат мероприятия обязателен.")
        EventFormat format,

        @NotNull(message = "Дата и время начала обязательны.")
        LocalDateTime startAt,

        @NotNull(message = "Дата и время окончания обязательны.")
        LocalDateTime endAt,

        @Size(max = 300)
        String location,

        @Size(max = 1000)
        @Pattern(regexp = "^https?://.+$", message = "Ссылка должна начинаться с http:// или https://.")
        String url,

        @Valid
        List<SpeakerRequest> speakers,

        @Valid
        List<MaterialLinkRequest> materialLinks
) {

        public record SpeakerRequest(

                @NotBlank(message = "ФИО спикера обязательно.")
                @Size(max = 300)
                String fullName,

                @Size(max = 300)
                String position
        ) {
        }

        public record MaterialLinkRequest(

                @NotBlank(message = "Название материала обязательно.")
                @Size(max = 200)
                String name,

                @NotBlank(message = "Ссылка на материал обязательна.")
                @Size(max = 1000)
                @Pattern(regexp = "^https?://.+$", message = "Ссылка должна начинаться с http:// или https://.")
                String url
        ) {
        }
}