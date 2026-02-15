package com.payme.domain.event;

import java.time.Instant;

public record WebhookFailed(
        String eventId,
        Instant occurredAt,
        String webhookId,
        String error
) implements DomainEvent {

    @Override
    public String aggregateId() {
        return webhookId;
    }

    @Override
    public String eventType() {
        return "WebhookFailed";
    }
}
