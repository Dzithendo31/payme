package com.payme.api;

import com.payme.adapters.provider.payfast.PayFastSignatureService;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Test controller to manually verify PayFast signature generation.
 * DELETE THIS FILE IN PRODUCTION!
 */
@RestController
@RequestMapping("/api/test/payfast")
public class PayFastTestController {

    @PostMapping("/signature")
    public Map<String, String> testSignature(@RequestBody SignatureTestRequest request) {
        Map<String, String> params = new LinkedHashMap<>();
        
        // Add parameters in order
        if (request.merchantId != null) params.put("merchant_id", request.merchantId);
        if (request.merchantKey != null) params.put("merchant_key", request.merchantKey);
        if (request.returnUrl != null) params.put("return_url", request.returnUrl);
        if (request.cancelUrl != null) params.put("cancel_url", request.cancelUrl);
        if (request.notifyUrl != null) params.put("notify_url", request.notifyUrl);
        if (request.amount != null) params.put("amount", request.amount);
        if (request.itemName != null) params.put("item_name", request.itemName);
        
        // Add any additional custom fields
        if (request.additionalParams != null) {
            request.additionalParams.forEach(params::put);
        }
        
        String signature = PayFastSignatureService.generateSignature(params, request.passphrase);
        
        Map<String, String> response = new LinkedHashMap<>();
        response.put("signature", signature);
        response.put("message", "Check server logs for signature string before MD5 hashing");
        
        return response;
    }
    
    public static class SignatureTestRequest {
        public String merchantId;
        public String merchantKey;
        public String returnUrl;
        public String cancelUrl;
        public String notifyUrl;
        public String amount;
        public String itemName;
        public String passphrase;
        public Map<String, String> additionalParams;
    }
}
