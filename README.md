# PayMe — Payment Link Platform

PayMe is a South-African-focused payment-link platform: merchants create
invoices, share a single PayMe link with a customer, and the customer picks
their preferred local rail (cards via PayFast, instant bank-to-bank via
PayShap, …) on a real-time pay page that flips to **PAID** without a refresh.

Built on clean / hexagonal architecture, an event-driven core (Redis Streams
outbox), and a pluggable `PaymentProvider` registry that makes adding a new
rail a single new adapter class.

## Features

- **Multi-rail checkout** — one PayMe link, customer chooses the payment rail
- **Real-time pay page** — Server-Sent Events stream invoice status changes
  live to every connected browser tab; no polling
- **Multiple payment providers** — pluggable adapters resolved by a registry,
  selectable per request via `?provider=`
- **Webhook-driven status updates** — verified, deduplicated, persisted
- **Event-driven core** — domain events flow through both Spring's in-process
  bus and a Redis Streams outbox
- **Audit trail** — every webhook, payment attempt, and domain event is stored
- **Idempotent webhooks** — automatic deduplication by event ID + payload hash
- **State-machine validation** — invalid invoice state transitions blocked at
  the domain layer
- **Fail-fast configuration** — provider credentials validated at startup with
  actionable error messages

## Supported payment providers

| Rail | Status | Notes |
|---|---|---|
| **PayFast** (cards / EFT / SnapScan / Mobicred) | ✅ Live | Real integration. ITN webhooks signed and IP-verified. |
| **PayShap** (instant bank-to-bank, request-to-pay) | 🟡 Mock | `MockPayShapPaymentProvider` simulates the full RtP UX end-to-end. Real Stitch / Ozow adapter is the next step — see [`decisions/INT-001.md`](decisions/INT-001.md). |
| **Fake provider** | ✅ Dev only | For local testing and stub-driven unit tests. |

Adding a new rail = one new class implementing `PaymentProvider`, registered
in `PaymentConfiguration`. The customer-facing pay page picks it up
automatically via `availableProviders`.

## Architecture

PayMe follows hexagonal / clean architecture with a CQRS-leaning split between
write-side commands (which mutate state and publish events) and a read-side
projection that's served from the same store today but ready to split.

```
┌─────────────────────────────────────────────────────────┐
│                       API Layer                         │
│  (REST controllers, SSE endpoint, demo HTML page)       │
└─────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────┐
│                   Application Layer                     │
│  (Command handlers, event handlers, query services)     │
└─────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────┐
│                     Domain Layer                        │
│  (Invoice, PaymentAttempt, domain events, commands)     │
└─────────────────────────────────────────────────────────┘
                          ↑
┌─────────────────────────────────────────────────────────┐
│                  Ports (interfaces)                     │
│  PaymentProvider, PaymentProviderRegistry, EventStore,  │
│  EventPublisher, repositories                           │
└─────────────────────────────────────────────────────────┘
                          ↑
┌─────────────────────────────────────────────────────────┐
│                   Adapters Layer                        │
│  JPA, PayFast, MockPayShap, Redis Streams outbox,       │
│  in-process Spring event publisher                      │
└─────────────────────────────────────────────────────────┘
```

The event-driven layer is documented in
[`EVENT_DRIVEN_IMPLEMENTATION_PLAN.md`](EVENT_DRIVEN_IMPLEMENTATION_PLAN.md).
The recent multi-rail and real-time pay-page work is summarised in
[`INNOVATION_ROADMAP.md`](INNOVATION_ROADMAP.md).

## Tech stack

- **Backend**: Java 17, Spring Boot 3.x
- **Database**: PostgreSQL 16 (via `infra/docker-compose.yml`)
- **Broker**: Redis 7 (event-driven outbox + future SSE fan-out)
- **Real-time**: Server-Sent Events via `SseEmitter`
- **Build**: Maven (wrapper: `backend/mvnw.cmd` on Windows, `backend/mvnw` on Unix)
- **Tests**: JUnit 5, 156 tests passing

