package com.payme.ports;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class CheckoutSession {
    private final String checkoutUrl;
    private final String providerReference;
    private final Map<String, String> formParams;

    public CheckoutSession(String checkoutUrl, String providerReference) {
        this(checkoutUrl, providerReference, Collections.emptyMap());
    }

    public CheckoutSession(String checkoutUrl, String providerReference, Map<String, String> formParams) {
        if (checkoutUrl == null || checkoutUrl.trim().isEmpty()) {
            throw new IllegalArgumentException("Checkout URL cannot be null or empty");
        }
        if (providerReference == null || providerReference.trim().isEmpty()) {
            throw new IllegalArgumentException("Provider reference cannot be null or empty");
        }
        this.checkoutUrl = checkoutUrl;
        this.providerReference = providerReference;
        this.formParams = formParams == null ? Collections.emptyMap() : new LinkedHashMap<>(formParams);
    }

    public String getCheckoutUrl() {
        return checkoutUrl;
    }

    public String getProviderReference() {
        return providerReference;
    }

    public Map<String, String> getFormParams() {
        return Collections.unmodifiableMap(formParams);
    }
}
