package com.payme.adapters.messaging;

import com.payme.config.StreamProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class RedisStreamPublisher {

    private static final Logger log = LoggerFactory.getLogger(RedisStreamPublisher.class);

    private final StringRedisTemplate redisTemplate;
    private final StreamProperties streamProperties;

    public RedisStreamPublisher(StringRedisTemplate redisTemplate, StreamProperties streamProperties) {
        this.redisTemplate = redisTemplate;
        this.streamProperties = streamProperties;
    }

    public void publish(String streamKey, Map<String, String> fields) {
        var record = StreamRecords.mapBacked(fields).withStreamKey(streamKey);
        var recordId = redisTemplate.opsForStream().add(record);
        log.debug("Published event to stream {}: recordId={}, eventType={}",
                streamKey, recordId, fields.get("eventType"));
    }

    public void publish(String aggregateType, String eventId, String eventType,
                        String aggregateId, String occurredAt, String payload) {
        String streamKey = streamProperties.streamKeyForAggregateType(aggregateType);
        Map<String, String> fields = Map.of(
                "eventId", eventId,
                "eventType", eventType,
                "aggregateId", aggregateId,
                "occurredAt", occurredAt,
                "payload", payload
        );
        publish(streamKey, fields);
    }
}
