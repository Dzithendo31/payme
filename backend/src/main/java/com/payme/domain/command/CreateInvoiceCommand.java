package com.payme.domain.command;

import java.math.BigDecimal;

public record CreateInvoiceCommand(
        String commandId,
        String merchantId,
        BigDecimal amount,
        String currency,
        String description,
        long expiryHours
) implements Command {}
