package com.payme.application;

import com.payme.domain.Money;
import com.payme.domain.ProviderName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Decides which rail a checkout actually runs on, given what the customer
 * asked for and what the environment allows.
 *
 * Two knobs, both optional:
 *  - {@code payme.payment.routing.pinned-provider}: when set, every checkout
 *    in this environment goes to that rail and the pay page only advertises
 *    it. Lets a merchant deploy run "PayShap only" without touching the
 *    registry wiring.
 *  - {@code payme.payment.routing.payshap-max-amount}: PayShap has a
 *    per-transaction cap at most SA banks (R3,000 at launch, higher at some).
 *    Invoices above the cap are routed to PayFast instead so the customer
 *    is not bounced by their bank after the request-to-pay is sent.
 */
@Component
public class CheckoutRoutingPolicy {

    private static final Logger log = LoggerFactory.getLogger(CheckoutRoutingPolicy.class);

    private final ProviderName pinnedProvider;
    private final BigDecimal payShapMaxAmount;

    public CheckoutRoutingPolicy(
            @Value("${payme.payment.routing.pinned-provider:}") String pinnedProvider,
            @Value("${payme.payment.routing.payshap-max-amount:}") String payShapMaxAmount
    ) {
        this.pinnedProvider = parseProvider(pinnedProvider);
        this.payShapMaxAmount = parseAmount(payShapMaxAmount);

        if (this.pinnedProvider != null) {
            log.info("Checkout routing: all checkouts pinned to {}", this.pinnedProvider);
        }
        if (this.payShapMaxAmount != null) {
            log.info("Checkout routing: PayShap capped at {} per transaction", this.payShapMaxAmount);
        }
    }

    /**
     * No-op policy: honours the requested provider as-is. Used by callers
     * that do not have routing configuration (tests, legacy wiring).
     */
    public static CheckoutRoutingPolicy passthrough() {
        return new CheckoutRoutingPolicy("", "");
    }

    /**
     * Resolves the rail to use for a checkout.
     *
     * @param requested what the customer selected, or {@code null} for "no preference"
     * @param envDefault the environment default used when nothing was requested
     * @param available rails registered in this environment
     * @param amount the invoice amount, used for the PayShap cap
     */
    public ProviderName route(ProviderName requested, ProviderName envDefault,
                              Set<ProviderName> available, Money amount) {
        if (pinnedProvider != null) {
            if (requested != null && requested != pinnedProvider) {
                log.info("Checkout routing: overriding requested {} with pinned {}", requested, pinnedProvider);
            }
            return pinnedProvider;
        }

        ProviderName chosen = requested != null ? requested : envDefault;

        if (chosen == ProviderName.PAYSHAP
                && exceedsPayShapCap(amount)
                && available.contains(ProviderName.PAYFAST)) {
            log.info("Checkout routing: amount {} exceeds PayShap cap {}, routing to PAYFAST",
                    amount.getAmount(), payShapMaxAmount);
            return ProviderName.PAYFAST;
        }

        return chosen;
    }

    /**
     * Narrows the rails offered on the pay page so the picker only shows
     * options that {@link #route} would actually honour for this invoice.
     */
    public Set<ProviderName> offered(Set<ProviderName> available, Money amount) {
        if (pinnedProvider != null) {
            Set<ProviderName> pinned = new LinkedHashSet<>();
            if (available.contains(pinnedProvider)) {
                pinned.add(pinnedProvider);
            }
            return pinned;
        }

        if (exceedsPayShapCap(amount) && available.contains(ProviderName.PAYFAST)) {
            Set<ProviderName> filtered = new LinkedHashSet<>(available);
            filtered.remove(ProviderName.PAYSHAP);
            return filtered;
        }

        return available;
    }

    private boolean exceedsPayShapCap(Money amount) {
        return payShapMaxAmount != null && amount.getAmount().compareTo(payShapMaxAmount) > 0;
    }

    private static ProviderName parseProvider(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return ProviderName.valueOf(raw.trim().toUpperCase());
    }

    private static BigDecimal parseAmount(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return new BigDecimal(raw.trim());
    }
}
