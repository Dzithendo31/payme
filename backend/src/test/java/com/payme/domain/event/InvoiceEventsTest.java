package com.payme.domain.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class InvoiceEventsTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
    }

    // --- InvoiceCreated ---

    @Test
    void invoiceCreated_implementsDomainEvent() {
        Instant now = Instant.now();
        InvoiceCreated event = new InvoiceCreated(
                "evt-1", now, "inv-1", "merchant-1",
                new BigDecimal("100.00"), "ZAR", "Test invoice", now.plusSeconds(3600)
        );

        assertInstanceOf(DomainEvent.class, event);
        assertEquals("evt-1", event.eventId());
        assertEquals(now, event.occurredAt());
        assertEquals("inv-1", event.aggregateId());
        assertEquals("InvoiceCreated", event.eventType());
    }

    @Test
    void invoiceCreated_exposesAllFields() {
        Instant now = Instant.now();
        Instant expires = now.plusSeconds(7200);
        InvoiceCreated event = new InvoiceCreated(
                "evt-1", now, "inv-1", "merchant-1",
                new BigDecimal("250.50"), "USD", "Payment for order", expires
        );

        assertEquals("inv-1", event.invoiceId());
        assertEquals("merchant-1", event.merchantId());
        assertEquals(new BigDecimal("250.50"), event.amount());
        assertEquals("USD", event.currency());
        assertEquals("Payment for order", event.description());
        assertEquals(expires, event.expiresAt());
    }

    @Test
    void invoiceCreated_serializationRoundTrip() throws Exception {
        Instant now = Instant.parse("2026-01-15T10:00:00Z");
        InvoiceCreated original = new InvoiceCreated(
                "evt-1", now, "inv-1", "merchant-1",
                new BigDecimal("100.00"), "ZAR", "Test invoice", now.plusSeconds(3600)
        );

        String json = objectMapper.writeValueAsString(original);
        InvoiceCreated deserialized = objectMapper.readValue(json, InvoiceCreated.class);

        assertEquals(original, deserialized);
    }

    // --- InvoiceExpired ---

    @Test
    void invoiceExpired_implementsDomainEvent() {
        Instant now = Instant.now();
        InvoiceExpired event = new InvoiceExpired("evt-2", now, "inv-2", now);

        assertInstanceOf(DomainEvent.class, event);
        assertEquals("evt-2", event.eventId());
        assertEquals("inv-2", event.aggregateId());
        assertEquals("InvoiceExpired", event.eventType());
    }

    @Test
    void invoiceExpired_exposesAllFields() {
        Instant now = Instant.now();
        Instant expiredAt = now.minusSeconds(60);
        InvoiceExpired event = new InvoiceExpired("evt-2", now, "inv-2", expiredAt);

        assertEquals("inv-2", event.invoiceId());
        assertEquals(expiredAt, event.expiredAt());
    }

    @Test
    void invoiceExpired_serializationRoundTrip() throws Exception {
        Instant now = Instant.parse("2026-01-15T10:00:00Z");
        InvoiceExpired original = new InvoiceExpired("evt-2", now, "inv-2", now);

        String json = objectMapper.writeValueAsString(original);
        InvoiceExpired deserialized = objectMapper.readValue(json, InvoiceExpired.class);

        assertEquals(original, deserialized);
    }

    // --- InvoiceMarkedPending ---

    @Test
    void invoiceMarkedPending_implementsDomainEvent() {
        Instant now = Instant.now();
        InvoiceMarkedPending event = new InvoiceMarkedPending("evt-3", now, "inv-3", "attempt-1");

        assertInstanceOf(DomainEvent.class, event);
        assertEquals("inv-3", event.aggregateId());
        assertEquals("InvoiceMarkedPending", event.eventType());
    }

    @Test
    void invoiceMarkedPending_exposesAllFields() {
        Instant now = Instant.now();
        InvoiceMarkedPending event = new InvoiceMarkedPending("evt-3", now, "inv-3", "attempt-1");

        assertEquals("inv-3", event.invoiceId());
        assertEquals("attempt-1", event.paymentAttemptId());
    }

    @Test
    void invoiceMarkedPending_serializationRoundTrip() throws Exception {
        Instant now = Instant.parse("2026-01-15T10:00:00Z");
        InvoiceMarkedPending original = new InvoiceMarkedPending("evt-3", now, "inv-3", "attempt-1");

        String json = objectMapper.writeValueAsString(original);
        InvoiceMarkedPending deserialized = objectMapper.readValue(json, InvoiceMarkedPending.class);

        assertEquals(original, deserialized);
    }

    // --- InvoicePaymentSucceeded ---

    @Test
    void invoicePaymentSucceeded_implementsDomainEvent() {
        Instant now = Instant.now();
        InvoicePaymentSucceeded event = new InvoicePaymentSucceeded(
                "evt-4", now, "inv-4", "attempt-2", "PAYFAST"
        );

        assertInstanceOf(DomainEvent.class, event);
        assertEquals("inv-4", event.aggregateId());
        assertEquals("InvoicePaymentSucceeded", event.eventType());
    }

    @Test
    void invoicePaymentSucceeded_exposesAllFields() {
        Instant now = Instant.now();
        InvoicePaymentSucceeded event = new InvoicePaymentSucceeded(
                "evt-4", now, "inv-4", "attempt-2", "PAYFAST"
        );

        assertEquals("inv-4", event.invoiceId());
        assertEquals("attempt-2", event.paymentAttemptId());
        assertEquals("PAYFAST", event.provider());
    }

    @Test
    void invoicePaymentSucceeded_serializationRoundTrip() throws Exception {
        Instant now = Instant.parse("2026-01-15T10:00:00Z");
        InvoicePaymentSucceeded original = new InvoicePaymentSucceeded(
                "evt-4", now, "inv-4", "attempt-2", "PAYFAST"
        );

        String json = objectMapper.writeValueAsString(original);
        InvoicePaymentSucceeded deserialized = objectMapper.readValue(json, InvoicePaymentSucceeded.class);

        assertEquals(original, deserialized);
    }

    // --- InvoicePaymentFailed ---

    @Test
    void invoicePaymentFailed_implementsDomainEvent() {
        Instant now = Instant.now();
        InvoicePaymentFailed event = new InvoicePaymentFailed(
                "evt-5", now, "inv-5", "attempt-3", "PAYFAST", "Insufficient funds"
        );

        assertInstanceOf(DomainEvent.class, event);
        assertEquals("inv-5", event.aggregateId());
        assertEquals("InvoicePaymentFailed", event.eventType());
    }

    @Test
    void invoicePaymentFailed_exposesAllFields() {
        Instant now = Instant.now();
        InvoicePaymentFailed event = new InvoicePaymentFailed(
                "evt-5", now, "inv-5", "attempt-3", "PAYFAST", "Insufficient funds"
        );

        assertEquals("inv-5", event.invoiceId());
        assertEquals("attempt-3", event.paymentAttemptId());
        assertEquals("PAYFAST", event.provider());
        assertEquals("Insufficient funds", event.reason());
    }

    @Test
    void invoicePaymentFailed_serializationRoundTrip() throws Exception {
        Instant now = Instant.parse("2026-01-15T10:00:00Z");
        InvoicePaymentFailed original = new InvoicePaymentFailed(
                "evt-5", now, "inv-5", "attempt-3", "PAYFAST", "Insufficient funds"
        );

        String json = objectMapper.writeValueAsString(original);
        InvoicePaymentFailed deserialized = objectMapper.readValue(json, InvoicePaymentFailed.class);

        assertEquals(original, deserialized);
    }
}
