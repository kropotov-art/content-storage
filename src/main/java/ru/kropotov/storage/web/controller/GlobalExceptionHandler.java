package ru.kropotov.storage.web.controller;

import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import ru.kropotov.storage.expection.AccessDeniedException;
import ru.kropotov.storage.expection.FileAlreadyExistsException;
import ru.kropotov.storage.expection.FileNotFoundException;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {
    
    @ExceptionHandler(FileAlreadyExistsException.class)
    public ResponseEntity<Map<String, Object>> handleFileAlreadyExists(FileAlreadyExistsException e) {
        log.warn("File already exists: {}", e.getMessage());
        return createErrorResponse(HttpStatus.CONFLICT, e.getMessage(), "FILE_ALREADY_EXISTS");
    }
    
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDenied(AccessDeniedException e) {
        log.warn("Access denied: {}", e.getMessage());
        return createErrorResponse(HttpStatus.FORBIDDEN, e.getMessage(), "ACCESS_DENIED");
    }
    
    @ExceptionHandler(FileNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleFileNotFound(FileNotFoundException e) {
        log.warn("File not found: {}", e.getMessage());
        return createErrorResponse(HttpStatus.NOT_FOUND, e.getMessage(), "FILE_NOT_FOUND");
    }
    
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationErrors(MethodArgumentNotValidException e) {
        StringBuilder message = new StringBuilder("Validation failed: ");
        e.getBindingResult().getFieldErrors().forEach(error -> 
            message.append(error.getField()).append(" - ").append(error.getDefaultMessage()).append("; "));
        
        return createErrorResponse(HttpStatus.BAD_REQUEST, message.toString(), "VALIDATION_ERROR");
    }
    
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Map<String, Object>> handleConstraintViolation(ConstraintViolationException e) {
        return createErrorResponse(HttpStatus.BAD_REQUEST, e.getMessage(), "VALIDATION_ERROR");
    }
    
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException e) {
        return createErrorResponse(HttpStatus.BAD_REQUEST, e.getMessage(), "INVALID_ARGUMENT");
    }
    
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(Exception e) {
        log.error("Unexpected error", e);
        return createErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, 
                                 "An unexpected error occurred", "INTERNAL_ERROR");
    }
    
    private ResponseEntity<Map<String, Object>> createErrorResponse(
            HttpStatus status, String message, String errorCode) {
        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", Instant.now());
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("message", message);
        body.put("errorCode", errorCode);
        
        return ResponseEntity.status(status).body(body);
    }
}