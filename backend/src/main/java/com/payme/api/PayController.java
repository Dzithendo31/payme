package com.payme.api;

import com.payme.api.dto.CheckoutResponse;
import com.payme.api.dto.PayPageResponse;
import com.payme.api.sse.InvoiceSseHub;
import com.payme.application.GetPayPageDataUseCase;
import com.payme.application.commandhandler.StartCheckoutCommandHandler;
import com.payme.domain.Invoice;
import com.payme.domain.ProviderName;
import com.payme.domain.command.StartCheckoutCommand;
import com.payme.ports.Clock;
import com.payme.ports.PaymentProviderRegistry;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/pay")
public class PayController {

    private static final Logger log = LoggerFactory.getLogger(PayController.class);

    private final GetPayPageDataUseCase getPayPageDataUseCase;
    private final StartCheckoutCommandHandler startCheckoutCommandHandler;
    private final PaymentProviderRegistry providerRegistry;
    private final InvoiceSseHub sseHub;
    private final Clock clock;

    public PayController(
            GetPayPageDataUseCase getPayPageDataUseCase,
            StartCheckoutCommandHandler startCheckoutCommandHandler,
            PaymentProviderRegistry providerRegistry,
            InvoiceSseHub sseHub,
            Clock clock
    ) {
        this.getPayPageDataUseCase = getPayPageDataUseCase;
        this.startCheckoutCommandHandler = startCheckoutCommandHandler;
        this.providerRegistry = providerRegistry;
        this.sseHub = sseHub;
        this.clock = clock;
    }

