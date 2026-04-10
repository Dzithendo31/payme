package com.payme.domain.command;

import com.payme.domain.ProviderName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CommandRecordsTest {

    // --- CreateInvoiceCommand ---

    @Test
    void createInvoiceCommand_implementsCommand() {
        CreateInvoiceCommand cmd = new CreateInvoiceCommand(
                "cmd-1", "merchant-1", new BigDecimal("100.00"), "ZAR", "Test invoice", 24
        );

        assertInstanceOf(Command.class, cmd);
        assertEquals("cmd-1", cmd.commandId());
    }

    @Test
    void createInvoiceCommand_exposesAllFields() {
        CreateInvoiceCommand cmd = new CreateInvoiceCommand(
                "cmd-1", "merchant-1", new BigDecimal("100.00"), "ZAR", "Test invoice", 24
        );

        assertEquals("merchant-1", cmd.merchantId());
        assertEquals(new BigDecimal("100.00"), cmd.amount());
        assertEquals("ZAR", cmd.currency());
        assertEquals("Test invoice", cmd.description());
        assertEquals(24, cmd.expiryHours());
    }

    @Test
    void createInvoiceCommand_equalityByFields() {
        CreateInvoiceCommand a = new CreateInvoiceCommand(
                "cmd-1", "merchant-1", new BigDecimal("100.00"), "ZAR", "Test", 24
        );
        CreateInvoiceCommand b = new CreateInvoiceCommand(
                "cmd-1", "merchant-1", new BigDecimal("100.00"), "ZAR", "Test", 24
        );

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    // --- StartCheckoutCommand ---

    @Test
    void startCheckoutCommand_implementsCommand() {
        StartCheckoutCommand cmd = new StartCheckoutCommand("cmd-2", "inv-1", ProviderName.PAYFAST);

        assertInstanceOf(Command.class, cmd);
        assertEquals("cmd-2", cmd.commandId());
    }

    @Test
    void startCheckoutCommand_exposesAllFields() {
        StartCheckoutCommand cmd = new StartCheckoutCommand("cmd-2", "inv-1", ProviderName.PAYFAST);

        assertEquals("inv-1", cmd.invoiceId());
        assertEquals(ProviderName.PAYFAST, cmd.provider());
    }

    @Test
    void startCheckoutCommand_equalityByFields() {
        StartCheckoutCommand a = new StartCheckoutCommand("cmd-2", "inv-1", ProviderName.PAYFAST);
        StartCheckoutCommand b = new StartCheckoutCommand("cmd-2", "inv-1", ProviderName.PAYFAST);

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void startCheckoutCommand_withDefaultProviderLeavesProviderNull() {
        StartCheckoutCommand cmd = StartCheckoutCommand.withDefaultProvider("cmd-2", "inv-1");

        assertNull(cmd.provider());
        assertEquals("inv-1", cmd.invoiceId());
    }

    // --- ProcessWebhookCommand ---

    @Test
    void processWebhookCommand_implementsCommand() {
        ProcessWebhookCommand cmd = new ProcessWebhookCommand(
                "cmd-3", "PAYFAST", "{\"key\":\"value\"}", Map.of("Content-Type", "application/json")
        );

        assertInstanceOf(Command.class, cmd);
        assertEquals("cmd-3", cmd.commandId());
    }

    @Test
    void processWebhookCommand_exposesAllFields() {
        Map<String, String> headers = Map.of("Content-Type", "application/json", "X-Signature", "abc123");
        ProcessWebhookCommand cmd = new ProcessWebhookCommand(
                "cmd-3", "PAYFAST", "{\"key\":\"value\"}", headers
        );

        assertEquals("PAYFAST", cmd.provider());
        assertEquals("{\"key\":\"value\"}", cmd.rawBody());
        assertEquals(headers, cmd.headers());
    }

    @Test
    void processWebhookCommand_equalityByFields() {
        Map<String, String> headers = Map.of("Content-Type", "application/json");
        ProcessWebhookCommand a = new ProcessWebhookCommand("cmd-3", "PAYFAST", "body", headers);
        ProcessWebhookCommand b = new ProcessWebhookCommand("cmd-3", "PAYFAST", "body", headers);

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    // --- ExpireInvoiceCommand ---

    @Test
    void expireInvoiceCommand_implementsCommand() {
        ExpireInvoiceCommand cmd = new ExpireInvoiceCommand("cmd-4", "inv-2");

        assertInstanceOf(Command.class, cmd);
        assertEquals("cmd-4", cmd.commandId());
    }

    @Test
    void expireInvoiceCommand_exposesAllFields() {
        ExpireInvoiceCommand cmd = new ExpireInvoiceCommand("cmd-4", "inv-2");

        assertEquals("inv-2", cmd.invoiceId());
    }

    @Test
    void expireInvoiceCommand_equalityByFields() {
        ExpireInvoiceCommand a = new ExpireInvoiceCommand("cmd-4", "inv-2");
        ExpireInvoiceCommand b = new ExpireInvoiceCommand("cmd-4", "inv-2");

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    // --- UpdatePaymentStatusCommand ---

    @Test
    void updatePaymentStatusCommand_implementsCommand() {
        UpdatePaymentStatusCommand cmd = new UpdatePaymentStatusCommand(
                "cmd-5", "attempt-1", "SUCCEEDED", "pf-event-123"
        );

        assertInstanceOf(Command.class, cmd);
        assertEquals("cmd-5", cmd.commandId());
    }

    @Test
    void updatePaymentStatusCommand_exposesAllFields() {
        UpdatePaymentStatusCommand cmd = new UpdatePaymentStatusCommand(
                "cmd-5", "attempt-1", "SUCCEEDED", "pf-event-123"
        );

        assertEquals("attempt-1", cmd.attemptId());
        assertEquals("SUCCEEDED", cmd.status());
        assertEquals("pf-event-123", cmd.providerEventId());
    }

    @Test
    void updatePaymentStatusCommand_equalityByFields() {
        UpdatePaymentStatusCommand a = new UpdatePaymentStatusCommand(
                "cmd-5", "attempt-1", "SUCCEEDED", "pf-event-123"
        );
        UpdatePaymentStatusCommand b = new UpdatePaymentStatusCommand(
                "cmd-5", "attempt-1", "SUCCEEDED", "pf-event-123"
        );

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }
}
