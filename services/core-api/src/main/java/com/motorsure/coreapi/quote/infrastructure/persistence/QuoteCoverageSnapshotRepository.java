package com.motorsure.coreapi.quote.infrastructure.persistence;

import com.motorsure.coreapi.quote.domain.QuoteCoverageSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface QuoteCoverageSnapshotRepository extends JpaRepository<QuoteCoverageSnapshot, UUID> {
    List<QuoteCoverageSnapshot> findAllByQuoteIdOrderByCoverageCode(UUID quoteId);
    List<QuoteCoverageSnapshot> findAllByQuoteIdInOrderByQuoteIdAscCoverageCodeAsc(Collection<UUID> quoteIds);
}
