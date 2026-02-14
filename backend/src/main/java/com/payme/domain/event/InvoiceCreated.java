package com.payme.domain.event;

import java.math.BigDecimal;
import java.time.Instant;

public record InvoiceCreated(
        String eventId,
        Instant occurredAt,
        String invoiceId,
        String merchantId,
        BigDecimal amount,
        String currency,
        String description,
        Instant expiresAt
) implements DomainEvent {

    @Override
    public String aggregateId() {
        return invoiceId;
    }

    @Override
    public String eventType() {
        return "InvoiceCreated";
    }
}
