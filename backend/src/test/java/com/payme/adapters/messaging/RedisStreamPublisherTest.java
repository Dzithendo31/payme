package com.payme.adapters.messaging;

import com.payme.config.StreamProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class RedisStreamPublisherTest {

    private StringRedisTemplate redisTemplate;
    private StreamOperations<String, Object, Object> streamOperations;
    private StreamProperties streamProperties;
    private RedisStreamPublisher publisher;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        streamOperations = mock(StreamOperations.class);
        when(redisTemplate.opsForStream()).thenReturn(streamOperations);
        when(streamOperations.add(any())).thenReturn(RecordId.of("1234567890-0"));

        streamProperties = new StreamProperties();
        publisher = new RedisStreamPublisher(redisTemplate, streamProperties);
    }

    @Test
    void publish_callsXaddOnRedisStream() {
        publisher.publish("Invoice", "evt-1", "InvoiceCreated",
                "inv-1", "2026-01-15T10:00:00Z", "{\"test\":true}");

        verify(streamOperations).add(any());
    }

    @Test
    void publish_routesInvoiceEventsToInvoiceStream() {
        publisher.publish("Invoice", "evt-1", "InvoiceCreated",
                "inv-1", "2026-01-15T10:00:00Z", "{}");

        verify(streamOperations).add(argThat(record ->
                record.getStream().equals("payme:events:invoice")
        ));
    }

    @Test
    void publish_routesPaymentEventsToPaymentStream() {
        publisher.publish("PaymentAttempt", "evt-2", "PaymentAttemptCreated",
                "attempt-1", "2026-01-15T10:00:00Z", "{}");

        verify(streamOperations).add(argThat(record ->
                record.getStream().equals("payme:events:payment")
        ));
    }

    @Test
    void publish_routesWebhookEventsToWebhookStream() {
        publisher.publish("Webhook", "evt-3", "WebhookReceived",
                "wh-1", "2026-01-15T10:00:00Z", "{}");

        verify(streamOperations).add(argThat(record ->
                record.getStream().equals("payme:events:webhook")
        ));
    }
}
