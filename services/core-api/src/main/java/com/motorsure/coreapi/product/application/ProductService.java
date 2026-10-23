package com.motorsure.coreapi.product.application;

import com.motorsure.coreapi.product.domain.*;
import com.motorsure.coreapi.product.domain.ProductVersion.CoverageDefinition;
import com.motorsure.coreapi.product.infrastructure.persistence.ProductRepository;
import com.motorsure.coreapi.product.infrastructure.persistence.ProductVersionRepository;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class ProductService {
    private final ProductRepository products;
    private final ProductVersionRepository versions;
    private final Clock clock;

    public ProductService(ProductRepository products, ProductVersionRepository versions, Clock clock) {
        this.products = products; this.versions = versions; this.clock = clock;
    }

    public Product create(String code, String name, String description, ProductType type) {
        return products.save(Product.draft(UUID.randomUUID(), code, name, description, type, clock));
    }

    @Transactional(readOnly = true)
    public Product get(UUID id) { return products.findById(id).orElseThrow(ProductService::notFound); }

    @Transactional(readOnly = true)
    public Page<Product> list(int page, int size, String sortBy, Sort.Direction direction) {
        return products.findAll(PageRequest.of(page, size,
                Sort.by(direction, sortBy).and(Sort.by(Sort.Direction.ASC, "id"))));
    }

    @Transactional(readOnly = true)
    public Page<Product> listAvailable(Instant at, int page, int size, String sortBy, Sort.Direction direction) {
        return products.findAvailableAt(at == null ? Instant.now(clock) : at, PageRequest.of(page, size,
                Sort.by(direction, sortBy).and(Sort.by(Sort.Direction.ASC, "id"))));
    }

    public ProductQuoteConfiguration quoteConfiguration(UUID productId, UUID versionId, Instant effectiveAt) {
        Product product = lockProduct(productId);
        ProductVersion version = versions.findByIdAndProduct_Id(versionId, productId)
                .orElseThrow(ProductService::notFound);
        if (product.getStatus() != ProductStatus.ACTIVE || version.getStatus() != ProductVersionStatus.ACTIVE
                || version.getEffectiveFrom().isAfter(effectiveAt)
                || (version.getEffectiveTo() != null && !effectiveAt.isBefore(version.getEffectiveTo()))) {
            throw conflict("Selected product version is not active at the requested coverage instant");
        }
        return new ProductQuoteConfiguration(productId, versionId, version.getCoverages().stream()
                .map(coverage -> new ProductQuoteConfiguration.Coverage(coverage.getCoverageCode(),
                        coverage.getDisplayName(), coverage.getDescription(), coverage.getCoverageType(),
                        coverage.isMandatory(), coverage.isOptional(), coverage.isAvailable()))
                .toList());
    }

    public Product updateDraft(UUID id, String name, String description, ProductType type) {
        Product product = lockProduct(id);
        if (product.getStatus() != ProductStatus.DRAFT) throw conflict("Only draft products can be edited");
        product.updateDraft(name, description, type, clock);
        return product;
    }

    public ProductVersion createVersion(UUID productId, Instant effectiveFrom, Instant effectiveTo,
                                        List<CoverageDefinition> coverages) {
        Product product = lockProduct(productId);
        if (product.getStatus() == ProductStatus.INACTIVE) throw conflict("Inactive products cannot receive versions");
        validateEffectiveRange(effectiveFrom, effectiveTo);
        validateCoverageCodes(coverages);
        int nextNumber = versions.findTopByProduct_IdOrderByVersionNumberDesc(productId)
                .map(existing -> existing.getVersionNumber() + 1).orElse(1);
        return versions.save(ProductVersion.draft(UUID.randomUUID(), product, nextNumber,
                effectiveFrom, effectiveTo, coverages, clock));
    }

    public ProductVersion updateDraftVersion(UUID productId, UUID versionId, Instant from, Instant to,
                                             List<CoverageDefinition> coverages) {
        lockProduct(productId);
        ProductVersion version = getVersion(productId, versionId);
        validateEffectiveRange(from, to);
        validateCoverageCodes(coverages);
        if (version.getStatus() != ProductVersionStatus.DRAFT) throw conflict("Only draft versions can be edited");
        version.editDraft(from, to, coverages);
        return version;
    }

    public ProductVersion activateVersion(UUID productId, UUID versionId) {
        Product product = lockProduct(productId);
        ProductVersion target = getVersion(productId, versionId);
        if (product.getStatus() == ProductStatus.INACTIVE) throw conflict("Inactive products cannot be activated");
        if (target.getStatus() != ProductVersionStatus.DRAFT) throw conflict("Only draft versions can be activated");
        if (target.getCoverages().isEmpty()) throw badRequest("At least one coverage is required");
        for (ProductVersion active : versions.findAllByProduct_IdAndStatus(productId, ProductVersionStatus.ACTIVE)) {
            if (overlaps(target.getEffectiveFrom(), target.getEffectiveTo(), active.getEffectiveFrom(), active.getEffectiveTo())) {
                throw conflict("Product version effective periods cannot overlap");
            }
        }
        target.activate();
        product.activate(clock);
        return target;
    }

    public ProductVersion deactivateVersion(UUID productId, UUID versionId) {
        Product product = lockProduct(productId);
        ProductVersion version = getVersion(productId, versionId);
        if (version.getStatus() != ProductVersionStatus.ACTIVE) throw conflict("Only active versions can be deactivated");
        version.deactivate();
        if (versions.findAllByProduct_IdAndStatus(productId, ProductVersionStatus.ACTIVE).stream()
                .noneMatch(active -> !active.getId().equals(versionId))) product.deactivate(clock);
        return version;
    }

    public Product deactivateProduct(UUID id) {
        Product product = lockProduct(id);
        if (product.getStatus() != ProductStatus.ACTIVE) throw conflict("Only active products can be deactivated");
        product.deactivate(clock);
        return product;
    }

    @Transactional(readOnly = true)
    public ProductVersion getVersion(UUID productId, UUID versionId) {
        if (!products.existsById(productId)) throw notFound();
        return versions.findByIdAndProduct_Id(versionId, productId).orElseThrow(ProductService::notFound);
    }

    @Transactional(readOnly = true)
    public List<ProductVersion> listVersions(UUID productId) {
        if (!products.existsById(productId)) throw notFound();
        return versions.findAllByProduct_IdOrderByVersionNumberDesc(productId);
    }

    private Product lockProduct(UUID id) { return products.findByIdForUpdate(id).orElseThrow(ProductService::notFound); }
    private static void validateEffectiveRange(Instant from, Instant to) {
        if (from == null || (to != null && !to.isAfter(from))) throw badRequest("Invalid effective date range");
    }
    private static void validateCoverageCodes(List<CoverageDefinition> coverages) {
        long distinct = coverages.stream().map(c -> c.code().trim().toUpperCase(java.util.Locale.ROOT)).distinct().count();
        if (distinct != coverages.size()) throw conflict("Coverage codes must be unique within a product version");
        if (coverages.isEmpty()) throw badRequest("At least one coverage is required");
    }
    private static boolean overlaps(Instant aFrom, Instant aTo, Instant bFrom, Instant bTo) {
        return (aTo == null || bFrom.isBefore(aTo)) && (bTo == null || aFrom.isBefore(bTo));
    }
    private static ResponseStatusException notFound() { return new ResponseStatusException(HttpStatus.NOT_FOUND, "Product resource not found"); }
    private static ResponseStatusException conflict(String message) { return new ResponseStatusException(HttpStatus.CONFLICT, message); }
    private static ResponseStatusException badRequest(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
}
