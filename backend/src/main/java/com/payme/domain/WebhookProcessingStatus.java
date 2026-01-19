package com.payme.domain;

public enum WebhookProcessingStatus {
    RECEIVED,   // Webhook stored but not yet processed
    PROCESSED,  // Successfully processed
    FAILED,     // Processing failed with error
    DUPLICATE   // Detected as duplicate, skipped
}
