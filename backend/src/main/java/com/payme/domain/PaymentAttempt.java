package com.payme.domain;

import com.payme.domain.exceptions.InvalidPaymentAttemptStateException;

import java.time.Instant;
import java.util.Objects;

public class PaymentAttempt {
    private final PaymentAttemptId attemptId;
    private final InvoiceId invoiceId;
    private final ProviderName provider;
    private final String providerReference;
    private PaymentAttemptStatus status;
    private final Instant createdAt;
    private Instant updatedAt;

    public PaymentAttempt(
            PaymentAttemptId attemptId,
            InvoiceId invoiceId,
            ProviderName provider,
            String providerReference,
            PaymentAttemptStatus status,
            Instant createdAt,
            Instant updatedAt
    ) {
        if (attemptId == null) {
            throw new IllegalArgumentException("AttemptId cannot be null");
        }
        if (invoiceId == null) {
            throw new IllegalArgumentException("InvoiceId cannot be null");
        }
        if (provider == null) {
            throw new IllegalArgumentException("Provider cannot be null");
        }
        if (providerReference == null || providerReference.trim().isEmpty()) {
            throw new IllegalArgumentException("ProviderReference cannot be null or empty");
        }
        if (status == null) {
            throw new IllegalArgumentException("Status cannot be null");
        }
        if (createdAt == null) {
            throw new IllegalArgumentException("CreatedAt cannot be null");
        }
        if (updatedAt == null) {
            throw new IllegalArgumentException("UpdatedAt cannot be null");
        }

        this.attemptId = attemptId;
        this.invoiceId = invoiceId;
        this.provider = provider;
        this.providerReference = providerReference;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public void markAsSucceeded(Instant now) {
        if (status == PaymentAttemptStatus.SUCCEEDED) {
            return; // Already succeeded, idempotent
        }
        if (status == PaymentAttemptStatus.FAILED) {
            throw new InvalidPaymentAttemptStateException(
                    "Cannot mark FAILED attempt as SUCCEEDED"
            );
        }
        this.status = PaymentAttemptStatus.SUCCEEDED;
        this.updatedAt = now;
    }

    public void markAsFailed(Instant now) {
        if (status == PaymentAttemptStatus.FAILED) {
            return; // Already failed, idempotent
        }
        if (status == PaymentAttemptStatus.SUCCEEDED) {
            throw new InvalidPaymentAttemptStateException(
                    "Cannot mark SUCCEEDED attempt as FAILED"
            );
        }
        this.status = PaymentAttemptStatus.FAILED;
        this.updatedAt = now;
    }

    // Getters
    public PaymentAttemptId getAttemptId() {
        return attemptId;
    }

    public InvoiceId getInvoiceId() {
        return invoiceId;
    }

    public ProviderName getProvider() {
        return provider;
    }

    public String getProviderReference() {
        return providerReference;
    }

    public PaymentAttemptStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PaymentAttempt that = (PaymentAttempt) o;
        return Objects.equals(attemptId, that.attemptId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(attemptId);
    }
}
