package com.payme.domain.command;

public record StartCheckoutCommand(
        String commandId,
        String invoiceId
) implements Command {}
