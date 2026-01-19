package com.payme.application;

import com.payme.domain.*;
import com.payme.domain.exceptions.InvoiceNotFoundException;
import com.payme.domain.exceptions.WebhookVerificationException;
import com.payme.ports.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Optional;

@Service
public class ProcessWebhookUseCase {

    private static final Logger log = LoggerFactory.getLogger(ProcessWebhookUseCase.class);

    private final PaymentProvider paymentProvider;
    private final WebhookEventRepository webhookEventRepository;
    private final PaymentAttemptRepository paymentAttemptRepository;
    private final InvoiceRepository invoiceRepository;
    private final HashService hashService;
    private final Clock clock;

    public ProcessWebhookUseCase(
            PaymentProvider paymentProvider,
            WebhookEventRepository webhookEventRepository,
            PaymentAttemptRepository paymentAttemptRepository,
            InvoiceRepository invoiceRepository,
            HashService hashService,
            Clock clock
    ) {
        this.paymentProvider = paymentProvider;
        this.webhookEventRepository = webhookEventRepository;
        this.paymentAttemptRepository = paymentAttemptRepository;
        this.invoiceRepository = invoiceRepository;
        this.hashService = hashService;
        this.clock = clock;
    }

    @Transactional
    public void processWebhook(ProviderName provider, String rawBody, Map<String, String> headers) {
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
            // Store as duplicate for audit trail
            WebhookEvent duplicateEvent = new WebhookEvent(
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
            webhookEventRepository.save(duplicateEvent);
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
        log.info("Webhook event stored with id: {}", webhookEvent.getId().getValue());

        // Step 5: Process the event and update state
        try {
            processPaymentEvent(event);

            // Mark webhook as processed
            webhookEvent.markAsProcessed(clock.now());
            webhookEventRepository.save(webhookEvent);
            log.info("Webhook processing completed successfully");
        } catch (Exception e) {
            log.error("Failed to process webhook", e);
            // Mark webhook as failed
            webhookEvent.markAsFailed(clock.now(), e.getMessage());
            webhookEventRepository.save(webhookEvent);
            throw new RuntimeException("Webhook processing failed: " + e.getMessage(), e);
        }
    }

    private boolean isDuplicate(ProviderName provider, String eventId, String payloadHash) {
        // Check by provider event ID first (if available)
        if (eventId != null && !eventId.isEmpty()) {
            if (webhookEventRepository.existsByProviderEventId(provider, eventId)) {
                log.debug("Duplicate detected by provider event ID: {}", eventId);
                return true;
            }
        }

        // Fallback to payload hash
        if (webhookEventRepository.existsByPayloadHash(payloadHash)) {
            log.debug("Duplicate detected by payload hash: {}", payloadHash);
            return true;
        }

        return false;
    }

    private void processPaymentEvent(CanonicalPaymentEvent event) {
        log.info("Processing payment event: status={}, attemptRef={}", event.getStatus(), event.getAttemptReference());

        // Find the payment attempt by provider reference
        PaymentAttempt attempt = findPaymentAttempt(event);

        // Update attempt status based on event
        updatePaymentAttemptStatus(attempt, event.getStatus());

        // Save updated attempt
        attempt = paymentAttemptRepository.save(attempt);
        log.info("Payment attempt updated: attemptId={}, status={}", 
                attempt.getAttemptId().getValue(), attempt.getStatus());

        // Update invoice status based on attempt outcome
        InvoiceId invoiceId = attempt.getInvoiceId();
        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new InvoiceNotFoundException("Invoice not found: " + invoiceId.getValue()));

        updateInvoiceStatus(invoice, attempt.getStatus());

        // Save updated invoice
        invoiceRepository.save(invoice);
        log.info("Invoice updated: invoiceId={}, status={}", 
                invoice.getInvoiceId().getValue(), invoice.getStatus());
    }

    private PaymentAttempt findPaymentAttempt(CanonicalPaymentEvent event) {
        // Try to find by attempt reference first
        if (event.getAttemptReference() != null && !event.getAttemptReference().isEmpty()) {
            Optional<PaymentAttempt> attemptOpt = paymentAttemptRepository.findByProviderReference(event.getAttemptReference());
            if (attemptOpt.isPresent()) {
                return attemptOpt.get();
            }
        }

        // If not found and we have an invoice ID, try to find the most recent attempt for that invoice
        if (event.getInvoiceId() != null) {
            var attempts = paymentAttemptRepository.findByInvoiceId(event.getInvoiceId());
            if (!attempts.isEmpty()) {
                // Return the most recent attempt (assuming last in list)
                return attempts.get(attempts.size() - 1);
            }
        }

        throw new RuntimeException("Payment attempt not found for reference: " + event.getAttemptReference());
    }

    private void updatePaymentAttemptStatus(PaymentAttempt attempt, PaymentEventStatus eventStatus) {
        switch (eventStatus) {
            case SUCCEEDED -> attempt.markAsSucceeded(clock.now());
            case FAILED -> attempt.markAsFailed(clock.now());
            case PENDING -> {
                // Already PENDING, no action needed
                log.debug("Payment attempt already in PENDING state");
            }
        }
    }

    private void updateInvoiceStatus(Invoice invoice, PaymentAttemptStatus attemptStatus) {
        switch (attemptStatus) {
            case SUCCEEDED -> invoice.markAsSucceeded(clock.now());
            case FAILED -> invoice.markAsFailed(clock.now());
            case PENDING -> {
                // Invoice should already be PENDING from checkout start
                log.debug("Invoice already in PENDING state");
            }
        }
    }
}
