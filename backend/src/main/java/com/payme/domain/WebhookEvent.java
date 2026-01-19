package com.payme.domain;

import java.time.Instant;
import java.util.Objects;

public class WebhookEvent {
    private final WebhookEventId id;
    private final ProviderName provider;
    private final String providerEventId;
    private final String payloadHash;
    private final Instant receivedAt;
    private Instant processedAt;
    private WebhookProcessingStatus processingStatus;
    private String error;
    private final String rawPayload;

    public WebhookEvent(
            WebhookEventId id,
            ProviderName provider,
            String providerEventId,
            String payloadHash,
            Instant receivedAt,
            Instant processedAt,
            WebhookProcessingStatus processingStatus,
            String error,
            String rawPayload
    ) {
        if (id == null) {
            throw new IllegalArgumentException("Id cannot be null");
        }
        if (provider == null) {
            throw new IllegalArgumentException("Provider cannot be null");
        }
        if (payloadHash == null || payloadHash.trim().isEmpty()) {
            throw new IllegalArgumentException("PayloadHash cannot be null or empty");
        }
        if (receivedAt == null) {
            throw new IllegalArgumentException("ReceivedAt cannot be null");
        }
        if (processingStatus == null) {
            throw new IllegalArgumentException("ProcessingStatus cannot be null");
        }
        if (rawPayload == null || rawPayload.trim().isEmpty()) {
            throw new IllegalArgumentException("RawPayload cannot be null or empty");
        }

        this.id = id;
        this.provider = provider;
        this.providerEventId = providerEventId;
        this.payloadHash = payloadHash;
        this.receivedAt = receivedAt;
        this.processedAt = processedAt;
        this.processingStatus = processingStatus;
        this.error = error;
        this.rawPayload = rawPayload;
    }

    public void markAsProcessed(Instant now) {
        if (processingStatus == WebhookProcessingStatus.PROCESSED) {
            return; // Already processed, idempotent
        }
        this.processingStatus = WebhookProcessingStatus.PROCESSED;
        this.processedAt = now;
        this.error = null;
    }

    public void markAsFailed(Instant now, String errorMessage) {
        if (errorMessage == null || errorMessage.trim().isEmpty()) {
            throw new IllegalArgumentException("Error message cannot be null or empty when marking as failed");
        }
        this.processingStatus = WebhookProcessingStatus.FAILED;
        this.processedAt = now;
        this.error = errorMessage;
    }

    public void markAsDuplicate() {
        this.processingStatus = WebhookProcessingStatus.DUPLICATE;
    }

    // Getters
    public WebhookEventId getId() {
        return id;
    }

    public ProviderName getProvider() {
        return provider;
    }

    public String getProviderEventId() {
        return providerEventId;
    }

    public String getPayloadHash() {
        return payloadHash;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }

    public WebhookProcessingStatus getProcessingStatus() {
        return processingStatus;
    }

    public String getError() {
        return error;
    }

    public String getRawPayload() {
        return rawPayload;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        WebhookEvent that = (WebhookEvent) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
