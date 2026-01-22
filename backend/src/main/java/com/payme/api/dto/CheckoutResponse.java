package com.payme.api.dto;

import java.util.Map;

public class CheckoutResponse {
    private String checkoutUrl;
    private String attemptId;
    private Map<String, String> formParameters;

    public CheckoutResponse() {
    }

    public CheckoutResponse(String checkoutUrl, String attemptId) {
        this.checkoutUrl = checkoutUrl;
        this.attemptId = attemptId;
    }

    public CheckoutResponse(String checkoutUrl, String attemptId, Map<String, String> formParameters) {
        this.checkoutUrl = checkoutUrl;
        this.attemptId = attemptId;
        this.formParameters = formParameters;
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

    public Map<String, String> getFormParameters() {
        return formParameters;
    }

    public void setFormParameters(Map<String, String> formParameters) {
        this.formParameters = formParameters;
    }
}
