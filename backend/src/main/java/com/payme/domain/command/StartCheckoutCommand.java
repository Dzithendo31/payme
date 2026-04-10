package com.payme.domain.command;

import com.payme.domain.ProviderName;

/**
 * Command to begin a checkout flow for a given invoice via a chosen rail.
 *
 * @dec(ARCH-001) provider field — see decisions/ARCH-001.md
 *
 * The {@code provider} field carries the customer's choice of rail. May be
 * {@code null}, in which case the command handler will resolve the default
 * via {@code PaymentProviderRegistry.defaultProvider()}. Backwards-compatible
 * for callers that have not yet been updated to pass a provider.
 */
public record StartCheckoutCommand(
        String commandId,
        String invoiceId,
        ProviderName provider
) implements Command {

    /**
     * Backwards-compat factory for callers that do not yet specify a provider.
     * Resolves to the default at the handler level.
     */
    public static StartCheckoutCommand withDefaultProvider(String commandId, String invoiceId) {
        return new StartCheckoutCommand(commandId, invoiceId, null);
    }
}
