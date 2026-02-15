package com.payme.adapters.messaging.consumers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.payme.config.StreamProperties;
import com.payme.domain.event.PaymentAttemptCreated;
import com.payme.domain.event.PaymentAttemptFailed;
import com.payme.domain.event.PaymentAttemptSucceeded;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.stream.StreamListener;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class PaymentStatusConsumer implements StreamListener<String, MapRecord<String, String, String>> {

    private static final Logger log = LoggerFactory.getLogger(PaymentStatusConsumer.class);

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    private final StringRedisTemplate redisTemplate;
    private final StreamProperties streamProperties;

    public PaymentStatusConsumer(StringRedisTemplate redisTemplate, StreamProperties streamProperties) {
        this.redisTemplate = redisTemplate;
        this.streamProperties = streamProperties;
    }

    @Override
    public void onMessage(MapRecord<String, String, String> message) {
        Map<String, String> body = message.getValue();
        String eventType = body.get("eventType");
        String payload = body.get("payload");

        log.info("Received payment event: type={}, recordId={}", eventType, message.getId());

        try {
            switch (eventType) {
                case "PaymentAttemptCreated" -> handlePaymentAttemptCreated(payload);
                case "PaymentAttemptSucceeded" -> handlePaymentAttemptSucceeded(payload);
                case "PaymentAttemptFailed" -> handlePaymentAttemptFailed(payload);
                default -> log.warn("Unknown payment event type: {}", eventType);
            }

            // Acknowledge the message
            redisTemplate.opsForStream().acknowledge(
                    streamProperties.getPaymentEvents(),
                    streamProperties.getConsumerGroup(),
                    message.getId()
            );
            log.debug("Acknowledged payment event: recordId={}", message.getId());

        } catch (Exception e) {
            log.error("Failed to process payment event: type={}, recordId={}",
                    eventType, message.getId(), e);
            // Do NOT acknowledge — message stays pending for retry
        }
    }

    private void handlePaymentAttemptCreated(String payload) throws JsonProcessingException {
        var event = OBJECT_MAPPER.readValue(payload, PaymentAttemptCreated.class);
        log.info("Processing PaymentAttemptCreated: attemptId={}, invoiceId={}, provider={}",
                event.attemptId(), event.invoiceId(), event.provider());
    }

    private void handlePaymentAttemptSucceeded(String payload) throws JsonProcessingException {
        var event = OBJECT_MAPPER.readValue(payload, PaymentAttemptSucceeded.class);
        log.info("Processing PaymentAttemptSucceeded: attemptId={}, providerEventId={}",
                event.attemptId(), event.providerEventId());
    }

    private void handlePaymentAttemptFailed(String payload) throws JsonProcessingException {
        var event = OBJECT_MAPPER.readValue(payload, PaymentAttemptFailed.class);
        log.info("Processing PaymentAttemptFailed: attemptId={}, reason={}",
                event.attemptId(), event.reason());
    }
}
