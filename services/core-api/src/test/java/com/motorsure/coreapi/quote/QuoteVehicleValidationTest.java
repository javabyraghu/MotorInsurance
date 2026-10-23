package com.motorsure.coreapi.quote;

import com.motorsure.coreapi.quote.api.QuoteRequests;
import com.motorsure.coreapi.quote.domain.UsageCategory;
import com.motorsure.coreapi.quote.domain.VehicleCategory;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.assertThat;

class QuoteVehicleValidationTest {
    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }
    @AfterAll static void tearDown() { factory.close(); }

    @Test
    void declaredValuePrecisionFollowsTheCurrencyMinorUnit() {
        assertThat(validator.validate(vehicle("100.001", "USD"))).isNotEmpty();
        assertThat(validator.validate(vehicle("100.125", "KWD"))).isEmpty();
        assertThat(validator.validate(vehicle("100.00", "JPY"))).isEmpty();
        assertThat(validator.validate(vehicle("100.01", "JPY"))).isNotEmpty();
        assertThat(validator.validate(vehicle("100.12345", "KWD"))).isNotEmpty();
    }

    @Test
    void declaredValueAndCurrencyMustBePairedAndCurrencyMustBeSupported() {
        assertThat(validator.validate(vehicle("100", null))).isNotEmpty();
        assertThat(validator.validate(vehicle(null, "USD"))).isNotEmpty();
        assertThat(validator.validate(vehicle("100", "ZZZ"))).isNotEmpty();
        assertThat(validator.validate(vehicle("0", "USD"))).isNotEmpty();
    }

    private static QuoteRequests.Vehicle vehicle(String value, String currency) {
        return new QuoteRequests.Vehicle(VehicleCategory.CAR, "Example Motors", "Example Model", 2020,
                null, null, UsageCategory.PRIVATE, value == null ? null : new BigDecimal(value), currency);
    }
}
