package org.donriro.nastavnik.config.common.exception;

import jakarta.persistence.OptimisticLockException;
import org.donriro.nastavnik.matching.exception.InvalidProfileMatchingRuleException;
import org.donriro.nastavnik.matching.exception.ProfileMatchingRuleAlreadyExistsException;
import org.donriro.nastavnik.matching.exception.ProfileMatchingRuleNotFoundException;
import org.donriro.nastavnik.profile.field.exception.*;
import org.donriro.nastavnik.profile.options.exception.ProfileFieldOptionsRequiredException;
import org.donriro.nastavnik.security.exception.InvalidRefreshTokenException;
import org.donriro.nastavnik.user.exception.*;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ValidationErrorResponse> handleValidation(MethodArgumentNotValidException exception) {
        Map<String, List<String>> fieldErrors = new LinkedHashMap<>();

        exception.getBindingResult().getFieldErrors().forEach(error -> fieldErrors.computeIfAbsent(error.getField(), key -> new ArrayList<>()).add(error.getDefaultMessage()));

        //        .stream()
        //        .collect(Collectors.groupingBy(
        //                FieldError::getField,
        //                LinkedHashMap::new,
        //                Collectors.mapping(FieldError::getDefaultMessage, Collectors.toList())
        //        ));

        ValidationErrorResponse response = new ValidationErrorResponse(ErrorCode.VALIDATION_ERROR, "Некорректные данные запроса.", fieldErrors);

        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(EmailAlreadyExistsException.class)
    public ResponseEntity<ApiError> handleEmailAlreadyExists(EmailAlreadyExistsException exception) {
        return buildError(HttpStatus.CONFLICT, ErrorCode.EMAIL_ALREADY_EXISTS, exception.getMessage());
    }

    @ExceptionHandler(MsuNotFoundException.class)
    public ResponseEntity<ApiError> handleMsuNotFound(MsuNotFoundException exception) {
        return buildError(HttpStatus.NOT_FOUND, ErrorCode.MSU_NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler(EducationalOrganizationNotFoundException.class)
    public ResponseEntity<ApiError> handleEducationalOrganizationNotFound(EducationalOrganizationNotFoundException exception) {
        return buildError(HttpStatus.NOT_FOUND, ErrorCode.EDUCATIONAL_ORGANIZATION_NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler(RoleNotFoundException.class)
    public ResponseEntity<ApiError> handleRoleNotFound(RoleNotFoundException exception) {
        return buildError(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.ROLE_NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler(AdministratorAlreadyExistsException.class)
    public ResponseEntity<ApiError> handleAdministratorAlreadyExists(AdministratorAlreadyExistsException exception) {
        return buildError(HttpStatus.CONFLICT, ErrorCode.ADMINISTRATOR_ALREADY_EXISTS, exception.getMessage());
    }

    @ExceptionHandler(InvalidRegistrationScopeException.class)
    public ResponseEntity<ApiError> handleInvalidRegistrationScope(InvalidRegistrationScopeException exception) {
        return buildError(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_REGISTRATION_SCOPE, exception.getMessage());
    }

    @ExceptionHandler(InvalidRegistrationRoleException.class)
    public ResponseEntity<ApiError> handleInvalidRegistrationRole(InvalidRegistrationRoleException exception) {
        return buildError(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_REGISTRATION_ROLE, exception.getMessage());
    }

    @ExceptionHandler(RegionNotInitializedException.class)
    public ResponseEntity<ApiError> handleRegionNotInitialized(RegionNotInitializedException exception) {
        return buildError(HttpStatus.CONFLICT, ErrorCode.REGION_NOT_INITIALIZED, exception.getMessage());
    }

    @ExceptionHandler(RegionAlreadyExistsException.class)
    public ResponseEntity<ApiError> handleRegionAlreadyExists(RegionAlreadyExistsException exception) {
        return buildError(HttpStatus.CONFLICT, ErrorCode.REGION_ALREADY_EXISTS, exception.getMessage());
    }

    @ExceptionHandler(RegionAlreadyInitializedException.class)
    public ResponseEntity<ApiError> handleRegionAlreadyInitialized(RegionAlreadyInitializedException exception) {
        return buildError(HttpStatus.CONFLICT, ErrorCode.REGION_ALREADY_INITIALIZED, exception.getMessage());
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ApiError> handleInvalidCredentials(InvalidCredentialsException exception) {
        return buildError(HttpStatus.UNAUTHORIZED, ErrorCode.INVALID_CREDENTIALS, exception.getMessage());
    }

    @ExceptionHandler(InvalidRefreshTokenException.class)
    public ResponseEntity<ApiError> handleInvalidRefreshToken(InvalidRefreshTokenException exception) {
        return buildError(HttpStatus.UNAUTHORIZED, ErrorCode.INVALID_REFRESH_TOKEN, exception.getMessage());
    }

    @ExceptionHandler(ProfileFieldTypeChangeNotAllowedException.class)
    public ResponseEntity<ApiError> handleProfileFieldTypeChangeNotAllowed(ProfileFieldTypeChangeNotAllowedException exception) {
        return buildError(HttpStatus.CONFLICT, ErrorCode.PROFILE_FIELD_TYPE_CHANGE_NOT_ALLOWED, exception.getMessage());
    }

    @ExceptionHandler(ProfileFieldNotFoundException.class)
    public ResponseEntity<ApiError> handleProfileFieldNotFound(ProfileFieldNotFoundException exception) {
        return buildError(HttpStatus.NOT_FOUND, ErrorCode.PROFILE_FIELD_NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler(ProfileFieldAlreadyExistsException.class)
    public ResponseEntity<ApiError> handleProfileFieldAlreadyExists(ProfileFieldAlreadyExistsException exception) {
        return buildError(HttpStatus.CONFLICT, ErrorCode.PROFILE_FIELD_ALREADY_EXISTS, exception.getMessage());
    }

    @ExceptionHandler(InactiveProfileFieldException.class)
    public ResponseEntity<ApiError> handleInactiveProfileField(InactiveProfileFieldException exception) {
        return buildError(HttpStatus.CONFLICT, ErrorCode.INACTIVE_PROFILE_FIELD, exception.getMessage());
    }

    @ExceptionHandler(ProfileFieldOptionsRequiredException.class)
    public ResponseEntity<ApiError> handleProfileFieldOptionsRequired(ProfileFieldOptionsRequiredException exception) {
        return buildError(HttpStatus.BAD_REQUEST, ErrorCode.PROFILE_FIELD_OPTIONS_REQUIRED, exception.getMessage());
    }

    @ExceptionHandler(InvalidProfileMatchingRuleException.class)
    public ResponseEntity<ApiError> handleInvalidProfileMatchingRule(InvalidProfileMatchingRuleException exception) {
        return buildError(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_PROFILE_MATCHING_RULE, exception.getMessage());
    }

    @ExceptionHandler(ProfileMatchingRuleAlreadyExistsException.class)
    public ResponseEntity<ApiError> handleProfileMatchingRuleAlreadyExists(ProfileMatchingRuleAlreadyExistsException exception) {
        return buildError(HttpStatus.CONFLICT, ErrorCode.PROFILE_MATCHING_RULE_ALREADY_EXISTS, exception.getMessage());
    }

    @ExceptionHandler(ProfileMatchingRuleNotFoundException.class)
    public ResponseEntity<ApiError> handleProfileMatchingRuleNotFound(ProfileMatchingRuleNotFoundException exception) {
        return buildError(HttpStatus.NOT_FOUND, ErrorCode.PROFILE_MATCHING_RULE_NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler({StaleProfileFieldException.class, ObjectOptimisticLockingFailureException.class, OptimisticLockException.class})
    public ResponseEntity<ApiError> handleOptimisticLockConflict(RuntimeException exception) {
        if (exception instanceof StaleProfileFieldException staleException) {
            return buildError(HttpStatus.CONFLICT, ErrorCode.STALE_PROFILE_FIELD, staleException.getMessage());
        }

        return buildError(HttpStatus.CONFLICT, ErrorCode.STALE_PROFILE_FIELD, "Поле профиля было изменено другим запросом. Обновите данные и повторите операцию.");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleDataIntegrityViolation(DataIntegrityViolationException exception) {
        String constraintName = findConstraintName(exception);

        return switch (constraintName) {
            case "uk_users_email" -> buildError(HttpStatus.CONFLICT, ErrorCode.EMAIL_ALREADY_EXISTS, "Пользователь с таким email уже зарегистрирован.");
            case "uk_users_one_region_admin" -> buildError(HttpStatus.CONFLICT, ErrorCode.REGION_ALREADY_INITIALIZED, "Региональный администратор уже зарегистрирован.");
            case "uk_region_code" -> buildError(HttpStatus.CONFLICT, ErrorCode.REGION_ALREADY_EXISTS, "Регион с таким кодом уже существует.");
            case "uk_users_one_msu_admin", "uk_users_one_oo_admin" -> buildError(HttpStatus.CONFLICT, ErrorCode.ADMINISTRATOR_ALREADY_EXISTS, "Для указанной территории уже существует администратор.");
            default -> buildError(HttpStatus.CONFLICT, ErrorCode.DATA_INTEGRITY_VIOLATION, "Операция конфликтует с ограничениями базы данных.");
        };
    }

    private String findConstraintName(Throwable exception) {
        Throwable current = exception;

        while (current != null) {
            if (current instanceof ConstraintViolationException violation) {
                return violation.getConstraintName() != null ? violation.getConstraintName() : "";
            }
            current = current.getCause();
        }
        return "";
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument( IllegalArgumentException exception ) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of( "timestamp", Instant.now().toString(),
                        "status", HttpStatus.BAD_REQUEST.value(),
                        "error", "Bad Request",
                        "message", exception.getMessage() )); }

    private ResponseEntity<ApiError> buildError(HttpStatus status, ErrorCode code, String message) {
        return ResponseEntity.status(status).body(new ApiError(code, message));
    }
}