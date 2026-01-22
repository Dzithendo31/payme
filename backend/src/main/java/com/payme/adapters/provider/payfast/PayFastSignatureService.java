package com.payme.adapters.provider.payfast;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

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
            params.forEach((k, v) -> log.debug("  {}: '{}' (trimmed: '{}')", k, v, v != null ? v.trim() : "null"));
            
            // Remove empty values (preserve insertion order) and trim
            Map<String, String> filteredParams = params.entrySet().stream()
                    .filter(entry -> entry.getValue() != null && !entry.getValue().trim().isEmpty())
                    .collect(Collectors.toMap(
                            Map.Entry::getKey,
                            entry -> entry.getValue().trim(),
                            (v1, v2) -> v1,
                            LinkedHashMap::new
                    ));

            log.debug("Filtered params keys (in order): {}", filteredParams.keySet());
            
            // Build query string with URL-encoded values
            String queryString = filteredParams.entrySet().stream()
                    .map(entry -> entry.getKey() + "=" + urlEncode(entry.getValue()))
                    .collect(Collectors.joining("&"));

            // Append passphrase if not empty
            if (passphrase != null && !passphrase.isEmpty()) {
                queryString += "&passphrase=" + urlEncode(passphrase);
            }

            String maskedQuery = queryString.replace(passphrase != null ? passphrase : "", "***");
            log.debug("Full signature string: {}", maskedQuery);
            log.debug("=== SIGNATURE GENERATION END ==="

            // Compute MD5 hash
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hash = md.digest(queryString.getBytes(StandardCharsets.UTF_8));

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
            log.debug("Generated signature: {}", signature);

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
            String encoded = URLEncoder.encode(value, StandardCharsets.UTF_8.name())
                    .replace("+", "%20"); // PayFast expects %20 for spaces, not +
            log.debug("URL Encoding: '{}' -> '{}'", value, encoded);
            return encoded;
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException("UTF-8 encoding not supported", e);
        }
    }
}
