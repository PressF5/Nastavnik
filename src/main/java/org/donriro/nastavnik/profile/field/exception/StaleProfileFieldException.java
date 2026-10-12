package org.donriro.nastavnik.profile.field.exception;

public class StaleProfileFieldException extends RuntimeException {

    public StaleProfileFieldException(Long fieldId, Long expectedVersion, Long actualVersion) {
        super("Поле с id=%d уже изменено. Ожидалась версия %d, текущая версия %d".formatted(fieldId, expectedVersion, actualVersion));
    }
}
