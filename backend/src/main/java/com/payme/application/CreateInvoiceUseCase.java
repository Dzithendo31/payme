package com.payme.application;

import com.payme.domain.*;
import com.payme.ports.Clock;
import com.payme.ports.InvoiceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;

@Service
public class CreateInvoiceUseCase {

    private final InvoiceRepository invoiceRepository;
    private final Clock clock;

    public CreateInvoiceUseCase(InvoiceRepository invoiceRepository, Clock clock) {
        this.invoiceRepository = invoiceRepository;
        this.clock = clock;
    }

    @Transactional
    public Invoice execute(
            String merchantId,
            BigDecimal amount,
            Currency currency,
            String description,
            long expiryHours
    ) {
        var now = clock.now();
        var expiresAt = now.plus(Duration.ofHours(expiryHours));

        var invoice = new Invoice(
                InvoiceId.generate(),
                new MerchantId(merchantId),
                new Money(amount, currency),
                description,
                InvoiceStatus.CREATED,
                expiresAt,
                now,
                now
        );

        return invoiceRepository.save(invoice);
    }
}
