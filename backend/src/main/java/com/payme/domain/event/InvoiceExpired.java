package com.payme.domain.event;

import java.time.Instant;

public record InvoiceExpired(
        String eventId,
        Instant occurredAt,
        String invoiceId,
        Instant expiredAt
) implements DomainEvent {

    @Override
    public String aggregateId() {
        return invoiceId;
    }

    @Override
    public String eventType() {
        return "InvoiceExpired";
    }
}
