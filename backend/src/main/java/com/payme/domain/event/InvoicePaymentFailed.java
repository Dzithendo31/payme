package com.payme.domain.event;

import java.time.Instant;

public record InvoicePaymentFailed(
        String eventId,
        Instant occurredAt,
        String invoiceId,
        String paymentAttemptId,
        String provider,
        String reason
) implements DomainEvent {

    @Override
    public String aggregateId() {
        return invoiceId;
    }

    @Override
    public String eventType() {
        return "InvoicePaymentFailed";
    }
}
