package com.juicyslew.moonstation14.ms14.examine.client;

/** Single outstanding examination request and coalesced range-transition refresh. */
public final class ExamineRefreshPolicy {
    private boolean inFlight;
    private boolean queued;

    /** Returns true if the caller may send now; otherwise coalesces the refresh. */
    public boolean request() {
        if (inFlight) { queued = true; return false; }
        return true;
    }
    /** An explicit inspection replaces the old latch and its pending automatic request. */
    public void explicitRequest() { reset(); }
    public void sent() { inFlight = true; }
    /** Stale/uncorrelated responses cannot release the request slot. */
    public boolean correlatedResponse(boolean correlated) {
        if (!correlated || !inFlight) return false;
        inFlight = false;
        boolean followUp = queued;
        queued = false;
        return followUp;
    }
    public boolean inFlight() { return inFlight; }
    public void reset() { inFlight = false; queued = false; }
}
