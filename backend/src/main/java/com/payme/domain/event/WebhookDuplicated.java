package com.payme.domain.event;

import java.time.Instant;

public record WebhookDuplicated(
        String eventId,
        Instant occurredAt,
        String webhookId,
        String originalWebhookId
) implements DomainEvent {

    @Override
    public String aggregateId() {
        return webhookId;
    }

    @Override
    public String eventType() {
        return "WebhookDuplicated";
    }
}
