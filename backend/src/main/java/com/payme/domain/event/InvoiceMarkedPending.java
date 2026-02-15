package com.payme.domain.event;

import java.time.Instant;

public record InvoiceMarkedPending(
        String eventId,
        Instant occurredAt,
        String invoiceId,
        String paymentAttemptId
) implements DomainEvent {

    @Override
    public String aggregateId() {
        return invoiceId;
    }

    @Override
    public String eventType() {
        return "InvoiceMarkedPending";
    }
}
