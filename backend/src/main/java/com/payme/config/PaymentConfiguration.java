package com.payme.config;

import com.payme.adapters.provider.fake.FakePaymentProvider;
import com.payme.ports.CheckoutUrls;
import com.payme.ports.PaymentProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PaymentConfiguration {

    @Value("${payme.checkout.success-url:http://localhost:8080/pay/success}")
    private String successUrl;

    @Value("${payme.checkout.cancel-url:http://localhost:8080/pay/cancel}")
    private String cancelUrl;

    @Bean
    public CheckoutUrls checkoutUrls() {
        return new CheckoutUrls(successUrl, cancelUrl);
    }

    @Bean
    public PaymentProvider paymentProvider() {
        return new FakePaymentProvider();
    }
}
