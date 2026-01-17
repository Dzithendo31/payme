package com.payme.domain.exceptions;

import com.payme.domain.InvoiceId;

public class InvoiceNotFoundException extends RuntimeException {
    
    public InvoiceNotFoundException(InvoiceId invoiceId) {
        super("Invoice not found: " + invoiceId.getValue());
    }

    public InvoiceNotFoundException(String message) {
        super(message);
    }
}
