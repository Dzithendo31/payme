package com.payme.application.eventhandler;

import com.payme.adapters.messaging.SpringEventPublisher;
import com.payme.domain.event.*;
import com.payme.ports.EventPublisher;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.context.ApplicationEventPublisher;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test verifying that @TransactionalEventListener handlers
 * are invoked after events are published through SpringEventPublisher
 * within a committed transaction.
 */
@SpringJUnitConfig(EventHandlerIntegrationTest.TestConfig.class)
class EventHandlerIntegrationTest {

    @Autowired
    private EventPublisher eventPublisher;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private EventCapture eventCapture;

    @Configuration
    static class TestConfig {

        @Bean
        public DataSource dataSource() {
            return new EmbeddedDatabaseBuilder()
                    .setType(EmbeddedDatabaseType.H2)
                    .build();
        }

        @Bean
        public PlatformTransactionManager transactionManager(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }

        @Bean
        public TransactionTemplate transactionTemplate(PlatformTransactionManager txManager) {
            return new TransactionTemplate(txManager);
        }

        @Bean
        public SpringEventPublisher springEventPublisher(ApplicationEventPublisher publisher) {
            return new SpringEventPublisher(publisher);
        }

        @Bean
        public InvoiceEventHandler invoiceEventHandler() {
            return new InvoiceEventHandler();
        }

        @Bean
        public PaymentEventHandler paymentEventHandler() {
            return new PaymentEventHandler();
        }

        @Bean
        public WebhookEventHandler webhookEventHandler() {
            return new WebhookEventHandler();
        }

        @Bean
        public EventCapture eventCapture() {
            return new EventCapture();
        }
    }

    /**
     * Captures events received by @TransactionalEventListener to verify they were invoked.
     */
    static class EventCapture {
        private final List<DomainEvent> receivedEvents = Collections.synchronizedList(new ArrayList<>());

        @TransactionalEventListener
        public void onEvent(DomainEvent event) {
            receivedEvents.add(event);
        }

        List<DomainEvent> getReceivedEvents() {
            return receivedEvents;
        }

        void clear() {
            receivedEvents.clear();
        }
    }

    @Test
    void invoiceCreatedEvent_isReceivedAfterTransactionCommit() {
        eventCapture.clear();

        var event = new InvoiceCreated(
                "evt-1", Instant.now(), "inv-1", "merchant-1",
                new BigDecimal("100.00"), "ZAR", "Test invoice", Instant.now().plusSeconds(3600)
        );

        transactionTemplate.executeWithoutResult(status -> eventPublisher.publish(event));

        assertTrue(eventCapture.getReceivedEvents().stream()
                .anyMatch(e -> e instanceof InvoiceCreated));
    }

    @Test
    void invoicePaymentSucceededEvent_isReceivedAfterTransactionCommit() {
        eventCapture.clear();

        var event = new InvoicePaymentSucceeded(
                "evt-2", Instant.now(), "inv-1", "attempt-1", "PAYFAST"
        );

        transactionTemplate.executeWithoutResult(status -> eventPublisher.publish(event));

        assertTrue(eventCapture.getReceivedEvents().stream()
                .anyMatch(e -> e instanceof InvoicePaymentSucceeded));
    }

    @Test
    void invoicePaymentFailedEvent_isReceivedAfterTransactionCommit() {
        eventCapture.clear();

        var event = new InvoicePaymentFailed(
                "evt-3", Instant.now(), "inv-1", "attempt-1", "PAYFAST", "Insufficient funds"
        );

        transactionTemplate.executeWithoutResult(status -> eventPublisher.publish(event));

        assertTrue(eventCapture.getReceivedEvents().stream()
                .anyMatch(e -> e instanceof InvoicePaymentFailed));
    }

    @Test
    void invoiceExpiredEvent_isReceivedAfterTransactionCommit() {
        eventCapture.clear();

        var event = new InvoiceExpired(
                "evt-4", Instant.now(), "inv-1", Instant.now()
        );

        transactionTemplate.executeWithoutResult(status -> eventPublisher.publish(event));

        assertTrue(eventCapture.getReceivedEvents().stream()
                .anyMatch(e -> e instanceof InvoiceExpired));
    }

