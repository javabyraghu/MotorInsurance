package com.motorsure.coreapi.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.motorsure.coreapi.common.web.CorrelationIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;

@Component
public class SecurityProblemWriter {
    private final ObjectMapper objectMapper;

    public SecurityProblemWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void write(HttpServletRequest request, HttpServletResponse response, HttpStatus status) throws IOException {
        boolean unauthorized = status == HttpStatus.UNAUTHORIZED;
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status,
                unauthorized ? "Authentication is required to access this resource."
                        : "The authenticated principal is not authorized to access this resource.");
        problem.setType(URI.create(unauthorized ? "urn:motorsure:problem:authentication-required"
                : "urn:motorsure:problem:access-denied"));
        problem.setTitle(unauthorized ? "Authentication required" : "Access denied");
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("code", unauthorized ? "security.authentication_required" : "security.access_denied");
        Object correlationId = request.getAttribute(CorrelationIdFilter.REQUEST_ATTRIBUTE);
        if (correlationId instanceof String value) {
            problem.setProperty("correlationId", value);
        }
        response.setStatus(status.value());
        if (unauthorized) {
            response.setHeader("WWW-Authenticate", "Bearer");
        }
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), problem);
    }
}
