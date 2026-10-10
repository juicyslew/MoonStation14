package com.juicyslew.moonstation14.ms14.stripping;

import com.juicyslew.moonstation14.ms14.character.components.MobStateComponent;

/** Composes resolved slot/hand duration with supported upstream item and mob-state modifiers. */
public final class StripTimingPolicy {
    private StripTimingPolicy() { }

    public static int durationTicks(double baseSeconds, double clothingStripDelay, MobStateComponent.State targetState) {
        if (!Double.isFinite(baseSeconds) || baseSeconds < 0 || !Double.isFinite(clothingStripDelay) || targetState == null)
            throw new IllegalArgumentException("invalid stripping timing input");
        double stateMultiplier = switch (targetState) {
            case ALIVE -> 1.0;
            case CRITICAL -> 0.5;
            case DEAD -> 1.0 / 3.0;
        };
        double seconds = Math.max(0, baseSeconds + clothingStripDelay) * stateMultiplier;
        // Runtime timed operations have a one-tick minimum; zero-time upstream actions complete next tick.
        return Math.max(1, (int) Math.ceil(seconds * 20.0));
    }
}
