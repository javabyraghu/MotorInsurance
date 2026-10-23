package com.motorsure.coreapi.quote.api;

import com.motorsure.coreapi.quote.domain.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class QuoteResponses {
    private QuoteResponses() { }
    public record VehicleView(VehicleCategory vehicleCategory, String manufacturer, String model,
            int manufactureYear, String registrationNumber, FuelType fuelType, UsageCategory usageCategory,
            BigDecimal declaredVehicleValue, String declaredVehicleCurrency) {
        public static VehicleView from(QuoteVehicleRisk v) { return new VehicleView(v.getVehicleCategory(), v.getManufacturer(), v.getModel(),
                v.getManufactureYear(), v.getRegistrationNumber(), v.getFuelType(), v.getUsageCategory(),
                v.getDeclaredVehicleValue(), v.getDeclaredVehicleCurrency()); }
    }
    public record CoverageSnapshot(UUID id, UUID sourceProductVersionId, String coverageCode, String displayName,
            String description, String coverageType, boolean mandatory, boolean optional, boolean available, Instant snapshotCreatedAt) {
        public static CoverageSnapshot from(QuoteCoverageSnapshot c) { return new CoverageSnapshot(c.getId(), c.getSourceProductVersionId(),
                c.getCoverageCode(), c.getDisplayName(), c.getDescription(), c.getCoverageType(), c.isMandatory(), c.isOptional(),
                c.isAvailable(), c.getSnapshotCreatedAt()); }
    }
    public record View(UUID id, String quoteNumber, UUID customerId, UUID productId, UUID productVersionId,
            QuoteStatus status, PricingStatus pricingStatus, Instant coverageEffectiveAt, Instant expiresAt,
            Instant createdAt, Instant updatedAt, VehicleView vehicle, List<CoverageSnapshot> coverageSnapshot) { }
    public record PageView(List<View> items, int page, int size, long totalElements, int totalPages) { }
}
