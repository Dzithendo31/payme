package com.payme.adapters.persistence.jpa;

import com.payme.domain.event.DomainEvent;
import com.payme.ports.EventStore;
import org.springframework.stereotype.Component;

@Component
public class EventStoreAdapter implements EventStore {

    private final JpaDomainEventRepository jpaDomainEventRepository;

    public EventStoreAdapter(JpaDomainEventRepository jpaDomainEventRepository) {
        this.jpaDomainEventRepository = jpaDomainEventRepository;
    }

    @Override
    public void store(DomainEvent event, String aggregateType) {
        DomainEventEntity entity = DomainEventEntity.fromDomainEvent(event, aggregateType);
        jpaDomainEventRepository.save(entity);
    }
}
