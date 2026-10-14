package com.juicyslew.moonstation14.ms14.medical;

/** Resolved ItemToggle policy for the generic in-hand activation input. */
public final class HealthAnalyzerActivationPolicy {
    public enum Decision { OPEN_ONLY, TOGGLE_OFF, TOGGLE_ON_OPEN }

    private HealthAnalyzerActivationPolicy() { }

    public static Decision decide(boolean onUse, boolean active) {
        if (!onUse) return Decision.OPEN_ONLY;
        return active ? Decision.TOGGLE_OFF : Decision.TOGGLE_ON_OPEN;
    }
}

