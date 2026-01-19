package com.payme.adapters.persistence.jpa;

import com.payme.domain.ProviderName;
import com.payme.domain.WebhookEvent;
import com.payme.ports.WebhookEventRepository;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class WebhookEventRepositoryAdapter implements WebhookEventRepository {

    private final JpaWebhookEventRepository jpaRepository;

    public WebhookEventRepositoryAdapter(JpaWebhookEventRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public WebhookEvent save(WebhookEvent event) {
        WebhookEventJpaEntity entity = WebhookEventJpaEntity.fromDomain(event);
        WebhookEventJpaEntity saved = jpaRepository.save(entity);
        return saved.toDomain();
    }

    @Override
    public Optional<WebhookEvent> findByProviderEventId(ProviderName provider, String eventId) {
        return jpaRepository.findByProviderAndProviderEventId(provider, eventId)
                .map(WebhookEventJpaEntity::toDomain);
    }

    @Override
    public Optional<WebhookEvent> findByPayloadHash(String hash) {
        return jpaRepository.findByPayloadHash(hash)
                .map(WebhookEventJpaEntity::toDomain);
    }

    @Override
    public boolean existsByProviderEventId(ProviderName provider, String eventId) {
        return jpaRepository.existsByProviderAndProviderEventId(provider, eventId);
    }

    @Override
    public boolean existsByPayloadHash(String hash) {
        return jpaRepository.existsByPayloadHash(hash);
    }
}
