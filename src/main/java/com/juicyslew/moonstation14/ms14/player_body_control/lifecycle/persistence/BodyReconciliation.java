package com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.persistence;

/** Pure fail-closed decision only; an UNLOADED result never triggers chunk loading or spawning. */
public final class BodyReconciliation {
    private BodyReconciliation() { }
    public enum Observation { LOADED_ALIVE_MATCH, UNLOADED, MISSING, AMBIGUOUS, LOADED_DEAD, OWNER_MISMATCH }
    public enum Decision { SAME_BODY_AVAILABLE, DEFER_KNOWN_BODY, RECOVERY_REQUIRED }
    public static Decision decide(Observation observation) {
        return switch (observation) {
            case LOADED_ALIVE_MATCH -> Decision.SAME_BODY_AVAILABLE;
            case UNLOADED -> Decision.DEFER_KNOWN_BODY;
            case MISSING, AMBIGUOUS, LOADED_DEAD, OWNER_MISMATCH -> Decision.RECOVERY_REQUIRED;
        };
    }
}
