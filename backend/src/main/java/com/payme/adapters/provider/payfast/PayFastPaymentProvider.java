package com.payme.adapters.provider.payfast;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payme.config.PayFastConfig;
import com.payme.domain.*;
import com.payme.domain.exceptions.PayFastSignatureException;
import com.payme.domain.exceptions.WebhookVerificationException;
import com.payme.ports.CheckoutSession;
import com.payme.ports.CheckoutUrls;
import com.payme.ports.HashService;
import com.payme.ports.PaymentProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * PayFast payment provider implementation for South African payment processing.
 * 
 * Supports:
 * - Hosted checkout (form POST to PayFast)
 * - ITN (Instant Transaction Notification) webhooks
 * - MD5 signature verification for security
 */
public class PayFastPaymentProvider implements PaymentProvider {

    private static final Logger log = LoggerFactory.getLogger(PayFastPaymentProvider.class);

    private final PayFastConfig config;
    private final HashService hashService;
    private final ObjectMapper objectMapper;
    private final PayFastIpValidator ipValidator;

    public PayFastPaymentProvider(PayFastConfig config, HashService hashService, ObjectMapper objectMapper, PayFastIpValidator ipValidator) {
        this.config = config;
        this.hashService = hashService;
        this.objectMapper = objectMapper;
        this.ipValidator = ipValidator;
    }

    @Override
    public CheckoutSession createCheckoutSession(Invoice invoice, PaymentAttemptId attemptId, CheckoutUrls urls) {
        log.info("PayFast: Creating checkout session for invoice {} and attempt {}",
                invoice.getInvoiceId().getValue(), attemptId.getValue());

        // Build payment parameters
        Map<String, String> params = new LinkedHashMap<>();
        params.put("merchant_id", config.getMerchantId());
        params.put("merchant_key", config.getMerchantKey());
        params.put("return_url", urls.getSuccessUrl());
        params.put("cancel_url", urls.getCancelUrl());
        params.put("notify_url", config.getNotifyUrl());
        
        // Amount must be formatted to 2 decimal places
        params.put("amount", String.format("%.2f", invoice.getMoney().getAmount()));
        params.put("item_name", invoice.getDescription());
        
        // Custom fields for correlation
        params.put("m_payment_id", invoice.getInvoiceId().getValue());
        params.put("custom_str1", attemptId.getValue());

        // Generate signature
        String signature = PayFastSignatureService.generateSignature(params, config.getPassphrase());
        params.put("signature", signature);

        // The checkout URL is the PayFast process endpoint
        // In a real implementation, we'd need to submit a form with these parameters
        // For now, we return the URL and parameters - the frontend will need to POST them
        String checkoutUrl = config.getProcessUrl();

        log.info("PayFast: Generated checkout URL: {}", checkoutUrl);
        log.info("PayFast: Signature: {}", signature);

        // For PayFast, the providerReference isn't available until after payment
        // We use the invoiceId as a temporary reference
        String providerReference = "payfast_" + invoice.getInvoiceId().getValue();

        return new CheckoutSession(checkoutUrl, providerReference);
    }

    @Override
    public CanonicalPaymentEvent verifyAndParseWebhook(String rawBody, Map<String, String> headers)
            throws WebhookVerificationException {
        
        log.info("PayFast: Parsing ITN webhook");

        try {
            // Parse form-encoded body into map
            Map<String, String> params = parseFormEncodedBody(rawBody);

            // Extract and remove signature
            String providedSignature = params.remove("signature");
            if (providedSignature == null || providedSignature.isEmpty()) {
                throw new PayFastSignatureException("No signature provided in PayFast ITN");
            }

            log.debug("PayFast ITN parameters: {}", params.keySet());
            log.debug("Provided signature: {}", providedSignature);

            // Verify signature
            boolean signatureValid = PayFastSignatureService.verifySignature(
                    params, providedSignature, config.getPassphrase());

            if (!signatureValid) {
                throw new PayFastSignatureException(
                        "PayFast signature verification failed. Expected signature does not match provided signature.");
            }

            log.info("PayFast: Signature verified successfully");

            // Extract key fields
            String pfPaymentId = params.get("pf_payment_id");
            String paymentStatus = params.get("payment_status");
            String mPaymentId = params.get("m_payment_id");
            String customStr1 = params.get("custom_str1");
            String amountGross = params.get("amount_gross");

            log.info("PayFast ITN: pf_payment_id={}, payment_status={}, m_payment_id={}, amount_gross={}",
                    pfPaymentId, paymentStatus, mPaymentId, amountGross);

            // Map payment status to canonical status
            PaymentEventStatus status = mapPaymentStatus(paymentStatus);

            // Build canonical event
            InvoiceId invoiceId = mPaymentId != null ? new InvoiceId(mPaymentId) : null;

            CanonicalPaymentEvent event = new CanonicalPaymentEvent(
                    ProviderName.PAYFAST,
                    pfPaymentId,
                    customStr1, // attemptId stored in custom_str1
                    invoiceId,
                    status,
                    Instant.now(),
                    paymentStatus
            );

            log.info("PayFast: Successfully parsed ITN to canonical event - status: {}", status);

            return event;

        } catch (PayFastSignatureException e) {
            log.error("PayFast: Signature verification failed", e);
            throw e;
        } catch (Exception e) {
            log.error("PayFast: Failed to parse ITN webhook", e);
            throw new WebhookVerificationException("Failed to parse PayFast ITN: " + e.getMessage(), e);
        }
    }

    /**
     * Parses form-encoded body (application/x-www-form-urlencoded) into a map.
     *
     * @param body Form-encoded body
     * @return Map of parameters
     */
    private Map<String, String> parseFormEncodedBody(String body) {
        Map<String, String> params = new HashMap<>();

        if (body == null || body.isEmpty()) {
            return params;
        }

        String[] pairs = body.split("&");
        for (String pair : pairs) {
            String[] keyValue = pair.split("=", 2);
            if (keyValue.length == 2) {
                try {
                    String key = URLDecoder.decode(keyValue[0], StandardCharsets.UTF_8.name());
                    String value = URLDecoder.decode(keyValue[1], StandardCharsets.UTF_8.name());
                    params.put(key, value);
                } catch (UnsupportedEncodingException e) {
                    log.warn("Failed to decode parameter: {}", pair);
                }
            }
        }

        return params;
    }

    /**
     * Maps PayFast payment status to canonical event status.
     *
     * @param paymentStatus PayFast payment status
     * @return Canonical payment event status
     */
    private PaymentEventStatus mapPaymentStatus(String paymentStatus) {
        if (paymentStatus == null) {
            throw new WebhookVerificationException("Payment status is null");
        }

        return switch (paymentStatus.toUpperCase()) {
            case "COMPLETE" -> PaymentEventStatus.SUCCEEDED;
            case "FAILED" -> PaymentEventStatus.FAILED;
            case "CANCELLED" -> PaymentEventStatus.FAILED; // Treat cancelled as failed
            case "PENDING" -> PaymentEventStatus.PENDING;
            default -> {
                log.warn("Unknown PayFast payment status: {}", paymentStatus);
                throw new WebhookVerificationException("Unknown PayFast payment status: " + paymentStatus);
            }
        };
    }
}
