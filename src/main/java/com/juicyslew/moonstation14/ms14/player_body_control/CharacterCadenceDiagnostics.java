package com.juicyslew.moonstation14.ms14.player_body_control;

/** Temporary, fail-open budget shared by paired CHARACTER cadence trace sites. */
public final class CharacterCadenceDiagnostics {
    private static final CharacterCadenceDiagnostics RECEIPT_BUDGET = new CharacterCadenceDiagnostics();
    public static boolean allowReceipt(long nowNanos) { return RECEIPT_BUDGET.allowGlobal(nowNanos); }
    public static final String MARKER = "[CHARACTER_CADENCE]";
    private static final int MAX_LINES = 120;
    private static final int MAX_ORDINARY_LINES = 90;
    private static final long WINDOW_NANOS = 60_000_000_000L;
    private long epoch;
    private long startedAt;
    private int lines;
    private long globalStartedAt;
    private int globalLines;

    /** Shared server-safe diagnostic allowance, independent of character/action identity. */
    public synchronized boolean allowGlobal(long nowNanos) {
        if (globalStartedAt == 0 || nowNanos - globalStartedAt < 0 || nowNanos - globalStartedAt >= WINDOW_NANOS) {
            globalStartedAt = nowNanos == 0 ? 1 : nowNanos;
            globalLines = 0;
        }
        if (globalLines >= MAX_LINES) return false;
        globalLines++;
        return true;
    }

    public synchronized boolean allow(long sessionEpoch, long nowNanos, boolean important) {
        if (sessionEpoch <= 0) return false;
        if (epoch != sessionEpoch) {
            epoch = sessionEpoch;
            startedAt = nowNanos;
            lines = 0;
        }
        if (nowNanos - startedAt < 0 || nowNanos - startedAt > WINDOW_NANOS || lines >= MAX_LINES
                || (!important && lines >= MAX_ORDINARY_LINES))
            return false;
        // Reserve the final 30 slots in each window for genuine input/error transitions.
        lines++;
        return true;
    }

    public synchronized void reset() {
        epoch = 0;
        startedAt = 0;
        lines = 0;
        globalStartedAt = 0;
        globalLines = 0;
    }
}
