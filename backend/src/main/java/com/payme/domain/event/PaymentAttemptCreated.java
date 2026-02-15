package com.payme.domain.event;

import java.time.Instant;

public record PaymentAttemptCreated(
        String eventId,
        Instant occurredAt,
        String attemptId,
        String invoiceId,
        String provider,
        String providerReference
) implements DomainEvent {

    @Override
    public String aggregateId() {
        return attemptId;
    }

    @Override
    public String eventType() {
        return "PaymentAttemptCreated";
    }
}
