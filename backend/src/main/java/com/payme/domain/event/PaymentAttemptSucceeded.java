package com.payme.domain.event;

import java.time.Instant;

public record PaymentAttemptSucceeded(
        String eventId,
        Instant occurredAt,
        String attemptId,
        String providerEventId
) implements DomainEvent {

    @Override
    public String aggregateId() {
        return attemptId;
    }

    @Override
    public String eventType() {
        return "PaymentAttemptSucceeded";
    }
}
