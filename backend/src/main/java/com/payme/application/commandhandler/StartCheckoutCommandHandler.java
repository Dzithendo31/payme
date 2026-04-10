package com.payme.application.commandhandler;

import com.payme.domain.*;
import com.payme.domain.command.StartCheckoutCommand;
import com.payme.domain.event.InvoiceMarkedPending;
import com.payme.domain.event.PaymentAttemptCreated;
import com.payme.domain.exceptions.InvalidInvoiceStateException;
import com.payme.domain.exceptions.InvoiceNotFoundException;
import com.payme.ports.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Component
public class StartCheckoutCommandHandler {

    private static final Logger log = LoggerFactory.getLogger(StartCheckoutCommandHandler.class);

    private final InvoiceRepository invoiceRepository;
    private final PaymentAttemptRepository paymentAttemptRepository;
    private final PaymentProvider paymentProvider;
    private final Clock clock;
    private final CheckoutUrls checkoutUrls;
    private final EventStore eventStore;
    private final EventPublisher eventPublisher;
    private final ProviderName providerName;

    public StartCheckoutCommandHandler(
            InvoiceRepository invoiceRepository,
            PaymentAttemptRepository paymentAttemptRepository,
            PaymentProvider paymentProvider,
            Clock clock,
            CheckoutUrls checkoutUrls,
            EventStore eventStore,
            EventPublisher eventPublisher,
            @Value("${payme.payment.provider:FAKE}") String providerName
    ) {
        this.invoiceRepository = invoiceRepository;
        this.paymentAttemptRepository = paymentAttemptRepository;
        this.paymentProvider = paymentProvider;
        this.clock = clock;
        this.checkoutUrls = checkoutUrls;
        this.eventStore = eventStore;
        this.eventPublisher = eventPublisher;
        this.providerName = ProviderName.valueOf(providerName.toUpperCase());
    }

    @Transactional
    public CheckoutResult handle(StartCheckoutCommand cmd) {
        Instant now = clock.now();
        InvoiceId invoiceId = new InvoiceId(cmd.invoiceId());

        log.info("Starting checkout for invoice: {}", invoiceId.getValue());

        // 1. Fetch invoice
        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new InvoiceNotFoundException("Invoice not found: " + invoiceId.getValue()));

        // 2. Validate invoice is payable
        if (!invoice.isPayable(now)) {
            if (invoice.isExpired(now)) {
                throw new InvalidInvoiceStateException("Cannot start checkout for expired invoice");
            }
            if (invoice.getStatus() == InvoiceStatus.SUCCEEDED) {
                throw new InvalidInvoiceStateException("Invoice already paid");
            }
            throw new InvalidInvoiceStateException("Invoice is not in a payable state: " + invoice.getStatus());
        }

        // 3. Create new payment attempt
        PaymentAttemptId attemptId = PaymentAttemptId.generate();
        log.info("Created payment attempt: {}", attemptId.getValue());

        // 4. Call payment provider to create checkout session
        CheckoutSession session = paymentProvider.createCheckoutSession(invoice, attemptId, checkoutUrls);
        log.info("Checkout session created with URL: {}", session.getCheckoutUrl());

        // 5. Create and save payment attempt
        PaymentAttempt attempt = new PaymentAttempt(
                attemptId,
                invoiceId,
                providerName,
                session.getProviderReference(),
                PaymentAttemptStatus.PENDING,
                now,
                now
        );
        paymentAttemptRepository.save(attempt);
        log.info("Payment attempt saved: {}", attemptId.getValue());

        // 6. Publish PaymentAttemptCreated event
        var attemptCreatedEvent = new PaymentAttemptCreated(
                UUID.randomUUID().toString(),
                now,
                attemptId.getValue(),
                invoiceId.getValue(),
                attempt.getProvider().name(),
                session.getProviderReference()
        );
        eventStore.store(attemptCreatedEvent, "PaymentAttempt");
        eventPublisher.publish(attemptCreatedEvent);

        // 7. Mark invoice as PENDING
        invoice.markAsPending(now);
        invoiceRepository.save(invoice);
        log.info("Invoice marked as PENDING: {}", invoiceId.getValue());

        // 8. Publish InvoiceMarkedPending event
        var invoicePendingEvent = new InvoiceMarkedPending(
                UUID.randomUUID().toString(),
                now,
                invoiceId.getValue(),
                attemptId.getValue()
        );
        eventStore.store(invoicePendingEvent, "Invoice");
        eventPublisher.publish(invoicePendingEvent);

        // 9. Return checkout URL
        return new CheckoutResult(session.getCheckoutUrl(), attemptId.getValue(), session.getFormParams());
    }

    public static class CheckoutResult {
        private final String checkoutUrl;
        private final String attemptId;
        private final java.util.Map<String, String> formParams;

        public CheckoutResult(String checkoutUrl, String attemptId, java.util.Map<String, String> formParams) {
            this.checkoutUrl = checkoutUrl;
            this.attemptId = attemptId;
            this.formParams = formParams == null ? java.util.Collections.emptyMap() : formParams;
        }

        public String getCheckoutUrl() {
            return checkoutUrl;
        }

        public String getAttemptId() {
            return attemptId;
        }

        public java.util.Map<String, String> getFormParams() {
            return formParams;
        }
    }
}
