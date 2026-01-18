package com.payme.adapters.persistence.jpa;

import com.payme.domain.*;
import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "payment_attempts")
public class PaymentAttemptJpaEntity {

    @Id
    @Column(name = "attempt_id", nullable = false)
    private String attemptId;

    @Column(name = "invoice_id", nullable = false)
    private String invoiceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false)
    private ProviderName provider;

    @Column(name = "provider_reference", nullable = false)
    private String providerReference;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private PaymentAttemptStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // JPA requires default constructor
    protected PaymentAttemptJpaEntity() {
    }

    public PaymentAttemptJpaEntity(
            String attemptId,
            String invoiceId,
            ProviderName provider,
            String providerReference,
            PaymentAttemptStatus status,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.attemptId = attemptId;
        this.invoiceId = invoiceId;
        this.provider = provider;
        this.providerReference = providerReference;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static PaymentAttemptJpaEntity fromDomain(PaymentAttempt attempt) {
        return new PaymentAttemptJpaEntity(
                attempt.getAttemptId().getValue(),
                attempt.getInvoiceId().getValue(),
                attempt.getProvider(),
                attempt.getProviderReference(),
                attempt.getStatus(),
                attempt.getCreatedAt(),
                attempt.getUpdatedAt()
        );
    }

    public PaymentAttempt toDomain() {
        return new PaymentAttempt(
                new PaymentAttemptId(attemptId),
                new InvoiceId(invoiceId),
                provider,
                providerReference,
                status,
                createdAt,
                updatedAt
        );
    }

    // Getters and setters
    public String getAttemptId() {
        return attemptId;
    }

    public void setAttemptId(String attemptId) {
        this.attemptId = attemptId;
    }

    public String getInvoiceId() {
        return invoiceId;
    }

    public void setInvoiceId(String invoiceId) {
        this.invoiceId = invoiceId;
    }

    public ProviderName getProvider() {
        return provider;
    }

    public void setProvider(ProviderName provider) {
        this.provider = provider;
    }

    public String getProviderReference() {
        return providerReference;
    }

    public void setProviderReference(String providerReference) {
        this.providerReference = providerReference;
    }

    public PaymentAttemptStatus getStatus() {
        return status;
    }

    public void setStatus(PaymentAttemptStatus status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
