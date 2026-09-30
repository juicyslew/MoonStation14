package com.juicyslew.moonstation14.ms14.power.ui;

/** Pure revision and desired-state decision; caller retains session, access, and mutation authority. */
public final class ApcBreakerIntentPolicy {
    private ApcBreakerIntentPolicy() { }

    public enum Decision { DENY, NO_CHANGE, TOGGLE }

    public static Decision decide(long expectedRevision, long currentRevision,
                                  boolean currentClosed, boolean desiredClosed) {
        if (expectedRevision < 0 || currentRevision < 0 || expectedRevision != currentRevision)
            return Decision.DENY;
        return currentClosed == desiredClosed ? Decision.NO_CHANGE : Decision.TOGGLE;
    }
}
