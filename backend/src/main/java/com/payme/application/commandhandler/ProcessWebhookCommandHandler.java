package com.payme.application.commandhandler;

import com.payme.domain.*;
import com.payme.domain.command.ProcessWebhookCommand;
import com.payme.domain.event.*;
import com.payme.domain.exceptions.InvoiceNotFoundException;
import com.payme.domain.exceptions.WebhookVerificationException;
import com.payme.ports.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Component
public class ProcessWebhookCommandHandler {

    private static final Logger log = LoggerFactory.getLogger(ProcessWebhookCommandHandler.class);

    private final PaymentProvider paymentProvider;
    private final WebhookEventRepository webhookEventRepository;
    private final PaymentAttemptRepository paymentAttemptRepository;
    private final InvoiceRepository invoiceRepository;
    private final HashService hashService;
    private final Clock clock;
    private final EventStore eventStore;
    private final EventPublisher eventPublisher;

    public ProcessWebhookCommandHandler(
            PaymentProvider paymentProvider,
            WebhookEventRepository webhookEventRepository,
            PaymentAttemptRepository paymentAttemptRepository,
            InvoiceRepository invoiceRepository,
            HashService hashService,
            Clock clock,
            EventStore eventStore,
            EventPublisher eventPublisher
    ) {
        this.paymentProvider = paymentProvider;
        this.webhookEventRepository = webhookEventRepository;
        this.paymentAttemptRepository = paymentAttemptRepository;
        this.invoiceRepository = invoiceRepository;
        this.hashService = hashService;
        this.clock = clock;
        this.eventStore = eventStore;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public void handle(ProcessWebhookCommand cmd) {
        ProviderName provider = ProviderName.valueOf(cmd.provider());
        String rawBody = cmd.rawBody();
        Map<String, String> headers = cmd.headers();

        log.info("Processing webhook for provider: {}", provider);

        // Step 1: Compute payload hash for deduplication
        String payloadHash = hashService.sha256(rawBody);
        log.debug("Computed payload hash: {}", payloadHash);

        // Step 2: Verify and parse webhook
        CanonicalPaymentEvent event;
        try {
            event = paymentProvider.verifyAndParseWebhook(rawBody, headers);
            log.info("Webhook verified and parsed: eventId={}, status={}", event.getEventId(), event.getStatus());
        } catch (WebhookVerificationException e) {
            log.error("Webhook verification failed", e);
            throw e;
        }

        // Step 3: Check for duplicates
        if (isDuplicate(provider, event.getEventId(), payloadHash)) {
            log.warn("Duplicate webhook detected - eventId={}, hash={}", event.getEventId(), payloadHash);

            WebhookEvent duplicateWebhook = new WebhookEvent(
                    WebhookEventId.generate(),
                    provider,
                    event.getEventId(),
                    payloadHash,
                    clock.now(),
                    null,
                    WebhookProcessingStatus.DUPLICATE,
                    null,
                    rawBody
            );
            webhookEventRepository.save(duplicateWebhook);

            // Find the original webhook for the domain event
            String originalWebhookId = findOriginalWebhookId(provider, event.getEventId(), payloadHash);

            var duplicatedEvent = new WebhookDuplicated(
                    UUID.randomUUID().toString(),
                    clock.now(),
                    duplicateWebhook.getId().getValue(),
                    originalWebhookId
            );
            eventStore.store(duplicatedEvent, "Webhook");
            eventPublisher.publish(duplicatedEvent);
            return;
        }

        // Step 4: Create and save webhook event with RECEIVED status
        WebhookEvent webhookEvent = new WebhookEvent(
                WebhookEventId.generate(),
                provider,
                event.getEventId(),
                payloadHash,
                clock.now(),
                null,
                WebhookProcessingStatus.RECEIVED,
                null,
                rawBody
        );
        webhookEvent = webhookEventRepository.save(webhookEvent);
        String webhookId = webhookEvent.getId().getValue();
        log.info("Webhook event stored with id: {}", webhookId);

        // Publish WebhookReceived event
        var receivedEvent = new WebhookReceived(
                UUID.randomUUID().toString(),
                clock.now(),
                webhookId,
                provider.name(),
                payloadHash,
                rawBody
        );
        eventStore.store(receivedEvent, "Webhook");
        eventPublisher.publish(receivedEvent);

        // Step 5: Process the event and update state
        try {
            processPaymentEvent(event, webhookId, provider);

            // Mark webhook as processed
            webhookEvent.markAsProcessed(clock.now());
            webhookEventRepository.save(webhookEvent);
            log.info("Webhook processing completed successfully");

            // Publish WebhookProcessed event
            PaymentAttempt attempt = findPaymentAttempt(event);
            var processedEvent = new WebhookProcessed(
                    UUID.randomUUID().toString(),
                    clock.now(),
                    webhookId,
                    attempt.getAttemptId().getValue()
            );
            eventStore.store(processedEvent, "Webhook");
            eventPublisher.publish(processedEvent);

        } catch (Exception e) {
            log.error("Failed to process webhook", e);

            // Mark webhook as failed
            webhookEvent.markAsFailed(clock.now(), e.getMessage());
            webhookEventRepository.save(webhookEvent);

            // Publish WebhookFailed event
            var failedEvent = new WebhookFailed(
                    UUID.randomUUID().toString(),
                    clock.now(),
                    webhookId,
                    e.getMessage()
            );
            eventStore.store(failedEvent, "Webhook");
            eventPublisher.publish(failedEvent);

            throw new RuntimeException("Webhook processing failed: " + e.getMessage(), e);
        }
    }

    private boolean isDuplicate(ProviderName provider, String eventId, String payloadHash) {
        if (eventId != null && !eventId.isEmpty()) {
            if (webhookEventRepository.existsByProviderEventId(provider, eventId)) {
                log.debug("Duplicate detected by provider event ID: {}", eventId);
                return true;
            }
        }

        if (webhookEventRepository.existsByPayloadHash(payloadHash)) {
            log.debug("Duplicate detected by payload hash: {}", payloadHash);
            return true;
        }

        return false;
    }

    private String findOriginalWebhookId(ProviderName provider, String eventId, String payloadHash) {
        if (eventId != null && !eventId.isEmpty()) {
            Optional<WebhookEvent> original = webhookEventRepository.findByProviderEventId(provider, eventId);
            if (original.isPresent()) {
                return original.get().getId().getValue();
            }
        }
        Optional<WebhookEvent> original = webhookEventRepository.findByPayloadHash(payloadHash);
        return original.map(w -> w.getId().getValue()).orElse("unknown");
    }

    private void processPaymentEvent(CanonicalPaymentEvent event, String webhookId, ProviderName provider) {
        log.info("Processing payment event: status={}, attemptRef={}", event.getStatus(), event.getAttemptReference());

        PaymentAttempt attempt = findPaymentAttempt(event);

        updatePaymentAttemptStatus(attempt, event.getStatus());
        attempt = paymentAttemptRepository.save(attempt);
        log.info("Payment attempt updated: attemptId={}, status={}",
                attempt.getAttemptId().getValue(), attempt.getStatus());

        // Publish payment attempt events
        publishPaymentAttemptEvent(attempt, event, provider);

        // Update invoice status
        InvoiceId invoiceId = attempt.getInvoiceId();
        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new InvoiceNotFoundException("Invoice not found: " + invoiceId.getValue()));

        updateInvoiceStatus(invoice, attempt.getStatus());
        invoiceRepository.save(invoice);
        log.info("Invoice updated: invoiceId={}, status={}",
                invoice.getInvoiceId().getValue(), invoice.getStatus());

        // Publish invoice payment events
        publishInvoicePaymentEvent(invoice, attempt, provider, event);
    }

