package com.payme.adapters.persistence.jpa;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.payme.domain.event.DomainEvent;
import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(
        name = "domain_events",
        indexes = {
                @Index(name = "idx_events_aggregate", columnList = "aggregate_type, aggregate_id"),
                @Index(name = "idx_events_type", columnList = "event_type"),
                @Index(name = "idx_events_unpublished", columnList = "published")
        }
)
public class DomainEventEntity {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    @Id
    @Column(name = "event_id", nullable = false, length = 36)
    private String eventId;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(name = "aggregate_id", nullable = false, length = 36)
    private String aggregateId;

    @Column(name = "aggregate_type", nullable = false, length = 50)
    private String aggregateType;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "payload", nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(name = "published", nullable = false)
    private boolean published;

    protected DomainEventEntity() {
    }

    public DomainEventEntity(
            String eventId,
            String eventType,
            String aggregateId,
            String aggregateType,
            Instant occurredAt,
            String payload,
            boolean published
    ) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.aggregateId = aggregateId;
        this.aggregateType = aggregateType;
        this.occurredAt = occurredAt;
        this.payload = payload;
        this.published = published;
    }

    public static DomainEventEntity fromDomainEvent(DomainEvent event, String aggregateType) {
        return new DomainEventEntity(
                event.eventId(),
                event.eventType(),
                event.aggregateId(),
                aggregateType,
                event.occurredAt(),
                toJson(event),
                false
        );
    }

    public void markPublished() {
        this.published = true;
    }

    // --- JSON serialization ---

    private static String toJson(DomainEvent event) {
        try {
            return OBJECT_MAPPER.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize domain event: " + event.eventType(), e);
        }
    }

    public <T extends DomainEvent> T toEvent(Class<T> eventClass) {
        try {
            return OBJECT_MAPPER.readValue(payload, eventClass);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to deserialize domain event: " + eventType, e);
        }
    }

    // --- Getters ---

    public String getEventId() {
        return eventId;
    }

    public String getEventType() {
        return eventType;
    }

    public String getAggregateId() {
        return aggregateId;
    }

    public String getAggregateType() {
        return aggregateType;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public String getPayload() {
        return payload;
    }

    public boolean isPublished() {
        return published;
    }
}
