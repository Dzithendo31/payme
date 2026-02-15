package com.payme.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StreamPropertiesTest {

    private StreamProperties properties;

    @BeforeEach
    void setUp() {
        properties = new StreamProperties();
    }

    @Test
    void defaults_areSetCorrectly() {
        assertEquals("payme:events:invoice", properties.getInvoiceEvents());
        assertEquals("payme:events:payment", properties.getPaymentEvents());
        assertEquals("payme:events:webhook", properties.getWebhookEvents());
        assertEquals("payme-service", properties.getConsumerGroup());
    }

    @Test
    void streamKeyForAggregateType_returnsInvoiceStream() {
        assertEquals("payme:events:invoice", properties.streamKeyForAggregateType("Invoice"));
    }

    @Test
    void streamKeyForAggregateType_returnsPaymentStream() {
        assertEquals("payme:events:payment", properties.streamKeyForAggregateType("PaymentAttempt"));
    }

    @Test
    void streamKeyForAggregateType_returnsWebhookStream() {
        assertEquals("payme:events:webhook", properties.streamKeyForAggregateType("Webhook"));
    }

    @Test
    void streamKeyForAggregateType_throwsForUnknownType() {
        assertThrows(IllegalArgumentException.class,
                () -> properties.streamKeyForAggregateType("Unknown"));
    }

    @Test
    void setters_overrideDefaults() {
        properties.setInvoiceEvents("custom:invoice");
        properties.setPaymentEvents("custom:payment");
        properties.setWebhookEvents("custom:webhook");
        properties.setConsumerGroup("custom-group");

        assertEquals("custom:invoice", properties.getInvoiceEvents());
        assertEquals("custom:payment", properties.getPaymentEvents());
        assertEquals("custom:webhook", properties.getWebhookEvents());
        assertEquals("custom-group", properties.getConsumerGroup());
    }
}