    private PaymentAttempt findPaymentAttempt(CanonicalPaymentEvent event) {
        if (event.getAttemptReference() != null && !event.getAttemptReference().isEmpty()) {
            Optional<PaymentAttempt> attemptOpt = paymentAttemptRepository.findByProviderReference(event.getAttemptReference());
            if (attemptOpt.isPresent()) {
                return attemptOpt.get();
            }
        }

        if (event.getInvoiceId() != null) {
            var attempts = paymentAttemptRepository.findByInvoiceId(event.getInvoiceId());
            if (!attempts.isEmpty()) {
                return attempts.get(attempts.size() - 1);
            }
        }

        throw new RuntimeException("Payment attempt not found for reference: " + event.getAttemptReference());
    }

    private void updatePaymentAttemptStatus(PaymentAttempt attempt, PaymentEventStatus eventStatus) {
        switch (eventStatus) {
            case SUCCEEDED -> attempt.markAsSucceeded(clock.now());
            case FAILED -> attempt.markAsFailed(clock.now());
            case PENDING -> log.debug("Payment attempt already in PENDING state");
        }
    }

    private void updateInvoiceStatus(Invoice invoice, PaymentAttemptStatus attemptStatus) {
        switch (attemptStatus) {
            case SUCCEEDED -> invoice.markAsSucceeded(clock.now());
            case FAILED -> invoice.markAsFailed(clock.now());
            case PENDING -> log.debug("Invoice already in PENDING state");
        }
    }

    private void publishPaymentAttemptEvent(PaymentAttempt attempt, CanonicalPaymentEvent event, ProviderName provider) {
        switch (attempt.getStatus()) {
            case SUCCEEDED -> {
                var attemptEvent = new PaymentAttemptSucceeded(
                        UUID.randomUUID().toString(),
                        clock.now(),
                        attempt.getAttemptId().getValue(),
                        event.getEventId()
                );
                eventStore.store(attemptEvent, "PaymentAttempt");
                eventPublisher.publish(attemptEvent);
            }
            case FAILED -> {
                var attemptEvent = new PaymentAttemptFailed(
                        UUID.randomUUID().toString(),
                        clock.now(),
                        attempt.getAttemptId().getValue(),
                        event.getEventId(),
                        "Payment failed via " + provider.name()
                );
                eventStore.store(attemptEvent, "PaymentAttempt");
                eventPublisher.publish(attemptEvent);
            }
            case PENDING -> { /* No event for PENDING status updates */ }
        }
    }

    private void publishInvoicePaymentEvent(Invoice invoice, PaymentAttempt attempt, ProviderName provider, CanonicalPaymentEvent event) {
        switch (invoice.getStatus()) {
            case SUCCEEDED -> {
                var invoiceEvent = new InvoicePaymentSucceeded(
                        UUID.randomUUID().toString(),
                        clock.now(),
                        invoice.getInvoiceId().getValue(),
                        attempt.getAttemptId().getValue(),
                        provider.name()
                );
                eventStore.store(invoiceEvent, "Invoice");
                eventPublisher.publish(invoiceEvent);
            }
            case FAILED -> {
                var invoiceEvent = new InvoicePaymentFailed(
                        UUID.randomUUID().toString(),
                        clock.now(),
                        invoice.getInvoiceId().getValue(),
                        attempt.getAttemptId().getValue(),
                        provider.name(),
                        "Payment failed via " + provider.name()
                );
                eventStore.store(invoiceEvent, "Invoice");
                eventPublisher.publish(invoiceEvent);
            }
            default -> { /* No event for other statuses */ }
        }
    }
}
