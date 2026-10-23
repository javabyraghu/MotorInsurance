package com.motorsure.quote.api;

import com.motorsure.quote.domain.QuoteNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import java.net.URI;
import java.time.Instant;
import java.util.Map;
import java.util.TreeMap;

@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);
    @ExceptionHandler(QuoteNotFoundException.class)
    ResponseEntity<ProblemDetail> notFound(QuoteNotFoundException ex, HttpServletRequest request) {
        return problem(HttpStatus.NOT_FOUND, "Resource not found", ex.getMessage(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ProblemDetail> validation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        ProblemDetail detail = base(HttpStatus.BAD_REQUEST, "Request validation failed", "One or more fields are invalid", request);
        Map<String, String> errors = new TreeMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error -> errors.putIfAbsent(error.getField(), error.getDefaultMessage()));
        detail.setProperty("fieldErrors", errors);
        return ResponseEntity.badRequest().body(detail);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ProblemDetail> malformed(HttpMessageNotReadableException ex, HttpServletRequest request) {
        return problem(HttpStatus.BAD_REQUEST, "Malformed request", "Request body is missing or malformed", request);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ProblemDetail> typeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid parameter", "A request parameter has an invalid value", request);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ProblemDetail> methodNotAllowed(HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        return problem(HttpStatus.METHOD_NOT_ALLOWED, "Method not allowed", "The HTTP method is not supported for this resource", request);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ProblemDetail> conflict(DataIntegrityViolationException ex, HttpServletRequest request) {
        return problem(HttpStatus.CONFLICT, "Data conflict", "The request conflicts with existing data", request);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> internal(Exception ex, HttpServletRequest request) {
        log.error("Unhandled API exception for {} {}", request.getMethod(), request.getRequestURI(), ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error", "The request could not be completed", request);
    }

    private ResponseEntity<ProblemDetail> problem(HttpStatus status, String title, String message, HttpServletRequest request) {
        return ResponseEntity.status(status).body(base(status, title, message, request));
    }

    private ProblemDetail base(HttpStatus status, String title, String message, HttpServletRequest request) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(status, message);
        detail.setTitle(title);
        detail.setType(URI.create("about:blank"));
        detail.setProperty("timestamp", Instant.now());
        detail.setProperty("path", request.getRequestURI());
        return detail;
    }
}