    @Test
    void paymentAttemptCreatedEvent_isReceivedAfterTransactionCommit() {
        eventCapture.clear();

        var event = new PaymentAttemptCreated(
                "evt-5", Instant.now(), "attempt-1", "inv-1", "PAYFAST", "ref-123"
        );

        transactionTemplate.executeWithoutResult(status -> eventPublisher.publish(event));

        assertTrue(eventCapture.getReceivedEvents().stream()
                .anyMatch(e -> e instanceof PaymentAttemptCreated));
    }

    @Test
    void paymentAttemptSucceededEvent_isReceivedAfterTransactionCommit() {
        eventCapture.clear();

        var event = new PaymentAttemptSucceeded(
                "evt-6", Instant.now(), "attempt-1", "provider-evt-1"
        );

        transactionTemplate.executeWithoutResult(status -> eventPublisher.publish(event));

        assertTrue(eventCapture.getReceivedEvents().stream()
                .anyMatch(e -> e instanceof PaymentAttemptSucceeded));
    }

    @Test
    void paymentAttemptFailedEvent_isReceivedAfterTransactionCommit() {
        eventCapture.clear();

        var event = new PaymentAttemptFailed(
                "evt-7", Instant.now(), "attempt-1", "provider-evt-2", "Card declined"
        );

        transactionTemplate.executeWithoutResult(status -> eventPublisher.publish(event));

        assertTrue(eventCapture.getReceivedEvents().stream()
                .anyMatch(e -> e instanceof PaymentAttemptFailed));
    }

    @Test
    void webhookReceivedEvent_isReceivedAfterTransactionCommit() {
        eventCapture.clear();

        var event = new WebhookReceived(
                "evt-8", Instant.now(), "wh-1", "PAYFAST", "hash-abc", "{}"
        );

        transactionTemplate.executeWithoutResult(status -> eventPublisher.publish(event));

        assertTrue(eventCapture.getReceivedEvents().stream()
                .anyMatch(e -> e instanceof WebhookReceived));
    }

    @Test
    void webhookProcessedEvent_isReceivedAfterTransactionCommit() {
        eventCapture.clear();

        var event = new WebhookProcessed(
                "evt-9", Instant.now(), "wh-1", "attempt-1"
        );

        transactionTemplate.executeWithoutResult(status -> eventPublisher.publish(event));

        assertTrue(eventCapture.getReceivedEvents().stream()
                .anyMatch(e -> e instanceof WebhookProcessed));
    }

    @Test
    void webhookFailedEvent_isReceivedAfterTransactionCommit() {
        eventCapture.clear();

        var event = new WebhookFailed(
                "evt-10", Instant.now(), "wh-1", "Verification failed"
        );

        transactionTemplate.executeWithoutResult(status -> eventPublisher.publish(event));

        assertTrue(eventCapture.getReceivedEvents().stream()
                .anyMatch(e -> e instanceof WebhookFailed));
    }

    @Test
    void webhookDuplicatedEvent_isReceivedAfterTransactionCommit() {
        eventCapture.clear();

        var event = new WebhookDuplicated(
                "evt-11", Instant.now(), "wh-2", "wh-1"
        );

        transactionTemplate.executeWithoutResult(status -> eventPublisher.publish(event));

        assertTrue(eventCapture.getReceivedEvents().stream()
                .anyMatch(e -> e instanceof WebhookDuplicated));
    }

    @Test
    void multipleEventsPublishedInSameTransaction_areAllDelivered() {
        eventCapture.clear();

        var event1 = new PaymentAttemptCreated(
                "evt-multi-1", Instant.now(), "attempt-1", "inv-1", "FAKE", "ref-1"
        );
        var event2 = new InvoiceMarkedPending(
                "evt-multi-2", Instant.now(), "inv-1", "attempt-1"
        );

        transactionTemplate.executeWithoutResult(status -> {
            eventPublisher.publish(event1);
            eventPublisher.publish(event2);
        });

        assertEquals(2, eventCapture.getReceivedEvents().size());
        assertTrue(eventCapture.getReceivedEvents().stream()
                .anyMatch(e -> e instanceof PaymentAttemptCreated));
        assertTrue(eventCapture.getReceivedEvents().stream()
                .anyMatch(e -> e instanceof InvoiceMarkedPending));
    }
}
