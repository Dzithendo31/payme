package com.payme.api;

import com.payme.api.dto.PayPageResponse;
import com.payme.application.GetPayPageDataUseCase;
import com.payme.domain.Invoice;
import com.payme.ports.Clock;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/pay")
public class PayController {

    private final GetPayPageDataUseCase getPayPageDataUseCase;
    private final Clock clock;

    public PayController(GetPayPageDataUseCase getPayPageDataUseCase, Clock clock) {
        this.getPayPageDataUseCase = getPayPageDataUseCase;
        this.clock = clock;
    }

    @GetMapping("/{invoiceId}")
    public ResponseEntity<PayPageResponse> getPayPage(@PathVariable String invoiceId) {
        Invoice invoice = getPayPageDataUseCase.execute(invoiceId);
        PayPageResponse response = PayPageResponse.fromDomain(invoice, clock);
        return ResponseEntity.ok(response);
    }
}
