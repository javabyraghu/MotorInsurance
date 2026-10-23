package com.motorsure.policy;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record PolicyRequest(
        @NotBlank @Size(max = 160) String customerName,
        @NotBlank @Size(max = 16) @Pattern(regexp = "[A-Za-z0-9 -]+") String vehicleRegistration,
        @NotBlank @Size(max = 80) String vehicleMake,
        @NotBlank @Pattern(regexp = "(?i)Comprehensive|Third party") String coverType) {}
