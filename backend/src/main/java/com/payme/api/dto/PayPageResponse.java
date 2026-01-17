package com.payme.api.dto;

import com.payme.domain.Currency;
import com.payme.domain.Invoice;
import com.payme.domain.InvoiceStatus;
import com.payme.ports.Clock;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PayPageResponse {

    private String invoiceId;
    private String merchantName;
    private BigDecimal amount;
    private Currency currency;
    private String description;
    private InvoiceStatus status;
    private boolean isPayable;
    private Instant expiresAt;

    public static PayPageResponse fromDomain(Invoice invoice, Clock clock) {
        return PayPageResponse.builder()
                .invoiceId(invoice.getInvoiceId().getValue())
                .merchantName("Merchant " + invoice.getMerchantId().getValue().substring(0, 8))
                .amount(invoice.getMoney().getAmount())
                .currency(invoice.getMoney().getCurrency())
                .description(invoice.getDescription())
                .status(invoice.getStatus())
                .isPayable(invoice.isPayable(clock.now()))
                .expiresAt(invoice.getExpiresAt())
                .build();
    }
}
