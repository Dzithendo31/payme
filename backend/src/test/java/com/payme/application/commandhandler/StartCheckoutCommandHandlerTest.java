package com.payme.application.commandhandler;

import com.payme.domain.*;
import com.payme.domain.command.StartCheckoutCommand;
import com.payme.domain.event.DomainEvent;
import com.payme.domain.event.InvoiceMarkedPending;
import com.payme.domain.event.PaymentAttemptCreated;
import com.payme.domain.exceptions.InvalidInvoiceStateException;
import com.payme.domain.exceptions.InvoiceNotFoundException;
import com.payme.ports.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class StartCheckoutCommandHandlerTest {

    private StubInvoiceRepository invoiceRepository;
    private StubPaymentAttemptRepository paymentAttemptRepository;
    private StubPaymentProvider paymentProvider;
    private StubClock clock;
    private CheckoutUrls checkoutUrls;
    private CapturingEventStore eventStore;
    private CapturingEventPublisher eventPublisher;
    private StartCheckoutCommandHandler handler;

    private static final Instant FIXED_TIME = Instant.parse("2026-01-15T10:00:00Z");

    @BeforeEach
    void setUp() {
        invoiceRepository = new StubInvoiceRepository();
        paymentAttemptRepository = new StubPaymentAttemptRepository();
        paymentProvider = new StubPaymentProvider();
        clock = new StubClock(FIXED_TIME);
        checkoutUrls = new CheckoutUrls("http://localhost/success", "http://localhost/cancel");
        eventStore = new CapturingEventStore();
        eventPublisher = new CapturingEventPublisher();
        handler = new StartCheckoutCommandHandler(
                invoiceRepository, paymentAttemptRepository, paymentProvider,
                clock, checkoutUrls, eventStore, eventPublisher, "FAKE"
        );
    }

    private Invoice createPayableInvoice(String invoiceId) {
        return new Invoice(
                new InvoiceId(invoiceId),
                new MerchantId("merchant-1"),
                new Money(new BigDecimal("100.00"), Currency.ZAR),
                "Test invoice",
                InvoiceStatus.CREATED,
                FIXED_TIME.plus(Duration.ofHours(24)),
                FIXED_TIME.minus(Duration.ofHours(1)),
                FIXED_TIME.minus(Duration.ofHours(1))
        );
    }

    @Test
    void handle_returnsCheckoutResult() {
        Invoice invoice = createPayableInvoice("inv-1");
        invoiceRepository.addInvoice(invoice);

        StartCheckoutCommand cmd = new StartCheckoutCommand("cmd-1", "inv-1");
        StartCheckoutCommandHandler.CheckoutResult result = handler.handle(cmd);

        assertNotNull(result.getCheckoutUrl());
        assertNotNull(result.getAttemptId());
        assertTrue(result.getCheckoutUrl().contains("fake-gateway"));
    }

    @Test
    void handle_savesPaymentAttempt() {
        Invoice invoice = createPayableInvoice("inv-1");
        invoiceRepository.addInvoice(invoice);

        StartCheckoutCommand cmd = new StartCheckoutCommand("cmd-1", "inv-1");
        handler.handle(cmd);

        assertEquals(1, paymentAttemptRepository.savedAttempts.size());
        PaymentAttempt saved = paymentAttemptRepository.savedAttempts.get(0);
        assertEquals("inv-1", saved.getInvoiceId().getValue());
        assertEquals(PaymentAttemptStatus.PENDING, saved.getStatus());
    }

    @Test
    void handle_marksInvoiceAsPending() {
        Invoice invoice = createPayableInvoice("inv-1");
        invoiceRepository.addInvoice(invoice);

        StartCheckoutCommand cmd = new StartCheckoutCommand("cmd-1", "inv-1");
        handler.handle(cmd);

        // Invoice should be saved twice: once original lookup creates it, once after marking pending
        Invoice savedInvoice = invoiceRepository.savedInvoices.get(invoiceRepository.savedInvoices.size() - 1);
        assertEquals(InvoiceStatus.PENDING, savedInvoice.getStatus());
    }

    @Test
    void handle_publishesPaymentAttemptCreatedAndInvoiceMarkedPendingEvents() {
        Invoice invoice = createPayableInvoice("inv-1");
        invoiceRepository.addInvoice(invoice);

        StartCheckoutCommand cmd = new StartCheckoutCommand("cmd-1", "inv-1");
        handler.handle(cmd);

        assertEquals(2, eventPublisher.publishedEvents.size());

        assertInstanceOf(PaymentAttemptCreated.class, eventPublisher.publishedEvents.get(0));
        PaymentAttemptCreated attemptEvent = (PaymentAttemptCreated) eventPublisher.publishedEvents.get(0);
        assertEquals("inv-1", attemptEvent.invoiceId());

        assertInstanceOf(InvoiceMarkedPending.class, eventPublisher.publishedEvents.get(1));
        InvoiceMarkedPending pendingEvent = (InvoiceMarkedPending) eventPublisher.publishedEvents.get(1);
        assertEquals("inv-1", pendingEvent.invoiceId());
    }

    @Test
    void handle_storesEventsInEventStore() {
        Invoice invoice = createPayableInvoice("inv-1");
        invoiceRepository.addInvoice(invoice);

        StartCheckoutCommand cmd = new StartCheckoutCommand("cmd-1", "inv-1");
        handler.handle(cmd);

        assertEquals(2, eventStore.storedEvents.size());
        assertEquals("PaymentAttempt", eventStore.storedEvents.get(0).aggregateType);
        assertEquals("Invoice", eventStore.storedEvents.get(1).aggregateType);
    }

    @Test
    void handle_storedAndPublishedEventsMatch() {
        Invoice invoice = createPayableInvoice("inv-1");
        invoiceRepository.addInvoice(invoice);

        StartCheckoutCommand cmd = new StartCheckoutCommand("cmd-1", "inv-1");
        handler.handle(cmd);

        for (int i = 0; i < eventStore.storedEvents.size(); i++) {
            assertSame(eventStore.storedEvents.get(i).event, eventPublisher.publishedEvents.get(i));
        }
    }

    @Test
    void handle_throwsWhenInvoiceNotFound() {
        StartCheckoutCommand cmd = new StartCheckoutCommand("cmd-1", "non-existent");

        assertThrows(InvoiceNotFoundException.class, () -> handler.handle(cmd));
    }

    @Test
    void handle_throwsWhenInvoiceExpired() {
        Invoice expiredInvoice = new Invoice(
                new InvoiceId("inv-expired"),
                new MerchantId("merchant-1"),
                new Money(new BigDecimal("100.00"), Currency.ZAR),
                "Expired invoice",
                InvoiceStatus.CREATED,
                FIXED_TIME.minus(Duration.ofHours(1)), // expired
                FIXED_TIME.minus(Duration.ofHours(25)),
                FIXED_TIME.minus(Duration.ofHours(25))
        );
        invoiceRepository.addInvoice(expiredInvoice);

        StartCheckoutCommand cmd = new StartCheckoutCommand("cmd-1", "inv-expired");

        InvalidInvoiceStateException ex = assertThrows(
                InvalidInvoiceStateException.class, () -> handler.handle(cmd));
        assertTrue(ex.getMessage().contains("expired"));
    }

    @Test
    void handle_throwsWhenInvoiceAlreadyPaid() {
        Invoice paidInvoice = new Invoice(
                new InvoiceId("inv-paid"),
                new MerchantId("merchant-1"),
                new Money(new BigDecimal("100.00"), Currency.ZAR),
                "Paid invoice",
                InvoiceStatus.SUCCEEDED,
                FIXED_TIME.plus(Duration.ofHours(24)),
                FIXED_TIME.minus(Duration.ofHours(1)),
                FIXED_TIME.minus(Duration.ofHours(1))
        );
        invoiceRepository.addInvoice(paidInvoice);

        StartCheckoutCommand cmd = new StartCheckoutCommand("cmd-1", "inv-paid");

        InvalidInvoiceStateException ex = assertThrows(
                InvalidInvoiceStateException.class, () -> handler.handle(cmd));
        assertTrue(ex.getMessage().contains("already paid"));
    }

    @Test
    void handle_eventsHaveCorrectMetadata() {
        Invoice invoice = createPayableInvoice("inv-1");
        invoiceRepository.addInvoice(invoice);

        StartCheckoutCommand cmd = new StartCheckoutCommand("cmd-1", "inv-1");
        handler.handle(cmd);

        PaymentAttemptCreated attemptEvent = (PaymentAttemptCreated) eventPublisher.publishedEvents.get(0);
        assertNotNull(attemptEvent.eventId());
        assertFalse(attemptEvent.eventId().isEmpty());
        assertEquals(FIXED_TIME, attemptEvent.occurredAt());
        assertEquals("PaymentAttemptCreated", attemptEvent.eventType());

        InvoiceMarkedPending pendingEvent = (InvoiceMarkedPending) eventPublisher.publishedEvents.get(1);
        assertNotNull(pendingEvent.eventId());
        assertEquals(FIXED_TIME, pendingEvent.occurredAt());
        assertEquals("InvoiceMarkedPending", pendingEvent.eventType());
    }

    // --- Test doubles ---

    private static class StubInvoiceRepository implements InvoiceRepository {
        final List<Invoice> savedInvoices = new ArrayList<>();
        private final List<Invoice> invoices = new ArrayList<>();

        void addInvoice(Invoice invoice) {
            invoices.add(invoice);
        }

        @Override
        public Invoice save(Invoice invoice) {
            savedInvoices.add(invoice);
            return invoice;
        }

        @Override
        public Optional<Invoice> findById(InvoiceId invoiceId) {
            return invoices.stream()
                    .filter(i -> i.getInvoiceId().getValue().equals(invoiceId.getValue()))
                    .findFirst();
        }

        @Override
        public boolean existsById(InvoiceId invoiceId) {
            return invoices.stream().anyMatch(i -> i.getInvoiceId().getValue().equals(invoiceId.getValue()));
        }
    }

    private static class StubPaymentAttemptRepository implements PaymentAttemptRepository {
        final List<PaymentAttempt> savedAttempts = new ArrayList<>();

        @Override
        public PaymentAttempt save(PaymentAttempt attempt) {
            savedAttempts.add(attempt);
            return attempt;
        }

        @Override
        public Optional<PaymentAttempt> findById(PaymentAttemptId attemptId) {
            return Optional.empty();
        }

        @Override
        public List<PaymentAttempt> findByInvoiceId(InvoiceId invoiceId) {
            return List.of();
        }

        @Override
        public Optional<PaymentAttempt> findByProviderReference(String providerReference) {
            return Optional.empty();
        }
    }

    private static class StubPaymentProvider implements PaymentProvider {
        @Override
        public CheckoutSession createCheckoutSession(Invoice invoice, PaymentAttemptId attemptId, CheckoutUrls urls) {
            return new CheckoutSession(
                    "http://fake-gateway.local/checkout/" + attemptId.getValue(),
                    "fake-ref-" + attemptId.getValue()
            );
        }

        @Override
        public CanonicalPaymentEvent verifyAndParseWebhook(String rawBody, java.util.Map<String, String> headers) {
            throw new UnsupportedOperationException("Not used in checkout tests");
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
