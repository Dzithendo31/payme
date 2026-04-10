package com.payme.application.eventhandler;

import com.payme.api.sse.InvoiceSseHub;
import com.payme.domain.event.InvoiceCreated;
import com.payme.domain.event.InvoiceExpired;
import com.payme.domain.event.InvoiceMarkedPending;
import com.payme.domain.event.InvoicePaymentFailed;
import com.payme.domain.event.InvoicePaymentSucceeded;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @dec(ARCH-002) Bridges in-process domain events to the SSE hub — see decisions/ARCH-002.md
 *
 * The handler already exists for logging side-effects from the event-driven
 * migration. Adding the SSE bridge here (rather than a separate component)
 * keeps "what happens after an invoice event" in one place and means the
 * fan-out fires after the {@code AFTER_COMMIT} phase — i.e. only once the
 * transaction has actually persisted the new state.
 */
@Component
public class InvoiceEventHandler {

    private static final Logger log = LoggerFactory.getLogger(InvoiceEventHandler.class);

    private final InvoiceSseHub sseHub;

    public InvoiceEventHandler(InvoiceSseHub sseHub) {
        this.sseHub = sseHub;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onInvoiceCreated(InvoiceCreated event) {
        log.info("Invoice created: invoiceId={}, merchantId={}, amount={} {}",
                event.invoiceId(), event.merchantId(), event.amount(), event.currency());
        // CREATED is the initial state; no SSE subscribers exist yet so nothing to push.
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onInvoiceMarkedPending(InvoiceMarkedPending event) {
        log.info("Invoice marked pending: invoiceId={}, paymentAttemptId={}",
                event.invoiceId(), event.paymentAttemptId());
        sseHub.publish(event.invoiceId(), "status", payload(
                event.invoiceId(), "PENDING", event.paymentAttemptId(), null
        ));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onInvoicePaymentSucceeded(InvoicePaymentSucceeded event) {
        log.info("Invoice payment succeeded: invoiceId={}, paymentAttemptId={}, provider={}",
                event.invoiceId(), event.paymentAttemptId(), event.provider());
        sseHub.publish(event.invoiceId(), "status", payload(
                event.invoiceId(), "SUCCEEDED", event.paymentAttemptId(), event.provider()
        ));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onInvoicePaymentFailed(InvoicePaymentFailed event) {
        log.info("Invoice payment failed: invoiceId={}, paymentAttemptId={}, provider={}, reason={}",
                event.invoiceId(), event.paymentAttemptId(), event.provider(), event.reason());
        Map<String, Object> body = payload(
                event.invoiceId(), "FAILED", event.paymentAttemptId(), event.provider()
        );
        body.put("reason", event.reason());
        sseHub.publish(event.invoiceId(), "status", body);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onInvoiceExpired(InvoiceExpired event) {
        log.info("Invoice expired: invoiceId={}, expiredAt={}",
                event.invoiceId(), event.expiredAt());
        Map<String, Object> body = payload(event.invoiceId(), "EXPIRED", null, null);
        body.put("expiredAt", event.expiredAt().toString());
        sseHub.publish(event.invoiceId(), "status", body);
    }

    private static Map<String, Object> payload(
            String invoiceId, String status, String paymentAttemptId, String provider
    ) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("invoiceId", invoiceId);
        body.put("status", status);
        if (paymentAttemptId != null) {
            body.put("paymentAttemptId", paymentAttemptId);
        }
        if (provider != null) {
            body.put("provider", provider);
        }
        return body;
    }
}
