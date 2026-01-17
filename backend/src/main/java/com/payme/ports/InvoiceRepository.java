package com.payme.ports;

import com.payme.domain.Invoice;
import com.payme.domain.InvoiceId;

import java.util.Optional;

public interface InvoiceRepository {
    
    Invoice save(Invoice invoice);
    
    Optional<Invoice> findById(InvoiceId invoiceId);
    
    boolean existsById(InvoiceId invoiceId);
}
