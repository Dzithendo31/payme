# PayMe: Event-Driven & Command-Driven Architecture Implementation Plan

## 1. Current State Analysis

PayMe is a payment-link platform built on **hexagonal/clean architecture** with synchronous request-response patterns. The current flow is:

```
HTTP Request → Controller → UseCase → Domain + Repository → HTTP Response
```

All operations (invoice creation, checkout, webhook processing) happen synchronously within a single `@Transactional` boundary. While the domain already has event-like concepts (`CanonicalPaymentEvent`, `WebhookEvent`), these are treated as regular entities — not as first-class events that drive the system.

### Problems with the Current Approach

| Problem | Impact |
|---------|--------|
| Tight coupling between use cases | Adding notifications, analytics, or audit logging requires modifying existing use cases |
| Synchronous webhook processing | PayFast ITN timeout risk if processing is slow; no retry on transient failures |
| No event replay capability | Cannot reconstruct state or debug issues by replaying events |
| Scaling bottleneck | All work happens in the HTTP request thread; cannot scale consumers independently |
| No cross-service communication path | Future microservice extraction would require rearchitecting |

---

## 2. Target Architecture

### 2.1 High-Level Overview

```
                    ┌─────────────────────────────────────────────────────┐
                    │                   API Gateway                       │
                    │          (REST Controllers - Thin Layer)            │
                    └──────────┬──────────────────────┬──────────────────┘
                               │                      │
                          Commands                  Queries
                               │                      │
                    ┌──────────▼──────────┐  ┌───────▼────────────────┐
                    │   Command Bus       │  │   Query Service        │
                    │                     │  │   (Read Models)        │
                    │ CreateInvoiceCmd    │  │                        │
                    │ StartCheckoutCmd    │  │  GET /invoices/{id}    │
                    │ ProcessWebhookCmd   │  │  GET /pay/{id}         │
                    └──────────┬──────────┘  └───────▲────────────────┘
                               │                      │
                    ┌──────────▼──────────┐           │
                    │  Command Handlers   │           │
                    │  (Use Cases)        │           │
                    └──────────┬──────────┘           │
                               │                      │
                        Domain Events                 │
                               │                      │
                    ┌──────────▼──────────────────────┴──────────────┐
                    │              Event Store / Stream               │
                    │                                                 │
                    │  InvoiceCreated | CheckoutStarted | Payment... │
                    └──────────┬──────────┬──────────┬──────────────┘
                               │          │          │
                    ┌──────────▼──┐ ┌─────▼────┐ ┌──▼───────────────┐
                    │  Projection  │ │ Webhook  │ │  Notification    │
                    │  Updater     │ │ Retry    │ │  Service         │
                    │ (Read Model) │ │ Consumer │ │  (Future)        │
                    └─────────────┘ └──────────┘ └──────────────────┘
```

### 2.2 CQRS + Event-Driven Split

- **Commands** (writes): Handled by command handlers that produce domain events
- **Queries** (reads): Served from read-optimized projections
- **Events**: Published to streams/queues, consumed by projections and side-effect handlers

---

## 3. Technology Choice

### Recommended: **Redis Streams** (Phase 1) → **Apache Kafka** (Phase 2, if needed)

| Criteria | Redis Streams | Kafka | RabbitMQ |
|----------|--------------|-------|----------|
| Already in ecosystem | Easy to add | Heavier infra | Medium |
| Consumer groups | Yes | Yes | Yes (competing consumers) |
| Message replay | Yes (by ID) | Yes (by offset) | No (ack = gone) |
| Ordering guarantees | Per-stream | Per-partition | Per-queue |
| Persistence | Configurable | Always | Configurable |
| Complexity | Low | High | Medium |
| Scaling needs (current) | Sufficient | Overkill | Sufficient |

**Rationale**: Redis Streams provide ordering, consumer groups, message acknowledgment, and replay — all requirements for event-driven architecture — without the operational overhead of Kafka. If PayMe scales to high throughput or needs multi-datacenter replication, migrate to Kafka later.

### Alternative: **Spring Cloud Stream with RabbitMQ**

If the team prefers a more Spring-native approach, Spring Cloud Stream with RabbitMQ is a solid alternative. The abstractions below are broker-agnostic, so switching is a configuration change.

---

## 4. Domain Events Catalog

### 4.1 Invoice Aggregate Events

