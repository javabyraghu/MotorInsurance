package com.motorsure.coreapi.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
class ErrorHandlingTestController {

    @PostMapping("/test-only/validation")
    void validate(@Valid @RequestBody ValidationProbe request) {
    }

    @GetMapping("/test-only/missing")
    void missing() {
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Sensitive lookup value");
    }

    @GetMapping("/test-only/conflict")
    void conflict() {
        throw new ResponseStatusException(HttpStatus.CONFLICT, "Internal conflict detail");
    }

    @GetMapping("/test-only/unexpected")
    void unexpected() {
        throw new IllegalStateException("Sensitive persistence detail");
    }

    record ValidationProbe(
            @NotBlank(message = "must not be blank")
            @Size(max = 8, message = "must be at most 8 characters")
            String value) {
    }
}
