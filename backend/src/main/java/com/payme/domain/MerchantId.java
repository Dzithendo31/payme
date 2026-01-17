package com.payme.domain;

import java.util.Objects;
import java.util.UUID;

public class MerchantId {
    private final String value;

    public MerchantId(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("MerchantId cannot be null or empty");
        }
        this.value = value;
    }

    public static MerchantId generate() {
        return new MerchantId(UUID.randomUUID().toString());
    }

    public String getValue() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MerchantId that = (MerchantId) o;
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
