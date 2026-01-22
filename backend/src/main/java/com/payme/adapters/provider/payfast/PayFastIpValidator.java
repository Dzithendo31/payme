package com.payme.adapters.provider.payfast;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Validates that webhook requests come from PayFast's servers.
 * 
 * PayFast production IP addresses are configured in application.yml.
 * For sandbox mode, IP validation is relaxed to allow testing from localhost/ngrok.
 */
@Component
public class PayFastIpValidator {

    private static final Logger log = LoggerFactory.getLogger(PayFastIpValidator.class);

    private final boolean sandbox;
    private final List<String> allowedIps;

    public PayFastIpValidator(
            @Value("${payfast.sandbox}") boolean sandbox,
            @Value("${payfast.allowed-ips}") List<String> allowedIps) {
        this.sandbox = sandbox;
        this.allowedIps = allowedIps;
        log.info("PayFastIpValidator initialized with {} allowed IPs (sandbox: {})", allowedIps.size(), sandbox);
    }

    /**
     * Validates if the request IP is from PayFast.
     *
     * @param ipAddress IP address of the incoming request
     * @return true if IP is valid (or if in sandbox mode)
     */
    public boolean isValidPayFastIp(String ipAddress) {
        if (sandbox) {
            log.debug("Sandbox mode: Allowing IP {} without validation", ipAddress);
            return true; // In sandbox, allow all IPs for testing with ngrok/localhost
        }

        // Production mode: strict validation
        boolean isValid = allowedIps.contains(ipAddress);

        if (!isValid) {
            log.warn("Invalid PayFast IP address: {}. Expected one of: {}", ipAddress, allowedIps);
        } else {
            log.debug("Valid PayFast IP: {}", ipAddress);
        }

        return isValid;
    }
}
