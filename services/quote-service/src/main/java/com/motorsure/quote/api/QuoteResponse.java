package com.motorsure.quote.api;

import com.motorsure.quote.domain.Quote;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record QuoteResponse(UUID id, String customerName, String vehicleRegistration,
                           String vehicleMake, String coverType, BigDecimal annualPremium,
                           String status, Instant createdAt) {
    public static QuoteResponse from(Quote quote) {
        return new QuoteResponse(quote.getId(), quote.getCustomerName(), quote.getVehicleRegistration(),
                quote.getVehicleMake(), quote.getCoverType(), quote.getAnnualPremium(), quote.getStatus(), quote.getCreatedAt());
    }
}
