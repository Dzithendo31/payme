package com.payme.api;

import com.payme.config.StreamProperties;
import org.springframework.data.redis.connection.stream.PendingMessagesSummary;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class StreamLagController {

    private final StringRedisTemplate redisTemplate;
    private final StreamProperties streamProperties;

    public StreamLagController(StringRedisTemplate redisTemplate, StreamProperties streamProperties) {
        this.redisTemplate = redisTemplate;
        this.streamProperties = streamProperties;
    }

    @GetMapping("/stream-lag")
    public ResponseEntity<List<Map<String, Object>>> getStreamLag() {
        List<Map<String, Object>> results = new ArrayList<>();

        addStreamInfo(results, "invoice-events", streamProperties.getInvoiceEvents());
        addStreamInfo(results, "payment-events", streamProperties.getPaymentEvents());
        addStreamInfo(results, "webhook-events", streamProperties.getWebhookEvents());

        return ResponseEntity.ok(results);
    }

    private void addStreamInfo(List<Map<String, Object>> results, String name, String streamKey) {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("stream", name);
        info.put("streamKey", streamKey);

        try {
            Long streamLength = redisTemplate.opsForStream().size(streamKey);
            info.put("length", streamLength != null ? streamLength : 0);

            PendingMessagesSummary pending = redisTemplate.opsForStream()
                    .pending(streamKey, streamProperties.getConsumerGroup());
            info.put("consumerGroup", streamProperties.getConsumerGroup());
            info.put("pendingCount", pending.getTotalPendingMessages());
            info.put("minId", pending.minMessageId());
            info.put("maxId", pending.maxMessageId());

        } catch (Exception e) {
            info.put("error", "Stream or consumer group not initialized: " + e.getMessage());
        }

        results.add(info);
    }
}
