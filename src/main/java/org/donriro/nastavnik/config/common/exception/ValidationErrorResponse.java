package org.donriro.nastavnik.config.common.exception;

import java.util.List;
import java.util.Map;

public record ValidationErrorResponse(
        ErrorCode code,
        String message,
        Map<String, List<String>> fieldErrors
) {}
