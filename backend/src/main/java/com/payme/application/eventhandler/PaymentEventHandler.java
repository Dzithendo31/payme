package com.payme.application.eventhandler;

import com.payme.domain.event.PaymentAttemptCreated;
import com.payme.domain.event.PaymentAttemptFailed;
import com.payme.domain.event.PaymentAttemptSucceeded;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class PaymentEventHandler {

    private static final Logger log = LoggerFactory.getLogger(PaymentEventHandler.class);

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPaymentAttemptCreated(PaymentAttemptCreated event) {
        log.info("Payment attempt created: attemptId={}, invoiceId={}, provider={}, providerRef={}",
                event.attemptId(), event.invoiceId(), event.provider(), event.providerReference());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPaymentAttemptSucceeded(PaymentAttemptSucceeded event) {
        log.info("Payment attempt succeeded: attemptId={}, providerEventId={}",
                event.attemptId(), event.providerEventId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPaymentAttemptFailed(PaymentAttemptFailed event) {
        log.info("Payment attempt failed: attemptId={}, providerEventId={}, reason={}",
                event.attemptId(), event.providerEventId(), event.reason());
    }
}
