package com.juicyslew.moonstation14.ms14.hands.network;

/** Maps synchronous construction admission to an acknowledgement without claiming deferred work committed. */
public final class BodyConstructionOutcomePolicy {
    private BodyConstructionOutcomePolicy() { }

    public static BodyBlockUseResult.Outcome outcome(boolean handled, long beforeRevision, Long afterRevision) {
        if (!handled) return BodyBlockUseResult.Outcome.DENIED;
        return afterRevision != null && afterRevision > beforeRevision
                ? BodyBlockUseResult.Outcome.PLACED : BodyBlockUseResult.Outcome.NO_CHANGE;
    }
}
