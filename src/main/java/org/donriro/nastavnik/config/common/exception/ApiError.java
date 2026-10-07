package org.donriro.nastavnik.config.common.exception;

public record ApiError(
        ErrorCode code,
        String message
) {}
