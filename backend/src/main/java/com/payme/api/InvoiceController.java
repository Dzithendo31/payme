package com.payme.api;

import com.payme.api.dto.CreateInvoiceRequest;
import com.payme.api.dto.InvoiceResponse;
import com.payme.application.CreateInvoiceUseCase;
import com.payme.application.GetInvoiceUseCase;
import com.payme.domain.Invoice;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/invoices")
public class InvoiceController {

    private final CreateInvoiceUseCase createInvoiceUseCase;
    private final GetInvoiceUseCase getInvoiceUseCase;

    public InvoiceController(
            CreateInvoiceUseCase createInvoiceUseCase,
            GetInvoiceUseCase getInvoiceUseCase
    ) {
        this.createInvoiceUseCase = createInvoiceUseCase;
        this.getInvoiceUseCase = getInvoiceUseCase;
    }

    @PostMapping
    public ResponseEntity<InvoiceResponse> createInvoice(
            @Valid @RequestBody CreateInvoiceRequest request,
            HttpServletRequest httpRequest
    ) {
        Invoice invoice = createInvoiceUseCase.execute(
                request.getMerchantId(),
                request.getAmount(),
                request.getCurrency(),
                request.getDescription(),
                request.getExpiryHours()
        );

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
