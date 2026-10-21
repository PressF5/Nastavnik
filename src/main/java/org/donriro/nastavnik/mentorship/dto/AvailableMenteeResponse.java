package org.donriro.nastavnik.mentorship.dto;

import java.time.LocalDate;

/**
 * Данные наставляемого, необходимые администратору ОО
 * для выбора кандидата на наставничество.
 */
public record AvailableMenteeResponse(
        Long id,
        String firstName,
        String lastName,
        String middleName,
        LocalDate birthDate
) {
}