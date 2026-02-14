package com.payme.api;

import com.payme.api.dto.CreateInvoiceRequest;
import com.payme.api.dto.InvoiceResponse;
import com.payme.application.GetInvoiceUseCase;
import com.payme.application.commandhandler.CreateInvoiceCommandHandler;
import com.payme.domain.Invoice;
import com.payme.domain.command.CreateInvoiceCommand;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/invoices")
public class InvoiceController {

    private final CreateInvoiceCommandHandler createInvoiceCommandHandler;
    private final GetInvoiceUseCase getInvoiceUseCase;

    public InvoiceController(
            CreateInvoiceCommandHandler createInvoiceCommandHandler,
            GetInvoiceUseCase getInvoiceUseCase
    ) {
        this.createInvoiceCommandHandler = createInvoiceCommandHandler;
        this.getInvoiceUseCase = getInvoiceUseCase;
    }

    @PostMapping
    public ResponseEntity<InvoiceResponse> createInvoice(
            @Valid @RequestBody CreateInvoiceRequest request,
            HttpServletRequest httpRequest
    ) {
        CreateInvoiceCommand cmd = new CreateInvoiceCommand(
                UUID.randomUUID().toString(),
                request.getMerchantId(),
                request.getAmount(),
                request.getCurrency().name(),
                request.getDescription(),
                request.getExpiryHours()
        );
        Invoice invoice = createInvoiceCommandHandler.handle(cmd);

        String baseUrl = getBaseUrl(httpRequest);
        InvoiceResponse response = InvoiceResponse.fromDomain(invoice, baseUrl);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{invoiceId}")
    public ResponseEntity<InvoiceResponse> getInvoice(
            @PathVariable String invoiceId,
            HttpServletRequest httpRequest
    ) {
        Invoice invoice = getInvoiceUseCase.execute(invoiceId);

        String baseUrl = getBaseUrl(httpRequest);
        InvoiceResponse response = InvoiceResponse.fromDomain(invoice, baseUrl);

        return ResponseEntity.ok(response);
    }

    private String getBaseUrl(HttpServletRequest request) {
        String scheme = request.getScheme();
        String serverName = request.getServerName();
        int serverPort = request.getServerPort();

        String portPart = "";
        if ((scheme.equals("http") && serverPort != 80) ||
            (scheme.equals("https") && serverPort != 443)) {
            portPart = ":" + serverPort;
        }

        return scheme + "://" + serverName + portPart;
    }
}
