package com.payme.domain.exceptions;

/**
 * Exception thrown when PayFast signature validation fails.
 * This extends WebhookVerificationException as signature validation
 * is a critical part of webhook security.
 */
public class PayFastSignatureException extends WebhookVerificationException {

    public PayFastSignatureException(String message) {
        super(message);
    }

    public PayFastSignatureException(String message, Throwable cause) {
        super(message, cause);
    }
}
