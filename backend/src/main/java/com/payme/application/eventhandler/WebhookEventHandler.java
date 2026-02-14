package com.payme.application.eventhandler;

import com.payme.domain.event.WebhookDuplicated;
import com.payme.domain.event.WebhookFailed;
import com.payme.domain.event.WebhookProcessed;
import com.payme.domain.event.WebhookReceived;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class WebhookEventHandler {

    private static final Logger log = LoggerFactory.getLogger(WebhookEventHandler.class);

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onWebhookReceived(WebhookReceived event) {
        log.info("Webhook received: webhookId={}, provider={}, payloadHash={}",
                event.webhookId(), event.provider(), event.payloadHash());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onWebhookProcessed(WebhookProcessed event) {
        log.info("Webhook processed: webhookId={}, correlatedAttemptId={}",
                event.webhookId(), event.correlatedAttemptId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onWebhookDuplicated(WebhookDuplicated event) {
        log.info("Webhook duplicated: webhookId={}, originalWebhookId={}",
                event.webhookId(), event.originalWebhookId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onWebhookFailed(WebhookFailed event) {
        log.warn("Webhook failed: webhookId={}, error={}",
                event.webhookId(), event.error());
    }
}
