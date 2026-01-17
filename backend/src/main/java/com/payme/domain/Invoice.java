package com.payme.domain;

import com.payme.domain.exceptions.InvalidInvoiceStateException;

import java.time.Instant;
import java.util.Objects;

public class Invoice {
    private final InvoiceId invoiceId;
    private final MerchantId merchantId;
    private final Money money;
    private final String description;
    private InvoiceStatus status;
    private final Instant expiresAt;
    private final Instant createdAt;
    private Instant updatedAt;

    public Invoice(
            InvoiceId invoiceId,
            MerchantId merchantId,
            Money money,
            String description,
            InvoiceStatus status,
            Instant expiresAt,
            Instant createdAt,
            Instant updatedAt
    ) {
        if (invoiceId == null) {
            throw new IllegalArgumentException("InvoiceId cannot be null");
        }
        if (merchantId == null) {
            throw new IllegalArgumentException("MerchantId cannot be null");
        }
        if (money == null) {
            throw new IllegalArgumentException("Money cannot be null");
        }
        if (description == null || description.trim().isEmpty()) {
            throw new IllegalArgumentException("Description cannot be null or empty");
        }
        if (status == null) {
            throw new IllegalArgumentException("Status cannot be null");
        }
        if (expiresAt == null) {
            throw new IllegalArgumentException("ExpiresAt cannot be null");
        }
        if (createdAt == null) {
            throw new IllegalArgumentException("CreatedAt cannot be null");
        }
        if (updatedAt == null) {
            throw new IllegalArgumentException("UpdatedAt cannot be null");
        }

        this.invoiceId = invoiceId;
        this.merchantId = merchantId;
        this.money = money;
        this.description = description;
        this.status = status;
        this.expiresAt = expiresAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public boolean isPayable(Instant now) {
        return (status == InvoiceStatus.CREATED || status == InvoiceStatus.PENDING)
                && !isExpired(now);
    }

    public boolean isExpired(Instant now) {
        return now.isAfter(expiresAt);
    }

    public void markAsPending(Instant now) {
        if (status != InvoiceStatus.CREATED) {
            throw new InvalidInvoiceStateException(
                    "Cannot mark as PENDING from status: " + status
            );
        }
        if (isExpired(now)) {
            throw new InvalidInvoiceStateException(
                    "Cannot mark expired invoice as PENDING"
            );
        }
        this.status = InvoiceStatus.PENDING;
        this.updatedAt = now;
    }

    public void markAsSucceeded(Instant now) {
        if (status != InvoiceStatus.PENDING) {
            throw new InvalidInvoiceStateException(
                    "Cannot mark as SUCCEEDED from status: " + status
            );
        }
        this.status = InvoiceStatus.SUCCEEDED;
        this.updatedAt = now;
    }

    public void markAsFailed(Instant now) {
        if (status != InvoiceStatus.PENDING) {
            throw new InvalidInvoiceStateException(
                    "Cannot mark as FAILED from status: " + status
            );
        }
        this.status = InvoiceStatus.FAILED;
        this.updatedAt = now;
    }

    public void markAsExpired(Instant now) {
        if (status == InvoiceStatus.SUCCEEDED) {
            throw new InvalidInvoiceStateException(
                    "Cannot mark SUCCEEDED invoice as EXPIRED"
            );
        }
        if (status == InvoiceStatus.EXPIRED) {
            return; // Already expired, no-op
        }
        if (!isExpired(now)) {
            throw new InvalidInvoiceStateException(
                    "Cannot mark as EXPIRED before expiry time"
            );
        }
        this.status = InvoiceStatus.EXPIRED;
        this.updatedAt = now;
    }

    // Getters
    public InvoiceId getInvoiceId() {
        return invoiceId;
    }

    public MerchantId getMerchantId() {
        return merchantId;
    }

    public Money getMoney() {
        return money;
    }

    public String getDescription() {
        return description;
    }

    public InvoiceStatus getStatus() {
        return status;
    }

    public Instant getExpiresAt() {
        return expiresAt;
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
        Invoice invoice = (Invoice) o;
        return Objects.equals(invoiceId, invoice.invoiceId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(invoiceId);
    }
}
