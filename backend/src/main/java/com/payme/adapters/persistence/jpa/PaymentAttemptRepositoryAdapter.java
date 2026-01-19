package com.payme.adapters.persistence.jpa;

import com.payme.domain.InvoiceId;
import com.payme.domain.PaymentAttempt;
import com.payme.domain.PaymentAttemptId;
import com.payme.ports.PaymentAttemptRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
public class PaymentAttemptRepositoryAdapter implements PaymentAttemptRepository {

    private final JpaPaymentAttemptRepository jpaRepository;

    public PaymentAttemptRepositoryAdapter(JpaPaymentAttemptRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public PaymentAttempt save(PaymentAttempt attempt) {
        PaymentAttemptJpaEntity entity = PaymentAttemptJpaEntity.fromDomain(attempt);
        PaymentAttemptJpaEntity saved = jpaRepository.save(entity);
        return saved.toDomain();
    }

    @Override
    public Optional<PaymentAttempt> findById(PaymentAttemptId attemptId) {
        return jpaRepository.findById(attemptId.getValue())
                .map(PaymentAttemptJpaEntity::toDomain);
    }

    @Override
    public List<PaymentAttempt> findByInvoiceId(InvoiceId invoiceId) {
        return jpaRepository.findByInvoiceId(invoiceId.getValue())
                .stream()
                .map(PaymentAttemptJpaEntity::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public Optional<PaymentAttempt> findByProviderReference(String providerReference) {
        return jpaRepository.findByProviderReference(providerReference)
                .map(PaymentAttemptJpaEntity::toDomain);
    }
}
