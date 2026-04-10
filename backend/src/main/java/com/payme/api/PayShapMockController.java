package com.payme.api;

import com.payme.application.commandhandler.ProcessWebhookCommandHandler;
import com.payme.domain.command.ProcessWebhookCommand;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * @dec(INT-001) Simulated PayShap approve page — see decisions/INT-001.md
 *
 * Renders a tiny HTML page that mimics a banking-app "approve request to pay"
 * screen. The "Approve" button POSTs a synthetic webhook to the same
 * {@code /webhooks/PAYSHAP} endpoint a real bank would hit, exercising the
 * full canonical webhook path (verifier, dedup, attempt update, invoice
 * update, event publish, projection, SSE fan-out).
 *
 * Only registered when {@code payme.payshap.mode=mock} so the simulated
 * approve route can never appear in a deploy that should be talking to a real
 * aggregator.
 */
@RestController
@RequestMapping("/pay")
@ConditionalOnProperty(name = "payme.payshap.mode", havingValue = "mock", matchIfMissing = false)
public class PayShapMockController {

    private static final Logger log = LoggerFactory.getLogger(PayShapMockController.class);

    private final ProcessWebhookCommandHandler processWebhookCommandHandler;

    public PayShapMockController(ProcessWebhookCommandHandler processWebhookCommandHandler) {
        this.processWebhookCommandHandler = processWebhookCommandHandler;
    }

