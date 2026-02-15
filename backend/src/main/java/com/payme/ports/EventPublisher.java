package com.payme.ports;

import com.payme.domain.event.DomainEvent;

import java.util.List;

public interface EventPublisher {

    void publish(DomainEvent event);

    void publishAll(List<DomainEvent> events);
}
