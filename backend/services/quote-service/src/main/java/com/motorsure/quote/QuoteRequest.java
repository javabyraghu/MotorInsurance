package com.motorsure.quote;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record QuoteRequest(
        @NotBlank @Size(max = 160) String customerName,
        @NotBlank @Size(max = 16) @Pattern(regexp = "[A-Za-z0-9 -]+") String vehicleRegistration,
        @Size(max = 80) String vehicleMake,
        @Pattern(regexp = "(?i)Comprehensive|Third party") String coverType) {}