    @GetMapping(value = "/success", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> paymentSuccess() {
        return ResponseEntity.ok(
                "<!DOCTYPE html>\n"
                        + "<html lang=\"en\"><head><meta charset=\"UTF-8\"/><title>Payment successful</title></head>\n"
                        + "<body><h1>Payment successful</h1>"
                        + "<p>Thank you. Your payment has been received and is being processed.</p>"
                        + "</body></html>\n");
    }

    @GetMapping(value = "/cancel", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> paymentCancelled() {
        return ResponseEntity.ok(
                "<!DOCTYPE html>\n"
                        + "<html lang=\"en\"><head><meta charset=\"UTF-8\"/><title>Payment cancelled</title></head>\n"
                        + "<body><h1>Payment cancelled</h1>"
                        + "<p>Your payment was cancelled. You can try again from the merchant's checkout page.</p>"
                        + "</body></html>\n");
    }

    @GetMapping("/{invoiceId}")
    public ResponseEntity<PayPageResponse> getPayPage(@PathVariable String invoiceId) {
        Invoice invoice = getPayPageDataUseCase.execute(invoiceId);

        // @dec(ARCH-001) Pay page advertises real rails available in this env
        Set<ProviderName> publicProviders = publicFacingProviders();
        ProviderName defaultProvider = publicDefaultProvider(publicProviders);

        PayPageResponse response = PayPageResponse.fromDomain(
                invoice, clock, publicProviders, defaultProvider
        );
        return ResponseEntity.ok(response);
    }

    /**
     * @dec(ARCH-002) Demo HTML pay page — see decisions/ARCH-002.md
     *
     * Vanilla JS — no build step, no separate frontend project. Loads the
     * existing JSON pay-page contract, renders provider buttons from
     * {@code availableProviders}, and opens an EventSource on
     * {@code /pay/{id}/events} so the status banner flips live.
     *
     * Lives at {@code /pay/{id}/page} rather than overloading {@code /pay/{id}}
     * to avoid breaking the existing JSON contract.
     */
    @GetMapping(value = "/{invoiceId}/page", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> payPageHtml(@PathVariable String invoiceId) {
        log.info("Rendering demo pay page for invoice {}", invoiceId);
        return ResponseEntity.ok(renderPayPageHtml(invoiceId));
    }

    /**
     * @dec(ARCH-002) SSE stream of invoice status changes — see decisions/ARCH-002.md
     *
     * The customer's pay page subscribes here. Sends an immediate {@code state}
     * event with the current invoice status (so reconnecting clients are not
     * blank), then streams subsequent status changes as the events fire.
     */
    @GetMapping(value = "/{invoiceId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamInvoiceEvents(@PathVariable String invoiceId) {
        log.info("SSE: client subscribing to invoice {}", invoiceId);

        SseEmitter emitter = sseHub.subscribe(invoiceId);

        // @dec(ARCH-002) initial snapshot lets reconnects skip the gap
        try {
            Invoice invoice = getPayPageDataUseCase.execute(invoiceId);
            Map<String, Object> initial = new LinkedHashMap<>();
            initial.put("invoiceId", invoiceId);
            initial.put("status", invoice.getStatus().name());
            initial.put("isPayable", invoice.isPayable(clock.now()));
            emitter.send(SseEmitter.event().name("state").data(initial));
        } catch (Exception e) {
            log.warn("SSE: failed to send initial state for invoice {}: {}", invoiceId, e.getMessage());
            emitter.completeWithError(e);
        }

        return emitter;
    }

    @PostMapping("/{invoiceId}/checkout")
    public ResponseEntity<CheckoutResponse> startCheckout(
            @PathVariable String invoiceId,
            @RequestParam(value = "provider", required = false) ProviderName provider
    ) {
        log.info("Received checkout request for invoice: {} (provider={})", invoiceId, provider);

        StartCheckoutCommand cmd = new StartCheckoutCommand(
                UUID.randomUUID().toString(),
                invoiceId,
                provider
        );
        StartCheckoutCommandHandler.CheckoutResult result = startCheckoutCommandHandler.handle(cmd);

        CheckoutResponse response = new CheckoutResponse(
                result.getCheckoutUrl(),
                result.getAttemptId()
        );

        log.info("Checkout started successfully for invoice: {}, attempt: {}",
                invoiceId, result.getAttemptId());

        return ResponseEntity.ok(response);
    }

    /**
     * Initiates checkout and routes the customer to the chosen rail.
     *
     * @dec~ Two-mode endpoint: providers that need a signed POST (e.g. PayFast)
     * are sent via an auto-submit HTML form; providers that just need a GET
     * redirect (e.g. mock PayShap → our internal approve page) get a 302.
     * The signal is whether the {@link CheckoutSession} carries form params:
     * empty = simple redirect, populated = POST form. Without this split, the
     * pay-page JS would have to know how each rail wants to be invoked.
     */
    @GetMapping(value = "/{invoiceId}/checkout/redirect")
    public ResponseEntity<String> redirectToProviderCheckout(
            @PathVariable String invoiceId,
            @RequestParam(value = "provider", required = false) ProviderName provider
    ) {
        log.info("Received checkout redirect request for invoice: {} (provider={})", invoiceId, provider);

        StartCheckoutCommand cmd = new StartCheckoutCommand(
                UUID.randomUUID().toString(),
                invoiceId,
                provider
        );
        StartCheckoutCommandHandler.CheckoutResult result = startCheckoutCommandHandler.handle(cmd);

        log.info("Checkout redirect for invoice {} attempt {} (formParams={})",
                invoiceId, result.getAttemptId(), result.getFormParams().size());

        if (result.getFormParams().isEmpty()) {
            // Plain GET redirect — used by adapters whose checkout URL is its
            // own self-hosted page (e.g. mock PayShap approve screen).
            return ResponseEntity.status(HttpStatus.FOUND)
                    .header(HttpHeaders.LOCATION, result.getCheckoutUrl())
                    .build();
        }

        // POST-form provider (PayFast). Render an HTML page that auto-submits
        // the signed form to the provider's hosted checkout URL.
        String html = renderAutoSubmitForm(result.getCheckoutUrl(), result.getFormParams());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_HTML_VALUE)
                .body(html);
    }

    /**
     * @dec~ FAKE provider is a development fixture; never advertise it on the
     * customer-facing pay page even when wired into the registry
     */
    private Set<ProviderName> publicFacingProviders() {
        Set<ProviderName> filtered = new LinkedHashSet<>(providerRegistry.available());
        filtered.remove(ProviderName.FAKE);
        return filtered;
    }

    private ProviderName publicDefaultProvider(Set<ProviderName> publicProviders) {
        ProviderName envDefault = providerRegistry.defaultProvider();
        if (publicProviders.contains(envDefault)) {
            return envDefault;
        }
        // env default is FAKE (or another non-public rail) — fall back to the first public one
        return publicProviders.isEmpty() ? null : publicProviders.iterator().next();
    }

    @ExceptionHandler(PaymentProviderRegistry.UnknownProviderException.class)
    public ResponseEntity<Map<String, String>> handleUnknownProvider(
            PaymentProviderRegistry.UnknownProviderException e
    ) {
        log.warn("Rejected checkout: {}", e.getMessage());
        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }

    /**
     * @dec~ Inline HTML with vanilla JS — see ARCH-002 §"Pay-page UI". Pulling
     * in Thymeleaf or React for a single demo page would be premature for the
     * v1 scope. The page is intentionally tiny: status banner + provider
     * buttons + EventSource subscription.
     */
    private String renderPayPageHtml(String invoiceId) {
        String safeId = htmlEscape(invoiceId);
        return ""
                + "<!DOCTYPE html>\n"
                + "<html lang=\"en\">\n"
                + "<head>\n"
                + "  <meta charset=\"UTF-8\"/>\n"
                + "  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\"/>\n"
                + "  <title>PayMe — Invoice " + safeId + "</title>\n"
                + "  <style>\n"
                + "    body { font-family: -apple-system, BlinkMacSystemFont, \"Segoe UI\", sans-serif;\n"
                + "           background: #f8fafc; color: #0f172a; margin: 0; min-height: 100vh;\n"
                + "           display: flex; align-items: center; justify-content: center; padding: 2rem; }\n"
                + "    .card { background: white; border-radius: 16px; padding: 2.5rem; max-width: 480px;\n"
                + "            width: 100%; box-shadow: 0 10px 40px rgba(15,23,42,0.08); }\n"
                + "    h1 { font-size: 1.4rem; margin: 0 0 0.25rem 0; }\n"
                + "    .desc { color: #64748b; margin: 0 0 1.5rem 0; font-size: 0.95rem; }\n"
                + "    .amount { font-size: 2.5rem; font-weight: 700; margin: 0.5rem 0 1.5rem 0; }\n"
                + "    .amount .currency { font-size: 1rem; color: #64748b; font-weight: 500; margin-right: 0.5rem; }\n"
                + "    .status { display: inline-block; padding: 6px 12px; border-radius: 999px;\n"
                + "              font-size: 0.75rem; font-weight: 700; letter-spacing: 0.05em;\n"
                + "              text-transform: uppercase; margin-bottom: 1.5rem; transition: all 0.3s; }\n"
                + "    .status.created  { background: #e2e8f0; color: #475569; }\n"
                + "    .status.pending  { background: #fef3c7; color: #92400e; }\n"
                + "    .status.succeeded{ background: #dcfce7; color: #166534; }\n"
                + "    .status.failed   { background: #fee2e2; color: #991b1b; }\n"
                + "    .status.expired  { background: #f1f5f9; color: #64748b; }\n"
                + "    .providers { display: flex; flex-direction: column; gap: 0.75rem; margin-top: 1rem; }\n"
                + "    .providers button { padding: 1rem 1.25rem; border-radius: 12px; border: 2px solid #e2e8f0;\n"
                + "                        background: white; font-size: 1rem; font-weight: 600; cursor: pointer;\n"
                + "                        text-align: left; transition: all 0.15s; display: flex;\n"
                + "                        justify-content: space-between; align-items: center; }\n"
                + "    .providers button:hover:not(:disabled) { border-color: #2563eb; background: #eff6ff; }\n"
                + "    .providers button:disabled { opacity: 0.5; cursor: not-allowed; }\n"
                + "    .providers .name { color: #0f172a; }\n"
                + "    .providers .hint { color: #64748b; font-size: 0.8rem; font-weight: 500; }\n"
                + "    .live { display: inline-block; width: 8px; height: 8px; border-radius: 50%;\n"
                + "            background: #22c55e; margin-right: 6px; animation: pulse 2s infinite; }\n"
                + "    @keyframes pulse { 0%, 100% { opacity: 1; } 50% { opacity: 0.4; } }\n"
                + "    .footer { margin-top: 2rem; font-size: 0.75rem; color: #94a3b8; text-align: center; }\n"
                + "    .err { color: #b91c1c; font-size: 0.85rem; margin-top: 1rem; }\n"
                + "  </style>\n"
                + "</head>\n"
                + "<body>\n"
                + "  <div class=\"card\">\n"
                + "    <span id=\"status\" class=\"status created\">Loading...</span>\n"
                + "    <h1 id=\"merchant\">&nbsp;</h1>\n"
                + "    <p class=\"desc\" id=\"description\">&nbsp;</p>\n"
                + "    <div class=\"amount\"><span class=\"currency\" id=\"currency\"></span><span id=\"amount\">—</span></div>\n"
                + "    <div class=\"providers\" id=\"providers\"></div>\n"
                + "    <div class=\"err\" id=\"error\"></div>\n"
                + "    <div class=\"footer\"><span class=\"live\"></span>Live status via SSE · Invoice " + safeId + "</div>\n"
                + "  </div>\n"
                + "  <script>\n"
                + "    const invoiceId = '" + safeId + "';\n"
                + "    const PROVIDER_LABELS = {\n"
                + "      PAYFAST: { name: 'Card / EFT',     hint: 'PayFast — Visa, Mastercard, Instant EFT' },\n"
                + "      PAYSHAP: { name: 'PayShap',        hint: 'Instant bank-to-bank via your banking app' },\n"
                + "      FAKE:    { name: 'Test provider',  hint: 'Dev only' }\n"
                + "    };\n"
                + "\n"
                + "    function setStatus(status) {\n"
                + "      const el = document.getElementById('status');\n"
                + "      el.textContent = status;\n"
                + "      el.className = 'status ' + status.toLowerCase();\n"
                + "      if (status === 'SUCCEEDED' || status === 'FAILED' || status === 'EXPIRED') {\n"
                + "        document.querySelectorAll('.providers button').forEach(b => b.disabled = true);\n"
                + "      }\n"
                + "    }\n"
                + "\n"
                + "    async function loadPayPage() {\n"
                + "      try {\n"
                + "        const res = await fetch('/pay/' + invoiceId);\n"
                + "        if (!res.ok) throw new Error('HTTP ' + res.status);\n"
                + "        const data = await res.json();\n"
                + "        document.getElementById('merchant').textContent = data.merchantName || 'Merchant';\n"
                + "        document.getElementById('description').textContent = data.description || '';\n"
                + "        document.getElementById('currency').textContent = data.currency || '';\n"
                + "        document.getElementById('amount').textContent = data.amount || '—';\n"
                + "        setStatus(data.status);\n"
                + "        renderProviders(data.availableProviders || []);\n"
                + "      } catch (e) {\n"
                + "        document.getElementById('error').textContent = 'Failed to load invoice: ' + e.message;\n"
                + "      }\n"
                + "    }\n"
                + "\n"
                + "    function renderProviders(providers) {\n"
                + "      // Build buttons with createElement + textContent so unknown\n"
                + "      // provider names from the API can never inject HTML.\n"
                + "      const container = document.getElementById('providers');\n"
                + "      container.replaceChildren();\n"
                + "      providers.forEach(p => {\n"
                + "        const label = PROVIDER_LABELS[p] || { name: p, hint: '' };\n"
                + "        const btn = document.createElement('button');\n"
                + "        const nameSpan = document.createElement('span');\n"
                + "        nameSpan.className = 'name';\n"
                + "        nameSpan.textContent = label.name;\n"
                + "        const hintSpan = document.createElement('span');\n"
                + "        hintSpan.className = 'hint';\n"
                + "        hintSpan.textContent = label.hint;\n"
                + "        btn.append(nameSpan, hintSpan);\n"
                + "        btn.onclick = () => startCheckout(p);\n"
                + "        container.appendChild(btn);\n"
                + "      });\n"
                + "    }\n"
                + "\n"
                + "    function startCheckout(provider) {\n"
                + "      // Server-side endpoint handles both POST-form (PayFast) and GET-redirect (PayShap)\n"
                + "      // providers — see PayController.redirectToProviderCheckout.\n"
                + "      window.location.href = '/pay/' + invoiceId + '/checkout/redirect?provider=' + provider;\n"
                + "    }\n"
                + "\n"
                + "    function subscribeToEvents() {\n"
                + "      const es = new EventSource('/pay/' + invoiceId + '/events');\n"
                + "      es.addEventListener('state', (e) => {\n"
                + "        const data = JSON.parse(e.data);\n"
                + "        if (data.status) setStatus(data.status);\n"
                + "      });\n"
                + "      es.addEventListener('status', (e) => {\n"
                + "        const data = JSON.parse(e.data);\n"
                + "        if (data.status) setStatus(data.status);\n"
                + "      });\n"
                + "      es.onerror = () => { /* browser will auto-reconnect */ };\n"
                + "    }\n"
                + "\n"
                + "    loadPayPage();\n"
                + "    subscribeToEvents();\n"
                + "  </script>\n"
                + "</body>\n"
                + "</html>\n";
    }

    private String renderAutoSubmitForm(String actionUrl, Map<String, String> params) {
        StringBuilder hiddenInputs = new StringBuilder();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            hiddenInputs.append("    <input type=\"hidden\" name=\"")
                    .append(htmlEscape(entry.getKey()))
                    .append("\" value=\"")
                    .append(htmlEscape(entry.getValue()))
                    .append("\"/>\n");
        }

        return "<!DOCTYPE html>\n"
                + "<html lang=\"en\">\n"
                + "<head>\n"
                + "  <meta charset=\"UTF-8\"/>\n"
                + "  <title>Redirecting to payment provider...</title>\n"
                + "</head>\n"
                + "<body onload=\"document.forms[0].submit()\">\n"
                + "  <p>Redirecting to payment provider. If nothing happens, click the button below.</p>\n"
                + "  <form method=\"POST\" action=\"" + htmlEscape(actionUrl) + "\">\n"
                + hiddenInputs
                + "    <button type=\"submit\">Continue to payment</button>\n"
                + "  </form>\n"
                + "</body>\n"
                + "</html>\n";
    }

    private String htmlEscape(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
