package com.payme.domain.event;

import java.time.Instant;

public interface DomainEvent {
    String eventId();
    Instant occurredAt();
    String aggregateId();
    String eventType();
}
