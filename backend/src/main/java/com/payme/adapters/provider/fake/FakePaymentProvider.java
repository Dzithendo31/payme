package com.payme.adapters.provider.fake;

import com.payme.domain.Invoice;
import com.payme.domain.PaymentAttemptId;
import com.payme.ports.CheckoutSession;
import com.payme.ports.CheckoutUrls;
import com.payme.ports.PaymentProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Fake payment provider for testing without external gateway integration.
 * Returns predictable checkout URLs that can be used for end-to-end testing.
 */
public class FakePaymentProvider implements PaymentProvider {

    private static final Logger log = LoggerFactory.getLogger(FakePaymentProvider.class);
    private static final String FAKE_GATEWAY_BASE_URL = "http://fake-gateway.local";

    @Override
    public CheckoutSession createCheckoutSession(Invoice invoice, PaymentAttemptId attemptId, CheckoutUrls urls) {
        log.info("FakeProvider: Creating checkout session for invoice {} and attempt {}",
                invoice.getInvoiceId().getValue(), attemptId.getValue());

        // In a real provider, this would make an API call to create a checkout session
        // For fake provider, we just generate a predictable URL
        String checkoutUrl = String.format("%s/checkout/%s", FAKE_GATEWAY_BASE_URL, attemptId.getValue());
        String providerReference = "fake_ref_" + attemptId.getValue();

        log.info("FakeProvider: Generated checkout URL: {}", checkoutUrl);
        log.info("FakeProvider: Provider reference: {}", providerReference);

        return new CheckoutSession(checkoutUrl, providerReference);
    }
}
