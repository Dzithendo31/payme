package com.payme.application.commandhandler;

import com.payme.domain.*;
import com.payme.domain.command.ProcessWebhookCommand;
import com.payme.domain.event.*;
import com.payme.domain.exceptions.WebhookVerificationException;
import com.payme.ports.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class ProcessWebhookCommandHandlerTest {

    private StubPaymentProvider paymentProvider;
    private StubWebhookEventRepository webhookEventRepository;
    private StubPaymentAttemptRepository paymentAttemptRepository;
    private StubInvoiceRepository invoiceRepository;
    private StubHashService hashService;
    private StubClock clock;
    private CapturingEventStore eventStore;
    private CapturingEventPublisher eventPublisher;
    private ProcessWebhookCommandHandler handler;

    private static final Instant FIXED_TIME = Instant.parse("2026-01-15T10:00:00Z");
    private static final String PAYLOAD_HASH = "sha256-hash-of-payload";

    @BeforeEach
    void setUp() {
        paymentProvider = new StubPaymentProvider();
        webhookEventRepository = new StubWebhookEventRepository();
        paymentAttemptRepository = new StubPaymentAttemptRepository();
        invoiceRepository = new StubInvoiceRepository();
        hashService = new StubHashService(PAYLOAD_HASH);
        clock = new StubClock(FIXED_TIME);
        eventStore = new CapturingEventStore();
        eventPublisher = new CapturingEventPublisher();
        handler = new ProcessWebhookCommandHandler(
                paymentProvider, webhookEventRepository, paymentAttemptRepository,
                invoiceRepository, hashService, clock, eventStore, eventPublisher
        );
    }

    @Test
    void handle_successfulPayment_updatesAttemptAndInvoice() {
        var attempt = createPendingAttempt("attempt-1", "invoice-1", "ref-123");
        var invoice = createPendingInvoice("invoice-1");
        paymentAttemptRepository.addAttempt(attempt);
        invoiceRepository.addInvoice(invoice);
        paymentProvider.setCanonicalEvent(createSucceededEvent("ref-123", "invoice-1"));

        var cmd = new ProcessWebhookCommand("cmd-1", "FAKE", "{\"data\":\"test\"}", Map.of());
        handler.handle(cmd);

        assertEquals(PaymentAttemptStatus.SUCCEEDED, paymentAttemptRepository.lastSaved().getStatus());
        assertEquals(InvoiceStatus.SUCCEEDED, invoiceRepository.lastSaved().getStatus());
    }

    @Test
    void handle_failedPayment_updatesAttemptAndInvoice() {
        var attempt = createPendingAttempt("attempt-1", "invoice-1", "ref-123");
        var invoice = createPendingInvoice("invoice-1");
        paymentAttemptRepository.addAttempt(attempt);
        invoiceRepository.addInvoice(invoice);
        paymentProvider.setCanonicalEvent(createFailedEvent("ref-123", "invoice-1"));

        var cmd = new ProcessWebhookCommand("cmd-1", "FAKE", "{\"data\":\"test\"}", Map.of());
        handler.handle(cmd);

        assertEquals(PaymentAttemptStatus.FAILED, paymentAttemptRepository.lastSaved().getStatus());
        assertEquals(InvoiceStatus.FAILED, invoiceRepository.lastSaved().getStatus());
    }

    @Test
    void handle_successfulPayment_publishesWebhookReceivedAndProcessedEvents() {
        var attempt = createPendingAttempt("attempt-1", "invoice-1", "ref-123");
        var invoice = createPendingInvoice("invoice-1");
        paymentAttemptRepository.addAttempt(attempt);
        invoiceRepository.addInvoice(invoice);
        paymentProvider.setCanonicalEvent(createSucceededEvent("ref-123", "invoice-1"));

        var cmd = new ProcessWebhookCommand("cmd-1", "FAKE", "{\"data\":\"test\"}", Map.of());
        handler.handle(cmd);

        var published = eventPublisher.publishedEvents;
        assertTrue(published.stream().anyMatch(e -> e instanceof WebhookReceived));
        assertTrue(published.stream().anyMatch(e -> e instanceof WebhookProcessed));
    }

    @Test
    void handle_successfulPayment_publishesPaymentAttemptSucceededEvent() {
        var attempt = createPendingAttempt("attempt-1", "invoice-1", "ref-123");
        var invoice = createPendingInvoice("invoice-1");
        paymentAttemptRepository.addAttempt(attempt);
        invoiceRepository.addInvoice(invoice);
        paymentProvider.setCanonicalEvent(createSucceededEvent("ref-123", "invoice-1"));

        var cmd = new ProcessWebhookCommand("cmd-1", "FAKE", "{\"data\":\"test\"}", Map.of());
        handler.handle(cmd);

        var attemptEvents = eventPublisher.publishedEvents.stream()
                .filter(e -> e instanceof PaymentAttemptSucceeded)
                .map(e -> (PaymentAttemptSucceeded) e)
                .toList();
        assertEquals(1, attemptEvents.size());
        assertEquals("attempt-1", attemptEvents.get(0).attemptId());
    }

    @Test
    void handle_successfulPayment_publishesInvoicePaymentSucceededEvent() {
        var attempt = createPendingAttempt("attempt-1", "invoice-1", "ref-123");
        var invoice = createPendingInvoice("invoice-1");
        paymentAttemptRepository.addAttempt(attempt);
        invoiceRepository.addInvoice(invoice);
        paymentProvider.setCanonicalEvent(createSucceededEvent("ref-123", "invoice-1"));

        var cmd = new ProcessWebhookCommand("cmd-1", "FAKE", "{\"data\":\"test\"}", Map.of());
        handler.handle(cmd);

        var invoiceEvents = eventPublisher.publishedEvents.stream()
                .filter(e -> e instanceof InvoicePaymentSucceeded)
                .map(e -> (InvoicePaymentSucceeded) e)
                .toList();
        assertEquals(1, invoiceEvents.size());
        assertEquals("invoice-1", invoiceEvents.get(0).invoiceId());
        assertEquals("attempt-1", invoiceEvents.get(0).paymentAttemptId());
        assertEquals("FAKE", invoiceEvents.get(0).provider());
    }

    @Test
    void handle_failedPayment_publishesPaymentAttemptFailedEvent() {
        var attempt = createPendingAttempt("attempt-1", "invoice-1", "ref-123");
        var invoice = createPendingInvoice("invoice-1");
        paymentAttemptRepository.addAttempt(attempt);
        invoiceRepository.addInvoice(invoice);
        paymentProvider.setCanonicalEvent(createFailedEvent("ref-123", "invoice-1"));

        var cmd = new ProcessWebhookCommand("cmd-1", "FAKE", "{\"data\":\"test\"}", Map.of());
        handler.handle(cmd);

        var attemptEvents = eventPublisher.publishedEvents.stream()
                .filter(e -> e instanceof PaymentAttemptFailed)
                .map(e -> (PaymentAttemptFailed) e)
                .toList();
        assertEquals(1, attemptEvents.size());
        assertEquals("attempt-1", attemptEvents.get(0).attemptId());
    }

    @Test
    void handle_failedPayment_publishesInvoicePaymentFailedEvent() {
        var attempt = createPendingAttempt("attempt-1", "invoice-1", "ref-123");
        var invoice = createPendingInvoice("invoice-1");
        paymentAttemptRepository.addAttempt(attempt);
        invoiceRepository.addInvoice(invoice);
        paymentProvider.setCanonicalEvent(createFailedEvent("ref-123", "invoice-1"));

        var cmd = new ProcessWebhookCommand("cmd-1", "FAKE", "{\"data\":\"test\"}", Map.of());
        handler.handle(cmd);

        var invoiceEvents = eventPublisher.publishedEvents.stream()
                .filter(e -> e instanceof InvoicePaymentFailed)
                .map(e -> (InvoicePaymentFailed) e)
                .toList();
        assertEquals(1, invoiceEvents.size());
        assertEquals("invoice-1", invoiceEvents.get(0).invoiceId());
    }

    @Test
    void handle_duplicateWebhook_publishesDuplicatedEventAndReturnsEarly() {
        // Set up an existing webhook so duplicate detection triggers
        var existingWebhook = new WebhookEvent(
                WebhookEventId.of("existing-webhook-1"),
                ProviderName.FAKE,
                "evt-123",
                PAYLOAD_HASH,
                FIXED_TIME,
                null,
                WebhookProcessingStatus.PROCESSED,
                null,
                "{\"data\":\"original\"}"
        );
        webhookEventRepository.addExisting(existingWebhook);

        paymentProvider.setCanonicalEvent(createSucceededEvent("ref-123", "invoice-1"));

        var cmd = new ProcessWebhookCommand("cmd-1", "FAKE", "{\"data\":\"test\"}", Map.of());
        handler.handle(cmd);

        // Should only publish WebhookDuplicated
        assertEquals(1, eventPublisher.publishedEvents.size());
        assertInstanceOf(WebhookDuplicated.class, eventPublisher.publishedEvents.get(0));

        var duplicated = (WebhookDuplicated) eventPublisher.publishedEvents.get(0);
        assertEquals("existing-webhook-1", duplicated.originalWebhookId());
    }

    @Test
    void handle_duplicateWebhook_savesWebhookWithDuplicateStatus() {
        var existingWebhook = new WebhookEvent(
                WebhookEventId.of("existing-webhook-1"),
                ProviderName.FAKE,
                "evt-123",
                PAYLOAD_HASH,
                FIXED_TIME,
                null,
                WebhookProcessingStatus.PROCESSED,
                null,
                "{\"data\":\"original\"}"
        );
        webhookEventRepository.addExisting(existingWebhook);
        paymentProvider.setCanonicalEvent(createSucceededEvent("ref-123", "invoice-1"));

        var cmd = new ProcessWebhookCommand("cmd-1", "FAKE", "{\"data\":\"test\"}", Map.of());
        handler.handle(cmd);

        var saved = webhookEventRepository.lastSaved();
        assertEquals(WebhookProcessingStatus.DUPLICATE, saved.getProcessingStatus());
    }

    @Test
    void handle_verificationFailure_throwsException() {
        paymentProvider.setVerificationException(new WebhookVerificationException("Invalid signature"));

        var cmd = new ProcessWebhookCommand("cmd-1", "FAKE", "{\"data\":\"test\"}", Map.of());

        assertThrows(WebhookVerificationException.class, () -> handler.handle(cmd));
        assertTrue(eventPublisher.publishedEvents.isEmpty());
    }

    @Test
    void handle_processingFailure_publishesWebhookFailedEvent() {
        // No attempt exists, so processing will fail
        paymentProvider.setCanonicalEvent(createSucceededEvent("ref-123", "invoice-1"));

        var cmd = new ProcessWebhookCommand("cmd-1", "FAKE", "{\"data\":\"test\"}", Map.of());

        assertThrows(RuntimeException.class, () -> handler.handle(cmd));

        var failedEvents = eventPublisher.publishedEvents.stream()
                .filter(e -> e instanceof WebhookFailed)
                .map(e -> (WebhookFailed) e)
                .toList();
        assertEquals(1, failedEvents.size());
        assertNotNull(failedEvents.get(0).error());
    }

    @Test
    void handle_processingFailure_marksWebhookAsFailed() {
        paymentProvider.setCanonicalEvent(createSucceededEvent("ref-123", "invoice-1"));

        var cmd = new ProcessWebhookCommand("cmd-1", "FAKE", "{\"data\":\"test\"}", Map.of());

        assertThrows(RuntimeException.class, () -> handler.handle(cmd));

        // The last save should be the failed webhook
        var saved = webhookEventRepository.lastSaved();
        assertEquals(WebhookProcessingStatus.FAILED, saved.getProcessingStatus());
        assertNotNull(saved.getError());
    }

    @Test
    void handle_allEventsStoredAndPublished() {
        var attempt = createPendingAttempt("attempt-1", "invoice-1", "ref-123");
        var invoice = createPendingInvoice("invoice-1");
        paymentAttemptRepository.addAttempt(attempt);
        invoiceRepository.addInvoice(invoice);
        paymentProvider.setCanonicalEvent(createSucceededEvent("ref-123", "invoice-1"));

        var cmd = new ProcessWebhookCommand("cmd-1", "FAKE", "{\"data\":\"test\"}", Map.of());
        handler.handle(cmd);

        // Should have: WebhookReceived, PaymentAttemptSucceeded, InvoicePaymentSucceeded, WebhookProcessed
        assertEquals(4, eventStore.storedEvents.size());
        assertEquals(4, eventPublisher.publishedEvents.size());

        // Verify stored and published events match
        for (int i = 0; i < eventStore.storedEvents.size(); i++) {
            assertSame(eventStore.storedEvents.get(i).event, eventPublisher.publishedEvents.get(i));
        }
    }

    @Test
    void handle_eventsHaveCorrectMetadata() {
        var attempt = createPendingAttempt("attempt-1", "invoice-1", "ref-123");
        var invoice = createPendingInvoice("invoice-1");
        paymentAttemptRepository.addAttempt(attempt);
        invoiceRepository.addInvoice(invoice);
        paymentProvider.setCanonicalEvent(createSucceededEvent("ref-123", "invoice-1"));

        var cmd = new ProcessWebhookCommand("cmd-1", "FAKE", "{\"data\":\"test\"}", Map.of());
        handler.handle(cmd);

        for (DomainEvent event : eventPublisher.publishedEvents) {
            assertNotNull(event.eventId());
            assertFalse(event.eventId().isEmpty());
            assertEquals(FIXED_TIME, event.occurredAt());
            assertNotNull(event.aggregateId());
            assertNotNull(event.eventType());
        }
    }

    @Test
    void handle_storesEventsWithCorrectAggregateTypes() {
        var attempt = createPendingAttempt("attempt-1", "invoice-1", "ref-123");
        var invoice = createPendingInvoice("invoice-1");
        paymentAttemptRepository.addAttempt(attempt);
        invoiceRepository.addInvoice(invoice);
        paymentProvider.setCanonicalEvent(createSucceededEvent("ref-123", "invoice-1"));

        var cmd = new ProcessWebhookCommand("cmd-1", "FAKE", "{\"data\":\"test\"}", Map.of());
        handler.handle(cmd);

        var aggregateTypes = eventStore.storedEvents.stream()
                .map(e -> e.aggregateType)
                .toList();
        assertTrue(aggregateTypes.contains("Webhook"));
        assertTrue(aggregateTypes.contains("PaymentAttempt"));
        assertTrue(aggregateTypes.contains("Invoice"));
    }

    @Test
    void handle_pendingStatus_noPaymentAttemptOrInvoiceEventsPublished() {
        var attempt = createPendingAttempt("attempt-1", "invoice-1", "ref-123");
        var invoice = createPendingInvoice("invoice-1");
        paymentAttemptRepository.addAttempt(attempt);
        invoiceRepository.addInvoice(invoice);
        paymentProvider.setCanonicalEvent(createPendingEvent("ref-123", "invoice-1"));

        var cmd = new ProcessWebhookCommand("cmd-1", "FAKE", "{\"data\":\"test\"}", Map.of());
        handler.handle(cmd);

        // Should have: WebhookReceived, WebhookProcessed — no payment attempt or invoice events
        var paymentEvents = eventPublisher.publishedEvents.stream()
                .filter(e -> e instanceof PaymentAttemptSucceeded || e instanceof PaymentAttemptFailed
                        || e instanceof InvoicePaymentSucceeded || e instanceof InvoicePaymentFailed)
                .toList();
        assertTrue(paymentEvents.isEmpty());
    }

    @Test
    void handle_webhookReceivedEventHasCorrectPayloadInfo() {
        var attempt = createPendingAttempt("attempt-1", "invoice-1", "ref-123");
        var invoice = createPendingInvoice("invoice-1");
        paymentAttemptRepository.addAttempt(attempt);
        invoiceRepository.addInvoice(invoice);
        paymentProvider.setCanonicalEvent(createSucceededEvent("ref-123", "invoice-1"));

        var cmd = new ProcessWebhookCommand("cmd-1", "FAKE", "{\"data\":\"test\"}", Map.of());
        handler.handle(cmd);

        var received = (WebhookReceived) eventPublisher.publishedEvents.stream()
                .filter(e -> e instanceof WebhookReceived)
                .findFirst()
                .orElseThrow();

        assertEquals("FAKE", received.provider());
        assertEquals(PAYLOAD_HASH, received.payloadHash());
        assertEquals("{\"data\":\"test\"}", received.rawPayload());
    }

    // --- Helper methods ---

    private PaymentAttempt createPendingAttempt(String attemptId, String invoiceId, String providerRef) {
        return new PaymentAttempt(
                new PaymentAttemptId(attemptId),
                new InvoiceId(invoiceId),
                ProviderName.FAKE,
                providerRef,
                PaymentAttemptStatus.PENDING,
                FIXED_TIME,
                FIXED_TIME
        );
    }

    private Invoice createPendingInvoice(String invoiceId) {
        return new Invoice(
                new InvoiceId(invoiceId),
                new MerchantId("merchant-1"),
                new Money(new BigDecimal("100.00"), com.payme.domain.Currency.ZAR),
                "Test invoice",
                InvoiceStatus.PENDING,
                FIXED_TIME.plusSeconds(86400),
                FIXED_TIME,
                FIXED_TIME
        );
    }

    private CanonicalPaymentEvent createSucceededEvent(String attemptRef, String invoiceId) {
        return new CanonicalPaymentEvent(
                ProviderName.FAKE, "evt-123", attemptRef, new InvoiceId(invoiceId),
                PaymentEventStatus.SUCCEEDED, FIXED_TIME, "payment.succeeded"
        );
    }

    private CanonicalPaymentEvent createFailedEvent(String attemptRef, String invoiceId) {
        return new CanonicalPaymentEvent(
                ProviderName.FAKE, "evt-123", attemptRef, new InvoiceId(invoiceId),
                PaymentEventStatus.FAILED, FIXED_TIME, "payment.failed"
        );
    }

    private CanonicalPaymentEvent createPendingEvent(String attemptRef, String invoiceId) {
        return new CanonicalPaymentEvent(
                ProviderName.FAKE, "evt-123", attemptRef, new InvoiceId(invoiceId),
                PaymentEventStatus.PENDING, FIXED_TIME, "payment.pending"
        );
    }

    // --- Test doubles ---

    private static class StubPaymentProvider implements PaymentProvider {
        private CanonicalPaymentEvent canonicalEvent;
        private WebhookVerificationException verificationException;

        void setCanonicalEvent(CanonicalPaymentEvent event) {
            this.canonicalEvent = event;
        }

        void setVerificationException(WebhookVerificationException e) {
            this.verificationException = e;
        }

        @Override
        public CheckoutSession createCheckoutSession(Invoice invoice, PaymentAttemptId attemptId, CheckoutUrls urls) {
            throw new UnsupportedOperationException("Not used in webhook tests");
        }

        @Override
        public CanonicalPaymentEvent verifyAndParseWebhook(String rawBody, Map<String, String> headers) {
            if (verificationException != null) {
                throw verificationException;
            }
            return canonicalEvent;
        }
    }

    private static class StubWebhookEventRepository implements WebhookEventRepository {
        private final List<WebhookEvent> savedEvents = new ArrayList<>();
        private final List<WebhookEvent> existingEvents = new ArrayList<>();

        void addExisting(WebhookEvent event) {
            existingEvents.add(event);
        }

        WebhookEvent lastSaved() {
            return savedEvents.get(savedEvents.size() - 1);
        }

        @Override
        public WebhookEvent save(WebhookEvent event) {
            savedEvents.add(event);
            return event;
        }

        @Override
        public Optional<WebhookEvent> findByProviderEventId(ProviderName provider, String eventId) {
            return existingEvents.stream()
                    .filter(e -> e.getProvider() == provider && eventId.equals(e.getProviderEventId()))
                    .findFirst();
        }

        @Override
        public Optional<WebhookEvent> findByPayloadHash(String hash) {
            return existingEvents.stream()
                    .filter(e -> hash.equals(e.getPayloadHash()))
                    .findFirst();
        }

        @Override
        public boolean existsByProviderEventId(ProviderName provider, String eventId) {
            return findByProviderEventId(provider, eventId).isPresent();
        }

        @Override
        public boolean existsByPayloadHash(String hash) {
            return findByPayloadHash(hash).isPresent();
        }
    }

    private static class StubPaymentAttemptRepository implements PaymentAttemptRepository {
        private final List<PaymentAttempt> attempts = new ArrayList<>();
        private final List<PaymentAttempt> savedAttempts = new ArrayList<>();

        void addAttempt(PaymentAttempt attempt) {
            attempts.add(attempt);
        }

        PaymentAttempt lastSaved() {
            return savedAttempts.get(savedAttempts.size() - 1);
        }

        @Override
        public PaymentAttempt save(PaymentAttempt attempt) {
            savedAttempts.add(attempt);
            return attempt;
        }

        @Override
        public Optional<PaymentAttempt> findById(PaymentAttemptId attemptId) {
            return attempts.stream()
                    .filter(a -> a.getAttemptId().equals(attemptId))
                    .findFirst();
        }

        @Override
        public List<PaymentAttempt> findByInvoiceId(InvoiceId invoiceId) {
            return attempts.stream()
                    .filter(a -> a.getInvoiceId().equals(invoiceId))
                    .toList();
        }

        @Override
        public Optional<PaymentAttempt> findByProviderReference(String providerReference) {
            return attempts.stream()
                    .filter(a -> providerReference.equals(a.getProviderReference()))
                    .findFirst();
        }
    }

    private static class StubInvoiceRepository implements InvoiceRepository {
        private final List<Invoice> invoices = new ArrayList<>();
        private final List<Invoice> savedInvoices = new ArrayList<>();

        void addInvoice(Invoice invoice) {
            invoices.add(invoice);
        }

        Invoice lastSaved() {
            return savedInvoices.get(savedInvoices.size() - 1);
        }

        @Override
        public Invoice save(Invoice invoice) {
            savedInvoices.add(invoice);
            return invoice;
        }

        @Override
        public Optional<Invoice> findById(InvoiceId invoiceId) {
            return invoices.stream()
                    .filter(i -> i.getInvoiceId().equals(invoiceId))
                    .findFirst();
        }

        @Override
        public boolean existsById(InvoiceId invoiceId) {
            return findById(invoiceId).isPresent();
        }
    }

    private static class StubHashService implements HashService {
        private final String fixedHash;

        StubHashService(String fixedHash) {
            this.fixedHash = fixedHash;
        }

        @Override
        public String sha256(String input) {
            return fixedHash;
        }
    }

    private static class StubClock implements Clock {
        private final Instant fixedTime;

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
