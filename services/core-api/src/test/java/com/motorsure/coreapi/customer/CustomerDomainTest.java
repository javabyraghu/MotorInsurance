package com.motorsure.coreapi.customer;

import com.motorsure.coreapi.customer.domain.Customer;
import com.motorsure.coreapi.customer.domain.CustomerStatus;
import org.junit.jupiter.api.Test;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

class CustomerDomainTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-01-01T12:00:00Z"), ZoneOffset.UTC);

    @Test
    void registrationNormalizesProfileAndCreatesStableServerManagedValues() {
        UUID id = UUID.fromString("4f372fa2-615d-4da7-91f0-dc483f84ad28");
        Customer c = Customer.register(id, " Asha ", " Rao ", " ASHA.RAO@EXAMPLE.TEST ", " +1 415 555 0132 ",
                LocalDate.of(1988, 4, 12), CLOCK);
        assertThat(c.getId()).isEqualTo(id);
        assertThat(c.getCustomerNumber()).isEqualTo("CUS-4F372FA2615D4DA791F0DC483F84AD28");
        assertThat(c.getFirstName()).isEqualTo("Asha");
        assertThat(c.getEmail()).isEqualTo("asha.rao@example.test");
        assertThat(c.getStatus()).isEqualTo(CustomerStatus.ACTIVE);
        assertThat(c.getCreatedAt()).isEqualTo(Instant.parse("2026-01-01T12:00:00Z"));
    }

    @Test
    void profileAndStatusChangesUseTheInjectedClock() {
        Customer c = Customer.register(UUID.randomUUID(), "Asha", "Rao", "asha@example.test", null,
                LocalDate.of(1988, 4, 12), CLOCK);
        c.updateProfile("Asha", "Rao", "new@example.test", null, LocalDate.of(1988, 4, 12), CLOCK);
        c.changeStatus(CustomerStatus.INACTIVE, CLOCK);
        assertThat(c.getEmail()).isEqualTo("new@example.test");
        assertThat(c.getPhoneNumber()).isNull();
        assertThat(c.getStatus()).isEqualTo(CustomerStatus.INACTIVE);
        assertThat(c.getUpdatedAt()).isEqualTo(Instant.parse("2026-01-01T12:00:00Z"));
    }
}
