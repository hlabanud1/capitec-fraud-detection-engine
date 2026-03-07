package za.co.capitec.fraud.exception;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationExceptions(MethodArgumentNotValidException ex) {

        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            if (error instanceof FieldError fieldError) {
                errors.put(fieldError.getField(), error.getDefaultMessage());
            } else {
                errors.put(error.getObjectName(), error.getDefaultMessage());
            }
        });

        log.warn("Validation error: {}", errors);
        return ResponseEntity.badRequest().body(
                buildErrorResponse(HttpStatus.BAD_REQUEST.value(), "Validation Failed",
                        "Invalid input data", errors)
        );
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolationException(ConstraintViolationException ex) {

        Map<String, String> errors = new HashMap<>();
        for (ConstraintViolation<?> violation : ex.getConstraintViolations()) {
            String propertyPath = violation.getPropertyPath().toString();
            String message = violation.getMessage();
            errors.put(propertyPath, message);
        }

        log.warn("Constraint violation: {}", errors);
        return ResponseEntity.badRequest().body(
                buildErrorResponse(HttpStatus.BAD_REQUEST.value(), "Validation Failed",
                        "Invalid parameter value", errors)
        );
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgumentException(IllegalArgumentException ex) {

        log.warn("Illegal argument: {}", ex.getMessage());
        return ResponseEntity.badRequest().body(
                buildErrorResponse(HttpStatus.BAD_REQUEST.value(), "Bad Request",
                        ex.getMessage(), null)
        );
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolation(DataIntegrityViolationException ex) {

        // Check if it's a duplicate transaction ID (cross-database compatible)
        if (isDuplicateTransactionId(ex)) {
            log.warn("Duplicate transaction ID attempted");
            return ResponseEntity.status(HttpStatus.CONFLICT).body(
                    buildErrorResponse(HttpStatus.CONFLICT.value(), "Duplicate Transaction",
                            "Transaction ID already exists. Each transaction must have a unique ID.", null)
            );
        }

        // Other integrity violations
        log.warn("Data integrity violation: {}", ex.getMessage());
        return ResponseEntity.badRequest().body(
                buildErrorResponse(HttpStatus.BAD_REQUEST.value(), "Data Integrity Violation",
                        "The request violates data integrity constraints", null)
        );
    }

    /**
     * Detects duplicate transaction_id violations across different database vendors.
     * Checks for constraint name (uk_transaction_id) and generic duplicate key patterns.
     *
     * Cross-database compatible detection for:
     * - PostgreSQL: "duplicate key value violates unique constraint"
     * - MySQL: "Duplicate entry"
     * - H2: "Unique index or primary key violation"
     * - Oracle: "unique constraint" violated
     *
     */
    private boolean isDuplicateTransactionId(DataIntegrityViolationException ex) {
        Throwable cause = ex;
        while (cause != null) {
            var message = cause.getMessage();
            if (message != null) {
                var lowerMessage = message.toLowerCase();

                // Check for explicit constraint name (most reliable)
                if (lowerMessage.contains("uk_transaction_id")) {
                    return true;
                }

                // Cross-database pattern: (duplicate/unique constraint) AND transaction_id
                var hasDuplicateKeyword = lowerMessage.contains("duplicate") ||
                        lowerMessage.contains("unique constraint") ||
                        lowerMessage.contains("unique index");
                var hasTransactionIdField = lowerMessage.contains("transaction_id");

                if (hasDuplicateKeyword && hasTransactionIdField) {
                    return true;
                }
            }
            cause = cause.getCause();
        }
        return false;
    }

    private ErrorResponse buildErrorResponse(int status, String error, String message,
                                             Map<String, String> validationErrors) {
        return ErrorResponse.of(status, error, message, validationErrors);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneralException(Exception ex) {

        log.error("Unexpected error occurred", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR.value(), "Internal Server Error",
                        "An unexpected error occurred", null)
        );
    }
}
