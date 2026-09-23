package com.pawpasta.glowscan_be.shared.handler;


import com.pawpasta.glowscan_be.shared.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;


@Slf4j
@RestControllerAdvice
public class GlobalHandleException {

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiResponse<Void>> handle(ResponseStatusException exception) {

        String message = exception.getStatusCode().is5xxServerError()
                ? "System Error"
                : exception.getReason();

        if(message == null){
            message = "Request failed";
        }
        return ResponseEntity
                .status(exception.getStatusCode())
                .headers(exception.getHeaders())
                .body(ApiResponse.error(message));

    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .filter(fieldMessage -> fieldMessage != null && !fieldMessage.isBlank())
                .findFirst()
                .orElse("Validation failed");

        return ResponseEntity.badRequest().body(ApiResponse.error(message));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalState(IllegalStateException exception) {
        log.error("Invalid application state", exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("A system error has occurred. Please try again later."));
    }
}
