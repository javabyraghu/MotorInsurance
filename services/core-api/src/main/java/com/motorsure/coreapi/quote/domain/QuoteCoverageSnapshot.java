package com.motorsure.coreapi.quote.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "quote_coverage_snapshots", uniqueConstraints = @UniqueConstraint(
        name = "quote_coverage_snapshot_quote_code_unique", columnNames = {"quote_id", "coverage_code"}))
public class QuoteCoverageSnapshot {
    @Id private UUID id;
    @Column(name = "quote_id", nullable = false, updatable = false) private UUID quoteId;
    @Column(name = "source_product_version_id", nullable = false, updatable = false) private UUID sourceProductVersionId;
    @Column(name = "coverage_code", nullable = false, length = 64, updatable = false) private String coverageCode;
    @Column(name = "display_name", nullable = false, length = 120, updatable = false) private String displayName;
    @Column(length = 2000, updatable = false) private String description;
    @Column(name = "coverage_type", nullable = false, length = 32, updatable = false) private String coverageType;
    @Column(name = "is_mandatory", nullable = false, updatable = false) private boolean mandatory;
    @Column(name = "is_optional", nullable = false, updatable = false) private boolean optional;
    @Column(name = "is_available", nullable = false, updatable = false) private boolean available;
    @Column(name = "snapshot_created_at", nullable = false, updatable = false) private Instant snapshotCreatedAt;

    protected QuoteCoverageSnapshot() { }
    public static QuoteCoverageSnapshot from(UUID quoteId, UUID versionId, String coverageCode,
            String displayName, String description, String coverageType, boolean mandatory,
            boolean optional, boolean available, Instant at) {
        QuoteCoverageSnapshot snapshot = new QuoteCoverageSnapshot();
        snapshot.id = UUID.randomUUID();
        snapshot.quoteId = quoteId;
        snapshot.sourceProductVersionId = versionId;
        snapshot.coverageCode = coverageCode;
        snapshot.displayName = displayName;
        snapshot.description = description;
        snapshot.coverageType = coverageType;
        snapshot.mandatory = mandatory;
        snapshot.optional = optional;
        snapshot.available = available;
        snapshot.snapshotCreatedAt = at;
        return snapshot;
    }
    public UUID getId() { return id; }
    public UUID getQuoteId() { return quoteId; }
    public UUID getSourceProductVersionId() { return sourceProductVersionId; }
    public String getCoverageCode() { return coverageCode; }
    public String getDisplayName() { return displayName; }
    public String getDescription() { return description; }
    public String getCoverageType() { return coverageType; }
    public boolean isMandatory() { return mandatory; }
    public boolean isOptional() { return optional; }
    public boolean isAvailable() { return available; }
    public Instant getSnapshotCreatedAt() { return snapshotCreatedAt; }
}
