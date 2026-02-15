package com.payme.domain.event;

import java.time.Instant;

public record InvoicePaymentSucceeded(
        String eventId,
        Instant occurredAt,
        String invoiceId,
        String paymentAttemptId,
        String provider
) implements DomainEvent {

    @Override
    public String aggregateId() {
        return invoiceId;
    }

    @Override
    public String eventType() {
        return "InvoicePaymentSucceeded";
    }
}
