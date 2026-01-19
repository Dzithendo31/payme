package com.payme.domain;

import java.time.Instant;
import java.util.Objects;

public class CanonicalPaymentEvent {
    private final ProviderName provider;
    private final String eventId;
    private final String attemptReference;
    private final InvoiceId invoiceId;
    private final PaymentEventStatus status;
    private final Instant occurredAt;
    private final String rawType;

    public CanonicalPaymentEvent(
            ProviderName provider,
            String eventId,
            String attemptReference,
            InvoiceId invoiceId,
            PaymentEventStatus status,
            Instant occurredAt,
            String rawType
    ) {
        if (provider == null) {
            throw new IllegalArgumentException("Provider cannot be null");
        }
        if (status == null) {
            throw new IllegalArgumentException("Status cannot be null");
        }
        if (occurredAt == null) {
            throw new IllegalArgumentException("OccurredAt cannot be null");
        }
        if (rawType == null || rawType.trim().isEmpty()) {
            throw new IllegalArgumentException("RawType cannot be null or empty");
        }

        this.provider = provider;
        this.eventId = eventId;
        this.attemptReference = attemptReference;
        this.invoiceId = invoiceId;
        this.status = status;
        this.occurredAt = occurredAt;
        this.rawType = rawType;
    }

    public ProviderName getProvider() {
        return provider;
    }

    public String getEventId() {
        return eventId;
    }

    public String getAttemptReference() {
        return attemptReference;
    }

    public InvoiceId getInvoiceId() {
        return invoiceId;
    }

    public PaymentEventStatus getStatus() {
        return status;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public String getRawType() {
        return rawType;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CanonicalPaymentEvent that = (CanonicalPaymentEvent) o;
        return Objects.equals(eventId, that.eventId) &&
                provider == that.provider;
    }

    @Override
    public int hashCode() {
        return Objects.hash(provider, eventId);
    }
}
