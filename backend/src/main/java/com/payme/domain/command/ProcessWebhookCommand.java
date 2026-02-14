package com.payme.domain.command;

import java.util.Map;

public record ProcessWebhookCommand(
        String commandId,
        String provider,
        String rawBody,
        Map<String, String> headers
) implements Command {}
