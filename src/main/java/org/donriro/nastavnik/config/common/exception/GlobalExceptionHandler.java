package org.donriro.nastavnik.config.common.exception;

import org.donriro.nastavnik.user.exception.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

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

    private ResponseEntity<ApiError> buildError(HttpStatus status, ErrorCode code, String message) {
        return ResponseEntity.status(status).body(new ApiError(code, message));
    }
}