| Event | Trigger | Key Data |
|-------|---------|----------|
| `InvoiceCreated` | Merchant creates invoice | invoiceId, merchantId, amount, currency, description, expiresAt |
| `InvoiceExpired` | System detects expiration | invoiceId, expiredAt |
| `InvoiceMarkedPending` | Checkout starts | invoiceId, paymentAttemptId |
| `InvoicePaymentSucceeded` | Webhook confirms success | invoiceId, paymentAttemptId, provider |
| `InvoicePaymentFailed` | Webhook confirms failure | invoiceId, paymentAttemptId, provider, reason |

### 4.2 Payment Attempt Events

| Event | Trigger | Key Data |
|-------|---------|----------|
| `PaymentAttemptCreated` | Checkout initiated | attemptId, invoiceId, provider, providerReference |
| `PaymentAttemptSucceeded` | Webhook processed | attemptId, providerEventId |
| `PaymentAttemptFailed` | Webhook processed | attemptId, providerEventId, reason |

### 4.3 Webhook Events

| Event | Trigger | Key Data |
|-------|---------|----------|
| `WebhookReceived` | Provider sends notification | webhookId, provider, payloadHash, rawPayload |
| `WebhookProcessed` | Successfully handled | webhookId, correlatedAttemptId |
| `WebhookDuplicated` | Duplicate detected | webhookId, originalWebhookId |
| `WebhookFailed` | Processing error | webhookId, error |

---

## 5. Commands Catalog

| Command | Handler | Produces Events |
|---------|---------|----------------|
| `CreateInvoiceCommand` | `CreateInvoiceCommandHandler` | `InvoiceCreated` |
| `StartCheckoutCommand` | `StartCheckoutCommandHandler` | `PaymentAttemptCreated`, `InvoiceMarkedPending` |
| `ProcessWebhookCommand` | `ProcessWebhookCommandHandler` | `WebhookReceived`, then `WebhookProcessed` / `WebhookFailed` / `WebhookDuplicated` |
| `ExpireInvoiceCommand` | `ExpireInvoiceCommandHandler` | `InvoiceExpired` |
| `UpdatePaymentStatusCommand` | `UpdatePaymentStatusCommandHandler` | `PaymentAttemptSucceeded` / `PaymentAttemptFailed`, `InvoicePaymentSucceeded` / `InvoicePaymentFailed` |

---

## 6. Implementation Phases

### Phase 1: Foundation — Commands, Events, and In-Process Bus

**Goal**: Introduce command/event abstractions without external infrastructure. All dispatching is in-process via Spring's `ApplicationEventPublisher`. This lets us validate the architecture before adding queues.

#### 6.1.1 Define Core Abstractions

**New package**: `com.payme.domain.event`

```java
// Base marker for all domain events
public interface DomainEvent {
    String eventId();        // UUID
    Instant occurredAt();    // Timestamp
    String aggregateId();    // e.g., invoiceId
    String eventType();      // e.g., "InvoiceCreated"
}
```

**New package**: `com.payme.domain.command`

```java
// Base marker for all commands
public interface Command {
    String commandId();      // UUID for idempotency
}
```

#### 6.1.2 Implement Domain Events

**File**: `com/payme/domain/event/InvoiceCreated.java`

```java
public record InvoiceCreated(
    String eventId,
    Instant occurredAt,
    String invoiceId,
    String merchantId,
    BigDecimal amount,
    Currency currency,
    String description,
    Instant expiresAt
) implements DomainEvent {
    @Override public String aggregateId() { return invoiceId; }
    @Override public String eventType() { return "InvoiceCreated"; }
}
```

Similarly for all events in the catalog (Section 4).

#### 6.1.3 Implement Commands

**File**: `com/payme/domain/command/CreateInvoiceCommand.java`

```java
public record CreateInvoiceCommand(
    String commandId,
    String merchantId,
    BigDecimal amount,
    Currency currency,
    String description,
    long expiryHours
) implements Command {}
```

Similarly for all commands in the catalog (Section 5).

#### 6.1.4 Refactor Use Cases into Command Handlers

Transform existing use cases to accept commands and emit events.

**Before** (`CreateInvoiceUseCase`):
```java
public Invoice execute(String merchantId, BigDecimal amount, ...) {
    Invoice invoice = Invoice.create(...);
    return invoiceRepository.save(invoice);
}
```

