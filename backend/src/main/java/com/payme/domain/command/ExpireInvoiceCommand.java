package com.payme.domain.command;

public record ExpireInvoiceCommand(
        String commandId,
        String invoiceId
) implements Command {}