## Quick start

### Prerequisites

- Java 17+
- Docker & Docker Compose
- Maven (use the included wrapper)

### 1. Configure `.env`

Copy or create `.env` at the project root with the credentials you want:

```bash
# Required for PayFast (otherwise set PAYFAST_ENABLED=false)
PAYFAST_MERCHANT_ID=10000100        # 8 digits
PAYFAST_MERCHANT_KEY=46f0cd694581a  # 13 characters
PAYFAST_PASSPHRASE=jt7NOE43FZPn     # optional

# PayShap defaults to mock — no creds needed
PAYSHAP_MODE=mock

# Optional toggles
PAYFAST_ENABLED=true
PAYMENT_PROVIDER=FAKE               # default rail when ?provider= is not specified
```

PayFast publishes sandbox test credentials in their
[developer documentation](https://developers.payfast.co.za/docs).

### 2. Start the app

The supported entry point is `start.sh`. It loads `.env`, brings up Postgres
and Redis via docker-compose, and runs the Spring Boot app:

```bash
./start.sh
```

> **Don't run `./backend/mvnw.cmd spring-boot:run` directly.** Spring Boot does
> not auto-load `.env`, and the PayFast credential validator will fail-fast
> at startup if `PAYFAST_MERCHANT_ID` / `PAYFAST_MERCHANT_KEY` are missing
> from the process environment. See [`CLAUDE.md`](CLAUDE.md) for the rationale.

The API will be available at `http://localhost:8080`.

### 3. Drive the demo

```bash
# Create an invoice
curl -X POST http://localhost:8080/api/invoices \
  -H "Content-Type: application/json" \
  -d '{
    "merchantId": "merchant_001",
    "amount": 99.00,
    "currency": "ZAR",
    "description": "Demo payment",
    "expiryHours": 24
  }'
```

Response includes an `invoiceId`. Open the live pay page:

```
http://localhost:8080/pay/{invoiceId}/page
```

Click **PayShap** → mock approve page → **Approve** → both buttons disable
and the status pill flips to `SUCCEEDED` in real time. Open the URL in a
second browser tab to see SSE fan-out in action.

For PayFast, click **Card / EFT** → you're sent through the auto-submit form
to PayFast's sandbox checkout → on success you land on the redesigned
`/pay/success` page.

## API endpoints

### Merchant API
- `POST /api/invoices` — create a new invoice
- `GET /api/invoices/{id}` — fetch invoice status

### Customer pay flow
- `GET /pay/{invoiceId}` — pay-page JSON (status, amount, `availableProviders`, `defaultProvider`)
- `GET /pay/{invoiceId}/page` — vanilla-JS HTML demo page with provider picker + live status banner
- `GET /pay/{invoiceId}/events` — Server-Sent Events stream of status changes (initial `state` snapshot + subsequent `status` events)
- `POST /pay/{invoiceId}/checkout?provider=PAYFAST|PAYSHAP` — start checkout, returns `{ checkoutUrl, attemptId }` JSON
- `GET /pay/{invoiceId}/checkout/redirect?provider=PAYFAST|PAYSHAP` — server-side redirect: `302` for GET-redirect rails (PayShap), HTML auto-submit form for POST-form rails (PayFast)
- `GET /pay/success`, `GET /pay/cancel` — post-checkout result pages (used as PayFast `return_url` / `cancel_url`)

### PayShap mock (only registered when `PAYSHAP_MODE=mock`)
- `GET /pay/{invoiceId}/payshap-mock?attempt={id}&proxy={proxyId}` — simulated bank "approve request to pay" screen
- `POST /pay/{invoiceId}/payshap-mock/approve?attempt={id}&outcome=APPROVED|DECLINED` — simulates the bank confirming the payment, posts a synthetic webhook through the standard handler

### Webhook API
- `POST /webhooks/{provider}` — receives payment notifications from the gateway
  - `/webhooks/PAYFAST` — PayFast ITN
  - `/webhooks/PAYSHAP` — used by both real adapters (future) and the mock approve flow
  - `/webhooks/FAKE` — fake-provider webhooks for tests

### Operations
- `GET /health` — application health
- `GET /actuator/health` — detailed health
- `GET /api/admin/stream-lag` — Redis stream consumer lag (custom endpoint)

## Environment variables

### Database
- `SPRING_DATASOURCE_URL` (default `jdbc:postgresql://localhost:5432/payme`)
- `SPRING_DATASOURCE_USERNAME` (default `payme`)
- `SPRING_DATASOURCE_PASSWORD` (default `payme`)

### Redis
- `REDIS_HOST` (default `localhost`)
- `REDIS_PORT` (default `6379`)

### Payment routing
- `PAYMENT_PROVIDER` — default rail when no `?provider=` is supplied: `FAKE` | `PAYFAST` | `PAYSHAP` (default `FAKE`)

### PayFast
- `PAYFAST_ENABLED` — `true` | `false` (default `true`). When `false`, the PayFast adapter is not registered and the pay page won't show the Card / EFT button — useful for demos without sandbox creds.
- `PAYFAST_MERCHANT_ID` — exactly 8 digits. Validated at startup.
- `PAYFAST_MERCHANT_KEY` — exactly 13 characters. Validated at startup.
- `PAYFAST_PASSPHRASE` — optional but recommended for signature security.
- `PAYFAST_SANDBOX` — `true` | `false` (default `true`).
- `PAYFAST_NOTIFY_URL` — webhook URL for ITN notifications (default `http://localhost:8080/webhooks/PAYFAST`).

### PayShap
- `PAYSHAP_MODE` — `mock` | `stitch` | `off` (default `mock`)
  - `mock` — register `MockPayShapPaymentProvider` (the simulated request-to-pay flow)
  - `stitch` — reserved for the future real Stitch adapter; throws on startup until built
  - `off` — do not register PayShap at all
- `PAYSHAP_MOCK_BASE_URL` — public base URL the mock approve page redirects to (default `http://localhost:8080`)

### Checkout URLs
- `PAYME_CHECKOUT_SUCCESS_URL` (default `http://localhost:8080/pay/success`)
- `PAYME_CHECKOUT_CANCEL_URL` (default `http://localhost:8080/pay/cancel`)

## Project structure

```
payme/
├── backend/                          # Java / Spring Boot application
│   ├── src/main/java/com/payme/
│   │   ├── api/                      # REST controllers, DTOs, demo HTML page
│   │   │   ├── PayController.java        # /pay/* — JSON, HTML, SSE
│   │   │   ├── PayShapMockController.java# /pay/{id}/payshap-mock — mock RtP page
│   │   │   ├── WebhookController.java    # /webhooks/{provider}
│   │   │   ├── InvoiceController.java    # /api/invoices
│   │   │   └── sse/InvoiceSseHub.java    # In-process SSE fan-out
│   │   ├── application/
│   │   │   ├── commandhandler/       # Command handlers (CreateInvoice, StartCheckout, ProcessWebhook)
│   │   │   ├── eventhandler/         # @TransactionalEventListener side effects
│   │   │   └── *.UseCase.java        # Query-side use cases
│   │   ├── domain/                   # Aggregates, value objects, commands, events
│   │   │   ├── command/
│   │   │   └── event/
│   │   ├── ports/                    # PaymentProvider, PaymentProviderRegistry, repositories, EventStore
│   │   ├── adapters/                 # JPA, PayFast, MockPayShap, Redis Streams outbox, hashing, time
│   │   │   ├── persistence/jpa/
│   │   │   ├── provider/payfast/
│   │   │   ├── provider/payshap/         # MockPayShapPaymentProvider
│   │   │   ├── provider/fake/
│   │   │   └── messaging/                # OutboxPoller, RedisStreamPublisher, consumers/
│   │   └── config/                   # PaymentConfiguration (registry + validation), RedisStreamConfig
│   └── src/main/resources/application.yml
│
├── infra/                            # docker-compose for postgres + redis
│   └── docker-compose.yml
│
├── decisions/                        # Decision-driven development records (DDD)
│   ├── _index.md                     # aggregated index of all tiered decisions
│   ├── ARCH-001.md                   # multi-provider registry over global provider
│   ├── ARCH-002.md                   # SSE for real-time pay-page status updates
│   └── INT-001.md                    # PayShap mock-first, real Stitch as follow-up
│
├── docs/
│   └── PAYFAST_SETUP.md
│
├── requests/                         # Bruno API client collection
├── start.sh                          # canonical entry point — loads .env, brings up infra, runs the app
├── CLAUDE.md                         # per-project agent guide (start.sh rule, DDD pointers)
├── INNOVATION_ROADMAP.md             # human-readable companion to the ADRs
├── EVENT_DRIVEN_IMPLEMENTATION_PLAN.md
├── progress.md
└── README.md
```

## Decisions

PayMe uses **Decision-Driven Development**: code that embodies a non-obvious
choice carries an `@dec(ID)` tag pointing into a decision record under
`decisions/`. Read [`decisions/_index.md`](decisions/_index.md) for the index;
the active records are:

| ID | Title |
|---|---|
| [`ARCH-001`](decisions/ARCH-001.md) | Multi-provider registry over global provider |
| [`ARCH-002`](decisions/ARCH-002.md) | SSE for real-time pay-page status updates |
| [`INT-001`](decisions/INT-001.md) | PayShap mock-first, Stitch as future provider |

Before changing any code carrying an `@dec(...)` tag, read the corresponding
record. Inline `@dec~` (Tier 0) tags are local rationale — the one-line note
at the call site is the full record.

## Testing

```bash
./backend/mvnw.cmd test    # 156 tests
```

There are no Testcontainers-backed integration tests yet — the existing test
suite uses Spring's H2 + in-memory stubs for repositories. Adding real
Postgres / Redis containers is a tracked follow-up.

## Security

- **Webhook signature verification** — PayFast ITNs verified by MD5 signature; mock PayShap is intentionally unsigned and only registered when `PAYSHAP_MODE=mock`.
- **IP allowlist** — production webhooks validated against PayFast's documented IP range.
- **HTTPS required** — production webhook URLs must use HTTPS.
- **Idempotent processing** — duplicate webhooks rejected by event ID and payload hash.
- **State-machine validation** — invalid invoice state transitions blocked at the domain level.
- **Startup credential validation** — PayFast `merchant_id` / `merchant_key` are checked for shape (8 digits / 13 chars) and unresolved `${VAR}` placeholders. Bad config fails the app at startup with an actionable error.
- **SSE single-instance only** — see [`decisions/ARCH-002.md`](decisions/ARCH-002.md) §"Tradeoffs Accepted" before scaling out horizontally.

## Contributing

This is a personal learning project demonstrating:
- Hexagonal / clean architecture in a real payment context
- Domain-driven design with first-class commands and events
- Event-driven architecture with a transactional outbox + Redis Streams
- CQRS-leaning split between writes and reads
- Real provider integrations (PayFast) alongside faithful mocks (PayShap)
- Decision-driven development as a way to capture *why* alongside *what*

If you're contributing or pairing on this with an AI agent, also read
[`CLAUDE.md`](CLAUDE.md) — it's the per-project guide that captures the
durable rules (always start via `./start.sh`, the decision system, common
gotchas).

## License

[Add license here]

## Support

- [PayFast developer documentation](https://developers.payfast.co.za/docs)
- [PayFast Setup Guide](docs/PAYFAST_SETUP.md)
- For application issues, check the application logs — startup failures (missing PayFast creds, etc.) print actionable error messages with two fix options.
