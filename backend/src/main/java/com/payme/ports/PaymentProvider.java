package com.payme.ports;

import com.payme.domain.CanonicalPaymentEvent;
import com.payme.domain.Invoice;
import com.payme.domain.PaymentAttemptId;
import com.payme.domain.exceptions.WebhookVerificationException;

import java.util.Map;

public interface PaymentProvider {
    /**
     * Creates a checkout session with the payment provider.
     *
     * @param invoice   The invoice to be paid
     * @param attemptId The payment attempt ID for correlation
     * @param urls      Success and cancel redirect URLs
     * @return CheckoutSession containing the checkout URL and provider reference
     */
    CheckoutSession createCheckoutSession(Invoice invoice, PaymentAttemptId attemptId, CheckoutUrls urls);

    /**
     * Verifies the webhook signature and parses the event into a canonical format.
     *
     * @param rawBody The raw webhook body
     * @param headers The HTTP headers
     * @return Canonical payment event
     * @throws WebhookVerificationException if signature verification fails
     */
    CanonicalPaymentEvent verifyAndParseWebhook(String rawBody, Map<String, String> headers) throws WebhookVerificationException;
}
