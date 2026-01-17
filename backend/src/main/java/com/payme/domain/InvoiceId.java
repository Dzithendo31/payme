package com.payme.domain;

import java.util.Objects;
import java.util.UUID;

public class InvoiceId {
    private final String value;

    public InvoiceId(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("InvoiceId cannot be null or empty");
        }
        this.value = value;
    }

    public static InvoiceId generate() {
        return new InvoiceId(UUID.randomUUID().toString());
    }

    public String getValue() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        InvoiceId invoiceId = (InvoiceId) o;
        return Objects.equals(value, invoiceId.value);
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
