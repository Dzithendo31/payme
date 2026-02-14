package com.payme.application.eventhandler;

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

@Component
public class InvoiceEventHandler {

    private static final Logger log = LoggerFactory.getLogger(InvoiceEventHandler.class);

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onInvoiceCreated(InvoiceCreated event) {
        log.info("Invoice created: invoiceId={}, merchantId={}, amount={} {}",
                event.invoiceId(), event.merchantId(), event.amount(), event.currency());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onInvoiceMarkedPending(InvoiceMarkedPending event) {
        log.info("Invoice marked pending: invoiceId={}, paymentAttemptId={}",
                event.invoiceId(), event.paymentAttemptId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onInvoicePaymentSucceeded(InvoicePaymentSucceeded event) {
        log.info("Invoice payment succeeded: invoiceId={}, paymentAttemptId={}, provider={}",
                event.invoiceId(), event.paymentAttemptId(), event.provider());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onInvoicePaymentFailed(InvoicePaymentFailed event) {
        log.info("Invoice payment failed: invoiceId={}, paymentAttemptId={}, provider={}, reason={}",
                event.invoiceId(), event.paymentAttemptId(), event.provider(), event.reason());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onInvoiceExpired(InvoiceExpired event) {
        log.info("Invoice expired: invoiceId={}, expiredAt={}",
                event.invoiceId(), event.expiredAt());
    }
}
