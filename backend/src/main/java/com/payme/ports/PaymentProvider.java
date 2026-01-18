package com.payme.ports;

import com.payme.domain.Invoice;
import com.payme.domain.PaymentAttemptId;

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
}
