package com.motorsure.coreapi.product.api;

import com.motorsure.coreapi.product.domain.CoverageType;
import com.motorsure.coreapi.product.domain.ProductType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;

public final class ProductRequests {
    private ProductRequests() { }

    public record ProductWrite(
            @NotBlank @Pattern(regexp = "[A-Za-z][A-Za-z0-9_-]{1,31}") String productCode,
            @NotBlank @Size(max = 160) String name,
            @Size(max = 2000) String description,
            @NotNull ProductType productType) { }

    public record ProductUpdate(
            @NotBlank @Size(max = 160) String name,
            @Size(max = 2000) String description,
            @NotNull ProductType productType) { }

    public record VersionWrite(
            @NotNull Instant effectiveFrom,
            Instant effectiveTo,
            @NotEmpty @Size(max = 100) List<@NotNull @Valid Coverage> coverages) {
        @AssertTrue(message = "effectiveTo must be later than effectiveFrom")
        public boolean isEffectiveRangeValid() {
            return effectiveFrom == null || effectiveTo == null || effectiveTo.isAfter(effectiveFrom);
        }
    }

    public record Coverage(
            @NotBlank @Pattern(regexp = "[A-Za-z][A-Za-z0-9_]{1,63}") String code,
            @NotBlank @Size(max = 120) String displayName,
            @Size(max = 2000) String description,
            @NotNull CoverageType coverageType,
            @NotNull Boolean mandatory,
            @NotNull Boolean optional,
            @NotNull Boolean available) {
        @AssertTrue(message = "exactly one of mandatory or optional must be true")
        public boolean isSelectionValid() {
            return mandatory != null && optional != null && available != null
                    && mandatory.booleanValue() != optional.booleanValue()
                    && (!mandatory || available);
        }
    }
}
