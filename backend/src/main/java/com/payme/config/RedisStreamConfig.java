package com.payme.config;

import com.payme.adapters.messaging.consumers.PaymentStatusConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;
import org.springframework.data.redis.stream.Subscription;

import java.time.Duration;

@Configuration
public class RedisStreamConfig {

    private static final Logger log = LoggerFactory.getLogger(RedisStreamConfig.class);

    private final StreamProperties streamProperties;
    private final StringRedisTemplate redisTemplate;

    public RedisStreamConfig(StreamProperties streamProperties, StringRedisTemplate redisTemplate) {
        this.streamProperties = streamProperties;
        this.redisTemplate = redisTemplate;
    }

    @Bean
    StreamMessageListenerContainer<String, ?> paymentStreamListenerContainer(
            RedisConnectionFactory connectionFactory,
            PaymentStatusConsumer paymentStatusConsumer) {

        var options = StreamMessageListenerContainer.StreamMessageListenerContainerOptions
                .builder()
                .pollTimeout(Duration.ofSeconds(2))
                .build();

        var container = StreamMessageListenerContainer.create(connectionFactory, options);

        createConsumerGroupIfNotExists(streamProperties.getPaymentEvents());

        container.receive(
                Consumer.from(streamProperties.getConsumerGroup(), "payment-consumer-1"),
                StreamOffset.create(streamProperties.getPaymentEvents(), ReadOffset.lastConsumed()),
                paymentStatusConsumer
        );

        container.start();
        return container;
    }

    @Bean
    StreamMessageListenerContainer<String, ?> invoiceStreamListenerContainer(
            RedisConnectionFactory connectionFactory) {

        var options = StreamMessageListenerContainer.StreamMessageListenerContainerOptions
                .builder()
                .pollTimeout(Duration.ofSeconds(2))
                .build();

        var container = StreamMessageListenerContainer.create(connectionFactory, options);

        createConsumerGroupIfNotExists(streamProperties.getInvoiceEvents());

        container.start();
        return container;
    }

    @Bean
    StreamMessageListenerContainer<String, ?> webhookStreamListenerContainer(
            RedisConnectionFactory connectionFactory) {

        var options = StreamMessageListenerContainer.StreamMessageListenerContainerOptions
                .builder()
                .pollTimeout(Duration.ofSeconds(2))
                .build();

        var container = StreamMessageListenerContainer.create(connectionFactory, options);

        createConsumerGroupIfNotExists(streamProperties.getWebhookEvents());

        container.start();
        return container;
    }

    private void createConsumerGroupIfNotExists(String streamKey) {
        try {
            redisTemplate.opsForStream().createGroup(streamKey, streamProperties.getConsumerGroup());
            log.info("Created consumer group '{}' for stream '{}'",
                    streamProperties.getConsumerGroup(), streamKey);
        } catch (RedisSystemException e) {
            log.debug("Consumer group '{}' already exists for stream '{}' (or stream needs first message)",
                    streamProperties.getConsumerGroup(), streamKey);
        }
    }
}
