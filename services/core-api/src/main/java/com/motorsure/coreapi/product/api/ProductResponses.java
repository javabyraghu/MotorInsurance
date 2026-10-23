package com.motorsure.coreapi.product.api;

import com.motorsure.coreapi.product.domain.*;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class ProductResponses {
    private ProductResponses() { }

    public record ProductView(UUID id, String productCode, String name, String description,
                              ProductType productType, ProductStatus status, Instant createdAt, Instant updatedAt) {
        public static ProductView from(Product p) {
            return new ProductView(p.getId(), p.getProductCode(), p.getName(), p.getDescription(),
                    p.getProductType(), p.getStatus(), p.getCreatedAt(), p.getUpdatedAt());
        }
    }

    public record CoverageView(String code, String displayName, String description, CoverageType coverageType,
                               boolean mandatory, boolean optional, boolean available) {
        public static CoverageView from(ProductVersionCoverage c) {
            return new CoverageView(c.getCoverageCode(), c.getDisplayName(), c.getDescription(), c.getCoverageType(),
                    c.isMandatory(), c.isOptional(), c.isAvailable());
        }
    }

    public record VersionView(UUID id, UUID productId, int versionNumber, Instant effectiveFrom, Instant effectiveTo,
                              ProductVersionStatus status, Instant createdAt, List<CoverageView> coverages) {
        public static VersionView from(ProductVersion v) {
            return new VersionView(v.getId(), v.getProduct().getId(), v.getVersionNumber(), v.getEffectiveFrom(),
                    v.getEffectiveTo(), v.getStatus(), v.getCreatedAt(),
                    v.getCoverages().stream().map(CoverageView::from).toList());
        }
    }

    public record PageView<T>(List<T> items, int page, int size, long totalElements, int totalPages) { }
}
