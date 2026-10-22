package com.parkflow.ParkFlow.common;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.server.ResponseStatusException;

/** Turns errors into {"error": "..."} JSON so every client (web, iOS, ESP32) can show the message. */
@RestControllerAdvice
public class ApiErrors {

    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<Map<String, Object>> status(ResponseStatusException e) {
        return ResponseEntity.status(e.getStatusCode())
                .body(Map.of("error", e.getReason() == null ? "Request failed" : e.getReason()));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<Map<String, Object>> large(Exception e) {
        return ResponseEntity.status(413).body(Map.of("error", "File too large. Maximum file size is 10 MB."));
    }

    /** Bean validation on a JSON body: returns the first message plus every field's message. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Map<String, Object>> invalid(MethodArgumentNotValidException e) {
        Map<String, String> fields = new LinkedHashMap<>();
        e.getBindingResult().getFieldErrors().forEach(f -> fields.putIfAbsent(f.getField(), f.getDefaultMessage()));
        String first = fields.isEmpty() ? "Some fields are not valid." : fields.values().iterator().next();
        return ResponseEntity.badRequest().body(Map.of("error", first, "fields", fields));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<Map<String, Object>> unreadable(Exception e) {
        return ResponseEntity.badRequest().body(Map.of("error", "Send a valid JSON body."));
    }

    @ExceptionHandler({MissingServletRequestParameterException.class, MethodArgumentTypeMismatchException.class,
            HandlerMethodValidationException.class})
    ResponseEntity<Map<String, Object>> badParameter(Exception e) {
        return ResponseEntity.badRequest().body(Map.of("error", "A required parameter is missing or not valid."));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<Map<String, Object>> conflict(Exception e) {
        return ResponseEntity.status(409).body(Map.of("error", "That conflicts with existing data. Check the values and try again."));
    }
}
