package com.payme.application;

import com.payme.domain.Invoice;
import com.payme.domain.InvoiceId;
import com.payme.domain.InvoiceStatus;
import com.payme.domain.exceptions.InvoiceNotFoundException;
import com.payme.ports.Clock;
import com.payme.ports.InvoiceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GetInvoiceUseCase {

    private final InvoiceRepository invoiceRepository;
    private final Clock clock;

    public GetInvoiceUseCase(InvoiceRepository invoiceRepository, Clock clock) {
        this.invoiceRepository = invoiceRepository;
        this.clock = clock;
    }

    @Transactional
    public Invoice execute(String invoiceIdStr) {
        var invoiceId = new InvoiceId(invoiceIdStr);
        var invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new InvoiceNotFoundException(invoiceId));

        var now = clock.now();
        
        // Auto-expire if needed
        if (invoice.getStatus() == InvoiceStatus.CREATED && invoice.isExpired(now)) {
            invoice.markAsExpired(now);
            invoice = invoiceRepository.save(invoice);
        }

        return invoice;
    }
}
