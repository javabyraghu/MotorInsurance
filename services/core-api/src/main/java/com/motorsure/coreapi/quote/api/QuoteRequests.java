package com.motorsure.coreapi.quote.api;

import com.motorsure.coreapi.quote.domain.*;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;

public final class QuoteRequests {
    private QuoteRequests() { }
    public record Create(
            @NotNull java.util.UUID customerId,
            @NotNull java.util.UUID productId,
            @NotNull java.util.UUID productVersionId,
            Instant coverageEffectiveAt,
            @NotNull @jakarta.validation.Valid Vehicle vehicle) { }

    public record Vehicle(
            @NotNull VehicleCategory vehicleCategory,
            @NotBlank @Size(max = 120) String manufacturer,
            @NotBlank @Size(max = 120) String model,
            @Min(1886) @Max(9999) int manufactureYear,
            @Size(max = 32) String registrationNumber,
            FuelType fuelType,
            @NotNull UsageCategory usageCategory,
            @DecimalMin(value = "0.0001") @Digits(integer = 12, fraction = 4) BigDecimal declaredVehicleValue,
            @Pattern(regexp = "[A-Za-z]{3}") String declaredVehicleCurrency) {
        @jakarta.validation.constraints.AssertTrue(message = "Declared vehicle value and currency must be provided together")
        public boolean isDeclaredValueCurrencyPairValid() {
            return (declaredVehicleValue == null) == (declaredVehicleCurrency == null);
        }
        @jakarta.validation.constraints.AssertTrue(message = "Declared vehicle currency must be a supported ISO 4217 code")
        public boolean isDeclaredCurrencyValid() {
            if (declaredVehicleCurrency == null) return true;
            try {
                Currency currency = supportedCurrency();
                return currency != null && currency.getDefaultFractionDigits() >= 0;
            }
            catch (RuntimeException ignored) { return false; }
        }
        @jakarta.validation.constraints.AssertTrue(message = "Declared value precision must match the currency's minor unit")
        public boolean isDeclaredValuePrecisionValid() {
            if (declaredVehicleValue == null || declaredVehicleCurrency == null) return true;
            Currency currency = supportedCurrency();
            return currency != null && currency.getDefaultFractionDigits() >= 0
                    && declaredVehicleValue.stripTrailingZeros().scale() <= currency.getDefaultFractionDigits();
        }
        private Currency supportedCurrency() {
            return Currency.getAvailableCurrencies().stream()
                    .filter(c -> c.getCurrencyCode().equalsIgnoreCase(declaredVehicleCurrency))
                    .findFirst().orElse(null);
        }
    }
}
