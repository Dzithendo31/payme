package com.payme.ports;

public class CheckoutUrls {
    private final String successUrl;
    private final String cancelUrl;

    public CheckoutUrls(String successUrl, String cancelUrl) {
        if (successUrl == null || successUrl.trim().isEmpty()) {
            throw new IllegalArgumentException("Success URL cannot be null or empty");
        }
        if (cancelUrl == null || cancelUrl.trim().isEmpty()) {
            throw new IllegalArgumentException("Cancel URL cannot be null or empty");
        }
        this.successUrl = successUrl;
        this.cancelUrl = cancelUrl;
    }

    public String getSuccessUrl() {
        return successUrl;
    }

    public String getCancelUrl() {
        return cancelUrl;
    }
}
