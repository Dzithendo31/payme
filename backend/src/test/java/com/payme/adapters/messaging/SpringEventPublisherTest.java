package com.payme.adapters.messaging;

import com.payme.domain.event.DomainEvent;
import com.payme.domain.event.InvoiceCreated;
import com.payme.domain.event.InvoiceExpired;
import com.payme.domain.event.PaymentAttemptCreated;
import com.payme.ports.EventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SpringEventPublisherTest {

    private List<Object> publishedEvents;
    private SpringEventPublisher publisher;

    @BeforeEach
    void setUp() {
        publishedEvents = new ArrayList<>();
        ApplicationEventPublisher mockSpringPublisher = publishedEvents::add;
        publisher = new SpringEventPublisher(mockSpringPublisher);
    }

    @Test
    void implementsEventPublisher() {
        assertInstanceOf(EventPublisher.class, publisher);
    }

    @Test
    void publish_delegatesToSpringApplicationEventPublisher() {
        Instant now = Instant.now();
        InvoiceCreated event = new InvoiceCreated(
                "evt-1", now, "inv-1", "merchant-1",
                new BigDecimal("100.00"), "ZAR", "Test invoice", now.plusSeconds(3600)
        );

        publisher.publish(event);

        assertEquals(1, publishedEvents.size());
        assertSame(event, publishedEvents.get(0));
    }

    @Test
    void publishAll_delegatesEachEventToSpring() {
        Instant now = Instant.now();
        InvoiceCreated event1 = new InvoiceCreated(
                "evt-1", now, "inv-1", "merchant-1",
                new BigDecimal("100.00"), "ZAR", "Test", now.plusSeconds(3600)
        );
        InvoiceExpired event2 = new InvoiceExpired("evt-2", now, "inv-2", now);
        PaymentAttemptCreated event3 = new PaymentAttemptCreated(
                "evt-3", now, "attempt-1", "inv-1", "PAYFAST", "ref-123"
        );

        publisher.publishAll(List.of(event1, event2, event3));

        assertEquals(3, publishedEvents.size());
        assertSame(event1, publishedEvents.get(0));
        assertSame(event2, publishedEvents.get(1));
        assertSame(event3, publishedEvents.get(2));
    }

    @Test
    void publishAll_withEmptyList_publishesNothing() {
        publisher.publishAll(List.of());

        assertTrue(publishedEvents.isEmpty());
    }

    @Test
    void publish_preservesEventType() {
        Instant now = Instant.now();
        InvoiceCreated event = new InvoiceCreated(
                "evt-1", now, "inv-1", "merchant-1",
                new BigDecimal("50.00"), "USD", "Order payment", now.plusSeconds(7200)
        );

        publisher.publish(event);

        DomainEvent published = (DomainEvent) publishedEvents.get(0);
        assertEquals("InvoiceCreated", published.eventType());
        assertEquals("inv-1", published.aggregateId());
        assertEquals("evt-1", published.eventId());
    }
}
