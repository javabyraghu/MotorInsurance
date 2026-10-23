package com.motorsure.quote;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

@Component
public class OutboxPublisher {
    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);
    private static final int BATCH_SIZE = 50;
    private final OutboxEventRepository outbox;
    private final KafkaTemplate<Object, Object> kafka;
    private final TransactionTemplate transactions;

    public OutboxPublisher(OutboxEventRepository outbox, KafkaTemplate<Object, Object> kafka,
                           PlatformTransactionManager transactionManager) {
        this.outbox = outbox;
        this.kafka = kafka;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    @Scheduled(fixedDelayString = "${app.outbox.poll-interval-ms:1000}")
    public void publishPendingEvents() {
        try {
            transactions.executeWithoutResult(status -> {
                for (OutboxEvent event : outbox.lockPendingBatch(BATCH_SIZE)) {
                    try {
                        kafka.send(topicFor(event), event.getAggregateId(), event.getPayload()).get(15, TimeUnit.SECONDS);
                        event.markPublished(Instant.now());
                    } catch (Exception exception) {
                        status.setRollbackOnly();
                        throw new OutboxPublishException(event.getId(), exception);
                    }
                }
            });
        } catch (OutboxPublishException exception) {
            log.warn("Outbox batch was rolled back; event {} will be retried", exception.eventId(), exception.getCause());
        } catch (Exception exception) {
            log.error("Outbox publisher failed; pending events will be retried", exception);
        }
    }

    private String topicFor(OutboxEvent event) {
        return switch (event.getEventType()) {
            case "QuoteCreated" -> "motor.quote.created";
            default -> throw new IllegalArgumentException("Unsupported outbox event type: " + event.getEventType());
        };
    }

    private static class OutboxPublishException extends RuntimeException {
        private final java.util.UUID eventId;
        private OutboxPublishException(java.util.UUID eventId, Throwable cause) { super(cause); this.eventId = eventId; }
        private java.util.UUID eventId() { return eventId; }
    }
}
