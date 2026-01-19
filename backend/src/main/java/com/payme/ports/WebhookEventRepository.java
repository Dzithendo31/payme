package com.payme.ports;

import com.payme.domain.ProviderName;
import com.payme.domain.WebhookEvent;

import java.util.Optional;

public interface WebhookEventRepository {
    /**
     * Saves a webhook event.
     *
     * @param event The webhook event to save
     * @return The saved webhook event
     */
    WebhookEvent save(WebhookEvent event);

    /**
     * Finds a webhook event by provider and provider event ID.
     *
     * @param provider The provider name
     * @param eventId  The provider's event ID
     * @return Optional containing the webhook event if found
     */
    Optional<WebhookEvent> findByProviderEventId(ProviderName provider, String eventId);

    /**
     * Finds a webhook event by payload hash.
     *
     * @param hash The payload hash
     * @return Optional containing the webhook event if found
     */
    Optional<WebhookEvent> findByPayloadHash(String hash);

    /**
     * Checks if a webhook event exists by provider and provider event ID.
     *
     * @param provider The provider name
     * @param eventId  The provider's event ID
     * @return true if exists, false otherwise
     */
    boolean existsByProviderEventId(ProviderName provider, String eventId);

    /**
     * Checks if a webhook event exists by payload hash.
     *
     * @param hash The payload hash
     * @return true if exists, false otherwise
     */
    boolean existsByPayloadHash(String hash);
}
