package com.payme.domain.event;

import java.time.Instant;

public record WebhookReceived(
        String eventId,
        Instant occurredAt,
        String webhookId,
        String provider,
        String payloadHash,
        String rawPayload
) implements DomainEvent {

    @Override
    public String aggregateId() {
        return webhookId;
    }

    @Override
    public String eventType() {
        return "WebhookReceived";
    }
}
