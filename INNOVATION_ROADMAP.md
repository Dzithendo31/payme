# PayMe Innovation Roadmap — Multi-Rail + Real-Time Pay Page

This document is the human-readable companion to the formal decision records
in `decisions/`. It explains *what we are building*, *why it matters*, and
*what success looks like*. The decisions themselves capture the reasoning at
the level of detail an engineer or AI agent needs to safely change the code.

> **Demo goal (v1):** A customer creates an invoice, opens `/pay/{id}`, sees
> two payment rails (PayFast and PayShap), picks PayShap, scans a QR code,
> "approves" the request, and watches the same browser tab flip to **PAID**
> in real time — no refresh, no polling.

## Why this and why now

PayMe just finished its event-driven migration. The plumbing for real-time,
multi-consumer behaviour is in place but is not yet visible to a single human
being. This roadmap is the first piece of work that **cashes in** the event
foundation in a way a customer or stakeholder can feel.

It also stakes out PayMe's actual moat: **South African payment rails**.
Stripe-clones are everywhere. PayShap, SnapScan, Capitec Pay, and pay-by-bank
are not. One PayMe link routing to whichever local rail the customer prefers
is a product story no global card processor will ever tell.

## Decisions that drive this work

| ID | Title | Why it matters here |
|---|---|---|
| [ARCH-001](decisions/ARCH-001.md) | Multi-provider registry over global provider | Without this, "one link, multiple rails" is impossible — the JVM only knows about one provider at a time. |
| [ARCH-002](decisions/ARCH-002.md) | SSE for real-time pay-page status updates | The visible payoff of the event-driven migration. Customers see status flip live. |
| [INT-001](decisions/INT-001.md) | PayShap mock-first, Stitch as future provider | Lets us ship the multi-rail demo without waiting on aggregator onboarding. |

The full index is at [`decisions/_index.md`](decisions/_index.md).

## Phase plan

```
Phase A — Multi-provider registry  ──┐
  (ARCH-001)                          │
                                      ├──▶  Phase B — Mock PayShap  ──▶  end-to-end demo
Phase C — SSE pay page  ──────────────┘      (INT-001)
  (ARCH-002)
```

A and C are independent and can be built in either order. B depends on A.

### Phase A — Multi-provider registry  *(in progress)*

Replaces the single global `PaymentProvider` bean with a `PaymentProviderRegistry`
keyed by `ProviderName`. `StartCheckoutCommand` carries the chosen provider.
Controllers accept `?provider=`. The `payme.payment.provider` env var becomes
the *default*, not the *only*, provider.

**Backwards compatibility:** absent `?provider=`, behaviour is identical to
today. Existing PayFast and Fake-provider flows are unchanged.

**Done when:** existing checkout flow still works, and `GET /pay/{id}` returns
an `availableProviders` list.

### Phase B — Mock PayShap

A `MockPayShapPaymentProvider` that simulates PayShap's request-to-pay flow:
generate a synthetic ProxyID, render a "approve in your banking app" page
with a QR code, and let the user trigger the simulated bank confirmation
manually. The webhook flows through the same `/webhooks/{provider}` endpoint
the real adapter will use later.

**Safety:** the mock bean and its controller are only registered when
`payme.payshap.mode=mock`. Production config sets it to `stitch`.

**Done when:** picking PayShap on the pay page renders a QR + approve button,
and approving it flips the invoice to `SUCCEEDED` via the real webhook path.

### Phase C — SSE pay page

A new endpoint `GET /pay/{invoiceId}/events` produces `text/event-stream`.
A single in-process `InvoiceSseHub` keyed by invoice ID holds open emitters.
The existing `InvoiceEventHandler` publishes to the hub on
`InvoicePaymentSucceeded`, `InvoicePaymentFailed`, `InvoiceMarkedPending`,
and `InvoiceExpired`.

The pay page also serves a small vanilla-JS HTML view that opens an
`EventSource` and updates a status banner live.

**Known limitation:** single-instance fan-out only. See ARCH-002 for the
multi-instance follow-up.

**Done when:** two browser tabs on `/pay/{id}` both flip to `PAID` within
~200ms of a webhook, with no refresh.

## What this work is *not*

- **Not a real PayShap integration.** That comes later, when we have
  aggregator credentials. Mock-first is deliberate (see INT-001).
- **Not a merchant dashboard.** Out of scope. The SSE pay page is for the
  end customer.
- **Not a multi-instance scale-out.** PayMe is single-instance today and
  this work does not change that. The architecture is forward-compatible
  but does not pre-build the cross-instance fan-out.
- **Not a frontend rewrite.** The HTML page is intentionally vanilla JS,
  served from the backend, so the whole demo stays Java-only.

## Future follow-ups

These are *not* in scope for the v1 demo, but the decisions above are
designed to make them low-effort:

- Real PayShap via Stitch (swap one adapter, flip one config flag — INT-001).
- Multi-instance SSE fan-out via Redis pub/sub (additive, no controller
  changes — ARCH-002).
- Per-merchant provider rules (sits on top of the registry — ARCH-001).
- Additional rails: SnapScan, Capitec Pay, pay-by-bank — each is one new
  adapter and one enum value.

## How to read this repo as an agent or new contributor

1. Start with `decisions/_index.md`.
2. Any code carrying a `@dec(ARCH-001)` / `@dec(ARCH-002)` / `@dec(INT-001)`
   tag is governed by the corresponding decision file. Read it before
   changing that code.
3. Inline `@dec~` notes are local rationale — read them, but they live and
   die with the call site.
4. If you intend to undo or contradict an active decision, surface it
   explicitly — do not silently overwrite the code.
