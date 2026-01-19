package com.payme.api;

import com.payme.application.ProcessWebhookUseCase;
import com.payme.domain.ProviderName;
import com.payme.domain.exceptions.WebhookVerificationException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/webhooks")
public class WebhookController {

    private static final Logger log = LoggerFactory.getLogger(WebhookController.class);

    private final ProcessWebhookUseCase processWebhookUseCase;

    public WebhookController(ProcessWebhookUseCase processWebhookUseCase) {
        this.processWebhookUseCase = processWebhookUseCase;
    }

    @PostMapping("/{provider}")
    public ResponseEntity<Map<String, String>> handleWebhook(
            @PathVariable String provider,
            @RequestBody String rawBody,
            HttpServletRequest request
    ) {
        log.info("Received webhook for provider: {}", provider);

        try {
            // Parse provider name
            ProviderName providerName = ProviderName.valueOf(provider.toUpperCase());

            // Extract headers
            Map<String, String> headers = extractHeaders(request);

            // Process webhook
            processWebhookUseCase.processWebhook(providerName, rawBody, headers);

            log.info("Webhook processed successfully");
            return ResponseEntity.ok(Map.of("status", "success"));
        } catch (IllegalArgumentException e) {
            log.error("Invalid provider name: {}", provider, e);
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid provider: " + provider));
        } catch (WebhookVerificationException e) {
            log.error("Webhook verification failed", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "Webhook verification failed: " + e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to process webhook", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Internal server error"));
        }
    }

    private Map<String, String> extractHeaders(HttpServletRequest request) {
        Map<String, String> headers = new HashMap<>();
        Enumeration<String> headerNames = request.getHeaderNames();

        if (headerNames != null) {
            while (headerNames.hasMoreElements()) {
                String headerName = headerNames.nextElement();
                String headerValue = request.getHeader(headerName);
                headers.put(headerName, headerValue);
            }
        }

        return headers;
    }
}
