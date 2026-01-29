package com.payme.api.dto;

import java.util.List;
import java.util.Map;

public class CheckoutResponse {
    private String checkoutUrl;
    private String attemptId;
    private Map<String, String> formParameters;
    private List<FormParameter> orderedFormParameters;

    public static class FormParameter {
        private String name;
        private String value;

        public FormParameter() {}

        public FormParameter(String name, String value) {
            this.name = name;
            this.value = value;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }
    }

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

    public List<FormParameter> getOrderedFormParameters() {
        return orderedFormParameters;
    }

    public void setOrderedFormParameters(List<FormParameter> orderedFormParameters) {
        this.orderedFormParameters = orderedFormParameters;
    }
}
