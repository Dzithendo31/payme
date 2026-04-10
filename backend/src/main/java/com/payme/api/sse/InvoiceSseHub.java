package com.payme.api.sse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * @dec(ARCH-002) In-process SSE fan-out keyed by invoiceId — see decisions/ARCH-002.md
 *
 * Holds open {@link SseEmitter} connections from customers viewing
 * {@code /pay/{invoiceId}/page}. When invoice events fire (via the existing
 * {@code InvoiceEventHandler}), the corresponding emitters are pushed a
 * status update so the pay page flips in real time.
 *
 * <p><strong>Single-instance only.</strong> An emitter on instance A will
 * never see an event fired on instance B. PayMe is single-instance today;
 * the multi-instance fan-out (via Redis pub/sub) is a deliberate
 * follow-up — see ARCH-002 §"Revisit When".
 */
@Component
public class InvoiceSseHub {

    private static final Logger log = LoggerFactory.getLogger(InvoiceSseHub.class);

    /**
     * @dec~ 30-minute timeout matches the realistic upper bound of an
     * in-progress checkout. Long enough that customers don't get prematurely
     * disconnected mid-payment, short enough to release tomcat resources.
     */
    private static final long EMITTER_TIMEOUT_MS = Duration.ofMinutes(30).toMillis();

    private final ConcurrentHashMap<String, CopyOnWriteArrayList<SseEmitter>> emittersByInvoice =
            new ConcurrentHashMap<>();

    /**
     * Subscribes a new client to events for the given invoice.
     * Returns the {@link SseEmitter} for the controller to hand back to Spring.
     *
     * @dec~ Uses ConcurrentHashMap.compute() rather than computeIfAbsent + .add():
     * compute() holds the per-bucket lock for the duration of the lambda, so a
     * concurrent remove() that empties the list and nulls the map entry cannot
     * race with a fresh subscribe(). Without this, an emitter added between
     * "list became empty" and "map entry removed" would be silently orphaned.
     */
    public SseEmitter subscribe(String invoiceId) {
        SseEmitter emitter = new SseEmitter(EMITTER_TIMEOUT_MS);

        emittersByInvoice.compute(invoiceId, (k, existing) -> {
            CopyOnWriteArrayList<SseEmitter> list = existing != null
                    ? existing
                    : new CopyOnWriteArrayList<>();
            list.add(emitter);
            return list;
        });

        emitter.onCompletion(() -> remove(invoiceId, emitter));
        emitter.onTimeout(() -> remove(invoiceId, emitter));
        emitter.onError(t -> remove(invoiceId, emitter));

        log.debug("SSE: subscribed to invoice={}", invoiceId);
        return emitter;
    }

    /**
     * Pushes an event to every emitter subscribed to {@code invoiceId}.
     * Dead emitters (closed, timed out, errored) are silently dropped.
     */
    public void publish(String invoiceId, String eventName, Map<String, ?> payload) {
        CopyOnWriteArrayList<SseEmitter> emitters = emittersByInvoice.get(invoiceId);
        if (emitters == null || emitters.isEmpty()) {
            return;
        }

        log.debug("SSE: publishing {} to invoice={} ({} subscribers)",
                eventName, invoiceId, emitters.size());

        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name(eventName).data(payload));
            } catch (IOException | IllegalStateException e) {
                // Client disconnected mid-write — drop the emitter and move on.
                log.debug("SSE: dropping dead emitter for invoice={}: {}", invoiceId, e.getMessage());
                remove(invoiceId, emitter);
            }
        }
    }

    /**
     * @dec~ Atomic remove paired with subscribe(): compute() returns null to
     * delete the map entry only when the list is genuinely empty after this
     * removal, all under the bucket lock. Returning the same list reference
     * keeps the entry; returning null evicts it. Closes the
     * subscribe-while-removing race that would otherwise orphan a fresh emitter.
     */
    private void remove(String invoiceId, SseEmitter emitter) {
        emittersByInvoice.compute(invoiceId, (k, list) -> {
            if (list == null) {
                return null;
            }
            list.remove(emitter);
            return list.isEmpty() ? null : list;
        });
    }
}
