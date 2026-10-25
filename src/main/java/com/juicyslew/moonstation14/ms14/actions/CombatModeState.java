package com.juicyslew.moonstation14.ms14.actions;

/** Mutable combat-mode projection owned by one body. */
public final class CombatModeState {
    private boolean enabled;

    public boolean enabled() {
        return enabled;
    }

    public boolean toggle() {
        enabled = !enabled;
        return enabled;
    }
    public void set(boolean enabled) { this.enabled = enabled; }
}
