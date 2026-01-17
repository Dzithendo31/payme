package com.payme.adapters.persistence.jpa;

import com.payme.domain.Invoice;
import com.payme.domain.InvoiceId;
import com.payme.ports.InvoiceRepository;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class InvoiceRepositoryAdapter implements InvoiceRepository {

    private final JpaInvoiceRepository jpaRepository;

    public InvoiceRepositoryAdapter(JpaInvoiceRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Invoice save(Invoice invoice) {
        InvoiceJpaEntity entity = InvoiceJpaEntity.fromDomain(invoice);
        InvoiceJpaEntity saved = jpaRepository.save(entity);
        return saved.toDomain();
    }

    @Override
    public Optional<Invoice> findById(InvoiceId invoiceId) {
        return jpaRepository.findById(invoiceId.getValue())
                .map(InvoiceJpaEntity::toDomain);
    }

    @Override
    public boolean existsById(InvoiceId invoiceId) {
        return jpaRepository.existsById(invoiceId.getValue());
    }
}
