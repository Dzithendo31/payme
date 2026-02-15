package com.payme.api;

import com.payme.api.dto.CheckoutResponse;
import com.payme.api.dto.PayPageResponse;
import com.payme.application.GetPayPageDataUseCase;
import com.payme.application.commandhandler.StartCheckoutCommandHandler;
import com.payme.domain.Invoice;
import com.payme.domain.command.StartCheckoutCommand;
import com.payme.ports.Clock;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
}
