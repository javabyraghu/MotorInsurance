package com.motorsure.coreapi.product.domain;

import jakarta.persistence.*;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Entity
@Table(name = "products")
public class Product {
    @Id private UUID id;
    @Column(name = "product_code", nullable = false, unique = true, length = 32) private String productCode;
    @Column(nullable = false, length = 160) private String name;
    @Column(length = 2000) private String description;
    @Enumerated(EnumType.STRING) @Column(name = "product_type", nullable = false, length = 32) private ProductType productType;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 8) private ProductStatus status;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Version @Column(nullable = false) private long version;

    protected Product() { }

    public static Product draft(UUID id, String productCode, String name, String description,
                                ProductType productType, Clock clock) {
        Product product = new Product();
        product.id = id;
        product.productCode = productCode.trim().toUpperCase(Locale.ROOT);
        product.name = name.trim();
        product.description = cleanOptional(description);
        product.productType = productType;
        product.status = ProductStatus.DRAFT;
        product.createdAt = Instant.now(clock);
        product.updatedAt = product.createdAt;
        return product;
    }

    public void updateDraft(String name, String description, ProductType productType, Clock clock) {
        if (status != ProductStatus.DRAFT) throw new IllegalStateException("Only draft products can be edited");
        this.name = name.trim();
        this.description = cleanOptional(description);
        this.productType = productType;
        this.updatedAt = Instant.now(clock);
    }

    public void activate(Clock clock) { status = ProductStatus.ACTIVE; updatedAt = Instant.now(clock); }
    public void deactivate(Clock clock) { status = ProductStatus.INACTIVE; updatedAt = Instant.now(clock); }

    private static String cleanOptional(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    public UUID getId() { return id; }
    public String getProductCode() { return productCode; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public ProductType getProductType() { return productType; }
    public ProductStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
