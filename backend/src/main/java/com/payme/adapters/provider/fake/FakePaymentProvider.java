package com.payme.adapters.provider.fake;

import com.payme.domain.*;
import com.payme.domain.exceptions.WebhookVerificationException;
import com.payme.ports.CheckoutSession;
import com.payme.ports.CheckoutUrls;
import com.payme.ports.PaymentProvider;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Map;

/**
 * Fake payment provider for testing without external gateway integration.
 * Returns predictable checkout URLs that can be used for end-to-end testing.
 */
public class FakePaymentProvider implements PaymentProvider {

    private static final Logger log = LoggerFactory.getLogger(FakePaymentProvider.class);
    private static final String FAKE_GATEWAY_BASE_URL = "http://fake-gateway.local";
    private final ObjectMapper objectMapper = new ObjectMapper();

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

    @Override
    public CanonicalPaymentEvent verifyAndParseWebhook(String rawBody, Map<String, String> headers) throws WebhookVerificationException {
        log.info("FakeProvider: Parsing webhook");

        try {
            // Parse JSON webhook body
            // Expected format: {"eventId": "evt_123", "type": "payment.succeeded", "reference": "fake_ref_xxx", "invoiceId": "inv_xxx"}
            JsonNode root = objectMapper.readTree(rawBody);

            String eventId = root.has("eventId") ? root.get("eventId").asText() : null;
            String type = root.get("type").asText();
            String reference = root.has("reference") ? root.get("reference").asText() : null;
            String invoiceIdStr = root.has("invoiceId") ? root.get("invoiceId").asText() : null;

            // Map event type to status
            PaymentEventStatus status = mapEventTypeToStatus(type);

            InvoiceId invoiceId = null;
            if (invoiceIdStr != null && !invoiceIdStr.isEmpty()) {
                invoiceId = new InvoiceId(invoiceIdStr);
            }

            log.info("FakeProvider: Parsed webhook - eventId={}, type={}, status={}, reference={}",
                    eventId, type, status, reference);

            return new CanonicalPaymentEvent(
                    ProviderName.FAKE,
                    eventId,
                    reference,
                    invoiceId,
                    status,
                    Instant.now(),
                    type
            );
        } catch (Exception e) {
            log.error("FakeProvider: Failed to parse webhook", e);
            throw new WebhookVerificationException("Failed to parse fake webhook: " + e.getMessage(), e);
        }
    }

    private PaymentEventStatus mapEventTypeToStatus(String type) {
        return switch (type) {
            case "payment.succeeded" -> PaymentEventStatus.SUCCEEDED;
            case "payment.failed" -> PaymentEventStatus.FAILED;
            case "payment.pending" -> PaymentEventStatus.PENDING;
            default -> throw new WebhookVerificationException("Unknown event type: " + type);
        };
    }
}
