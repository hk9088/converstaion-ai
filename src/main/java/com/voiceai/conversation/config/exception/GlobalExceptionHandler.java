package com.voiceai.conversation.config.exception;

import com.voiceai.conversation.model.dto.ErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.time.Instant;

/**
 * Global exception handler for consistent error responses.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(SessionNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleSessionNotFound(
            SessionNotFoundException ex, WebRequest request) {
        log.warn("Session not found: {}", ex.getMessage());
        return buildErrorResponse(
                "SESSION_NOT_FOUND",
                ex.getMessage(),
                request.getDescription(false),
                HttpStatus.NOT_FOUND
        );
    }

    @ExceptionHandler(InvalidAudioException.class)
    public ResponseEntity<ErrorResponse> handleInvalidAudio(
            InvalidAudioException ex, WebRequest request) {
        log.warn("Invalid audio: {}", ex.getMessage());
        return buildErrorResponse(
                "INVALID_AUDIO",
                ex.getMessage(),
                request.getDescription(false),
                HttpStatus.BAD_REQUEST
        );
    }

    @ExceptionHandler(ServiceUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleServiceUnavailable(
            ServiceUnavailableException ex, WebRequest request) {
        log.error("Service unavailable: {}", ex.getMessage(), ex);
        return buildErrorResponse(
                "SERVICE_UNAVAILABLE",
                "The service is temporarily unavailable. Please try again later.",
                request.getDescription(false),
                HttpStatus.SERVICE_UNAVAILABLE
        );
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleMaxUploadSize(
            MaxUploadSizeExceededException ex, WebRequest request) {
        log.warn("Upload size exceeded: {}", ex.getMessage());
        return buildErrorResponse(
                "FILE_TOO_LARGE",
                "Audio file is too large. Maximum size is 10MB.",
                request.getDescription(false),
                HttpStatus.PAYLOAD_TOO_LARGE
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(
            Exception ex, WebRequest request) {
        log.error("Unexpected error: {}", ex.getMessage(), ex);
        return buildErrorResponse(
                "INTERNAL_ERROR",
                "An unexpected error occurred. Please try again.",
                request.getDescription(false),
                HttpStatus.INTERNAL_SERVER_ERROR
        );
    }

    private ResponseEntity<ErrorResponse> buildErrorResponse(
            String error, String message, String path, HttpStatus status) {
        ErrorResponse errorResponse = new ErrorResponse(
                error,
                message,
                Instant.now().toString(),
                path.replace("uri=", "")
        );
        return ResponseEntity.status(status).body(errorResponse);
    }
}
