package com.payme.application.eventhandler;

import com.payme.domain.event.PaymentAttemptCreated;
import com.payme.domain.event.PaymentAttemptFailed;
import com.payme.domain.event.PaymentAttemptSucceeded;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.lang.reflect.Method;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class PaymentEventHandlerTest {

    private PaymentEventHandler handler;

    @BeforeEach
    void setUp() {
        handler = new PaymentEventHandler();
    }

    @Test
    void onPaymentAttemptCreated_handlesEventWithoutError() {
        var event = new PaymentAttemptCreated(
                "evt-1", Instant.now(), "attempt-1", "inv-1", "PAYFAST", "ref-123"
        );

        assertDoesNotThrow(() -> handler.onPaymentAttemptCreated(event));
    }

    @Test
    void onPaymentAttemptSucceeded_handlesEventWithoutError() {
        var event = new PaymentAttemptSucceeded(
                "evt-2", Instant.now(), "attempt-1", "provider-evt-1"
        );

        assertDoesNotThrow(() -> handler.onPaymentAttemptSucceeded(event));
    }

    @Test
    void onPaymentAttemptFailed_handlesEventWithoutError() {
        var event = new PaymentAttemptFailed(
                "evt-3", Instant.now(), "attempt-1", "provider-evt-2", "Card declined"
        );

        assertDoesNotThrow(() -> handler.onPaymentAttemptFailed(event));
    }

    @Test
    void allHandlerMethods_areAnnotatedWithTransactionalEventListener() throws Exception {
        assertMethodHasAfterCommitListener("onPaymentAttemptCreated", PaymentAttemptCreated.class);
        assertMethodHasAfterCommitListener("onPaymentAttemptSucceeded", PaymentAttemptSucceeded.class);
        assertMethodHasAfterCommitListener("onPaymentAttemptFailed", PaymentAttemptFailed.class);
    }

    private void assertMethodHasAfterCommitListener(String methodName, Class<?> paramType) throws Exception {
        Method method = PaymentEventHandler.class.getMethod(methodName, paramType);
        TransactionalEventListener annotation = method.getAnnotation(TransactionalEventListener.class);
        assertNotNull(annotation, methodName + " should have @TransactionalEventListener");
        assertEquals(TransactionPhase.AFTER_COMMIT, annotation.phase(),
                methodName + " should use AFTER_COMMIT phase");
    }
}
