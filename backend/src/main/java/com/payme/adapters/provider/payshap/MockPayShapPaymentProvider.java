package com.payme.adapters.provider.payshap;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.payme.domain.*;
import com.payme.domain.exceptions.WebhookVerificationException;
import com.payme.ports.CheckoutSession;
import com.payme.ports.CheckoutUrls;
import com.payme.ports.PaymentProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Map;

/**
 * @dec(INT-001) Mock-first PayShap adapter — see decisions/INT-001.md
 *
 * Simulates PayShap's <em>request-to-pay</em> (RtP) UX end-to-end without
 * any external dependency or sandbox credentials. The chosen UX:
 *
 *  1. Merchant initiates a request via {@link #createCheckoutSession}; we
 *     synthesise a phone-shaped ProxyID and return a checkout URL pointing
 *     at our own mock approve page.
 *  2. Customer opens that page and sees "approve in your banking app".
 *  3. Customer clicks the simulated "approve" button which POSTs to the
 *     normal {@code /webhooks/PAYSHAP} endpoint, just like a real bank
 *     confirmation would.
 *  4. {@link #verifyAndParseWebhook} parses the JSON body into the same
 *     {@link CanonicalPaymentEvent} every other adapter produces.
 *
 * This is intentionally a faithful flow rather than a card-style form: PayShap
 * RtP is a push-to-phone interaction, not a card capture, and the demo only
 * sells the multi-rail story if it <em>looks</em> like a different rail.
 *
 * Replace with a real adapter (e.g. {@code StitchPayShapPaymentProvider}) by
 * registering it under {@link ProviderName#PAYSHAP} in
 * {@code PaymentConfiguration} when {@code payme.payshap.mode=stitch}. Nothing
 * else in the system needs to change.
 */
public class MockPayShapPaymentProvider implements PaymentProvider {

    private static final Logger log = LoggerFactory.getLogger(MockPayShapPaymentProvider.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final ObjectMapper objectMapper;
    private final String mockBaseUrl;

    public MockPayShapPaymentProvider(ObjectMapper objectMapper, String mockBaseUrl) {
        this.objectMapper = objectMapper;
        this.mockBaseUrl = mockBaseUrl;
    }

    @Override
    public CheckoutSession createCheckoutSession(Invoice invoice, PaymentAttemptId attemptId, CheckoutUrls urls) {
        log.info("MockPayShap: creating RtP for invoice {} / attempt {}",
                invoice.getInvoiceId().getValue(), attemptId.getValue());

        // @dec~ phone-shaped ProxyID — looks like a real RtP target without
        // pretending to be one. The "0820000xxx" range is a known unallocated
        // SA mobile prefix used in test fixtures.
        String proxyId = "08200000" + String.format("%02d", RANDOM.nextInt(100));
        String providerReference = "payshap_mock_" + attemptId.getValue();

        // @dec~ Customer is redirected to our internal mock approve page rather
        // than an external bank — the page knows how to drive the simulated flow.
        // No formParams: this is a plain GET redirect, not a POST-form provider
        // like PayFast. The /checkout/redirect endpoint uses empty formParams as
        // the signal to issue a 302 instead of rendering an auto-submit form.
        String checkoutUrl = mockBaseUrl
                + "/pay/" + invoice.getInvoiceId().getValue()
                + "/payshap-mock?attempt=" + attemptId.getValue()
                + "&proxy=" + proxyId;

        log.info("MockPayShap: RtP issued — proxyId={}, ref={}", proxyId, providerReference);
        return new CheckoutSession(checkoutUrl, providerReference);
    }

    @Override
    public CanonicalPaymentEvent verifyAndParseWebhook(String rawBody, Map<String, String> headers)
            throws WebhookVerificationException {
        log.info("MockPayShap: parsing simulated webhook");

        try {
            // Expected body:
            //   { "attemptId": "...", "invoiceId": "...", "status": "APPROVED" | "DECLINED",
            //     "providerEventId": "..." (optional) }
            JsonNode root = objectMapper.readTree(rawBody);

            String attemptId = textOrNull(root, "attemptId");
            String invoiceIdStr = textOrNull(root, "invoiceId");
            String statusStr = textOrNull(root, "status");
            String providerEventId = textOrNull(root, "providerEventId");

            if (attemptId == null || statusStr == null) {
                throw new WebhookVerificationException(
                        "MockPayShap webhook missing required fields (attemptId, status)"
                );
            }

            // @dec~ no real signature to verify — the mock is only registered
            // when payme.payshap.mode=mock, which is dev-only by config
            PaymentEventStatus status = mapStatus(statusStr);

            InvoiceId invoiceId = invoiceIdStr != null ? new InvoiceId(invoiceIdStr) : null;
            String eventId = providerEventId != null
                    ? providerEventId
                    : "payshap_mock_evt_" + attemptId + "_" + Instant.now().toEpochMilli();

            return new CanonicalPaymentEvent(
                    ProviderName.PAYSHAP,
                    eventId,
                    attemptId,
                    invoiceId,
                    status,
                    Instant.now(),
                    statusStr
            );

        } catch (WebhookVerificationException e) {
            throw e;
        } catch (Exception e) {
            log.error("MockPayShap: failed to parse webhook", e);
            throw new WebhookVerificationException("Failed to parse MockPayShap webhook: " + e.getMessage(), e);
        }
    }

    private static String textOrNull(JsonNode node, String field) {
        return node.has(field) && !node.get(field).isNull() ? node.get(field).asText() : null;
    }

    private PaymentEventStatus mapStatus(String status) {
        return switch (status.toUpperCase()) {
            case "APPROVED", "SUCCEEDED" -> PaymentEventStatus.SUCCEEDED;
            case "DECLINED", "FAILED" -> PaymentEventStatus.FAILED;
            case "PENDING" -> PaymentEventStatus.PENDING;
            default -> throw new WebhookVerificationException("Unknown MockPayShap status: " + status);
        };
    }
}
