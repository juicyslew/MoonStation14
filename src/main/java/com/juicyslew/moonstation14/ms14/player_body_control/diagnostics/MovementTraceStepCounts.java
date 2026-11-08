package com.juicyslew.moonstation14.ms14.player_body_control.diagnostics;

/** Counts replay-plan dispatch separately from actual motor outcomes. */
public final class MovementTraceStepCounts {
    public enum Outcome { COMPLETED, FAILED_AFTER_MOVE, SKIPPED }
    private int dispatched, attempted, completed, failed, skipped;
    public void observe(Outcome outcome) {
        dispatched++;
        switch (outcome) {
            case COMPLETED -> { attempted++; completed++; }
            case FAILED_AFTER_MOVE -> { attempted++; failed++; }
            case SKIPPED -> skipped++;
        }
    }
    public int dispatched() { return dispatched; }
    public int attempted() { return attempted; }
    public int completed() { return completed; }
    public int failed() { return failed; }
    public int skipped() { return skipped; }
}
