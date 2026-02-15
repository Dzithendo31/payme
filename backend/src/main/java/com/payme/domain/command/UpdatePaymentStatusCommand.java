package com.payme.domain.command;

public record UpdatePaymentStatusCommand(
        String commandId,
        String attemptId,
        String status,
        String providerEventId
) implements Command {}
