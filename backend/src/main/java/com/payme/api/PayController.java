package com.payme.api;

import com.payme.api.dto.CheckoutResponse;
import com.payme.api.dto.PayPageResponse;
import com.payme.application.GetPayPageDataUseCase;
import com.payme.application.StartCheckoutUseCase;
import com.payme.domain.Invoice;
import com.payme.domain.InvoiceId;
import com.payme.ports.Clock;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController
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

    @GetMapping("/{invoiceId}")
    public ResponseEntity<PayPageResponse> getPayPage(@PathVariable String invoiceId) {
        Invoice invoice = getPayPageDataUseCase.execute(invoiceId);
        PayPageResponse response = PayPageResponse.fromDomain(invoice, clock);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{invoiceId}/checkout")
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
