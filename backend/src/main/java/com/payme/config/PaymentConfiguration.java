package com.payme.config;

import com.payme.adapters.provider.fake.FakePaymentProvider;
import com.payme.adapters.provider.payfast.PayFastPaymentProvider;
import com.payme.domain.ProviderName;
import com.payme.ports.CheckoutUrls;
import com.payme.ports.HashService;
import com.payme.ports.PaymentProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PaymentConfiguration {

    @Value("${payme.checkout.success-url:http://localhost:8080/pay/success}")
    private String successUrl;

    @Value("${payme.checkout.cancel-url:http://localhost:8080/pay/cancel}")
    private String cancelUrl;

    @Value("${payme.payment.provider:FAKE}")
    private String providerName;

    @Bean
    public CheckoutUrls checkoutUrls() {
        return new CheckoutUrls(successUrl, cancelUrl);
    }

    @Bean
    public PaymentProvider paymentProvider(
            PayFastConfig payFastConfig,
            HashService hashService,
            ObjectMapper objectMapper,
            com.payme.adapters.provider.payfast.PayFastIpValidator ipValidator) {

        ProviderName provider = ProviderName.valueOf(providerName.toUpperCase());

        return switch (provider) {
            case FAKE -> new FakePaymentProvider();
            case PAYFAST -> new PayFastPaymentProvider(payFastConfig, hashService, objectMapper, ipValidator);
        };
    }
}
