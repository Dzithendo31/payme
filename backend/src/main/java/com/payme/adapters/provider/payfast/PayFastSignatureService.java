package com.payme.adapters.provider.payfast;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Service for generating and verifying PayFast MD5 signatures.
 *
 * PayFast signature requirements:
 * 1. Iterate parameters in their original (insertion) order — DO NOT sort.
 *    PayFast's reference PHP implementation iterates the form data array as-is,
 *    so the caller must pass parameters in PayFast's documented field order.
 * 2. Skip the 'signature' field itself.
 * 3. Include ALL fields, even those with empty values (PayFast's ITN sends
 *    `item_description=&custom_str2=&...` and includes them in the signature).
 *    Only null values are skipped.
 * 4. URL-encode each value using PHP urlencode() semantics
 *    (uppercase hex, spaces as '+').
 * 5. Join key=value pairs with '&'.
 * 6. Append '&passphrase=<encoded>' if a passphrase is configured.
 * 7. MD5 the resulting string and return lowercase hex.
 *
 * Callers MUST provide a Map with predictable iteration order
 * (e.g. LinkedHashMap) so the signature is reproducible.
 */
public class PayFastSignatureService {

    private static final Logger log = LoggerFactory.getLogger(PayFastSignatureService.class);

    /**
     * Generates MD5 signature for PayFast request parameters.
     *
     * @param params     Map of parameters in PayFast's expected order (will not be modified)
     * @param passphrase Optional passphrase (can be null or empty)
     * @return MD5 signature as lowercase hex string
     */
    public static String generateSignature(Map<String, String> params, String passphrase) {
        try {
            // Build query string in the caller's iteration order (PayFast does NOT sort).
            // Skip the signature field itself and null values, but INCLUDE empty strings:
            // PayFast's ITN sends fields like `item_description=&custom_str2=` and uses
            // them in its signature computation, so we must too.
            String queryString = params.entrySet().stream()
                    .filter(entry -> !"signature".equals(entry.getKey()))
                    .filter(entry -> entry.getValue() != null)
                    .map(entry -> entry.getKey() + "=" + urlEncode(entry.getValue()))
                    .collect(Collectors.joining("&"));

            // Append passphrase if not empty
            if (passphrase != null && !passphrase.isEmpty()) {
                queryString += "&passphrase=" + urlEncode(passphrase);
            }

            log.debug("Signature string: {}", queryString.replace(passphrase != null ? passphrase : "", "***"));

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
     *
     * PayFast's signature algorithm requires PHP urlencode()-compatible encoding:
     * uppercase hex and spaces as '+' (NOT %20). Java's URLEncoder.encode()
     * (application/x-www-form-urlencoded) already produces this format, so we use it as-is.
     * See https://developers.payfast.co.za/documentation/#checkout-page
     *
     * @param value Value to encode
     * @return URL-encoded value
     */
    private static String urlEncode(String value) {
        try {
            return URLEncoder.encode(value, StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException("UTF-8 encoding not supported", e);
        }
    }
}
