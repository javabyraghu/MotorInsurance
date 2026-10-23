package com.motorsure.coreapi.product.domain;

import jakarta.persistence.*;
import java.util.Locale;
import java.util.UUID;

@Entity
@Table(name = "product_version_coverages", uniqueConstraints =
        @UniqueConstraint(name = "product_coverages_version_code_unique", columnNames = {"product_version_id", "coverage_code"}))
public class ProductVersionCoverage {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_version_id", nullable = false) private ProductVersion productVersion;
    @Column(name = "coverage_code", nullable = false, length = 64) private String coverageCode;
    @Column(name = "display_name", nullable = false, length = 120) private String displayName;
    @Column(length = 2000) private String description;
    @Enumerated(EnumType.STRING) @Column(name = "coverage_type", nullable = false, length = 32) private CoverageType coverageType;
    @Column(name = "is_mandatory", nullable = false) private boolean mandatory;
    @Column(name = "is_optional", nullable = false) private boolean optional;
    @Column(name = "is_available", nullable = false) private boolean available;

    protected ProductVersionCoverage() { }
    static ProductVersionCoverage create(UUID id, ProductVersion parent, ProductVersion.CoverageDefinition definition) {
        ProductVersionCoverage coverage = new ProductVersionCoverage();
        coverage.id = id;
        coverage.productVersion = parent;
        coverage.coverageCode = definition.code().trim().toUpperCase(Locale.ROOT);
        coverage.displayName = definition.displayName().trim();
        coverage.description = definition.description() == null || definition.description().isBlank()
                ? null : definition.description().trim();
        coverage.coverageType = definition.type();
        coverage.mandatory = definition.mandatory();
        coverage.optional = definition.optional();
        coverage.available = definition.available();
        return coverage;
    }
    public UUID getId() { return id; }
    public String getCoverageCode() { return coverageCode; }
    public String getDisplayName() { return displayName; }
    public String getDescription() { return description; }
    public CoverageType getCoverageType() { return coverageType; }
    public boolean isMandatory() { return mandatory; }
    public boolean isOptional() { return optional; }
    public boolean isAvailable() { return available; }
}
