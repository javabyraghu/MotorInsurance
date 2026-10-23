package com.motorsure.claims.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record ClaimRequest(
        @NotBlank @Size(max = 64) String policyNumber,
        @NotBlank @Size(max = 16) @Pattern(regexp = "[A-Za-z0-9 -]+") String vehicleRegistration,
        @NotBlank @Size(max = 64) String incidentType,
        @NotNull @PastOrPresent LocalDate incidentDate,
        @NotBlank @Size(max = 1000) String description) {}
