package com.motorsure.coreapi.product.application;

import com.motorsure.coreapi.product.domain.CoverageType;
import java.util.List;
import java.util.UUID;

/** Immutable product-owned configuration view for creating a quote snapshot. */
public record ProductQuoteConfiguration(UUID productId, UUID productVersionId, List<Coverage> coverages) {
    public ProductQuoteConfiguration {
        coverages = List.copyOf(coverages);
    }

    public record Coverage(String code, String displayName, String description, CoverageType type,
                           boolean mandatory, boolean optional, boolean available) { }
}
