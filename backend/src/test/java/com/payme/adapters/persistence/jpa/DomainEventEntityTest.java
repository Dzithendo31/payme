package com.payme.adapters.persistence.jpa;

import com.payme.domain.event.InvoiceCreated;
import com.payme.domain.event.InvoiceExpired;
import com.payme.domain.event.PaymentAttemptCreated;
import com.payme.domain.event.WebhookReceived;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class DomainEventEntityTest {

    @Test
    void fromDomainEvent_mapsAllFieldsCorrectly() {
        Instant now = Instant.parse("2026-01-15T10:00:00Z");
        InvoiceCreated event = new InvoiceCreated(
                "evt-1", now, "inv-1", "merchant-1",
                new BigDecimal("100.00"), "ZAR", "Test invoice", now.plusSeconds(3600)
        );

        DomainEventEntity entity = DomainEventEntity.fromDomainEvent(event, "Invoice");

        assertEquals("evt-1", entity.getEventId());
        assertEquals("InvoiceCreated", entity.getEventType());
        assertEquals("inv-1", entity.getAggregateId());
        assertEquals("Invoice", entity.getAggregateType());
        assertEquals(now, entity.getOccurredAt());
        assertFalse(entity.isPublished());
        assertNotNull(entity.getPayload());
    }

    @Test
    void fromDomainEvent_setsPublishedToFalse() {
        Instant now = Instant.now();
        InvoiceExpired event = new InvoiceExpired("evt-2", now, "inv-2", now);

        DomainEventEntity entity = DomainEventEntity.fromDomainEvent(event, "Invoice");

        assertFalse(entity.isPublished());
    }

    @Test
    void markPublished_setsPublishedToTrue() {
        Instant now = Instant.now();
        InvoiceExpired event = new InvoiceExpired("evt-2", now, "inv-2", now);
        DomainEventEntity entity = DomainEventEntity.fromDomainEvent(event, "Invoice");

        entity.markPublished();

        assertTrue(entity.isPublished());
    }

    @Test
    void toEvent_deserializesPayloadBackToOriginalEvent() {
        Instant now = Instant.parse("2026-01-15T10:00:00Z");
        InvoiceCreated original = new InvoiceCreated(
                "evt-1", now, "inv-1", "merchant-1",
                new BigDecimal("100.00"), "ZAR", "Test invoice", now.plusSeconds(3600)
        );

        DomainEventEntity entity = DomainEventEntity.fromDomainEvent(original, "Invoice");
        InvoiceCreated deserialized = entity.toEvent(InvoiceCreated.class);

        assertEquals(original, deserialized);
    }

    @Test
    void toEvent_roundTripForPaymentAttemptEvent() {
        Instant now = Instant.parse("2026-01-15T10:00:00Z");
        PaymentAttemptCreated original = new PaymentAttemptCreated(
                "evt-3", now, "attempt-1", "inv-1", "PAYFAST", "ref-123"
        );

        DomainEventEntity entity = DomainEventEntity.fromDomainEvent(original, "PaymentAttempt");
        PaymentAttemptCreated deserialized = entity.toEvent(PaymentAttemptCreated.class);

        assertEquals(original, deserialized);
        assertEquals("PaymentAttempt", entity.getAggregateType());
    }

    @Test
    void toEvent_roundTripForWebhookEvent() {
        Instant now = Instant.parse("2026-01-15T10:00:00Z");
        WebhookReceived original = new WebhookReceived(
                "evt-4", now, "wh-1", "PAYFAST", "hash-abc", "{\"key\":\"value\"}"
        );

        DomainEventEntity entity = DomainEventEntity.fromDomainEvent(original, "Webhook");
        WebhookReceived deserialized = entity.toEvent(WebhookReceived.class);

        assertEquals(original, deserialized);
        assertEquals("Webhook", entity.getAggregateType());
    }

    @Test
    void payload_isValidJsonString() {
        Instant now = Instant.parse("2026-01-15T10:00:00Z");
        InvoiceCreated event = new InvoiceCreated(
                "evt-1", now, "inv-1", "merchant-1",
                new BigDecimal("100.00"), "ZAR", "Test invoice", now.plusSeconds(3600)
        );

        DomainEventEntity entity = DomainEventEntity.fromDomainEvent(event, "Invoice");

        String payload = entity.getPayload();
        assertTrue(payload.contains("\"eventId\""));
        assertTrue(payload.contains("\"invoiceId\""));
        assertTrue(payload.contains("evt-1"));
        assertTrue(payload.contains("inv-1"));
    }

    @Test
    void constructor_setsAllFields() {
        Instant now = Instant.now();
        DomainEventEntity entity = new DomainEventEntity(
                "evt-id", "SomeEvent", "agg-1", "SomeAggregate",
                now, "{}", false
        );

        assertEquals("evt-id", entity.getEventId());
        assertEquals("SomeEvent", entity.getEventType());
        assertEquals("agg-1", entity.getAggregateId());
        assertEquals("SomeAggregate", entity.getAggregateType());
        assertEquals(now, entity.getOccurredAt());
        assertEquals("{}", entity.getPayload());
        assertFalse(entity.isPublished());
    }
}
