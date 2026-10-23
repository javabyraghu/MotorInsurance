package com.motorsure.coreapi.common.web;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.net.URI;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ProblemDetail> handleValidation(MethodArgumentNotValidException exception,
                                                   HttpServletRequest request) {
        List<Map<String, String>> invalidParams = exception.getBindingResult().getFieldErrors().stream()
                .sorted(Comparator.comparing(error -> error.getField() + error.getCode()))
                .map(error -> Map.of(
                        "name", error.getField(),
                        "reason", safeValidationMessage(error.getDefaultMessage())))
                .toList();

        ProblemDetail problem = problem(
                HttpStatus.BAD_REQUEST,
                "Request validation failed",
                "One or more request fields are invalid.",
                "request.validation_failed",
                "urn:motorsure:problem:validation-error",
                request);
        problem.setProperty("invalidParams", invalidParams);
        return ResponseEntity.badRequest().body(problem);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ProblemDetail> handleUnreadableRequest(HttpServletRequest request) {
        return response(
                HttpStatus.BAD_REQUEST,
                "Malformed request",
                "The request body is missing or could not be parsed.",
                "request.malformed",
                "urn:motorsure:problem:malformed-request",
                request);
    }

    @ExceptionHandler({DataIntegrityViolationException.class, ObjectOptimisticLockingFailureException.class})
    ResponseEntity<ProblemDetail> handleConflict(Exception exception, HttpServletRequest request) {
        return response(HttpStatus.CONFLICT, "Request conflict",
                "The request conflicts with the current resource state.", "request.conflict",
                "urn:motorsure:problem:conflict", request);
    }

    private ResponseEntity<ProblemDetail> handleFrameworkError(ErrorResponse exception, HttpServletRequest request) {
        HttpStatusCode status = exception.getStatusCode();
        HttpStatus knownStatus = HttpStatus.resolve(status.value());
        String title = knownStatus == null ? "Request failed" : knownStatus.getReasonPhrase();
        String detail = safeDetailFor(status);
        String code = errorCodeFor(status);
        return response(status, title, detail, code, "urn:motorsure:problem:http-error", request);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> handleUnexpected(Exception exception, HttpServletRequest request) {
        if (exception instanceof ErrorResponse frameworkError) {
            return handleFrameworkError(frameworkError, request);
        }

        String correlationId = correlationId(request);
        Object route = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        String routePattern = route instanceof String pattern ? pattern : "unmatched";
        LOGGER.error("Unhandled API exception type={} method={} route={} correlationId={}",
                exception.getClass().getName(), request.getMethod(), routePattern, correlationId,
                sanitizedStackTrace(exception));

        return response(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Internal server error",
                "The server could not complete the request.",
                "internal.error",
                "urn:motorsure:problem:internal-error",
                request);
    }

    private ResponseEntity<ProblemDetail> response(HttpStatusCode status, String title, String detail, String code,
                                                   String type, HttpServletRequest request) {
        return ResponseEntity.status(status).body(problem(status, title, detail, code, type, request));
    }

    private ProblemDetail problem(HttpStatusCode status, String title, String detail, String code, String type,
                                  HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create(type));
        problem.setTitle(title);
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("code", code);
        problem.setProperty("correlationId", correlationId(request));
        return problem;
    }

    private String correlationId(HttpServletRequest request) {
        Object value = request.getAttribute(CorrelationIdFilter.REQUEST_ATTRIBUTE);
        return value instanceof String id ? id : "unavailable";
    }

    private String safeValidationMessage(String message) {
        if (message == null || message.isBlank()) {
            return "is invalid";
        }
        return message.length() <= 160 ? message : message.substring(0, 160);
    }

    private String safeDetailFor(HttpStatusCode status) {
        return switch (status.value()) {
            case 400 -> "The request is invalid.";
            case 401 -> "Authentication is required to access this resource.";
            case 403 -> "You are not permitted to access this resource.";
            case 404 -> "The requested resource was not found.";
            case 405 -> "The request method is not supported for this resource.";
            case 409 -> "The request conflicts with the current resource state.";
            case 413 -> "The request is larger than the server allows.";
            case 415 -> "The request content type is not supported.";
            case 429 -> "Too many requests. Try again later.";
            default -> "The request could not be completed.";
        };
    }

    private String errorCodeFor(HttpStatusCode status) {
        return switch (status.value()) {
            case 400 -> "request.invalid";
            case 401 -> "security.authentication_required";
            case 403 -> "security.forbidden";
            case 404 -> "resource.not_found";
            case 405 -> "request.method_not_allowed";
            case 409 -> "request.conflict";
            case 413 -> "request.too_large";
            case 415 -> "request.unsupported_media_type";
            case 429 -> "request.rate_limited";
            default -> "request.failed";
        };
    }

    private Throwable sanitizedStackTrace(Exception exception) {
        RuntimeException safeDiagnostic = new RuntimeException(exception.getClass().getName());
        safeDiagnostic.setStackTrace(exception.getStackTrace());
        return safeDiagnostic;
    }
}