    @GetMapping(value = "/{invoiceId}/payshap-mock", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> approvePage(
            @PathVariable String invoiceId,
            @RequestParam("attempt") String attemptId,
            @RequestParam("proxy") String proxyId
    ) {
        log.info("MockPayShap: rendering approve page for invoice={} attempt={}", invoiceId, attemptId);
        return ResponseEntity.ok(renderApprovePage(invoiceId, attemptId, proxyId));
    }

    /**
     * @dec~ The Approve button posts here. We turn around and call the
     * standard webhook command handler with a JSON body that mimics what a
     * real bank would send. This means the mock exercises every code path
     * the real adapter will: dedup, store, publish, projection, SSE.
     */
    @PostMapping(value = "/{invoiceId}/payshap-mock/approve", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, String>> approve(
            @PathVariable String invoiceId,
            @RequestParam("attempt") String attemptId,
            @RequestParam(value = "outcome", defaultValue = "APPROVED") String outcome,
            HttpServletRequest request
    ) {
        log.info("MockPayShap: customer {} for invoice={} attempt={}", outcome, invoiceId, attemptId);

        String body = String.format(
                "{\"attemptId\":\"%s\",\"invoiceId\":\"%s\",\"status\":\"%s\",\"providerEventId\":\"payshap_mock_%s\"}",
                attemptId, invoiceId, outcome, UUID.randomUUID()
        );

        ProcessWebhookCommand cmd = new ProcessWebhookCommand(
                UUID.randomUUID().toString(),
                "PAYSHAP",
                body,
                Map.of("X-Source-IP", request.getRemoteAddr(), "X-Mock", "true")
        );
        processWebhookCommandHandler.handle(cmd);

        return ResponseEntity.ok(Map.of(
                "status", "submitted",
                "outcome", outcome.toUpperCase()
        ));
    }

    private String renderApprovePage(String invoiceId, String attemptId, String proxyId) {
        // @dec~ Inline HTML rather than a templating engine — there is exactly
        // one mock page; pulling in Thymeleaf for one screen is overkill.
        return ""
                + "<!DOCTYPE html>\n"
                + "<html lang=\"en\">\n"
                + "<head>\n"
                + "  <meta charset=\"UTF-8\"/>\n"
                + "  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\"/>\n"
                + "  <title>PayShap — Approve request to pay</title>\n"
                + "  <style>\n"
                + "    body { font-family: -apple-system, BlinkMacSystemFont, \"Segoe UI\", sans-serif;\n"
                + "           background: #0f172a; color: #e2e8f0; margin: 0; min-height: 100vh;\n"
                + "           display: flex; align-items: center; justify-content: center; padding: 2rem; }\n"
                + "    .card { background: #1e293b; border-radius: 16px; padding: 2.5rem; max-width: 420px;\n"
                + "            width: 100%; box-shadow: 0 20px 60px rgba(0,0,0,0.4); }\n"
                + "    .badge { display: inline-block; background: #16a34a; color: white; padding: 4px 10px;\n"
                + "             border-radius: 999px; font-size: 0.75rem; font-weight: 600; letter-spacing: 0.05em;\n"
                + "             text-transform: uppercase; margin-bottom: 1rem; }\n"
                + "    h1 { font-size: 1.4rem; margin: 0 0 0.5rem 0; }\n"
                + "    .sub { color: #94a3b8; font-size: 0.9rem; margin-bottom: 1.5rem; }\n"
                + "    .row { display: flex; justify-content: space-between; padding: 0.75rem 0;\n"
                + "           border-bottom: 1px solid #334155; }\n"
                + "    .row:last-child { border-bottom: none; }\n"
                + "    .label { color: #94a3b8; font-size: 0.85rem; }\n"
                + "    .val { font-weight: 600; font-family: ui-monospace, monospace; }\n"
                + "    .actions { display: flex; gap: 0.75rem; margin-top: 1.75rem; }\n"
                + "    button { flex: 1; padding: 0.85rem 1rem; border-radius: 10px; border: none;\n"
                + "             font-size: 0.95rem; font-weight: 600; cursor: pointer; transition: opacity 0.15s; }\n"
                + "    button:disabled { opacity: 0.5; cursor: not-allowed; }\n"
                + "    .approve { background: #16a34a; color: white; }\n"
                + "    .decline { background: #475569; color: #e2e8f0; }\n"
                + "    .status { margin-top: 1.25rem; padding: 0.75rem; border-radius: 8px; text-align: center;\n"
                + "              background: #0f172a; font-size: 0.85rem; min-height: 1.2em; }\n"
                + "    .ok { color: #4ade80; }\n"
                + "    .err { color: #f87171; }\n"
                + "  </style>\n"
                + "</head>\n"
                + "<body>\n"
                + "  <div class=\"card\">\n"
                + "    <span class=\"badge\">SIMULATED · MOCK</span>\n"
                + "    <h1>Request to Pay</h1>\n"
                + "    <p class=\"sub\">A merchant is requesting payment via PayShap. Approve to send funds.</p>\n"
                + "    <div class=\"row\"><span class=\"label\">To ProxyID</span><span class=\"val\">" + htmlEscape(proxyId) + "</span></div>\n"
                + "    <div class=\"row\"><span class=\"label\">Invoice</span><span class=\"val\">" + htmlEscape(invoiceId) + "</span></div>\n"
                + "    <div class=\"row\"><span class=\"label\">Attempt</span><span class=\"val\">" + htmlEscape(attemptId) + "</span></div>\n"
                + "    <div class=\"actions\">\n"
                + "      <button class=\"decline\" onclick=\"submit('DECLINED')\">Decline</button>\n"
                + "      <button class=\"approve\" onclick=\"submit('APPROVED')\">Approve</button>\n"
                + "    </div>\n"
                + "    <div class=\"status\" id=\"status\"></div>\n"
                + "  </div>\n"
                + "  <script>\n"
                + "    async function submit(outcome) {\n"
                + "      const status = document.getElementById('status');\n"
                + "      const buttons = document.querySelectorAll('button');\n"
                + "      buttons.forEach(b => b.disabled = true);\n"
                + "      status.textContent = 'Submitting to your bank...';\n"
                + "      status.className = 'status';\n"
                + "      try {\n"
                + "        const url = '/pay/" + invoiceId + "/payshap-mock/approve?attempt=" + attemptId + "&outcome=' + outcome;\n"
                + "        const res = await fetch(url, { method: 'POST' });\n"
                + "        if (!res.ok) throw new Error('HTTP ' + res.status);\n"
                + "        const data = await res.json();\n"
                + "        status.textContent = outcome === 'APPROVED'\n"
                + "          ? 'Payment approved. You can close this tab.'\n"
                + "          : 'Payment declined.';\n"
                + "        status.className = outcome === 'APPROVED' ? 'status ok' : 'status err';\n"
                + "      } catch (e) {\n"
                + "        status.textContent = 'Failed to submit: ' + e.message;\n"
                + "        status.className = 'status err';\n"
                + "        buttons.forEach(b => b.disabled = false);\n"
                + "      }\n"
                + "    }\n"
                + "  </script>\n"
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
