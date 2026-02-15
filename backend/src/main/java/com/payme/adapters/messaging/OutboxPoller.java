package com.payme.adapters.messaging;

import com.payme.adapters.persistence.jpa.DomainEventEntity;
import com.payme.adapters.persistence.jpa.JpaDomainEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
public class OutboxPoller {

    private static final Logger log = LoggerFactory.getLogger(OutboxPoller.class);
    private static final int BATCH_SIZE = 100;

    private final JpaDomainEventRepository domainEventRepository;
    private final RedisStreamPublisher redisStreamPublisher;

    public OutboxPoller(JpaDomainEventRepository domainEventRepository,
                        RedisStreamPublisher redisStreamPublisher) {
        this.domainEventRepository = domainEventRepository;
        this.redisStreamPublisher = redisStreamPublisher;
    }

    @Scheduled(fixedDelay = 500)
    @Transactional
    public void pollAndPublish() {
        List<DomainEventEntity> unpublished = domainEventRepository
                .findByPublishedFalseOrderByOccurredAtAsc(PageRequest.of(0, BATCH_SIZE));

        if (unpublished.isEmpty()) {
            return;
        }

        log.debug("Outbox poller found {} unpublished events", unpublished.size());

        for (DomainEventEntity entity : unpublished) {
            try {
                redisStreamPublisher.publish(
                        entity.getAggregateType(),
                        entity.getEventId(),
                        entity.getEventType(),
                        entity.getAggregateId(),
                        entity.getOccurredAt().toString(),
                        entity.getPayload()
                );
                entity.markPublished();
            } catch (Exception e) {
                log.error("Failed to publish event {} to Redis stream, will retry on next poll",
                        entity.getEventId(), e);
                break;
            }
        }
    }
}
