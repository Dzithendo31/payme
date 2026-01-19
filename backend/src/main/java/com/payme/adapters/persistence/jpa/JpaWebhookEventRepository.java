package com.payme.adapters.persistence.jpa;

import com.payme.domain.ProviderName;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface JpaWebhookEventRepository extends JpaRepository<WebhookEventJpaEntity, String> {

    Optional<WebhookEventJpaEntity> findByProviderAndProviderEventId(ProviderName provider, String providerEventId);

    Optional<WebhookEventJpaEntity> findByPayloadHash(String payloadHash);

    boolean existsByProviderAndProviderEventId(ProviderName provider, String providerEventId);

    boolean existsByPayloadHash(String payloadHash);
}
