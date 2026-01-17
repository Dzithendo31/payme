package com.payme.adapters.persistence.jpa;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface JpaInvoiceRepository extends JpaRepository<InvoiceJpaEntity, String> {
}
