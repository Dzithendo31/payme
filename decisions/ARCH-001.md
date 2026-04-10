---
id: ARCH-001
tier: 2
title: Multi-provider registry over global provider
status: active
date: 2026-04-10
author: Dzithendo31
tags: [architecture, payments, hexagonal]
supersedes: null
superseded_by: null
---

## Decision

PayMe will resolve `PaymentProvider` instances through a `PaymentProviderRegistry`
keyed by `ProviderName` rather than injecting a single global `PaymentProvider`
bean selected at startup via `payme.payment.provider`.

The registry exposes:
- `PaymentProvider get(ProviderName name)` — resolve by enum
- `Set<ProviderName> available()` — list providers wired in this environment
- `ProviderName defaultProvider()` — fallback when the caller does not specify one

`StartCheckoutCommand` carries the chosen `ProviderName`. Controllers accept
`?provider=` and fall back to `registry.defaultProvider()` if absent. The
`payme.payment.provider` property continues to control the *default*, not the
*only* provider.

## Context

PayMe is a payment-link platform. The product differentiator we are betting on
is **one PayMe link, multiple South African rails** — a customer should land on
`/pay/{id}` and choose between PayFast (cards) and PayShap (instant bank-to-bank
via ProxyID). That is impossible if the JVM only knows about one provider at a
time.

The hexagonal port `ports/PaymentProvider.java` already abstracts the provider
contract cleanly. The only thing forcing single-provider behaviour is
`config/PaymentConfiguration.java`, which uses a `switch` to construct exactly
one bean. Replacing that switch with a registry is a small, contained change
that unlocks the multi-rail product story without touching the domain layer.

This decision is the *foundation* for INT-001 (PayShap adapter) and ARCH-002
(SSE pay page). Both assume the registry exists.

## Alternatives Considered

### Keep global single provider, add `PAYSHAP` as a third enum value
**Rejected.** Solves nothing: a deploy can still only speak one rail at a time.
We would have to stand up two backend instances with different env vars to
demo the multi-rail product, which destroys the demo's whole point.

### Strategy resolved at runtime by reading the database (per-merchant config)
**Rejected for now.** The right end-state, but premature. We have one merchant
in the demo. The registry pattern is forward-compatible: a per-merchant
strategy resolver can sit *on top of* the registry later without changing the
ports or command shape.

### Spring profiles (`@Profile("payfast")`, `@Profile("payshap")`)
**Rejected.** Profiles are env-time, not request-time. Does not support a
customer choosing a rail at checkout.

## Tradeoffs Accepted

- Slightly more boilerplate at startup: every adapter is constructed even if
  it might never be used in this environment. Acceptable — payment provider
  beans are stateless and cheap.
- The `StartCheckoutCommand` shape changes (gains `provider`). All existing
  callers must be updated. Acceptable — there is exactly one caller today.
- We must defend against `provider=PAYFAST` being requested when PayFast is
  not configured in this env. The registry will throw a typed exception that
  the controller maps to HTTP 400.

## Revisit When

- We need per-merchant provider rules (some merchants get PayFast only, others
  get PayShap-only). At that point, replace direct registry calls with a
  `MerchantProviderPolicy` that *uses* the registry.
- We add provider-specific checkout flows that cannot be expressed through the
  current `PaymentProvider` port — at which point the port itself needs to
  evolve and this decision should be re-examined.

## References

- `backend/src/main/java/com/payme/ports/PaymentProvider.java`
- `backend/src/main/java/com/payme/ports/PaymentProviderRegistry.java` *(new)*
- `backend/src/main/java/com/payme/config/PaymentConfiguration.java`
- `backend/src/main/java/com/payme/application/commandhandler/StartCheckoutCommandHandler.java`
- `backend/src/main/java/com/payme/domain/command/StartCheckoutCommand.java`
- `backend/src/main/java/com/payme/api/PayController.java`
