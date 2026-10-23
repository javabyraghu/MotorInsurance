package com.motorsure.coreapi.product.infrastructure.persistence;

import com.motorsure.coreapi.product.domain.Product;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import java.util.UUID;
import java.time.Instant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ProductRepository extends JpaRepository<Product, UUID> {
    @Query(value = "select distinct p from Product p join ProductVersion v on v.product.id = p.id " +
            "where p.status = com.motorsure.coreapi.product.domain.ProductStatus.ACTIVE " +
            "and v.status = com.motorsure.coreapi.product.domain.ProductVersionStatus.ACTIVE " +
            "and v.effectiveFrom <= :at and (v.effectiveTo is null or v.effectiveTo > :at)",
            countQuery = "select count(distinct p) from Product p join ProductVersion v on v.product.id = p.id " +
                    "where p.status = com.motorsure.coreapi.product.domain.ProductStatus.ACTIVE " +
                    "and v.status = com.motorsure.coreapi.product.domain.ProductVersionStatus.ACTIVE " +
                    "and v.effectiveFrom <= :at and (v.effectiveTo is null or v.effectiveTo > :at)")
    Page<Product> findAvailableAt(@Param("at") Instant at, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.id = :id")
    Optional<Product> findByIdForUpdate(@Param("id") UUID id);
}
