# CLAUDE.md — PayMe project guide

Read this before changing code in this repo.

## Running the app — always use `./start.sh`

**Do not run `./backend/mvnw.cmd spring-boot:run` directly.** Spring Boot does
not auto-load `.env`, and the PayFast credential validator (see
`PaymentConfiguration.validatePayFastCredentials`) will fail-fast at startup
if `PAYFAST_MERCHANT_ID` / `PAYFAST_MERCHANT_KEY` aren't in the process
environment.

`start.sh` sources `.env` first, then runs Maven — that's the only path that
works without surprises.

```bash
./start.sh
```

If you genuinely need to bypass the credential check (e.g. demoing PayShap
without PayFast creds), set `PAYFAST_ENABLED=false` and the registry will
skip PayFast registration entirely.

## Tech stack pointers
- **Backend**: Java 17, Spring Boot 3.x, hexagonal architecture
- **Build**: Maven via wrapper at `backend/mvnw.cmd` (Windows) / `backend/mvnw` (Unix)
- **DB**: PostgreSQL 16 in `infra/docker-compose.yml` (volume-backed)
- **Broker**: Redis 7 (also in docker-compose) — used for the event-driven outbox
- **Tests**: `./backend/mvnw.cmd test` — 156 tests as of this writing, all green

## Decisions live in `decisions/`

This project uses Decision-Driven Development (DDD). Tagged code carries
`@dec(ID)` or `@dec~` markers — these are not comments, they are pointers
into the decision record.

**Before changing tagged code:**
1. Read `decisions/_index.md` to find the decision file.
2. Read the file. The "Why" and "Tradeoffs Accepted" sections are the
   important parts.
3. If you would contradict an active decision, surface that explicitly
   instead of silently overwriting.

Active decisions you will run into:
- `ARCH-001` — multi-provider registry over global provider
- `ARCH-002` — SSE for real-time pay-page status updates
- `INT-001` — PayShap is mock-first; the real Stitch adapter is a follow-up

`@dec~` (Tier 0) tags are inline-only — the one-line summary at the call
site is the full record.

## Where the architecture is right now
- **Phase A (multi-rail registry)** — done. `PaymentProviderRegistry` resolves
  by `ProviderName`. Pay page accepts `?provider=` and exposes
  `availableProviders`.
- **Phase B (mock PayShap)** — done. Behind `payme.payshap.mode=mock`,
  registers `MockPayShapPaymentProvider`. Real Stitch adapter is the
  follow-up tracked in `INT-001`.
- **Phase C (SSE pay page)** — done. `InvoiceSseHub` keyed by `invoiceId`,
  fed from `InvoiceEventHandler`'s `@TransactionalEventListener(AFTER_COMMIT)`
  hooks. Single-instance only by design — see `ARCH-002` §"Tradeoffs Accepted"
  before scaling out.

The big-picture roadmap is in `INNOVATION_ROADMAP.md`. The earlier
event-driven migration is documented in `EVENT_DRIVEN_IMPLEMENTATION_PLAN.md`.

## Things that have bitten us before
- **Hibernate `ddl-auto: update` does not rewrite CHECK constraints.**
  Adding a new value to a `@Enumerated(EnumType.STRING)` enum (like
  `ProviderName.PAYSHAP`) requires manually dropping the stale
  `*_provider_check` constraint from `payment_attempts` and `webhook_events`
  in the dev database. A proper Flyway/Liquibase migration is the eventual
  fix; until then, drop the constraint by hand and restart.
- **Don't bypass `start.sh`** (see top of file).

## Platform notes
- Dev environment is **Windows + bash via mingw**. Use Unix shell syntax
  in commands and scripts (forward slashes, `/dev/null`, etc.) — `start.sh`
  itself is bash.
- Prefer `gh pr create` over manual GitHub compare URLs when opening PRs.
