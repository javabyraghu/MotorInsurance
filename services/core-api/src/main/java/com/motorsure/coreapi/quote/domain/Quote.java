package com.motorsure.coreapi.quote.domain;

import jakarta.persistence.*;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "quotes")
public class Quote {
    @Id private UUID id;
    @Column(name = "quote_number", nullable = false, unique = true, length = 40, updatable = false) private String quoteNumber;
    @Column(name = "customer_id", nullable = false, updatable = false) private UUID customerId;
    @Column(name = "product_id", nullable = false, updatable = false) private UUID productId;
    @Column(name = "product_version_id", nullable = false, updatable = false) private UUID productVersionId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 32) private QuoteStatus status;
    @Enumerated(EnumType.STRING) @Column(name = "pricing_status", nullable = false, length = 32, updatable = false) private PricingStatus pricingStatus;
    @Column(name = "coverage_effective_at", nullable = false, updatable = false) private Instant coverageEffectiveAt;
    @Column(name = "expires_at") private Instant expiresAt;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Version @Column(name = "lock_version", nullable = false) private long lockVersion;

    protected Quote() { }

    public static Quote draft(UUID id, UUID customerId, UUID productId, UUID productVersionId,
                              Instant coverageEffectiveAt, Clock clock) {
        Quote quote = new Quote();
        quote.id = id;
        quote.quoteNumber = "QTE-" + id.toString().replace("-", "").toUpperCase(java.util.Locale.ROOT);
        quote.customerId = customerId;
        quote.productId = productId;
        quote.productVersionId = productVersionId;
        quote.status = QuoteStatus.DRAFT;
        quote.pricingStatus = PricingStatus.NOT_CALCULATED;
        quote.coverageEffectiveAt = coverageEffectiveAt;
        quote.createdAt = Instant.now(clock);
        quote.updatedAt = quote.createdAt;
        return quote;
    }

    public void submit(Clock clock) {
        if (status != QuoteStatus.DRAFT) throw new IllegalStateException("Quote cannot be submitted from its current status");
        status = QuoteStatus.PENDING_UNDERWRITING;
        updatedAt = Instant.now(clock);
    }

    public void cancel(Clock clock) {
        if (status != QuoteStatus.DRAFT && status != QuoteStatus.PENDING_UNDERWRITING)
            throw new IllegalStateException("Quote cannot be cancelled from its current status");
        status = QuoteStatus.CANCELLED;
        updatedAt = Instant.now(clock);
    }

    public UUID getId() { return id; }
    public String getQuoteNumber() { return quoteNumber; }
    public UUID getCustomerId() { return customerId; }
    public UUID getProductId() { return productId; }
    public UUID getProductVersionId() { return productVersionId; }
    public QuoteStatus getStatus() { return status; }
    public PricingStatus getPricingStatus() { return pricingStatus; }
    public Instant getCoverageEffectiveAt() { return coverageEffectiveAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
