package com.payme.adapters.persistence.jpa;

import com.payme.domain.*;
import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(
        name = "webhook_events",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_webhook_provider_event_id", columnNames = {"provider", "provider_event_id"}),
                @UniqueConstraint(name = "uk_webhook_payload_hash", columnNames = {"payload_hash"})
        },
        indexes = {
                @Index(name = "idx_webhook_provider_event_id", columnList = "provider, provider_event_id"),
                @Index(name = "idx_webhook_payload_hash", columnList = "payload_hash")
        }
)
public class WebhookEventJpaEntity {

    @Id
    @Column(name = "id", nullable = false, length = 36)
    private String id;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 50)
    private ProviderName provider;

    @Column(name = "provider_event_id", length = 255)
    private String providerEventId;

    @Column(name = "payload_hash", nullable = false, length = 64)
    private String payloadHash;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @Column(name = "processed_at")
    private Instant processedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "processing_status", nullable = false, length = 20)
    private WebhookProcessingStatus processingStatus;

    @Column(name = "error", columnDefinition = "TEXT")
    private String error;

    @Column(name = "raw_payload", nullable = false, columnDefinition = "TEXT")
    private String rawPayload;

    // Default constructor for JPA
    protected WebhookEventJpaEntity() {
    }

    public WebhookEventJpaEntity(
            String id,
            ProviderName provider,
            String providerEventId,
            String payloadHash,
            Instant receivedAt,
            Instant processedAt,
            WebhookProcessingStatus processingStatus,
            String error,
            String rawPayload
    ) {
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

    public static WebhookEventJpaEntity fromDomain(WebhookEvent event) {
        return new WebhookEventJpaEntity(
                event.getId().getValue(),
                event.getProvider(),
                event.getProviderEventId(),
                event.getPayloadHash(),
                event.getReceivedAt(),
                event.getProcessedAt(),
                event.getProcessingStatus(),
                event.getError(),
                event.getRawPayload()
        );
    }

    public WebhookEvent toDomain() {
        return new WebhookEvent(
                WebhookEventId.of(id),
                provider,
                providerEventId,
                payloadHash,
                receivedAt,
                processedAt,
                processingStatus,
                error,
                rawPayload
        );
    }

    // Getters and setters for JPA
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public ProviderName getProvider() {
        return provider;
    }

    public void setProvider(ProviderName provider) {
        this.provider = provider;
    }

    public String getProviderEventId() {
        return providerEventId;
    }

    public void setProviderEventId(String providerEventId) {
        this.providerEventId = providerEventId;
    }

    public String getPayloadHash() {
        return payloadHash;
    }

    public void setPayloadHash(String payloadHash) {
        this.payloadHash = payloadHash;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }

    public void setReceivedAt(Instant receivedAt) {
        this.receivedAt = receivedAt;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }

    public void setProcessedAt(Instant processedAt) {
        this.processedAt = processedAt;
    }

    public WebhookProcessingStatus getProcessingStatus() {
        return processingStatus;
    }

    public void setProcessingStatus(WebhookProcessingStatus processingStatus) {
        this.processingStatus = processingStatus;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public String getRawPayload() {
        return rawPayload;
    }

    public void setRawPayload(String rawPayload) {
        this.rawPayload = rawPayload;
    }
}
