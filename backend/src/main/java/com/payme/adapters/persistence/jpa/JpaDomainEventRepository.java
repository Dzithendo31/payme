package com.payme.adapters.persistence.jpa;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface JpaDomainEventRepository extends JpaRepository<DomainEventEntity, String> {

    List<DomainEventEntity> findByPublishedFalseOrderByOccurredAtAsc(Pageable pageable);
}
