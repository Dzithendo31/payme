package com.payme.domain.event;

import java.time.Instant;

public record WebhookProcessed(
        String eventId,
        Instant occurredAt,
        String webhookId,
        String correlatedAttemptId
) implements DomainEvent {

    @Override
    public String aggregateId() {
        return webhookId;
    }

    @Override
    public String eventType() {
        return "WebhookProcessed";
    }
}