**After** (`CreateInvoiceCommandHandler`):
```java
@Component
public class CreateInvoiceCommandHandler {

    private final InvoiceRepository invoiceRepository;
    private final EventPublisher eventPublisher;

    @Transactional
    public Invoice handle(CreateInvoiceCommand cmd) {
        Invoice invoice = Invoice.create(...);
        invoice = invoiceRepository.save(invoice);

        eventPublisher.publish(new InvoiceCreated(
            UUID.randomUUID().toString(),
            Instant.now(),
            invoice.getId().value(),
            invoice.getMerchantId().value(),
            invoice.getAmount().amount(),
            invoice.getAmount().currency(),
            invoice.getDescription(),
            invoice.getExpiresAt()
        ));

        return invoice;
    }
}
```

#### 6.1.5 Create EventPublisher Port

**File**: `com/payme/ports/EventPublisher.java`

```java
public interface EventPublisher {
    void publish(DomainEvent event);
    void publishAll(List<DomainEvent> events);
}
```

#### 6.1.6 Create In-Process EventPublisher Adapter (Spring Events)

**File**: `com/payme/adapters/messaging/SpringEventPublisher.java`

```java
@Component
public class SpringEventPublisher implements EventPublisher {

    private final ApplicationEventPublisher springPublisher;

    @Override
    public void publish(DomainEvent event) {
        springPublisher.publishEvent(event);
    }

    @Override
    public void publishAll(List<DomainEvent> events) {
        events.forEach(this::publish);
    }
}
```

#### 6.1.7 Create Event Store (Database-backed)

**Table**: `domain_events`

```sql
CREATE TABLE domain_events (
    event_id       VARCHAR(36)  PRIMARY KEY,
    event_type     VARCHAR(100) NOT NULL,
    aggregate_id   VARCHAR(36)  NOT NULL,
    aggregate_type VARCHAR(50)  NOT NULL,
    occurred_at    TIMESTAMP    NOT NULL,
    payload        JSONB        NOT NULL,
    published      BOOLEAN      DEFAULT FALSE,
    INDEX idx_events_aggregate (aggregate_type, aggregate_id),
    INDEX idx_events_type (event_type),
    INDEX idx_events_unpublished (published) WHERE published = FALSE
);
```

All events are persisted in the same transaction as the aggregate mutation (transactional outbox pattern). This guarantees events are never lost even if the message broker is temporarily down.

#### 6.1.8 Update Controllers to Dispatch Commands

**Before**:
```java
@PostMapping("/api/invoices")
public ResponseEntity<InvoiceResponse> createInvoice(@Valid @RequestBody CreateInvoiceRequest req) {
    Invoice invoice = createInvoiceUseCase.execute(req.merchantId(), ...);
    return ResponseEntity.status(201).body(InvoiceResponse.from(invoice));
}
```

**After**:
```java
@PostMapping("/api/invoices")
public ResponseEntity<InvoiceResponse> createInvoice(@Valid @RequestBody CreateInvoiceRequest req) {
    CreateInvoiceCommand cmd = new CreateInvoiceCommand(
        UUID.randomUUID().toString(),
        req.merchantId(), req.amount(), req.currency(),
        req.description(), req.expiryHours()
    );
    Invoice invoice = createInvoiceCommandHandler.handle(cmd);
    return ResponseEntity.status(201).body(InvoiceResponse.from(invoice));
}
```

#### 6.1.9 Add Event Listeners for Side Effects

```java
@Component
public class InvoiceEventListener {

    @TransactionalEventListener(phase = AFTER_COMMIT)
    public void onInvoiceCreated(InvoiceCreated event) {
        log.info("Invoice created: {}", event.invoiceId());
        // Future: send merchant notification, update analytics, etc.
    }

    @TransactionalEventListener(phase = AFTER_COMMIT)
    public void onPaymentSucceeded(InvoicePaymentSucceeded event) {
        log.info("Payment succeeded for invoice: {}", event.invoiceId());
        // Future: send receipt, trigger fulfillment, etc.
    }
}
```

#### Phase 1 Deliverables

- [ ] `DomainEvent` and `Command` interfaces
- [ ] All event records (Section 4)
- [ ] All command records (Section 5)
- [ ] `EventPublisher` port + `SpringEventPublisher` adapter
- [ ] `domain_events` table + JPA entity + repository
- [ ] Refactored use cases → command handlers
- [ ] Updated controllers dispatching commands
- [ ] `@TransactionalEventListener` side-effect handlers
- [ ] Existing tests passing (backward compatible)

