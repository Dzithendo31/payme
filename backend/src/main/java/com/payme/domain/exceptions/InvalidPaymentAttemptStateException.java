package com.payme.domain.exceptions;

public class InvalidPaymentAttemptStateException extends RuntimeException {
    public InvalidPaymentAttemptStateException(String message) {
        super(message);
    }
}
