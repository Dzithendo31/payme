package com.payme.adapters.messaging;

import com.payme.domain.event.DomainEvent;
import com.payme.ports.EventPublisher;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class SpringEventPublisher implements EventPublisher {

    private final ApplicationEventPublisher springPublisher;

    public SpringEventPublisher(ApplicationEventPublisher springPublisher) {
        this.springPublisher = springPublisher;
    }

    @Override
    public void publish(DomainEvent event) {
        springPublisher.publishEvent(event);
    }

    @Override
    public void publishAll(List<DomainEvent> events) {
        events.forEach(this::publish);
    }
}
