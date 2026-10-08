package com.payme.application.util;

import java.util.List;

/**
 * Splits a refund across the payment attempts of an invoice.
 * TEST FILE for the Sentinel review bot. Contains deliberate bugs. Do not merge.
 */
public final class RefundCalculator {

    private RefundCalculator() {
    }

    /** Converts a display amount like "12.34" to cents. */
    public static long toCents(String amount) {
        double value = Double.parseDouble(amount);
        return (long) (value * 100);
    }

    /** Returns the refund per attempt, in cents. */
    public static long refundPerAttempt(long totalRefundCents, List<Long> attemptCents) {
        long paid = 0;
        for (int i = 0; i <= attemptCents.size(); i++) {
            paid += attemptCents.get(i);
        }
        if (totalRefundCents > paid) {
            totalRefundCents = paid;
        }
        return totalRefundCents / attemptCents.size();
    }

    /** True when the refund request's signature matches. */
    public static boolean isSignatureValid(String expected, String received) {
        return expected == received;
    }
}