---

### Phase 2: External Message Broker — Redis Streams

**Goal**: Replace in-process Spring events with Redis Streams for durability, replay, and independent consumer scaling.

#### 6.2.1 Add Dependencies

```xml
<!-- pom.xml -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>
```

#### 6.2.2 Redis Streams Configuration

```yaml
# application.yml
spring:
  redis:
    host: ${REDIS_HOST:localhost}
    port: ${REDIS_PORT:6379}

payme:
  streams:
    invoice-events: "payme:events:invoice"
    payment-events: "payme:events:payment"
    webhook-events: "payme:events:webhook"
    consumer-group: "payme-service"
```

#### 6.2.3 Implement Transactional Outbox Pattern

Instead of publishing directly to Redis inside the transaction (which would break atomicity), use the **outbox pattern**:

```
1. Command Handler:
   - Mutate aggregate
   - Save aggregate to DB
   - Save event to `domain_events` table (published = false)
   - Commit transaction

2. Outbox Poller (scheduled):
   - SELECT * FROM domain_events WHERE published = FALSE ORDER BY occurred_at LIMIT 100
   - Publish each event to Redis Stream (XADD)
   - Mark event as published = TRUE
   - Commit
```

**File**: `com/payme/adapters/messaging/OutboxPoller.java`

```java
@Component
public class OutboxPoller {

    @Scheduled(fixedDelay = 500) // Poll every 500ms
    @Transactional
    public void pollAndPublish() {
        List<DomainEventEntity> unpublished = eventStore.findUnpublished(100);
        for (DomainEventEntity entity : unpublished) {
            redisStreamPublisher.publish(entity.toStreamRecord());
            entity.markPublished();
        }
    }
}
```

#### 6.2.4 Redis Stream Publisher

**File**: `com/payme/adapters/messaging/RedisStreamPublisher.java`

```java
@Component
public class RedisStreamPublisher {

    private final StringRedisTemplate redisTemplate;

    public void publish(String streamKey, Map<String, String> fields) {
        redisTemplate.opsForStream().add(
            StreamRecords.mapBacked(fields).withStreamKey(streamKey)
        );
    }
}
```

#### 6.2.5 Redis Stream Consumers

**File**: `com/payme/adapters/messaging/consumers/PaymentStatusConsumer.java`

```java
@Component
public class PaymentStatusConsumer implements StreamListener<String, MapRecord<String, String, String>> {

    @Override
    public void onMessage(MapRecord<String, String, String> message) {
        String eventType = message.getValue().get("eventType");
        String payload = message.getValue().get("payload");

        switch (eventType) {
            case "WebhookProcessed" -> handleWebhookProcessed(payload);
            case "InvoicePaymentSucceeded" -> handlePaymentSuccess(payload);
            // ...
        }

        // Acknowledge message
    }
}
```

#### 6.2.6 Consumer Group Setup

```java
@Configuration
public class RedisStreamConfig {

    @Bean
    Subscription invoiceEventSubscription(
            RedisConnectionFactory factory,
            PaymentStatusConsumer consumer) {

        var options = StreamMessageListenerContainer.StreamMessageListenerContainerOptions
            .builder()
            .pollTimeout(Duration.ofSeconds(2))
            .build();

        var container = StreamMessageListenerContainer.create(factory, options);

        // Create consumer group if not exists
        try {
            redisTemplate.opsForStream().createGroup("payme:events:invoice", "payme-service");
        } catch (RedisSystemException e) { /* group already exists */ }

        container.receive(
            Consumer.from("payme-service", "consumer-1"),
            StreamOffset.create("payme:events:invoice", ReadOffset.lastConsumed()),
            consumer
        );

        container.start();
        return container;
    }
}
```

#### 6.2.7 Update Docker Compose

```yaml
# infra/docker-compose.yml
services:
  postgres:
    # ... existing config

  redis:
    image: redis:7-alpine
    ports:
      - "6379:6379"
    volumes:
      - redis-data:/data
    command: redis-server --appendonly yes  # Persistence

volumes:
  redis-data:
```

#### Phase 2 Deliverables

- [ ] Redis added to `docker-compose.yml`
- [ ] `spring-boot-starter-data-redis` dependency
- [ ] `domain_events` outbox table with `published` flag
- [ ] `OutboxPoller` scheduled task
- [ ] `RedisStreamPublisher` adapter
- [ ] Stream consumer infrastructure (listener container, consumer groups)
- [ ] At least one consumer processing events from Redis
- [ ] Monitoring endpoint for stream lag

