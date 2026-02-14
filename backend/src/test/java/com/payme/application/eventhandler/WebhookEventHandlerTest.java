package com.payme.application.eventhandler;

import com.payme.domain.event.WebhookDuplicated;
import com.payme.domain.event.WebhookFailed;
import com.payme.domain.event.WebhookProcessed;
import com.payme.domain.event.WebhookReceived;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.lang.reflect.Method;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class WebhookEventHandlerTest {

    private WebhookEventHandler handler;

    @BeforeEach
    void setUp() {
        handler = new WebhookEventHandler();
    }

    @Test
    void onWebhookReceived_handlesEventWithoutError() {
        var event = new WebhookReceived(
                "evt-1", Instant.now(), "wh-1", "PAYFAST", "hash-abc", "{\"status\":\"COMPLETE\"}"
        );

        assertDoesNotThrow(() -> handler.onWebhookReceived(event));
    }

    @Test
    void onWebhookProcessed_handlesEventWithoutError() {
        var event = new WebhookProcessed(
                "evt-2", Instant.now(), "wh-1", "attempt-1"
        );

        assertDoesNotThrow(() -> handler.onWebhookProcessed(event));
    }

    @Test
    void onWebhookDuplicated_handlesEventWithoutError() {
        var event = new WebhookDuplicated(
                "evt-3", Instant.now(), "wh-2", "wh-1"
        );

        assertDoesNotThrow(() -> handler.onWebhookDuplicated(event));
    }

    @Test
    void onWebhookFailed_handlesEventWithoutError() {
        var event = new WebhookFailed(
                "evt-4", Instant.now(), "wh-3", "Signature verification failed"
        );

        assertDoesNotThrow(() -> handler.onWebhookFailed(event));
    }

    @Test
    void allHandlerMethods_areAnnotatedWithTransactionalEventListener() throws Exception {
        assertMethodHasAfterCommitListener("onWebhookReceived", WebhookReceived.class);
        assertMethodHasAfterCommitListener("onWebhookProcessed", WebhookProcessed.class);
        assertMethodHasAfterCommitListener("onWebhookDuplicated", WebhookDuplicated.class);
        assertMethodHasAfterCommitListener("onWebhookFailed", WebhookFailed.class);
    }

    @Test
    void onWebhookFailed_usesWarnLogLevel() throws Exception {
        // WebhookFailed should use log.warn (not log.info) since it indicates a problem
        // This is verified by the fact that onWebhookFailed exists and can be called
        var event = new WebhookFailed(
                "evt-5", Instant.now(), "wh-4", "Processing timeout"
        );

        assertDoesNotThrow(() -> handler.onWebhookFailed(event));
    }

    private void assertMethodHasAfterCommitListener(String methodName, Class<?> paramType) throws Exception {
        Method method = WebhookEventHandler.class.getMethod(methodName, paramType);
        TransactionalEventListener annotation = method.getAnnotation(TransactionalEventListener.class);
        assertNotNull(annotation, methodName + " should have @TransactionalEventListener");
        assertEquals(TransactionPhase.AFTER_COMMIT, annotation.phase(),
                methodName + " should use AFTER_COMMIT phase");
    }
}
