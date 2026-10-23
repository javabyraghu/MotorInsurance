package com.motorsure.quote.infrastructure.persistence;

import com.motorsure.quote.domain.Quote;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface QuoteRepository extends JpaRepository<Quote, UUID> {}
