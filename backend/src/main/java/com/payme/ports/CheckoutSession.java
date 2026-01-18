package com.payme.ports;

public class CheckoutSession {
    private final String checkoutUrl;
    private final String providerReference;

    public CheckoutSession(String checkoutUrl, String providerReference) {
        if (checkoutUrl == null || checkoutUrl.trim().isEmpty()) {
            throw new IllegalArgumentException("Checkout URL cannot be null or empty");
        }
        if (providerReference == null || providerReference.trim().isEmpty()) {
            throw new IllegalArgumentException("Provider reference cannot be null or empty");
        }
        this.checkoutUrl = checkoutUrl;
        this.providerReference = providerReference;
    }

    public String getCheckoutUrl() {
        return checkoutUrl;
    }

    public String getProviderReference() {
        return providerReference;
    }
}
