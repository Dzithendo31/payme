package com.payme.adapters.provider.payfast;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;

/**
 * Service for generating and verifying PayFast MD5 signatures.
 * 
 * PayFast signature requirements:
 * 1. Remove empty values from parameters
 * 2. Preserve parameter order (do NOT sort)
 * 3. Build query string with URL-encoded values
 * 4. Append passphrase if not empty
 * 5. Compute MD5 hash
 * 6. Return lowercase hex string
 */
public class PayFastSignatureService {

    private static final Logger log = LoggerFactory.getLogger(PayFastSignatureService.class);

    /**
     * Generates MD5 signature for PayFast request parameters.
     *
     * @param params     Map of parameters (will not be modified)
     * @param passphrase Optional passphrase (can be null or empty)
     * @return MD5 signature as lowercase hex string
     */
    public static String generateSignature(Map<String, String> params, String passphrase) {
        try {
            log.debug("=== SIGNATURE GENERATION START ===");
            log.debug("Input params keys (in order): {}", params.keySet());
            params.forEach((k, v) -> log.debug("  {}: '{}'", k, v));
            
            // Build parameter string exactly as PHP does
            StringBuilder pfOutput = new StringBuilder();
            
            for (Map.Entry<String, String> entry : params.entrySet()) {
                String key = entry.getKey();
                String val = entry.getValue();
                
                // Skip signature field
                if ("signature".equals(key)) {
                    continue;
                }
                
                // PHP: if($val !== '') - check for non-empty after trimming
                if (val != null && !val.trim().isEmpty()) {
                    // PHP: $key .'='. urlencode( trim( $val ) ) .'&'
                    pfOutput.append(key)
                           .append("=")
                           .append(urlEncode(val.trim()))
                           .append("&");
                }
            }
            
            // Remove last ampersand (PHP: substr( $pfOutput, 0, -1 ))
            String getString = pfOutput.length() > 0 
                ? pfOutput.substring(0, pfOutput.length() - 1) 
                : "";
            
            // Append passphrase if not null (PHP: if( $passPhrase !== null ))
            if (passphrase != null && !passphrase.trim().isEmpty()) {
                getString += "&passphrase=" + urlEncode(passphrase.trim());
            }
            
            log.info("Signature string (before MD5): {}", getString);
            
            // Compute MD5 hash (PHP: md5( $getString ))
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hash = md.digest(getString.getBytes(StandardCharsets.UTF_8));
            
            // Convert to lowercase hex string
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            
            String signature = hexString.toString();
            log.info("Generated MD5 signature: {}", signature);
            log.debug("=== SIGNATURE GENERATION END ===");
            
            return signature;
            
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("MD5 algorithm not available", e);
        }
    }

    /**
     * Verifies PayFast signature matches the provided signature.
     *
     * @param params            Map of parameters (excluding signature)
     * @param providedSignature Signature from PayFast
     * @param passphrase        Optional passphrase
     * @return true if signature is valid
     */
    public static boolean verifySignature(Map<String, String> params, String providedSignature, String passphrase) {
        String expectedSignature = generateSignature(params, passphrase);
        boolean isValid = expectedSignature.equalsIgnoreCase(providedSignature);

        if (!isValid) {
            log.warn("Signature mismatch. Expected: {}, Provided: {}", expectedSignature, providedSignature);
        }

        return isValid;
    }

    /**
     * URL-encodes a value for use in signature generation.
     * PayFast requires standard URL encoding.
     *
     * @param value Value to encode
     * @return URL-encoded value
     */
   private static String urlEncode(String value) {
        try {
            return URLEncoder.encode(value, StandardCharsets.UTF_8.toString());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
