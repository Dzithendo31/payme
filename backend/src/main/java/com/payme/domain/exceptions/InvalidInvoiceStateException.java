package com.payme.domain.exceptions;

public class InvalidInvoiceStateException extends RuntimeException {
    
    public InvalidInvoiceStateException(String message) {
        super(message);
    }
}
