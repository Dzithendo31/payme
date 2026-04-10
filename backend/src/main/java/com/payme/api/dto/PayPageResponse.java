package com.payme.api.dto;

import com.payme.domain.Currency;
import com.payme.domain.Invoice;
import com.payme.domain.InvoiceStatus;
import com.payme.domain.ProviderName;
import com.payme.ports.Clock;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;

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

    /**
     * @dec(ARCH-001) Surfaces the rails available in this environment so the
     * pay page can render a picker — see decisions/ARCH-001.md
     *
     * Populated from {@code PaymentProviderRegistry.available()} at the
     * controller layer. Excludes {@code FAKE} from public-facing responses;
     * the controller is responsible for that filtering, not this DTO.
     */
    private List<ProviderName> availableProviders;

    private ProviderName defaultProvider;

    public static PayPageResponse fromDomain(
            Invoice invoice,
            Clock clock,
            Set<ProviderName> availableProviders,
            ProviderName defaultProvider
    ) {
        return PayPageResponse.builder()
                .invoiceId(invoice.getInvoiceId().getValue())
                .merchantName("Merchant " + invoice.getMerchantId().getValue().substring(0, 8))
                .amount(invoice.getMoney().getAmount())
                .currency(invoice.getMoney().getCurrency())
                .description(invoice.getDescription())
                .status(invoice.getStatus())
                .isPayable(invoice.isPayable(clock.now()))
                .expiresAt(invoice.getExpiresAt())
                .availableProviders(List.copyOf(availableProviders))
                .defaultProvider(defaultProvider)
                .build();
    }
}
