package com.payme.config;

import com.payme.adapters.provider.fake.FakePaymentProvider;
import com.payme.adapters.provider.payfast.PayFastPaymentProvider;
import com.payme.adapters.provider.payshap.MockPayShapPaymentProvider;
import com.payme.domain.ProviderName;
import com.payme.ports.CheckoutUrls;
import com.payme.ports.HashService;
import com.payme.ports.PaymentProvider;
import com.payme.ports.PaymentProviderRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

/**
 * Wires every {@link PaymentProvider} adapter the application knows about into
 * a single {@link PaymentProviderRegistry}.
 *
 * @dec(ARCH-001) Multi-provider registry over global provider — see decisions/ARCH-001.md
 *
 * Previously this configuration produced exactly one {@code PaymentProvider}
 * bean selected by {@code payme.payment.provider}. That made it impossible to
 * speak more than one rail per deploy, which blocks the multi-rail product
 * story (one PayMe link, customer picks PayFast or PayShap).
 *
 * The legacy {@code payme.payment.provider} env var is preserved as the
 * <em>default</em> provider for callers that do not specify one — so existing
 * clients keep working with no changes.
 */
@Configuration
public class PaymentConfiguration {

    private static final Logger log = LoggerFactory.getLogger(PaymentConfiguration.class);

    @Value("${payme.checkout.success-url:http://localhost:8080/pay/success}")
    private String successUrl;

    @Value("${payme.checkout.cancel-url:http://localhost:8080/pay/cancel}")
    private String cancelUrl;

    @Value("${payme.payment.provider:FAKE}")
    private String defaultProviderName;

    /**
     * @dec(INT-001) PayShap implementation selector — see decisions/INT-001.md
     *
     * Values:
     *  - {@code mock}: register {@link MockPayShapPaymentProvider}
     *  - {@code stitch}: future — register {@code StitchPayShapPaymentProvider}
     *  - {@code off}: do not register PayShap at all
     *
     * Defaults to {@code mock} so the demo works out of the box. Production
     * deployments override to {@code stitch} (or {@code off} until ready).
     */
    @Value("${payme.payshap.mode:mock}")
    private String payShapMode;

    /**
     * Public base URL the mock PayShap "approve" page redirects to. Used by
     * the mock adapter to construct an absolute checkout URL. Tracks
     * {@code payme.checkout.success-url}'s host by default.
     */
    @Value("${payme.payshap.mock-base-url:http://localhost:8080}")
    private String payShapMockBaseUrl;

    /**
     * @dec~ Escape hatch for environments without PayFast credentials —
     * default true (PayFast remains a first-class rail), set false to skip
     * registration entirely so the demo runs PayShap-only without bogus
     * placeholder values reaching PayFast and triggering "merchant_id must be
     * 8 digits" errors. Same opt-out shape as payme.payshap.mode=off.
     */
    @Value("${payme.payfast.enabled:true}")
    private boolean payFastEnabled;

    @Bean
    public CheckoutUrls checkoutUrls() {
        return new CheckoutUrls(successUrl, cancelUrl);
    }

    /**
     * Builds the registry of every adapter wired in this environment.
     *
     * @dec(ARCH-001) Every adapter is constructed at startup, even if it is
     * not the default. Provider beans are stateless and cheap; the upside is
     * that any rail can be selected per request without re-bootstrapping.
     */
    @Bean
    public PaymentProviderRegistry paymentProviderRegistry(
            PayFastConfig payFastConfig,
            HashService hashService,
            ObjectMapper objectMapper,
            com.payme.adapters.provider.payfast.PayFastIpValidator ipValidator
    ) {
        Map<ProviderName, PaymentProvider> providers = new EnumMap<>(ProviderName.class);

        providers.put(ProviderName.FAKE, new FakePaymentProvider());

        // @dec~ PayFast registration is gated by (a) the enabled flag and
        // (b) credential validation. We refuse to register PayFast with
        // unresolved ${VAR} placeholders or obviously-malformed values
        // because PayFast itself rejects them with confusing errors at the
        // hosted-checkout page, and surfacing the problem at startup is
        // strictly more honest. See validatePayFastCredentials below.
        if (payFastEnabled) {
            // @dec~ sanitize first (mutates), then validate (pure). Splitting
            // these means a future caller can validate without surprise side
            // effects, and the read-only validator is easy to unit-test.
            sanitizePayFastConfig(payFastConfig);
            validatePayFastCredentials(payFastConfig);
            providers.put(
                    ProviderName.PAYFAST,
                    new PayFastPaymentProvider(payFastConfig, hashService, objectMapper, ipValidator)
            );
            log.info("PayFast registered (sandbox={}, merchant_id={}***)",
                    payFastConfig.isSandbox(),
                    payFastConfig.getMerchantId().substring(0, Math.min(4, payFastConfig.getMerchantId().length()))
            );
        } else {
            log.info("PayFast registration disabled (payme.payfast.enabled=false)");
        }

        // @dec(INT-001) PayShap mode selector — see decisions/INT-001.md
        switch (payShapMode.toLowerCase()) {
            case "mock" -> {
                providers.put(
                        ProviderName.PAYSHAP,
                        new MockPayShapPaymentProvider(objectMapper, payShapMockBaseUrl)
                );
                log.info("PayShap registered in MOCK mode (base url: {})", payShapMockBaseUrl);
            }
            case "stitch" -> throw new IllegalStateException(
                    "payme.payshap.mode=stitch is reserved for the real Stitch adapter, "
                            + "which has not been built yet (see decisions/INT-001.md)"
            );
            case "off" -> log.info("PayShap registration disabled (payme.payshap.mode=off)");
            default -> throw new IllegalStateException(
                    "Unknown payme.payshap.mode: " + payShapMode + " (expected: mock | stitch | off)"
            );
        }

        ProviderName resolvedDefault = ProviderName.valueOf(defaultProviderName.toUpperCase());
        if (!providers.containsKey(resolvedDefault)) {
            throw new IllegalStateException(
                    "Default provider " + resolvedDefault
                            + " (from payme.payment.provider) is not registered in this environment"
            );
        }

        return new SimplePaymentProviderRegistry(Collections.unmodifiableMap(providers), resolvedDefault);
    }

