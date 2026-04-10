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
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * @dec(ARCH-001) Resolves the payment provider via the registry — see decisions/ARCH-001.md
 *
 * Previously this handler held a single {@link PaymentProvider} instance and a
 * {@code @Value} injected provider name. Both are gone: the handler now asks
 * the {@link PaymentProviderRegistry} for the rail named on the command, and
 * falls back to the registry's default when the command leaves it null.
 */
@Component
public class StartCheckoutCommandHandler {

    private static final Logger log = LoggerFactory.getLogger(StartCheckoutCommandHandler.class);

    private final InvoiceRepository invoiceRepository;
    private final PaymentAttemptRepository paymentAttemptRepository;
    private final PaymentProviderRegistry providerRegistry;
    private final Clock clock;
    private final CheckoutUrls checkoutUrls;
    private final EventStore eventStore;
    private final EventPublisher eventPublisher;

    public StartCheckoutCommandHandler(
            InvoiceRepository invoiceRepository,
            PaymentAttemptRepository paymentAttemptRepository,
            PaymentProviderRegistry providerRegistry,
            Clock clock,
            CheckoutUrls checkoutUrls,
            EventStore eventStore,
            EventPublisher eventPublisher
    ) {
        this.invoiceRepository = invoiceRepository;
        this.paymentAttemptRepository = paymentAttemptRepository;
        this.providerRegistry = providerRegistry;
        this.clock = clock;
        this.checkoutUrls = checkoutUrls;
        this.eventStore = eventStore;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public CheckoutResult handle(StartCheckoutCommand cmd) {
        Instant now = clock.now();
        InvoiceId invoiceId = new InvoiceId(cmd.invoiceId());

        // @dec(ARCH-001) null provider on the command means "use the env default"
        ProviderName chosenProvider = cmd.provider() != null
                ? cmd.provider()
                : providerRegistry.defaultProvider();
        PaymentProvider paymentProvider = providerRegistry.get(chosenProvider);

        log.info("Starting checkout for invoice {} via provider {}",
                invoiceId.getValue(), chosenProvider);

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
                chosenProvider,
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
