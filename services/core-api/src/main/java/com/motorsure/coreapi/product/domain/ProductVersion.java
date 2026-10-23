package com.motorsure.coreapi.product.domain;

import jakarta.persistence.*;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "product_versions", uniqueConstraints =
        @UniqueConstraint(name = "product_versions_product_number_unique", columnNames = {"product_id", "version_number"}))
public class ProductVersion {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false) private Product product;
    @Column(name = "version_number", nullable = false) private int versionNumber;
    @Column(name = "effective_from", nullable = false) private Instant effectiveFrom;
    @Column(name = "effective_to") private Instant effectiveTo;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 8) private ProductVersionStatus status;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Version @Column(name = "lock_version", nullable = false) private long lockVersion;
    @OneToMany(mappedBy = "productVersion", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("coverageCode ASC") private List<ProductVersionCoverage> coverages = new ArrayList<>();

    protected ProductVersion() { }

    public static ProductVersion draft(UUID id, Product product, int versionNumber, Instant effectiveFrom,
                                       Instant effectiveTo, List<CoverageDefinition> definitions, Clock clock) {
        ProductVersion version = new ProductVersion();
        version.id = id;
        version.product = product;
        version.versionNumber = versionNumber;
        version.effectiveFrom = effectiveFrom;
        version.effectiveTo = effectiveTo;
        version.status = ProductVersionStatus.DRAFT;
        version.createdAt = Instant.now(clock);
        version.replaceCoverages(definitions);
        return version;
    }

    public void editDraft(Instant effectiveFrom, Instant effectiveTo, List<CoverageDefinition> definitions) {
        if (status != ProductVersionStatus.DRAFT) throw new IllegalStateException("Only draft versions can be edited");
        this.effectiveFrom = effectiveFrom;
        this.effectiveTo = effectiveTo;
        replaceCoverages(definitions);
    }

    public void activate() {
        if (status != ProductVersionStatus.DRAFT) throw new IllegalStateException("Only draft versions can be activated");
        status = ProductVersionStatus.ACTIVE;
    }
    public void deactivate() {
        if (status != ProductVersionStatus.ACTIVE) throw new IllegalStateException("Only active versions can be deactivated");
        status = ProductVersionStatus.INACTIVE;
    }

    private void replaceCoverages(List<CoverageDefinition> definitions) {
        coverages.clear();
        for (CoverageDefinition definition : definitions) {
            coverages.add(ProductVersionCoverage.create(UUID.randomUUID(), this, definition));
        }
    }

    public record CoverageDefinition(String code, String displayName, String description, CoverageType type,
                                     boolean mandatory, boolean optional, boolean available) { }
    public UUID getId() { return id; }
    public Product getProduct() { return product; }
    public int getVersionNumber() { return versionNumber; }
    public Instant getEffectiveFrom() { return effectiveFrom; }
    public Instant getEffectiveTo() { return effectiveTo; }
    public ProductVersionStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public List<ProductVersionCoverage> getCoverages() { return List.copyOf(coverages); }
}