    /**
     * Pre-validation cleanup: clears the passphrase field if it looks like an
     * unresolved {@code ${VAR}} placeholder so the literal text never reaches
     * the signature computation. This is the only place we mutate
     * {@link PayFastConfig}; keeping it separate from validation means the
     * validator itself is pure and easy to reason about.
     */
    private void sanitizePayFastConfig(PayFastConfig config) {
        String passphrase = config.getPassphrase();
        if (passphrase != null && passphrase.startsWith("${") && passphrase.endsWith("}")) {
            log.warn("PayFast passphrase looks like an unresolved placeholder ({}); treating as unset",
                    passphrase);
            config.setPassphrase(null);
        }
    }

    /**
     * Validates PayFast credentials at startup. Pure — does not mutate
     * {@code config}. Refuses to register the provider with values that
     * PayFast itself would reject — surfacing the problem here is strictly
     * more honest than letting the customer hit "merchant_id must be 8 digits"
     * on PayFast's hosted page.
     *
     * @dec~ Rules mirror PayFast's own format errors: merchant_id is exactly
     * 8 digits, merchant_key is exactly 13 characters. Also catches the
     * common Spring failure mode where ${PAYFAST_MERCHANT_ID} is left as a
     * literal string when the env var is unset and the YAML has no default.
     */
    private void validatePayFastCredentials(PayFastConfig config) {
        java.util.List<String> errors = new java.util.ArrayList<>();

        validateField(errors, "merchant_id", config.getMerchantId(), "PAYFAST_MERCHANT_ID",
                v -> v.matches("\\d{8}"), "must be exactly 8 digits");

        validateField(errors, "merchant_key", config.getMerchantKey(), "PAYFAST_MERCHANT_KEY",
                v -> v.length() == 13, "must be exactly 13 characters");

        if (!errors.isEmpty()) {
            String banner = "=".repeat(72);
            throw new IllegalStateException(
                    "\n" + banner + "\n"
                            + "PayFast credentials are invalid. The application cannot start.\n\n"
                            + String.join("\n", errors) + "\n\n"
                            + "Fix options:\n"
                            + "  1. Set the following env vars in your shell or .env file:\n"
                            + "       PAYFAST_MERCHANT_ID    (8 digits)\n"
                            + "       PAYFAST_MERCHANT_KEY   (13 characters)\n"
                            + "       PAYFAST_PASSPHRASE     (optional)\n"
                            + "     PayFast publishes sandbox test credentials in their developer\n"
                            + "     documentation: https://developers.payfast.co.za/docs\n\n"
                            + "  2. Or disable PayFast for this environment:\n"
                            + "       export PAYFAST_ENABLED=false\n"
                            + "     The pay page will only show PayShap.\n"
                            + banner
            );
        }
    }

    private static void validateField(
            java.util.List<String> errors,
            String fieldName,
            String value,
            String envVarName,
            java.util.function.Predicate<String> formatCheck,
            String formatHint
    ) {
        if (value == null || value.isBlank()) {
            errors.add("  - " + fieldName + " is empty (env var " + envVarName + " is not set)");
            return;
        }
        if (value.startsWith("${") && value.endsWith("}")) {
            errors.add("  - " + fieldName + " is the literal string \"" + value
                    + "\" — env var " + envVarName + " is not set, "
                    + "and application.yml has no default for it");
            return;
        }
        if (!formatCheck.test(value)) {
            String preview = value.length() > 4 ? value.substring(0, 4) + "***" : "***";
            errors.add("  - " + fieldName + " (\"" + preview + "\") " + formatHint);
        }
    }

    /**
     * @dec~ static nested class — registry has no state of its own beyond the
     * map; pulling it into a top-level file would just add a navigation hop
     */
    private static final class SimplePaymentProviderRegistry implements PaymentProviderRegistry {

        private final Map<ProviderName, PaymentProvider> providers;
        private final ProviderName defaultProvider;

        SimplePaymentProviderRegistry(Map<ProviderName, PaymentProvider> providers, ProviderName defaultProvider) {
            this.providers = providers;
            this.defaultProvider = defaultProvider;
        }

        @Override
        public PaymentProvider get(ProviderName name) {
            PaymentProvider provider = providers.get(name);
            if (provider == null) {
                throw new UnknownProviderException(
                        "Payment provider not configured in this environment: " + name
                );
            }
            return provider;
        }

        @Override
        public Set<ProviderName> available() {
            return providers.keySet();
        }

        @Override
        public ProviderName defaultProvider() {
            return defaultProvider;
        }
    }
}
