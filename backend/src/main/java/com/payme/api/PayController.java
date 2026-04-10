package com.payme.api;

import com.payme.api.dto.CheckoutResponse;
import com.payme.api.dto.PayPageResponse;
import com.payme.application.GetPayPageDataUseCase;
import com.payme.application.commandhandler.StartCheckoutCommandHandler;
import com.payme.domain.Invoice;
import com.payme.domain.command.StartCheckoutCommand;
import com.payme.ports.Clock;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/pay")
public class PayController {

    private static final Logger log = LoggerFactory.getLogger(PayController.class);

    private final GetPayPageDataUseCase getPayPageDataUseCase;
    private final StartCheckoutCommandHandler startCheckoutCommandHandler;
    private final Clock clock;

    public PayController(
            GetPayPageDataUseCase getPayPageDataUseCase,
            StartCheckoutCommandHandler startCheckoutCommandHandler,
            Clock clock
    ) {
        this.getPayPageDataUseCase = getPayPageDataUseCase;
        this.startCheckoutCommandHandler = startCheckoutCommandHandler;
        this.clock = clock;
    }

    @GetMapping(value = "/success", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> paymentSuccess() {
        return ResponseEntity.ok(
                "<!DOCTYPE html>\n"
                        + "<html lang=\"en\"><head><meta charset=\"UTF-8\"/><title>Payment successful</title></head>\n"
                        + "<body><h1>Payment successful</h1>"
                        + "<p>Thank you. Your payment has been received and is being processed.</p>"
                        + "</body></html>\n");
    }

    @GetMapping(value = "/cancel", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> paymentCancelled() {
        return ResponseEntity.ok(
                "<!DOCTYPE html>\n"
                        + "<html lang=\"en\"><head><meta charset=\"UTF-8\"/><title>Payment cancelled</title></head>\n"
                        + "<body><h1>Payment cancelled</h1>"
                        + "<p>Your payment was cancelled. You can try again from the merchant's checkout page.</p>"
                        + "</body></html>\n");
    }

    @GetMapping("/{invoiceId}")
    public ResponseEntity<PayPageResponse> getPayPage(@PathVariable String invoiceId) {
        Invoice invoice = getPayPageDataUseCase.execute(invoiceId);
        PayPageResponse response = PayPageResponse.fromDomain(invoice, clock);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{invoiceId}/checkout")
    public ResponseEntity<CheckoutResponse> startCheckout(@PathVariable String invoiceId) {
        log.info("Received checkout request for invoice: {}", invoiceId);

        StartCheckoutCommand cmd = new StartCheckoutCommand(
                UUID.randomUUID().toString(),
                invoiceId
        );
        StartCheckoutCommandHandler.CheckoutResult result = startCheckoutCommandHandler.handle(cmd);

        CheckoutResponse response = new CheckoutResponse(
                result.getCheckoutUrl(),
                result.getAttemptId()
        );

        log.info("Checkout started successfully for invoice: {}, attempt: {}",
                invoiceId, result.getAttemptId());

        return ResponseEntity.ok(response);
    }

    /**
     * Renders an HTML page that auto-submits a hidden form to the payment provider's
     * hosted checkout (e.g. PayFast). Open this URL in a browser to perform a real
     * end-to-end sandbox test.
     */
    @GetMapping(value = "/{invoiceId}/checkout/redirect", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> redirectToProviderCheckout(@PathVariable String invoiceId) {
        log.info("Received checkout redirect request for invoice: {}", invoiceId);

        StartCheckoutCommand cmd = new StartCheckoutCommand(
                UUID.randomUUID().toString(),
                invoiceId
        );
        StartCheckoutCommandHandler.CheckoutResult result = startCheckoutCommandHandler.handle(cmd);

        String html = renderAutoSubmitForm(result.getCheckoutUrl(), result.getFormParams());

        log.info("Rendered checkout redirect page for invoice: {}, attempt: {}",
                invoiceId, result.getAttemptId());

        return ResponseEntity.ok(html);
    }

    private String renderAutoSubmitForm(String actionUrl, Map<String, String> params) {
        StringBuilder hiddenInputs = new StringBuilder();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            hiddenInputs.append("    <input type=\"hidden\" name=\"")
                    .append(htmlEscape(entry.getKey()))
                    .append("\" value=\"")
                    .append(htmlEscape(entry.getValue()))
                    .append("\"/>\n");
        }

        return "<!DOCTYPE html>\n"
                + "<html lang=\"en\">\n"
                + "<head>\n"
                + "  <meta charset=\"UTF-8\"/>\n"
                + "  <title>Redirecting to payment provider...</title>\n"
                + "</head>\n"
                + "<body onload=\"document.forms[0].submit()\">\n"
                + "  <p>Redirecting to payment provider. If nothing happens, click the button below.</p>\n"
                + "  <form method=\"POST\" action=\"" + htmlEscape(actionUrl) + "\">\n"
                + hiddenInputs
                + "    <button type=\"submit\">Continue to payment</button>\n"
                + "  </form>\n"
                + "</body>\n"
                + "</html>\n";
    }

    private String htmlEscape(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
