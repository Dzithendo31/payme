package com.payme.application.eventhandler;

import com.payme.api.sse.InvoiceSseHub;
import com.payme.domain.event.InvoiceCreated;
import com.payme.domain.event.InvoiceExpired;
import com.payme.domain.event.InvoiceMarkedPending;
import com.payme.domain.event.InvoicePaymentFailed;
import com.payme.domain.event.InvoicePaymentSucceeded;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class InvoiceEventHandlerTest {

    private InvoiceEventHandler handler;

    @BeforeEach
    void setUp() {
        // Real hub instance is fine — without subscribers, publish() is a no-op.
        handler = new InvoiceEventHandler(new InvoiceSseHub());
    }

    @Test
    void onInvoiceCreated_handlesEventWithoutError() {
        var event = new InvoiceCreated(
                "evt-1", Instant.now(), "inv-1", "merchant-1",
                new BigDecimal("100.00"), "ZAR", "Test invoice", Instant.now().plusSeconds(3600)
        );

        assertDoesNotThrow(() -> handler.onInvoiceCreated(event));
    }

    @Test
    void onInvoiceMarkedPending_handlesEventWithoutError() {
        var event = new InvoiceMarkedPending(
                "evt-2", Instant.now(), "inv-1", "attempt-1"
        );

        assertDoesNotThrow(() -> handler.onInvoiceMarkedPending(event));
    }

    @Test
    void onInvoicePaymentSucceeded_handlesEventWithoutError() {
        var event = new InvoicePaymentSucceeded(
                "evt-3", Instant.now(), "inv-1", "attempt-1", "PAYFAST"
        );

        assertDoesNotThrow(() -> handler.onInvoicePaymentSucceeded(event));
    }

    @Test
    void onInvoicePaymentFailed_handlesEventWithoutError() {
        var event = new InvoicePaymentFailed(
                "evt-4", Instant.now(), "inv-1", "attempt-1", "PAYFAST", "Insufficient funds"
        );

        assertDoesNotThrow(() -> handler.onInvoicePaymentFailed(event));
    }

    @Test
    void onInvoiceExpired_handlesEventWithoutError() {
        var event = new InvoiceExpired(
                "evt-5", Instant.now(), "inv-1", Instant.now()
        );

        assertDoesNotThrow(() -> handler.onInvoiceExpired(event));
    }

    @Test
    void allHandlerMethods_areAnnotatedWithTransactionalEventListener() throws Exception {
        assertMethodHasAfterCommitListener("onInvoiceCreated", InvoiceCreated.class);
        assertMethodHasAfterCommitListener("onInvoiceMarkedPending", InvoiceMarkedPending.class);
        assertMethodHasAfterCommitListener("onInvoicePaymentSucceeded", InvoicePaymentSucceeded.class);
        assertMethodHasAfterCommitListener("onInvoicePaymentFailed", InvoicePaymentFailed.class);
        assertMethodHasAfterCommitListener("onInvoiceExpired", InvoiceExpired.class);
    }

    private void assertMethodHasAfterCommitListener(String methodName, Class<?> paramType) throws Exception {
        Method method = InvoiceEventHandler.class.getMethod(methodName, paramType);
        TransactionalEventListener annotation = method.getAnnotation(TransactionalEventListener.class);
        assertNotNull(annotation, methodName + " should have @TransactionalEventListener");
        assertEquals(TransactionPhase.AFTER_COMMIT, annotation.phase(),
                methodName + " should use AFTER_COMMIT phase");
    }
}
