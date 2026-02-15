package com.payme.adapters.messaging.consumers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.payme.config.StreamProperties;
import com.payme.domain.event.PaymentAttemptCreated;
import com.payme.domain.event.PaymentAttemptFailed;
import com.payme.domain.event.PaymentAttemptSucceeded;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Instant;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class PaymentStatusConsumerTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    private StringRedisTemplate redisTemplate;
    private StreamOperations<String, Object, Object> streamOperations;
    private StreamProperties streamProperties;
    private PaymentStatusConsumer consumer;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        streamOperations = mock(StreamOperations.class);
        when(redisTemplate.opsForStream()).thenReturn(streamOperations);

        streamProperties = new StreamProperties();
        consumer = new PaymentStatusConsumer(redisTemplate, streamProperties);
    }

    @Test
    void onMessage_paymentAttemptCreated_processesAndAcknowledges() throws Exception {
        Instant now = Instant.parse("2026-01-15T10:00:00Z");
        PaymentAttemptCreated event = new PaymentAttemptCreated(
                "evt-1", now, "attempt-1", "inv-1", "PAYFAST", "ref-123"
        );

        MapRecord<String, String, String> record = MapRecord.create(
                "payme:events:payment",
                Map.of(
                        "eventType", "PaymentAttemptCreated",
                        "payload", OBJECT_MAPPER.writeValueAsString(event)
                )
        ).withId(RecordId.of("1234567890-0"));

        consumer.onMessage(record);

        verify(streamOperations).acknowledge(
                eq("payme:events:payment"),
                eq("payme-service"),
                eq(RecordId.of("1234567890-0"))
        );
    }

    @Test
    void onMessage_paymentAttemptSucceeded_processesAndAcknowledges() throws Exception {
        Instant now = Instant.parse("2026-01-15T10:00:00Z");
        PaymentAttemptSucceeded event = new PaymentAttemptSucceeded(
                "evt-2", now, "attempt-1", "provider-evt-1"
        );

        MapRecord<String, String, String> record = MapRecord.create(
                "payme:events:payment",
                Map.of(
                        "eventType", "PaymentAttemptSucceeded",
                        "payload", OBJECT_MAPPER.writeValueAsString(event)
                )
        ).withId(RecordId.of("1234567891-0"));

        consumer.onMessage(record);

        verify(streamOperations).acknowledge(
                eq("payme:events:payment"),
                eq("payme-service"),
                eq(RecordId.of("1234567891-0"))
        );
    }

    @Test
    void onMessage_paymentAttemptFailed_processesAndAcknowledges() throws Exception {
        Instant now = Instant.parse("2026-01-15T10:00:00Z");
        PaymentAttemptFailed event = new PaymentAttemptFailed(
                "evt-3", now, "attempt-1", "provider-evt-2", "Insufficient funds"
        );

        MapRecord<String, String, String> record = MapRecord.create(
                "payme:events:payment",
                Map.of(
                        "eventType", "PaymentAttemptFailed",
                        "payload", OBJECT_MAPPER.writeValueAsString(event)
                )
        ).withId(RecordId.of("1234567892-0"));

        consumer.onMessage(record);

        verify(streamOperations).acknowledge(
                eq("payme:events:payment"),
                eq("payme-service"),
                eq(RecordId.of("1234567892-0"))
        );
    }

    @Test
    void onMessage_unknownEventType_doesNotThrow_butAcknowledges() {
        MapRecord<String, String, String> record = MapRecord.create(
                "payme:events:payment",
                Map.of(
                        "eventType", "UnknownEvent",
                        "payload", "{}"
                )
        ).withId(RecordId.of("1234567893-0"));

        consumer.onMessage(record);

        verify(streamOperations).acknowledge(
                eq("payme:events:payment"),
                eq("payme-service"),
                eq(RecordId.of("1234567893-0"))
        );
    }

    @Test
    void onMessage_invalidPayload_doesNotAcknowledge() {
        MapRecord<String, String, String> record = MapRecord.create(
                "payme:events:payment",
                Map.of(
                        "eventType", "PaymentAttemptCreated",
                        "payload", "not valid json{"
                )
        ).withId(RecordId.of("1234567894-0"));

        consumer.onMessage(record);

        verify(streamOperations, never()).acknowledge(anyString(), anyString(), any(RecordId.class));
    }
}
