package com.payme.domain.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class WebhookEventsTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
    }

    // --- WebhookReceived ---

    @Test
    void webhookReceived_implementsDomainEvent() {
        Instant now = Instant.now();
        WebhookReceived event = new WebhookReceived(
                "evt-1", now, "wh-1", "PAYFAST", "abc123hash", "{\"raw\":\"payload\"}"
        );

        assertInstanceOf(DomainEvent.class, event);
        assertEquals("evt-1", event.eventId());
        assertEquals(now, event.occurredAt());
        assertEquals("wh-1", event.aggregateId());
        assertEquals("WebhookReceived", event.eventType());
    }

    @Test
    void webhookReceived_exposesAllFields() {
        Instant now = Instant.now();
        WebhookReceived event = new WebhookReceived(
                "evt-1", now, "wh-1", "PAYFAST", "abc123hash", "{\"raw\":\"payload\"}"
        );

        assertEquals("wh-1", event.webhookId());
        assertEquals("PAYFAST", event.provider());
        assertEquals("abc123hash", event.payloadHash());
        assertEquals("{\"raw\":\"payload\"}", event.rawPayload());
    }

    @Test
    void webhookReceived_serializationRoundTrip() throws Exception {
        Instant now = Instant.parse("2026-01-15T10:00:00Z");
        WebhookReceived original = new WebhookReceived(
                "evt-1", now, "wh-1", "PAYFAST", "abc123hash", "{\"raw\":\"payload\"}"
        );

        String json = objectMapper.writeValueAsString(original);
        WebhookReceived deserialized = objectMapper.readValue(json, WebhookReceived.class);

        assertEquals(original, deserialized);
    }

    // --- WebhookProcessed ---

    @Test
    void webhookProcessed_implementsDomainEvent() {
        Instant now = Instant.now();
        WebhookProcessed event = new WebhookProcessed(
                "evt-2", now, "wh-2", "attempt-1"
        );

        assertInstanceOf(DomainEvent.class, event);
        assertEquals("wh-2", event.aggregateId());
        assertEquals("WebhookProcessed", event.eventType());
    }

    @Test
    void webhookProcessed_exposesAllFields() {
        Instant now = Instant.now();
        WebhookProcessed event = new WebhookProcessed(
                "evt-2", now, "wh-2", "attempt-1"
        );

        assertEquals("wh-2", event.webhookId());
        assertEquals("attempt-1", event.correlatedAttemptId());
    }

    @Test
    void webhookProcessed_serializationRoundTrip() throws Exception {
        Instant now = Instant.parse("2026-01-15T10:00:00Z");
        WebhookProcessed original = new WebhookProcessed(
                "evt-2", now, "wh-2", "attempt-1"
        );

        String json = objectMapper.writeValueAsString(original);
        WebhookProcessed deserialized = objectMapper.readValue(json, WebhookProcessed.class);

        assertEquals(original, deserialized);
    }

    // --- WebhookDuplicated ---

    @Test
    void webhookDuplicated_implementsDomainEvent() {
        Instant now = Instant.now();
        WebhookDuplicated event = new WebhookDuplicated(
                "evt-3", now, "wh-3", "wh-original"
        );

        assertInstanceOf(DomainEvent.class, event);
        assertEquals("wh-3", event.aggregateId());
        assertEquals("WebhookDuplicated", event.eventType());
    }

    @Test
    void webhookDuplicated_exposesAllFields() {
        Instant now = Instant.now();
        WebhookDuplicated event = new WebhookDuplicated(
                "evt-3", now, "wh-3", "wh-original"
        );

        assertEquals("wh-3", event.webhookId());
        assertEquals("wh-original", event.originalWebhookId());
    }

    @Test
    void webhookDuplicated_serializationRoundTrip() throws Exception {
        Instant now = Instant.parse("2026-01-15T10:00:00Z");
        WebhookDuplicated original = new WebhookDuplicated(
                "evt-3", now, "wh-3", "wh-original"
        );

        String json = objectMapper.writeValueAsString(original);
        WebhookDuplicated deserialized = objectMapper.readValue(json, WebhookDuplicated.class);

        assertEquals(original, deserialized);
    }

    // --- WebhookFailed ---

    @Test
    void webhookFailed_implementsDomainEvent() {
        Instant now = Instant.now();
        WebhookFailed event = new WebhookFailed(
                "evt-4", now, "wh-4", "Signature verification failed"
        );

        assertInstanceOf(DomainEvent.class, event);
        assertEquals("wh-4", event.aggregateId());
        assertEquals("WebhookFailed", event.eventType());
    }

    @Test
    void webhookFailed_exposesAllFields() {
        Instant now = Instant.now();
        WebhookFailed event = new WebhookFailed(
                "evt-4", now, "wh-4", "Signature verification failed"
        );

        assertEquals("wh-4", event.webhookId());
        assertEquals("Signature verification failed", event.error());
    }

    @Test
    void webhookFailed_serializationRoundTrip() throws Exception {
        Instant now = Instant.parse("2026-01-15T10:00:00Z");
        WebhookFailed original = new WebhookFailed(
                "evt-4", now, "wh-4", "Signature verification failed"
        );

        String json = objectMapper.writeValueAsString(original);
        WebhookFailed deserialized = objectMapper.readValue(json, WebhookFailed.class);

        assertEquals(original, deserialized);
    }
}
