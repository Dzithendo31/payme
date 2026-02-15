package com.payme.adapters.messaging;

import com.payme.adapters.persistence.jpa.DomainEventEntity;
import com.payme.adapters.persistence.jpa.JpaDomainEventRepository;
import com.payme.domain.event.InvoiceCreated;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class OutboxPollerTest {

    private JpaDomainEventRepository domainEventRepository;
    private RedisStreamPublisher redisStreamPublisher;
    private OutboxPoller outboxPoller;

    @BeforeEach
    void setUp() {
        domainEventRepository = mock(JpaDomainEventRepository.class);
        redisStreamPublisher = mock(RedisStreamPublisher.class);
        outboxPoller = new OutboxPoller(domainEventRepository, redisStreamPublisher);
    }

    @Test
    void pollAndPublish_noUnpublishedEvents_doesNothing() {
        when(domainEventRepository.findByPublishedFalseOrderByOccurredAtAsc(any()))
                .thenReturn(List.of());

        outboxPoller.pollAndPublish();

        verifyNoInteractions(redisStreamPublisher);
    }

    @Test
    void pollAndPublish_publishesEventToRedisStream() {
        Instant now = Instant.parse("2026-01-15T10:00:00Z");
        InvoiceCreated event = new InvoiceCreated(
                "evt-1", now, "inv-1", "merchant-1",
                new BigDecimal("100.00"), "ZAR", "Test", now.plusSeconds(3600)
        );
        DomainEventEntity entity = DomainEventEntity.fromDomainEvent(event, "Invoice");

        when(domainEventRepository.findByPublishedFalseOrderByOccurredAtAsc(any()))
                .thenReturn(List.of(entity));

        outboxPoller.pollAndPublish();

        verify(redisStreamPublisher).publish(
                eq("Invoice"),
                eq("evt-1"),
                eq("InvoiceCreated"),
                eq("inv-1"),
                eq(now.toString()),
                any(String.class)
        );
    }

    @Test
    void pollAndPublish_marksEventAsPublished() {
        Instant now = Instant.parse("2026-01-15T10:00:00Z");
        InvoiceCreated event = new InvoiceCreated(
                "evt-1", now, "inv-1", "merchant-1",
                new BigDecimal("100.00"), "ZAR", "Test", now.plusSeconds(3600)
        );
        DomainEventEntity entity = DomainEventEntity.fromDomainEvent(event, "Invoice");
        assertFalse(entity.isPublished());

        when(domainEventRepository.findByPublishedFalseOrderByOccurredAtAsc(any()))
                .thenReturn(List.of(entity));

        outboxPoller.pollAndPublish();

        assertTrue(entity.isPublished());
    }

    @Test
    void pollAndPublish_onRedisFailure_stopsProcessingBatch() {
        Instant now = Instant.parse("2026-01-15T10:00:00Z");
        InvoiceCreated event1 = new InvoiceCreated(
                "evt-1", now, "inv-1", "merchant-1",
                new BigDecimal("100.00"), "ZAR", "Test1", now.plusSeconds(3600)
        );
        InvoiceCreated event2 = new InvoiceCreated(
                "evt-2", now, "inv-2", "merchant-1",
                new BigDecimal("200.00"), "ZAR", "Test2", now.plusSeconds(3600)
        );
        DomainEventEntity entity1 = DomainEventEntity.fromDomainEvent(event1, "Invoice");
        DomainEventEntity entity2 = DomainEventEntity.fromDomainEvent(event2, "Invoice");

        when(domainEventRepository.findByPublishedFalseOrderByOccurredAtAsc(any()))
                .thenReturn(List.of(entity1, entity2));

        doThrow(new RuntimeException("Redis connection failed"))
                .when(redisStreamPublisher).publish(
                        anyString(), anyString(), anyString(), anyString(), anyString(), anyString()
                );

        outboxPoller.pollAndPublish();

        assertFalse(entity1.isPublished());
        assertFalse(entity2.isPublished());
        verify(redisStreamPublisher, times(1)).publish(
                anyString(), anyString(), anyString(), anyString(), anyString(), anyString()
        );
    }

    @Test
    void pollAndPublish_publishesMultipleEventsInOrder() {
        Instant now = Instant.parse("2026-01-15T10:00:00Z");
        InvoiceCreated event1 = new InvoiceCreated(
                "evt-1", now, "inv-1", "merchant-1",
                new BigDecimal("100.00"), "ZAR", "Test1", now.plusSeconds(3600)
        );
        InvoiceCreated event2 = new InvoiceCreated(
                "evt-2", now.plusSeconds(1), "inv-2", "merchant-1",
                new BigDecimal("200.00"), "ZAR", "Test2", now.plusSeconds(3600)
        );
        DomainEventEntity entity1 = DomainEventEntity.fromDomainEvent(event1, "Invoice");
        DomainEventEntity entity2 = DomainEventEntity.fromDomainEvent(event2, "Invoice");

        when(domainEventRepository.findByPublishedFalseOrderByOccurredAtAsc(any()))
                .thenReturn(List.of(entity1, entity2));

        outboxPoller.pollAndPublish();

        verify(redisStreamPublisher, times(2)).publish(
                anyString(), anyString(), anyString(), anyString(), anyString(), anyString()
        );
        assertTrue(entity1.isPublished());
        assertTrue(entity2.isPublished());
    }
}