---

### Phase 3: Async Webhook Processing with Retry

**Goal**: Decouple webhook reception from processing. The webhook endpoint immediately enqueues the raw payload and returns `200 OK` to the payment provider, while a consumer processes it asynchronously with retries.

This is critical because PayFast has a timeout on ITN responses, and synchronous processing risks timing out.

#### 6.3.1 New Flow

```
PayFast ──POST /webhooks/PAYFAST──▶ WebhookController
                                        │
                                   Save raw payload to DB
                                   Publish WebhookReceived to stream
                                   Return 200 OK immediately
                                        │
                                        ▼
                              Redis Stream: payme:events:webhook
                                        │
                                        ▼
                              WebhookProcessingConsumer
                                   │
                                   ├── Verify signature
                                   ├── Parse into CanonicalPaymentEvent
                                   ├── Check for duplicates
                                   ├── Update PaymentAttempt
                                   ├── Update Invoice
                                   ├── Publish WebhookProcessed
                                   │
                                   └── On failure:
                                       ├── Publish WebhookFailed
                                       └── Message stays in pending (retry via XCLAIM)
```

#### 6.3.2 Dead Letter Queue

After N retries (configurable, default: 5), move failed webhooks to a dead-letter stream:

```
payme:events:webhook           → Main processing stream
payme:events:webhook:dead      → Dead letter stream (manual investigation)
```

#### 6.3.3 Retry Strategy

```java
@Component
public class WebhookRetryScheduler {

    @Scheduled(fixedDelay = 30_000) // Every 30 seconds
    public void reclaimStaleMessages() {
        // Find messages pending for > 60 seconds (consumer crashed or timed out)
        // XCLAIM them back for reprocessing
        // After 5 claims, move to dead letter stream
    }
}
```

#### Phase 3 Deliverables

- [ ] Webhook controller becomes thin (save + enqueue + respond)
- [ ] `WebhookProcessingConsumer` handles full processing
- [ ] Retry logic with configurable max attempts
- [ ] Dead letter stream for failed webhooks
- [ ] Admin endpoint to view/replay dead letters

---

### Phase 4: Invoice Expiration via Scheduled Commands

**Goal**: Replace the current "check-on-read" expiration with a proactive scheduled approach.

#### Current Problem

Invoices expire only when someone reads them (`GetInvoiceUseCase` / `GetPayPageDataUseCase`). If nobody queries an invoice, it stays in `CREATED` or `PENDING` state forever in the database.

#### New Approach

```
┌──────────────────────────┐
│  ExpirationScheduler     │
│  (Runs every 60 seconds) │
└────────────┬─────────────┘
             │
             ▼
   SELECT * FROM invoices
   WHERE status IN ('CREATED', 'PENDING')
     AND expires_at < NOW()
             │
             ▼
   For each expired invoice:
     Dispatch ExpireInvoiceCommand
             │
             ▼
   ExpireInvoiceCommandHandler:
     - Mark invoice as EXPIRED
     - Publish InvoiceExpired event
             │
             ▼
   Consumers react:
     - Update read model
     - (Future) Notify merchant
```

#### Phase 4 Deliverables

- [ ] `ExpirationScheduler` component
- [ ] `ExpireInvoiceCommand` + handler
- [ ] `InvoiceExpired` event published to stream
- [ ] Remove expiration logic from `GetInvoiceUseCase` / `GetPayPageDataUseCase`

---

### Phase 5: Read Model Projections (CQRS Query Side)

**Goal**: Separate read and write models. Queries are served from denormalized, read-optimized projections rebuilt from events.

#### 5.1 Invoice Read Model

```sql
CREATE TABLE invoice_read_model (
    invoice_id      VARCHAR(36) PRIMARY KEY,
    merchant_id     VARCHAR(255) NOT NULL,
    amount          DECIMAL(19,2) NOT NULL,
    currency        VARCHAR(3) NOT NULL,
    description     VARCHAR(500),
    status          VARCHAR(20) NOT NULL,
    expires_at      TIMESTAMP NOT NULL,
    created_at      TIMESTAMP NOT NULL,
    updated_at      TIMESTAMP NOT NULL,
    last_attempt_id VARCHAR(36),
    last_attempt_status VARCHAR(20),
    payment_provider    VARCHAR(50),
    pay_url         VARCHAR(500)
);
```

