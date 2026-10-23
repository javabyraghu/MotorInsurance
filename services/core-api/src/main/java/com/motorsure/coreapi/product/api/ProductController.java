package com.motorsure.coreapi.product.api;

import com.motorsure.coreapi.product.application.ProductService;
import com.motorsure.coreapi.product.domain.ProductVersion;
import com.motorsure.coreapi.product.domain.ProductVersion.CoverageDefinition;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import java.net.URI;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/products")
public class ProductController {
    private static final Set<String> SORT_FIELDS = Set.of("productCode", "name", "productType", "status", "createdAt", "updatedAt");
    private final ProductService service;
    public ProductController(ProductService service) { this.service = service; }

    @PostMapping
    public ResponseEntity<ProductResponses.ProductView> create(@Valid @RequestBody ProductRequests.ProductWrite request) {
        var product = service.create(request.productCode(), request.name(), request.description(), request.productType());
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(product.getId()).toUri();
        return ResponseEntity.created(location).body(ProductResponses.ProductView.from(product));
    }

    @GetMapping
    public ProductResponses.PageView<ProductResponses.ProductView> list(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") Sort.Direction direction) {
        validateSort(sortBy);
        var result = service.list(page, size, sortBy, direction);
        return new ProductResponses.PageView<>(result.getContent().stream().map(ProductResponses.ProductView::from).toList(),
                result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    @GetMapping("/available")
    public ProductResponses.PageView<ProductResponses.ProductView> available(
            @RequestParam(required = false) Instant at,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(defaultValue = "name") String sortBy,
            @RequestParam(defaultValue = "ASC") Sort.Direction direction) {
        validateSort(sortBy);
        var result = service.listAvailable(at, page, size, sortBy, direction);
        return new ProductResponses.PageView<>(result.getContent().stream().map(ProductResponses.ProductView::from).toList(),
                result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    @GetMapping("/{productId}")
    public ProductResponses.ProductView get(@PathVariable UUID productId) {
        return ProductResponses.ProductView.from(service.get(productId));
    }

    @PutMapping("/{productId}")
    public ProductResponses.ProductView update(@PathVariable UUID productId, @Valid @RequestBody ProductRequests.ProductUpdate request) {
        var product = service.updateDraft(productId, request.name(), request.description(), request.productType());
        return ProductResponses.ProductView.from(product);
    }

    @PostMapping("/{productId}/versions")
    public ResponseEntity<ProductResponses.VersionView> createVersion(@PathVariable UUID productId,
            @Valid @RequestBody ProductRequests.VersionWrite request) {
        ProductVersion version = service.createVersion(productId, request.effectiveFrom(), request.effectiveTo(), definitions(request));
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{versionId}")
                .buildAndExpand(version.getId()).toUri();
        return ResponseEntity.created(location).body(ProductResponses.VersionView.from(version));
    }

    @GetMapping("/{productId}/versions")
    public java.util.List<ProductResponses.VersionView> versions(@PathVariable UUID productId) {
        return service.listVersions(productId).stream().map(ProductResponses.VersionView::from).toList();
    }

    @GetMapping("/{productId}/versions/{versionId}")
    public ProductResponses.VersionView version(@PathVariable UUID productId, @PathVariable UUID versionId) {
        return ProductResponses.VersionView.from(service.getVersion(productId, versionId));
    }

    @PutMapping("/{productId}/versions/{versionId}")
    public ProductResponses.VersionView updateVersion(@PathVariable UUID productId, @PathVariable UUID versionId,
            @Valid @RequestBody ProductRequests.VersionWrite request) {
        return ProductResponses.VersionView.from(service.updateDraftVersion(productId, versionId,
                request.effectiveFrom(), request.effectiveTo(), definitions(request)));
    }

    @PostMapping("/{productId}/versions/{versionId}/activate")
    public ProductResponses.VersionView activateVersion(@PathVariable UUID productId, @PathVariable UUID versionId) {
        return ProductResponses.VersionView.from(service.activateVersion(productId, versionId));
    }

    @PostMapping("/{productId}/versions/{versionId}/deactivate")
    public ProductResponses.VersionView deactivateVersion(@PathVariable UUID productId, @PathVariable UUID versionId) {
        return ProductResponses.VersionView.from(service.deactivateVersion(productId, versionId));
    }

    @PostMapping("/{productId}/deactivate")
    public ProductResponses.ProductView deactivateProduct(@PathVariable UUID productId) {
        return ProductResponses.ProductView.from(service.deactivateProduct(productId));
    }

    private void validateSort(String sortBy) {
        if (!SORT_FIELDS.contains(sortBy)) throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.BAD_REQUEST, "Unsupported sort field");
    }

    private java.util.List<CoverageDefinition> definitions(ProductRequests.VersionWrite request) {
        return request.coverages().stream().map(c -> new CoverageDefinition(c.code(), c.displayName(), c.description(),
                c.coverageType(), Boolean.TRUE.equals(c.mandatory()), Boolean.TRUE.equals(c.optional()),
                Boolean.TRUE.equals(c.available()))).toList();
    }
}
