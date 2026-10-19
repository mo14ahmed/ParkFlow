package com.parkflow.ParkFlow;
import java.util.Map;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.converter.HttpMessageNotReadableException;
@RestControllerAdvice
public class ApiErrors {
 @ExceptionHandler(ResponseStatusException.class)
 ResponseEntity<?> status(ResponseStatusException e) { return ResponseEntity.status(e.getStatusCode()).body(Map.of("error",e.getReason()==null?"Request failed":e.getReason())); }
 @ExceptionHandler(MaxUploadSizeExceededException.class)
 ResponseEntity<?> large(Exception e) { return ResponseEntity.status(413).body(Map.of("error","File too large. Maximum file size is 10 MB.")); }
 @ExceptionHandler({MethodArgumentNotValidException.class,HttpMessageNotReadableException.class})
 ResponseEntity<?> invalid(Exception e) { return ResponseEntity.badRequest().body(Map.of("error","Send valid JSON with a non-empty text field (maximum 100,000 characters).")); }
}
