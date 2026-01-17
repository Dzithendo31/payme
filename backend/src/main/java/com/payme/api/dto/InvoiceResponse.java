package com.payme.api.dto;

import com.payme.domain.Currency;
import com.payme.domain.Invoice;
import com.payme.domain.InvoiceStatus;
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
public class InvoiceResponse {

    private String invoiceId;
    private String merchantId;
    private BigDecimal amount;
    private Currency currency;
    private String description;
    private InvoiceStatus status;
    private Instant expiresAt;
    private Instant createdAt;
    private String payUrl;

    public static InvoiceResponse fromDomain(Invoice invoice, String baseUrl) {
        String payUrl = baseUrl + "/pay/" + invoice.getInvoiceId().getValue();
        
        return InvoiceResponse.builder()
                .invoiceId(invoice.getInvoiceId().getValue())
                .merchantId(invoice.getMerchantId().getValue())
                .amount(invoice.getMoney().getAmount())
                .currency(invoice.getMoney().getCurrency())
                .description(invoice.getDescription())
                .status(invoice.getStatus())
                .expiresAt(invoice.getExpiresAt())
                .createdAt(invoice.getCreatedAt())
                .payUrl(payUrl)
                .build();
    }
}
