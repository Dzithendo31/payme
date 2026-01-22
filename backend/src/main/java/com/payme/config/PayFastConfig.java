package com.payme.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration properties for PayFast payment gateway integration.
 * Maps to 'payfast' prefix in application.yml.
 */
@Configuration
@ConfigurationProperties(prefix = "payfast")
public class PayFastConfig {

    private String merchantId;
    private String merchantKey;
    private String passphrase;
    private boolean sandbox = true;
    private String notifyUrl;

    /**
     * Returns the PayFast process URL based on sandbox mode.
     *
     * @return Sandbox URL if sandbox=true, otherwise production URL
     */
    public String getProcessUrl() {
        return sandbox
                ? "https://sandbox.payfast.co.za/eng/process"
                : "https://www.payfast.co.za/eng/process";
    }

    // Getters and Setters

    public String getMerchantId() {
        return merchantId;
    }

    public void setMerchantId(String merchantId) {
        this.merchantId = merchantId;
    }

    public String getMerchantKey() {
        return merchantKey;
    }

    public void setMerchantKey(String merchantKey) {
        this.merchantKey = merchantKey;
    }

    public String getPassphrase() {
        return passphrase;
    }

    public void setPassphrase(String passphrase) {
        this.passphrase = passphrase;
    }

    public boolean isSandbox() {
        return sandbox;
    }

    public void setSandbox(boolean sandbox) {
        this.sandbox = sandbox;
    }

    public String getNotifyUrl() {
        return notifyUrl;
    }

    public void setNotifyUrl(String notifyUrl) {
        this.notifyUrl = notifyUrl;
    }
}
