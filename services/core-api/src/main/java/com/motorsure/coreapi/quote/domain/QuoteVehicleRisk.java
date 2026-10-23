package com.motorsure.coreapi.quote.domain;

import jakarta.persistence.*;
import org.springframework.data.domain.Persistable;
import java.math.BigDecimal;
import java.util.Locale;
import java.util.UUID;

@Entity
@Table(name = "quote_vehicle_risk")
public class QuoteVehicleRisk implements Persistable<UUID> {
    @Id @Column(name = "quote_id") private UUID quoteId;
    @MapsId @OneToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "quote_id") private Quote quote;
    @Enumerated(EnumType.STRING) @Column(name = "vehicle_category", nullable = false, length = 32) private VehicleCategory vehicleCategory;
    @Column(nullable = false, length = 120) private String manufacturer;
    @Column(nullable = false, length = 120) private String model;
    @Column(name = "manufacture_year", nullable = false) private int manufactureYear;
    @Column(name = "registration_number", length = 32) private String registrationNumber;
    @Enumerated(EnumType.STRING) @Column(name = "fuel_type", length = 16) private FuelType fuelType;
    @Enumerated(EnumType.STRING) @Column(name = "usage_category", nullable = false, length = 24) private UsageCategory usageCategory;
    @Column(name = "declared_vehicle_value", precision = 16, scale = 4) private BigDecimal declaredVehicleValue;
    @Column(name = "declared_vehicle_currency", length = 3) private String declaredVehicleCurrency;
    @Transient private boolean newEntity = true;

    protected QuoteVehicleRisk() { }
    public static QuoteVehicleRisk create(Quote quote, VehicleCategory category, String manufacturer, String model,
            int year, String registrationNumber, FuelType fuelType, UsageCategory usageCategory,
            BigDecimal value, String currency) {
        QuoteVehicleRisk risk = new QuoteVehicleRisk();
        risk.quote = quote;
        risk.quoteId = quote.getId();
        risk.vehicleCategory = category;
        risk.manufacturer = manufacturer.trim();
        risk.model = model.trim();
        risk.manufactureYear = year;
        risk.registrationNumber = registrationNumber == null || registrationNumber.isBlank() ? null : registrationNumber.trim();
        risk.fuelType = fuelType;
        risk.usageCategory = usageCategory;
        risk.declaredVehicleValue = value;
        risk.declaredVehicleCurrency = currency == null ? null : currency.toUpperCase(Locale.ROOT);
        return risk;
    }
    public UUID getQuoteId() { return quoteId; }
    @Override public UUID getId() { return quoteId; }
    @Override public boolean isNew() { return newEntity; }
    @PostPersist @PostLoad private void markNotNew() { newEntity = false; }
    public VehicleCategory getVehicleCategory() { return vehicleCategory; }
    public String getManufacturer() { return manufacturer; }
    public String getModel() { return model; }
    public int getManufactureYear() { return manufactureYear; }
    public String getRegistrationNumber() { return registrationNumber; }
    public FuelType getFuelType() { return fuelType; }
    public UsageCategory getUsageCategory() { return usageCategory; }
    public BigDecimal getDeclaredVehicleValue() { return declaredVehicleValue; }
    public String getDeclaredVehicleCurrency() { return declaredVehicleCurrency; }
}
