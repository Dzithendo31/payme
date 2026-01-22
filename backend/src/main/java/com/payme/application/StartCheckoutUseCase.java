package com.payme.application;

import com.payme.domain.*;
import com.payme.domain.exceptions.InvalidInvoiceStateException;
import com.payme.domain.exceptions.InvoiceNotFoundException;
import com.payme.ports.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class StartCheckoutUseCase {

    private static final Logger log = LoggerFactory.getLogger(StartCheckoutUseCase.class);

    private final InvoiceRepository invoiceRepository;
    private final PaymentAttemptRepository paymentAttemptRepository;
    private final PaymentProvider paymentProvider;
    private final Clock clock;
    private final CheckoutUrls checkoutUrls;

    public StartCheckoutUseCase(
            InvoiceRepository invoiceRepository,
            PaymentAttemptRepository paymentAttemptRepository,
            PaymentProvider paymentProvider,
            Clock clock,
            CheckoutUrls checkoutUrls
    ) {
        this.invoiceRepository = invoiceRepository;
        this.paymentAttemptRepository = paymentAttemptRepository;
        this.paymentProvider = paymentProvider;
        this.clock = clock;
        this.checkoutUrls = checkoutUrls;
    }

    @Transactional
    public CheckoutResult execute(InvoiceId invoiceId) {
        Instant now = clock.now();

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
                ProviderName.FAKE, // For now, hardcoded to FAKE
                session.getProviderReference(),
                PaymentAttemptStatus.PENDING,
                now,
                now
        );
        paymentAttemptRepository.save(attempt);
        log.info("Payment attempt saved: {}", attemptId.getValue());

        // 6. Mark invoice as PENDING
        invoice.markAsPending(now);
        invoiceRepository.save(invoice);
        log.info("Invoice marked as PENDING: {}", invoiceId.getValue());

        // 7. Return checkout URL and form parameters
        return new CheckoutResult(
                session.getCheckoutUrl(),
                attemptId.getValue(),
                session.getFormParameters()
        );
    }

    public static class CheckoutResult {
        private final String checkoutUrl;
        private final String attemptId;
        private final java.util.Map<String, String> formParameters;

        public CheckoutResult(String checkoutUrl, String attemptId, java.util.Map<String, String> formParameters) {
            this.checkoutUrl = checkoutUrl;
            this.attemptId = attemptId;
            this.formParameters = formParameters;
        }

        public String getCheckoutUrl() {
            return checkoutUrl;
        }

        public String getAttemptId() {
            return attemptId;
        }

        public java.util.Map<String, String> getFormParameters() {
            return formParameters;
        }
    }
}
