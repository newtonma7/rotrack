package com.rotrack.exception;

import com.rotrack.dto.ApiErrorResponse;
import com.rotrack.observability.RequestLogAttributes;
import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(
            ResourceNotFoundException ex,
            HttpServletRequest request
    ) {
        return error(HttpStatus.NOT_FOUND, "NOT_FOUND", ex.getMessage(), Map.of(), request);
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ApiErrorResponse> handleConflict(
            ConflictException ex,
            HttpServletRequest request
    ) {
        return error(HttpStatus.CONFLICT, ex.getCode(), ex.getMessage(), Map.of(), request);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleDataIntegrityViolation(HttpServletRequest request) {
        return error(
                HttpStatus.CONFLICT,
                "CONSTRAINT_VIOLATION",
                "The request conflicts with the current state",
                Map.of(),
                request
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(
            MethodArgumentNotValidException ex,
            HttpServletRequest request
    ) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(fieldError ->
                fieldErrors.putIfAbsent(fieldError.getField(), fieldError.getDefaultMessage())
        );
        return error(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_ERROR",
                "Request validation failed",
                fieldErrors,
                request
        );
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleMalformedJson(HttpServletRequest request) {
        return error(
                HttpStatus.BAD_REQUEST,
                "MALFORMED_JSON",
                "Request body is not valid JSON",
                Map.of(),
                request
        );
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiErrorResponse> handleMissingParameter(HttpServletRequest request) {
        return error(
                HttpStatus.BAD_REQUEST,
                "INVALID_PARAMETER",
                "A required request parameter is missing",
                Map.of(),
                request
        );
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrorResponse> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex,
            HttpServletRequest request
    ) {
        return error(
                HttpStatus.BAD_REQUEST,
                "INVALID_PARAMETER",
                "Request parameter has an invalid value",
                Map.of(),
                request
        );
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalArgument(HttpServletRequest request) {
        return error(
                HttpStatus.BAD_REQUEST,
                "BAD_REQUEST",
                "Request is invalid",
                Map.of(),
                request
        );
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalState(HttpServletRequest request) {
        return error(
                HttpStatus.CONFLICT,
                "CONFLICT",
                "Request conflicts with the current state",
                Map.of(),
                request
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(
            Exception exception,
            HttpServletRequest request
    ) {
        request.setAttribute(RequestLogAttributes.EXCEPTION_TYPE, RequestLogAttributes.UNEXPECTED_SERVER_ERROR);
        return error(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "INTERNAL_ERROR",
                "An unexpected error occurred",
                Map.of(),
                request
        );
    }

    private ResponseEntity<ApiErrorResponse> error(
            HttpStatus status,
            String code,
            String message,
            Map<String, String> fieldErrors,
            HttpServletRequest request
    ) {
        request.setAttribute(RequestLogAttributes.ERROR_CODE, code);
        return ResponseEntity.status(status).body(
                ApiErrorResponse.of(code, message, fieldErrors, request.getRequestURI())
        );
    }
}
