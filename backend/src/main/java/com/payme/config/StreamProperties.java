package com.payme.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "payme.streams")
public class StreamProperties {

    private String invoiceEvents = "payme:events:invoice";
    private String paymentEvents = "payme:events:payment";
    private String webhookEvents = "payme:events:webhook";
    private String consumerGroup = "payme-service";

    public String getInvoiceEvents() {
        return invoiceEvents;
    }

    public void setInvoiceEvents(String invoiceEvents) {
        this.invoiceEvents = invoiceEvents;
    }

    public String getPaymentEvents() {
        return paymentEvents;
    }

    public void setPaymentEvents(String paymentEvents) {
        this.paymentEvents = paymentEvents;
    }

    public String getWebhookEvents() {
        return webhookEvents;
    }

    public void setWebhookEvents(String webhookEvents) {
        this.webhookEvents = webhookEvents;
    }

    public String getConsumerGroup() {
        return consumerGroup;
    }

    public void setConsumerGroup(String consumerGroup) {
        this.consumerGroup = consumerGroup;
    }

    public String streamKeyForAggregateType(String aggregateType) {
        return switch (aggregateType) {
            case "Invoice" -> invoiceEvents;
            case "PaymentAttempt" -> paymentEvents;
            case "Webhook" -> webhookEvents;
            default -> throw new IllegalArgumentException("Unknown aggregate type: " + aggregateType);
        };
    }
}
