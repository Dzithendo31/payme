package com.payme.domain.event;

import java.time.Instant;

public record PaymentAttemptFailed(
        String eventId,
        Instant occurredAt,
        String attemptId,
        String providerEventId,
        String reason
) implements DomainEvent {

    @Override
    public String aggregateId() {
        return attemptId;
    }

    @Override
    public String eventType() {
        return "PaymentAttemptFailed";
    }
}
