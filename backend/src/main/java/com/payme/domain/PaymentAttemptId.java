package com.payme.domain;

import java.util.Objects;
import java.util.UUID;

public class PaymentAttemptId {
    private final String value;

    public PaymentAttemptId(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("PaymentAttemptId value cannot be null or empty");
        }
        this.value = value;
    }

    public static PaymentAttemptId generate() {
        return new PaymentAttemptId(UUID.randomUUID().toString());
    }

    public String getValue() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PaymentAttemptId that = (PaymentAttemptId) o;
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
