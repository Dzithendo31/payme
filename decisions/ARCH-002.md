---
id: ARCH-002
tier: 2
title: SSE for real-time pay-page status updates
status: active
date: 2026-04-10
author: Dzithendo31
tags: [architecture, realtime, pay-page, events]
supersedes: null
superseded_by: null
---

## Decision

The customer-facing pay page (`GET /pay/{invoiceId}`) will receive real-time
status updates via **Server-Sent Events** at `GET /pay/{invoiceId}/events`.

A single in-process `InvoiceSseHub` keyed by `invoiceId` holds open
`SseEmitter` connections. The existing `InvoiceEventHandler` (an
`@TransactionalEventListener(AFTER_COMMIT)`) will publish to the hub whenever
`InvoicePaymentSucceeded`, `InvoicePaymentFailed`, `InvoiceMarkedPending`, or
`InvoiceExpired` fire. This piggybacks on the event-driven foundation
delivered in the recent migration — we are not building a new transport, we
are tapping the one we already have.

For the v1 demo, the hub is **single-instance only**. If the same backend is
horizontally scaled, an SSE client connected to instance A will not see events
fired on instance B. This is documented as a known limitation.

## Context

PayMe just completed the event-driven migration. Domain events flow through
both Spring's in-process event bus *and* a Redis Streams outbox. The pay page,
however, is still a static JSON snapshot — a customer who pays via PayShap
must refresh to see the status flip. This is the most visible thing wrong
with the product, and the cheapest one to fix now that events are first-class.

The "innovation demo" we are scoping for stands or falls on this:
*"scan a QR, pay, watch the page flip to PAID without refreshing"*. The
backend already produces the right events at the right times. We just need a
push channel to the browser.

## Alternatives Considered

### WebSocket (e.g. Spring `@MessageMapping` over STOMP)
**Rejected.** Bidirectional, but the pay page only needs server→client. STOMP
adds a broker abstraction we do not need. Browsers, proxies, and load
balancers all handle SSE more uniformly than WebSocket upgrades. SSE works
through plain HTTP/1.1 with no special infra.

### Client-side polling (e.g. fetch every 2s)
**Rejected.** Works, but feels dated and undermines the point of the
event-driven architecture. Also: PayShap confirmations land in <500ms, and a
2s poll is visibly laggy in a live demo. Polling stays in our back pocket as
a fallback if SSE behaves badly behind a customer's corporate proxy.

### Long-poll
**Rejected.** Same downsides as polling, plus harder to reason about
connection lifecycle than SSE.

### Push events through Redis pub/sub *now* for multi-instance fan-out
**Rejected for v1.** PayMe runs as a single backend instance today. Building
the cross-instance fan-out before we have a second instance would be
speculative complexity. The hub interface is designed so that adding a Redis
pub/sub bridge later is purely additive — no consumer or controller change.

## Tradeoffs Accepted

- **Single-instance fan-out only.** A horizontally scaled deploy will see
  some clients miss events. We accept this because (a) we are single-instance,
  and (b) the fix is small and well-understood when needed.
- **Open connections cost a thread per client** unless we configure async
  servlet support. We will set Spring's async executor with a bounded pool;
  pay pages are short-lived (minutes), so the steady-state connection count
  is bounded by concurrent in-progress checkouts.
- **No replay on reconnect.** If a client disconnects and reconnects after a
  status change, they will miss the event. Mitigation: the SSE endpoint sends
  an initial `state` event with the current invoice status on every
  connection, so reconnecting clients get the latest state for free.
- **Browser SSE has a 6-connection-per-origin cap (HTTP/1.1)**. Not relevant
  for a one-tab pay page; would matter for a merchant dashboard, which is
  out of scope here.

## Revisit When

- PayMe is deployed to more than one backend instance — at that point, add a
  Redis pub/sub bridge that subscribes the hub to `payme:sse:invoice:*`
  channels and republishes locally.
- We need server→client push for things outside the pay page (merchant
  dashboard, admin tooling) — at that point, evaluate whether SSE still fits
  or whether the broader use case justifies WebSocket/STOMP.
- Customer reports that SSE is being killed by their corporate proxy — the
  fallback is short-poll, which we keep in reserve.

## References

- `backend/src/main/java/com/payme/api/sse/InvoiceSseHub.java` *(new)*
- `backend/src/main/java/com/payme/api/PayController.java`
- `backend/src/main/java/com/payme/application/eventhandler/InvoiceEventHandler.java`
- `EVENT_DRIVEN_IMPLEMENTATION_PLAN.md` — the foundation this decision builds on
