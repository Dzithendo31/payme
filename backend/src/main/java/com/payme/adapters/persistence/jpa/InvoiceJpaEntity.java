package com.payme.adapters.persistence.jpa;

import com.payme.domain.*;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "invoices")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InvoiceJpaEntity {

    @Id
    @Column(name = "id", nullable = false)
    private String id;

    @Column(name = "merchant_id", nullable = false)
    private String merchantId;

    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3)
    @Enumerated(EnumType.STRING)
    private Currency currency;

    @Column(name = "description", nullable = false)
    private String description;

    @Column(name = "status", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private InvoiceStatus status;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Invoice toDomain() {
        return new Invoice(
                new InvoiceId(this.id),
                new MerchantId(this.merchantId),
                new Money(this.amount, this.currency),
                this.description,
                this.status,
                this.expiresAt,
                this.createdAt,
                this.updatedAt
        );
    }

    public static InvoiceJpaEntity fromDomain(Invoice invoice) {
        return InvoiceJpaEntity.builder()
                .id(invoice.getInvoiceId().getValue())
                .merchantId(invoice.getMerchantId().getValue())
                .amount(invoice.getMoney().getAmount())
                .currency(invoice.getMoney().getCurrency())
                .description(invoice.getDescription())
                .status(invoice.getStatus())
                .expiresAt(invoice.getExpiresAt())
                .createdAt(invoice.getCreatedAt())
                .updatedAt(invoice.getUpdatedAt())
                .build();
    }
}
