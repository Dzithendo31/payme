package com.payme.domain.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class PaymentAttemptEventsTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
    }

    // --- PaymentAttemptCreated ---

    @Test
    void paymentAttemptCreated_implementsDomainEvent() {
        Instant now = Instant.now();
        PaymentAttemptCreated event = new PaymentAttemptCreated(
                "evt-1", now, "attempt-1", "inv-1", "PAYFAST", "pf-ref-123"
        );

        assertInstanceOf(DomainEvent.class, event);
        assertEquals("evt-1", event.eventId());
        assertEquals(now, event.occurredAt());
        assertEquals("attempt-1", event.aggregateId());
        assertEquals("PaymentAttemptCreated", event.eventType());
    }

    @Test
    void paymentAttemptCreated_exposesAllFields() {
        Instant now = Instant.now();
        PaymentAttemptCreated event = new PaymentAttemptCreated(
                "evt-1", now, "attempt-1", "inv-1", "PAYFAST", "pf-ref-123"
        );

        assertEquals("attempt-1", event.attemptId());
        assertEquals("inv-1", event.invoiceId());
        assertEquals("PAYFAST", event.provider());
        assertEquals("pf-ref-123", event.providerReference());
    }

    @Test
    void paymentAttemptCreated_serializationRoundTrip() throws Exception {
        Instant now = Instant.parse("2026-01-15T10:00:00Z");
        PaymentAttemptCreated original = new PaymentAttemptCreated(
                "evt-1", now, "attempt-1", "inv-1", "PAYFAST", "pf-ref-123"
        );

        String json = objectMapper.writeValueAsString(original);
        PaymentAttemptCreated deserialized = objectMapper.readValue(json, PaymentAttemptCreated.class);

        assertEquals(original, deserialized);
    }

    // --- PaymentAttemptSucceeded ---

    @Test
    void paymentAttemptSucceeded_implementsDomainEvent() {
        Instant now = Instant.now();
        PaymentAttemptSucceeded event = new PaymentAttemptSucceeded(
                "evt-2", now, "attempt-2", "pf-event-456"
        );

        assertInstanceOf(DomainEvent.class, event);
        assertEquals("attempt-2", event.aggregateId());
        assertEquals("PaymentAttemptSucceeded", event.eventType());
    }

    @Test
    void paymentAttemptSucceeded_exposesAllFields() {
        Instant now = Instant.now();
        PaymentAttemptSucceeded event = new PaymentAttemptSucceeded(
                "evt-2", now, "attempt-2", "pf-event-456"
        );

        assertEquals("attempt-2", event.attemptId());
        assertEquals("pf-event-456", event.providerEventId());
    }

    @Test
    void paymentAttemptSucceeded_serializationRoundTrip() throws Exception {
        Instant now = Instant.parse("2026-01-15T10:00:00Z");
        PaymentAttemptSucceeded original = new PaymentAttemptSucceeded(
                "evt-2", now, "attempt-2", "pf-event-456"
        );

        String json = objectMapper.writeValueAsString(original);
        PaymentAttemptSucceeded deserialized = objectMapper.readValue(json, PaymentAttemptSucceeded.class);

        assertEquals(original, deserialized);
    }

    // --- PaymentAttemptFailed ---

    @Test
    void paymentAttemptFailed_implementsDomainEvent() {
        Instant now = Instant.now();
        PaymentAttemptFailed event = new PaymentAttemptFailed(
                "evt-3", now, "attempt-3", "pf-event-789", "Card declined"
        );

        assertInstanceOf(DomainEvent.class, event);
        assertEquals("attempt-3", event.aggregateId());
        assertEquals("PaymentAttemptFailed", event.eventType());
    }

    @Test
    void paymentAttemptFailed_exposesAllFields() {
        Instant now = Instant.now();
        PaymentAttemptFailed event = new PaymentAttemptFailed(
                "evt-3", now, "attempt-3", "pf-event-789", "Card declined"
        );

        assertEquals("attempt-3", event.attemptId());
        assertEquals("pf-event-789", event.providerEventId());
        assertEquals("Card declined", event.reason());
    }

    @Test
    void paymentAttemptFailed_serializationRoundTrip() throws Exception {
        Instant now = Instant.parse("2026-01-15T10:00:00Z");
        PaymentAttemptFailed original = new PaymentAttemptFailed(
                "evt-3", now, "attempt-3", "pf-event-789", "Card declined"
        );

        String json = objectMapper.writeValueAsString(original);
        PaymentAttemptFailed deserialized = objectMapper.readValue(json, PaymentAttemptFailed.class);

        assertEquals(original, deserialized);
    }
}
