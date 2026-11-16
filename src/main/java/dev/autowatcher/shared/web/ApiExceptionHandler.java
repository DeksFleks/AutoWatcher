package dev.autowatcher.shared.web;

import dev.autowatcher.search.application.ProfileNotFoundException;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(ProfileNotFoundException.class)
    public ResponseEntity<ProblemDetail> notFound(ProfileNotFoundException e) {
        return problem(HttpStatus.NOT_FOUND, "Profile not found");
    }
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ProblemDetail> invalid(IllegalArgumentException e) {
        return problem(HttpStatus.BAD_REQUEST, e.getMessage() == null ? "Invalid request" : e.getMessage());
    }
    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class,
        MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ProblemDetail> malformed(Exception e) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid request body or parameter");
    }
    private ResponseEntity<ProblemDetail> problem(HttpStatus status, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        return ResponseEntity.status(status).body(problem);
    }
}
