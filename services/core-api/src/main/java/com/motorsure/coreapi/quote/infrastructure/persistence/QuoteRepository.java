package com.motorsure.coreapi.quote.infrastructure.persistence;

import com.motorsure.coreapi.quote.domain.Quote;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface QuoteRepository extends JpaRepository<Quote, UUID> {
    Page<Quote> findAllByCustomerId(UUID customerId, Pageable pageable);
}
