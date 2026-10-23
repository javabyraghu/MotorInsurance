package com.motorsure.coreapi.quote.infrastructure.persistence;

import com.motorsure.coreapi.quote.domain.QuoteVehicleRisk;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface QuoteVehicleRiskRepository extends JpaRepository<QuoteVehicleRisk, UUID> { }
