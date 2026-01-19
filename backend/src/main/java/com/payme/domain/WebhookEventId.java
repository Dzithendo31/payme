package com.payme.domain;

import java.util.Objects;
import java.util.UUID;

public class WebhookEventId {
    private final String value;

    private WebhookEventId(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("WebhookEventId value cannot be null or empty");
        }
        this.value = value;
    }

    public static WebhookEventId generate() {
        return new WebhookEventId(UUID.randomUUID().toString());
    }

    public static WebhookEventId of(String value) {
        return new WebhookEventId(value);
    }

    public String getValue() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        WebhookEventId that = (WebhookEventId) o;
        return Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
