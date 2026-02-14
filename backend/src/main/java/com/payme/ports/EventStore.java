package com.payme.ports;

import com.payme.domain.event.DomainEvent;

public interface EventStore {

    void store(DomainEvent event, String aggregateType);
}
