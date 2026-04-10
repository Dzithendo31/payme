package com.payme.ports;

import com.payme.domain.ProviderName;

import java.util.Set;

/**
 * Resolves a {@link PaymentProvider} by {@link ProviderName}.
 *
 * @dec(ARCH-001) Multi-provider registry over global provider — see decisions/ARCH-001.md
 *
 * Replaces the previous "one PaymentProvider bean per JVM" model so that the
 * application can speak more than one rail at a time. The chosen rail is
 * carried on the command (e.g. {@code StartCheckoutCommand.provider()}) and
 * resolved here at the boundary between the application layer and the
 * adapter layer.
 *
 * Implementations must:
 *  - throw {@link UnknownProviderException} for an unknown or unconfigured provider
 *  - return a non-null {@link #defaultProvider()} as long as at least one
 *    provider is wired in this environment
 */
public interface PaymentProviderRegistry {

    /**
     * Resolves the provider for the given name.
     *
     * @throws UnknownProviderException if the provider is not registered in this environment
     */
    PaymentProvider get(ProviderName name);

    /**
     * The set of providers actually wired in this environment.
     * Surfaced to clients (e.g. the pay page) so they only see real choices.
     */
    Set<ProviderName> available();

    /**
     * The default provider used when a caller does not specify one.
     * Backwards-compatible fallback for the legacy {@code payme.payment.provider} env var.
     */
    ProviderName defaultProvider();

    /**
     * Thrown when a caller asks for a provider that is not registered in the
     * current environment. Mapped to HTTP 400 by the API layer.
     */
    class UnknownProviderException extends RuntimeException {
        public UnknownProviderException(String message) {
            super(message);
        }
    }
}