#### 5.2 Projection Updater

```java
@Component
public class InvoiceProjectionUpdater {

    // Listens to events from the stream and updates the read model

    public void on(InvoiceCreated event) {
        readModelRepo.save(new InvoiceReadModel(event));
    }

    public void on(InvoiceMarkedPending event) {
        readModelRepo.updateStatus(event.invoiceId(), "PENDING");
    }

    public void on(InvoicePaymentSucceeded event) {
        readModelRepo.updateStatus(event.invoiceId(), "SUCCEEDED");
    }

    // ... etc
}
```

#### 5.3 Update Query Endpoints

```java
// GET /api/invoices/{id} → reads from invoice_read_model (no domain logic)
// GET /pay/{id} → reads from invoice_read_model
```

#### Phase 5 Deliverables

- [ ] `invoice_read_model` table
- [ ] `InvoiceProjectionUpdater` event consumer
- [ ] Query endpoints reading from projection
- [ ] Projection rebuild command (replay all events)

---

## 7. Package Structure (Target)

```
com.payme/
├── api/                                # REST Controllers (thin)
│   ├── InvoiceController.java
│   ├── PayController.java
│   ├── WebhookController.java
│   └── dto/
│
├── domain/
│   ├── Invoice.java
│   ├── PaymentAttempt.java
│   ├── WebhookEvent.java
│   ├── CanonicalPaymentEvent.java
│   ├── command/                        # NEW: Command definitions
│   │   ├── Command.java
│   │   ├── CreateInvoiceCommand.java
│   │   ├── StartCheckoutCommand.java
│   │   ├── ProcessWebhookCommand.java
│   │   ├── ExpireInvoiceCommand.java
│   │   └── UpdatePaymentStatusCommand.java
│   ├── event/                          # NEW: Domain event definitions
│   │   ├── DomainEvent.java
│   │   ├── InvoiceCreated.java
│   │   ├── InvoiceExpired.java
│   │   ├── InvoiceMarkedPending.java
│   │   ├── InvoicePaymentSucceeded.java
│   │   ├── InvoicePaymentFailed.java
│   │   ├── PaymentAttemptCreated.java
│   │   ├── PaymentAttemptSucceeded.java
│   │   ├── PaymentAttemptFailed.java
│   │   ├── WebhookReceived.java
│   │   ├── WebhookProcessed.java
│   │   ├── WebhookDuplicated.java
│   │   └── WebhookFailed.java
│   └── model/                          # Value objects (existing)
│
├── application/
│   ├── commandhandler/                 # NEW: Replace use cases
│   │   ├── CreateInvoiceCommandHandler.java
│   │   ├── StartCheckoutCommandHandler.java
│   │   ├── ProcessWebhookCommandHandler.java
│   │   ├── ExpireInvoiceCommandHandler.java
│   │   └── UpdatePaymentStatusCommandHandler.java
│   ├── query/                          # NEW: Query services
│   │   ├── GetInvoiceQueryService.java
│   │   └── GetPayPageQueryService.java
│   └── eventhandler/                   # NEW: React to domain events
│       ├── InvoiceEventHandler.java
│       ├── PaymentEventHandler.java
│       └── WebhookEventHandler.java
│
├── ports/
│   ├── EventPublisher.java             # NEW
│   ├── EventStore.java                 # NEW
│   ├── PaymentProvider.java
│   ├── InvoiceRepository.java
│   ├── PaymentAttemptRepository.java
│   ├── WebhookEventRepository.java
│   └── InvoiceReadModelRepository.java # NEW
│
├── adapters/
│   ├── messaging/                      # NEW
│   │   ├── SpringEventPublisher.java       # Phase 1
│   │   ├── RedisStreamPublisher.java       # Phase 2
│   │   ├── OutboxPoller.java               # Phase 2
│   │   └── consumers/                      # Phase 2+
│   │       ├── PaymentStatusConsumer.java
│   │       ├── WebhookProcessingConsumer.java
│   │       └── InvoiceProjectionConsumer.java
│   ├── persistence/
│   │   ├── jpa/                        # Existing
│   │   ├── eventstore/                 # NEW
│   │   │   ├── DomainEventEntity.java
│   │   │   └── JpaDomainEventRepository.java
│   │   └── readmodel/                  # NEW (Phase 5)
│   │       ├── InvoiceReadModelEntity.java
│   │       └── JpaInvoiceReadModelRepository.java
│   ├── provider/                       # Existing
│   │   ├── payfast/
│   │   └── fake/
│   ├── hashing/                        # Existing
│   └── time/                           # Existing
│
├── config/
│   ├── PaymentConfiguration.java       # Existing
│   ├── RedisStreamConfig.java          # NEW (Phase 2)
│   └── SchedulerConfig.java            # NEW (Phase 4)
│
└── PaymeApplication.java
```

