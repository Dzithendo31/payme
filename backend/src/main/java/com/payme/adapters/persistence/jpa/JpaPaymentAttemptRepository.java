package com.payme.adapters.persistence.jpa;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface JpaPaymentAttemptRepository extends JpaRepository<PaymentAttemptJpaEntity, String> {
    List<PaymentAttemptJpaEntity> findByInvoiceId(String invoiceId);
}
