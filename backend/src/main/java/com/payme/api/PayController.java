package com.payme.api;

import com.payme.api.dto.CheckoutResponse;
import com.payme.api.dto.PayPageResponse;
import com.payme.application.GetPayPageDataUseCase;
import com.payme.application.StartCheckoutUseCase;
import com.payme.domain.Invoice;
import com.payme.domain.InvoiceId;
import com.payme.ports.Clock;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Controller
@RequestMapping("/pay")
public class PayController {

    private static final Logger log = LoggerFactory.getLogger(PayController.class);

    private final GetPayPageDataUseCase getPayPageDataUseCase;
    private final StartCheckoutUseCase startCheckoutUseCase;
    private final Clock clock;

    public PayController(
            GetPayPageDataUseCase getPayPageDataUseCase,
            StartCheckoutUseCase startCheckoutUseCase,
            Clock clock
    ) {
        this.getPayPageDataUseCase = getPayPageDataUseCase;
        this.startCheckoutUseCase = startCheckoutUseCase;
        this.clock = clock;
    }

    @GetMapping(value = "/{invoiceId:[0-9a-fA-F\\-]{36}}", produces = MediaType.TEXT_HTML_VALUE)
    @ResponseBody
    public ResponseEntity<String> getPayPageHtml(@PathVariable String invoiceId) throws IOException {
        // Serve the static HTML page directly
        ClassPathResource resource = new ClassPathResource("static/pay/index.html");
        String html = StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .body(html);
    }

    @GetMapping(value = "/{invoiceId:[0-9a-fA-F\\-]{36}}", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<PayPageResponse> getPayPageJson(@PathVariable String invoiceId) {
        Invoice invoice = getPayPageDataUseCase.execute(invoiceId);
        PayPageResponse response = PayPageResponse.fromDomain(invoice, clock);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{invoiceId:[0-9a-fA-F\\-]{36}}/checkout")
    @ResponseBody
    public ResponseEntity<CheckoutResponse> startCheckout(@PathVariable String invoiceId) {
        log.info("Received checkout request for invoice: {}", invoiceId);

        InvoiceId id = new InvoiceId(invoiceId);
        StartCheckoutUseCase.CheckoutResult result = startCheckoutUseCase.execute(id);

        CheckoutResponse response = new CheckoutResponse(
                result.getCheckoutUrl(),
                result.getAttemptId()
        );

        log.info("Checkout started successfully for invoice: {}, attempt: {}",
                invoiceId, result.getAttemptId());

        return ResponseEntity.ok(response);
    }
}
