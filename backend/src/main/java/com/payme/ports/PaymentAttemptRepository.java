package com.payme.ports;

import com.payme.domain.InvoiceId;
import com.payme.domain.PaymentAttempt;
import com.payme.domain.PaymentAttemptId;

import java.util.List;
import java.util.Optional;

public interface PaymentAttemptRepository {
    /**
     * Saves a payment attempt.
     */
    PaymentAttempt save(PaymentAttempt attempt);

    /**
     * Finds a payment attempt by its ID.
     */
    Optional<PaymentAttempt> findById(PaymentAttemptId attemptId);

    /**
     * Finds all payment attempts for a given invoice.
     */
    List<PaymentAttempt> findByInvoiceId(InvoiceId invoiceId);

    /**
     * Finds a payment attempt by provider reference.
     */
    Optional<PaymentAttempt> findByProviderReference(String providerReference);
}