---

## 8. Stream Topology

```
Streams:
  payme:events:invoice          # All invoice lifecycle events
  payme:events:payment          # Payment attempt events
  payme:events:webhook          # Webhook lifecycle events
  payme:events:webhook:dead     # Dead letter for failed webhooks

Consumer Groups:
  payme-service                 # Main application consumer group
    ├── projection-updater      # Updates read models
    ├── webhook-processor       # Processes raw webhooks
    └── notification-handler    # (Future) Sends notifications

  payme-analytics               # (Future) Analytics consumer group
    └── analytics-processor     # Tracks metrics
```

---

## 9. Migration Strategy

### Backward Compatibility

Each phase is **backward compatible** with the previous one. The system remains fully functional after each phase.

| Phase | Breaking Changes | Rollback Strategy |
|-------|-----------------|-------------------|
| Phase 1 | None — same behavior, new structure | Revert to old use cases |
| Phase 2 | None — outbox polls silently | Disable poller, fall back to Spring events |
| Phase 3 | Webhook processing becomes async | Feature flag to switch back to sync |
| Phase 4 | Expiration no longer check-on-read | Keep both paths temporarily |
| Phase 5 | Queries read from projections | Feature flag to read from write model |

### Data Migration

No data migration is needed for existing invoices. The event store starts recording events from the point of deployment. Historical data remains in the existing tables and continues to be queryable.

---

## 10. Observability

### Metrics to Add

```
payme_commands_total{command_type, status}       # Command execution count
payme_events_published_total{event_type}         # Events published
payme_events_consumed_total{event_type, consumer} # Events consumed
payme_stream_lag{stream, consumer_group}         # Consumer lag
payme_outbox_pending_count                       # Unpublished events in outbox
payme_webhook_retry_count{provider}              # Webhook retries
payme_dead_letter_count{stream}                  # Dead letter queue size
```

### Logging

Each event should carry a `correlationId` (set when the command is received) that flows through all downstream events and consumers, enabling end-to-end tracing of a single payment flow.

---

## 11. Testing Strategy

| Test Type | What to Test |
|-----------|-------------|
| Unit | Command handlers produce correct events; domain state transitions |
| Integration | Events flow through Spring event bus; outbox poller publishes to Redis |
| Contract | Event schema compatibility (serialization/deserialization round-trip) |
| End-to-End | Full payment flow with async webhook processing |

### Event Schema Registry

Use a simple JSON Schema or Java record structure to define event contracts. Any change to an event record that removes or renames a field is a breaking change and requires a new event version (e.g., `InvoiceCreatedV2`).

---

## 12. Risks and Mitigations

| Risk | Likelihood | Impact | Mitigation |
|------|-----------|--------|------------|
| Eventual consistency confuses frontend | Medium | Medium | Add polling/SSE for real-time status updates on payment page |
| Outbox poller adds latency (up to 500ms) | Low | Low | Acceptable for payment status updates; tune poll interval |
| Redis unavailability loses events | Low | High | Outbox pattern ensures events are in DB first; Redis is only transport |
| Event schema evolution breaks consumers | Medium | High | Version events; maintain backward-compatible deserialization |
| Over-engineering for current scale | Medium | Medium | Phase 1 uses in-process events only; external infra is Phase 2+ |

---

## 13. Phase Summary and Dependencies

```
Phase 1: Commands + Events + In-Process Bus
    │     (No new infrastructure)
    │
    ▼
Phase 2: Redis Streams + Outbox Pattern
    │     (Requires: Redis)
    │
    ├──▶ Phase 3: Async Webhook Processing
    │     (Requires: Phase 2)
    │
    ├──▶ Phase 4: Scheduled Expiration
    │     (Independent, can run in parallel with Phase 3)
    │
    └──▶ Phase 5: CQRS Read Models
          (Requires: Phase 2)
```

Phases 3, 4, and 5 can be developed in parallel after Phase 2 is complete.
