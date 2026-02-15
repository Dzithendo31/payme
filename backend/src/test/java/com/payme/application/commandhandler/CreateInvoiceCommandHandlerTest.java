package com.payme.application.commandhandler;

import com.payme.domain.*;
import com.payme.domain.command.CreateInvoiceCommand;
import com.payme.domain.event.DomainEvent;
import com.payme.domain.event.InvoiceCreated;
import com.payme.ports.Clock;
import com.payme.ports.EventPublisher;
import com.payme.ports.EventStore;
import com.payme.ports.InvoiceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class CreateInvoiceCommandHandlerTest {

    private StubInvoiceRepository invoiceRepository;
    private StubClock clock;
    private CapturingEventStore eventStore;
    private CapturingEventPublisher eventPublisher;
    private CreateInvoiceCommandHandler handler;

    private static final Instant FIXED_TIME = Instant.parse("2026-01-15T10:00:00Z");

    @BeforeEach
    void setUp() {
        invoiceRepository = new StubInvoiceRepository();
        clock = new StubClock(FIXED_TIME);
        eventStore = new CapturingEventStore();
        eventPublisher = new CapturingEventPublisher();
        handler = new CreateInvoiceCommandHandler(
                invoiceRepository, clock, eventStore, eventPublisher
        );
    }

    @Test
    void handle_createsInvoiceWithCorrectFields() {
        CreateInvoiceCommand cmd = new CreateInvoiceCommand(
                "cmd-1", "merchant-1", new BigDecimal("100.00"), "ZAR", "Test invoice", 24
        );

        Invoice result = handler.handle(cmd);

        assertNotNull(result.getInvoiceId());
        assertEquals("merchant-1", result.getMerchantId().getValue());
        assertEquals(new BigDecimal("100.00"), result.getMoney().getAmount());
        assertEquals(Currency.ZAR, result.getMoney().getCurrency());
        assertEquals("Test invoice", result.getDescription());
        assertEquals(InvoiceStatus.CREATED, result.getStatus());
    }

    @Test
    void handle_setsTimestampsFromClock() {
        CreateInvoiceCommand cmd = new CreateInvoiceCommand(
                "cmd-1", "merchant-1", new BigDecimal("50.00"), "USD", "Order", 48
        );

        Invoice result = handler.handle(cmd);

        assertEquals(FIXED_TIME, result.getCreatedAt());
        assertEquals(FIXED_TIME, result.getUpdatedAt());
    }

    @Test
    void handle_setsExpiryBasedOnExpiryHours() {
        CreateInvoiceCommand cmd = new CreateInvoiceCommand(
                "cmd-1", "merchant-1", new BigDecimal("50.00"), "USD", "Order", 48
        );

        Invoice result = handler.handle(cmd);

        Instant expectedExpiry = FIXED_TIME.plus(Duration.ofHours(48));
        assertEquals(expectedExpiry, result.getExpiresAt());
    }

    @Test
    void handle_savesInvoiceToRepository() {
        CreateInvoiceCommand cmd = new CreateInvoiceCommand(
                "cmd-1", "merchant-1", new BigDecimal("100.00"), "ZAR", "Test", 24
        );

        Invoice result = handler.handle(cmd);

        assertEquals(1, invoiceRepository.savedInvoices.size());
        assertEquals(result.getInvoiceId(), invoiceRepository.savedInvoices.get(0).getInvoiceId());
    }

    @Test
    void handle_storesInvoiceCreatedEventInEventStore() {
        CreateInvoiceCommand cmd = new CreateInvoiceCommand(
                "cmd-1", "merchant-1", new BigDecimal("100.00"), "ZAR", "Test invoice", 24
        );

        Invoice result = handler.handle(cmd);

        assertEquals(1, eventStore.storedEvents.size());
        CapturingEventStore.StoredEntry entry = eventStore.storedEvents.get(0);
        assertEquals("Invoice", entry.aggregateType);

        InvoiceCreated stored = (InvoiceCreated) entry.event;
        assertEquals(result.getInvoiceId().getValue(), stored.invoiceId());
        assertEquals("InvoiceCreated", stored.eventType());
    }

    @Test
    void handle_publishesInvoiceCreatedEvent() {
        CreateInvoiceCommand cmd = new CreateInvoiceCommand(
                "cmd-1", "merchant-1", new BigDecimal("100.00"), "ZAR", "Test invoice", 24
        );

        Invoice result = handler.handle(cmd);

        assertEquals(1, eventPublisher.publishedEvents.size());
        DomainEvent published = eventPublisher.publishedEvents.get(0);
        assertInstanceOf(InvoiceCreated.class, published);

        InvoiceCreated event = (InvoiceCreated) published;
        assertEquals(result.getInvoiceId().getValue(), event.invoiceId());
        assertEquals("merchant-1", event.merchantId());
        assertEquals(new BigDecimal("100.00"), event.amount());
        assertEquals("ZAR", event.currency());
        assertEquals("Test invoice", event.description());
        assertEquals(result.getExpiresAt(), event.expiresAt());
    }

    @Test
    void handle_eventHasCorrectMetadata() {
        CreateInvoiceCommand cmd = new CreateInvoiceCommand(
                "cmd-1", "merchant-1", new BigDecimal("100.00"), "ZAR", "Test", 24
        );

        handler.handle(cmd);

        InvoiceCreated event = (InvoiceCreated) eventPublisher.publishedEvents.get(0);
        assertNotNull(event.eventId());
        assertFalse(event.eventId().isEmpty());
        assertEquals(FIXED_TIME, event.occurredAt());
        assertEquals("InvoiceCreated", event.eventType());
    }

    @Test
    void handle_storedAndPublishedEventsAreTheSame() {
        CreateInvoiceCommand cmd = new CreateInvoiceCommand(
                "cmd-1", "merchant-1", new BigDecimal("100.00"), "ZAR", "Test", 24
        );

        handler.handle(cmd);

        DomainEvent stored = eventStore.storedEvents.get(0).event;
        DomainEvent published = eventPublisher.publishedEvents.get(0);
        assertSame(stored, published);
    }

    // --- Test doubles ---

    private static class StubInvoiceRepository implements InvoiceRepository {
        final List<Invoice> savedInvoices = new ArrayList<>();

        @Override
        public Invoice save(Invoice invoice) {
            savedInvoices.add(invoice);
            return invoice;
        }

        @Override
        public Optional<Invoice> findById(InvoiceId invoiceId) {
            return Optional.empty();
        }

        @Override
        public boolean existsById(InvoiceId invoiceId) {
            return false;
        }
    }

    private static class StubClock implements Clock {
        final Instant fixedTime;

        StubClock(Instant fixedTime) {
            this.fixedTime = fixedTime;
        }

        @Override
        public Instant now() {
            return fixedTime;
        }
    }

    private static class CapturingEventStore implements EventStore {
        final List<StoredEntry> storedEvents = new ArrayList<>();

        record StoredEntry(DomainEvent event, String aggregateType) {}

        @Override
        public void store(DomainEvent event, String aggregateType) {
            storedEvents.add(new StoredEntry(event, aggregateType));
        }
    }

    private static class CapturingEventPublisher implements EventPublisher {
        final List<DomainEvent> publishedEvents = new ArrayList<>();

        @Override
        public void publish(DomainEvent event) {
            publishedEvents.add(event);
        }

        @Override
        public void publishAll(List<DomainEvent> events) {
            publishedEvents.addAll(events);
        }
    }
}
