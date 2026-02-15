package com.payme.domain.event;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class DomainEventTest {

    record TestEvent(
            String eventId,
            Instant occurredAt,
            String aggregateId,
            String eventType
    ) implements DomainEvent {}

    @Test
    void domainEvent_recordImplementation_exposesAllFields() {
        Instant now = Instant.now();
        DomainEvent event = new TestEvent("evt-1", now, "agg-42", "TestEvent");

        assertEquals("evt-1", event.eventId());
        assertEquals(now, event.occurredAt());
        assertEquals("agg-42", event.aggregateId());
        assertEquals("TestEvent", event.eventType());
    }

    @Test
    void domainEvent_isInstanceCheck() {
        DomainEvent event = new TestEvent("evt-2", Instant.now(), "agg-1", "TestEvent");
        assertInstanceOf(DomainEvent.class, event);
    }

    @Test
    void domainEvent_twoEventsWithSameData_areEqual() {
        Instant now = Instant.now();
        TestEvent a = new TestEvent("evt-1", now, "agg-1", "TestEvent");
        TestEvent b = new TestEvent("evt-1", now, "agg-1", "TestEvent");

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void domainEvent_differentEventIds_areNotEqual() {
        Instant now = Instant.now();
        TestEvent a = new TestEvent("evt-1", now, "agg-1", "TestEvent");
        TestEvent b = new TestEvent("evt-2", now, "agg-1", "TestEvent");

        assertNotEquals(a, b);
    }
}
