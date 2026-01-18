package com.payme.api.dto;

public class CheckoutResponse {
    private String checkoutUrl;
    private String attemptId;

    public CheckoutResponse() {
    }

    public CheckoutResponse(String checkoutUrl, String attemptId) {
        this.checkoutUrl = checkoutUrl;
        this.attemptId = attemptId;
    }

    public String getCheckoutUrl() {
        return checkoutUrl;
    }

    public void setCheckoutUrl(String checkoutUrl) {
        this.checkoutUrl = checkoutUrl;
    }

    public String getAttemptId() {
        return attemptId;
    }

    public void setAttemptId(String attemptId) {
        this.attemptId = attemptId;
    }
}
