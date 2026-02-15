package com.payme.application.commandhandler;

import com.payme.domain.*;
import com.payme.domain.command.CreateInvoiceCommand;
import com.payme.domain.event.InvoiceCreated;
import com.payme.ports.Clock;
import com.payme.ports.EventPublisher;
import com.payme.ports.EventStore;
import com.payme.ports.InvoiceRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.UUID;

@Component
public class CreateInvoiceCommandHandler {

    private final InvoiceRepository invoiceRepository;
    private final Clock clock;
    private final EventStore eventStore;
    private final EventPublisher eventPublisher;

    public CreateInvoiceCommandHandler(
            InvoiceRepository invoiceRepository,
            Clock clock,
            EventStore eventStore,
            EventPublisher eventPublisher
    ) {
        this.invoiceRepository = invoiceRepository;
        this.clock = clock;
        this.eventStore = eventStore;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public Invoice handle(CreateInvoiceCommand cmd) {
        var now = clock.now();
        var expiresAt = now.plus(Duration.ofHours(cmd.expiryHours()));

        var invoice = new Invoice(
                InvoiceId.generate(),
                new MerchantId(cmd.merchantId()),
                new Money(cmd.amount(), Currency.valueOf(cmd.currency())),
                cmd.description(),
                InvoiceStatus.CREATED,
                expiresAt,
                now,
                now
        );

        invoice = invoiceRepository.save(invoice);

        var event = new InvoiceCreated(
                UUID.randomUUID().toString(),
                now,
                invoice.getInvoiceId().getValue(),
                invoice.getMerchantId().getValue(),
                invoice.getMoney().getAmount(),
                invoice.getMoney().getCurrency().name(),
                invoice.getDescription(),
                invoice.getExpiresAt()
        );

        eventStore.store(event, "Invoice");
        eventPublisher.publish(event);

        return invoice;
    }
}
