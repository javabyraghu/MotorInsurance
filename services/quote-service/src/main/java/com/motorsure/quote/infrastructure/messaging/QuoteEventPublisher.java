package com.motorsure.quote.infrastructure.messaging;

import com.motorsure.quote.domain.Quote;
import com.motorsure.quote.infrastructure.persistence.OutboxEvent;
import com.motorsure.quote.infrastructure.persistence.OutboxEventRepository;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.util.UUID;

@Component
public class QuoteEventPublisher {
    private final OutboxEventRepository outbox;
    private final JsonMapper jsonMapper;

    public QuoteEventPublisher(OutboxEventRepository outbox, JsonMapper jsonMapper) {
        this.outbox = outbox;
        this.jsonMapper = jsonMapper;
    }

    public void recordCreated(Quote quote) {
        UUID eventId = UUID.randomUUID();
        var event = new QuoteCreatedEvent(eventId, quote.getId(), quote.getCustomerName(), quote.getVehicleRegistration(),
                quote.getVehicleMake(), quote.getCoverType(), quote.getAnnualPremium(), quote.getCreatedAt());
        try {
            outbox.save(new OutboxEvent(eventId, "Quote", quote.getId().toString(), "QuoteCreated", jsonMapper.writeValueAsString(event)));
        } catch (JacksonException exception) {
            throw new IllegalStateException("Could not serialize QuoteCreated event", exception);
        }
    }

    public record QuoteCreatedEvent(UUID eventId, UUID quoteId, String customerName, String vehicleRegistration,
                                    String vehicleMake, String coverType, BigDecimal annualPremium,
                                    java.time.Instant createdAt) {}
}
