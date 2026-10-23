package com.motorsure.coreapi.product.infrastructure.persistence;

import com.motorsure.coreapi.product.domain.ProductVersion;
import com.motorsure.coreapi.product.domain.ProductVersionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductVersionRepository extends JpaRepository<ProductVersion, UUID> {
    Optional<ProductVersion> findTopByProduct_IdOrderByVersionNumberDesc(UUID productId);
    @EntityGraph(attributePaths = {"product", "coverages"})
    Optional<ProductVersion> findByIdAndProduct_Id(UUID id, UUID productId);
    List<ProductVersion> findAllByProduct_IdAndStatus(UUID productId, ProductVersionStatus status);
    @EntityGraph(attributePaths = {"product", "coverages"})
    List<ProductVersion> findAllByProduct_IdOrderByVersionNumberDesc(UUID productId);
}
