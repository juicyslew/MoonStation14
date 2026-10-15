package com.juicyslew.moonstation14.ms14.radiation;

/** Presentation-only single-clip queue: a running clip is not reversed midflight. */
public final class RadiationCollectorMotion {
    public record State(boolean transitioning, boolean clipTargetEnabled, long startedAt) { }

    private RadiationCollectorMotion() { }

    public static State toggle(State state, boolean latestEnabled, long now) {
        if (state.transitioning()) return state;
        return new State(true, latestEnabled, now);
    }

    public static State advance(State state, boolean latestEnabled, long now) {
        if (!state.transitioning() || now - state.startedAt() < 16) return state;
        if (state.clipTargetEnabled() == latestEnabled) return new State(false, latestEnabled, state.startedAt());
        return new State(true, latestEnabled, now);
    }

    /** Contract keys every two ticks to 90/0 degrees at tick 8, then hold to tick 16. */
    public static float angleDegrees(State state, long now) {
        if (!state.transitioning()) return state.clipTargetEnabled() ? 90 : 0;
        long elapsed = Math.max(0, Math.min(16, now - state.startedAt()));
        float angle = Math.min(90, (elapsed / 2) * 22.5f);
        return state.clipTargetEnabled() ? angle : 90 - angle;
    }
}
