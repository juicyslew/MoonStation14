package com.juicyslew.moonstation14.ms14.player_body_control.client;

/** Replays the server's remaining flight steps independent of packet arrival cadence. */
final class ExternalFlightPredictionPolicy {
    private int remaining;
    private com.juicyslew.moonstation14.ms14.player_body_control.movement.ExternalFlightPhysics mode =
            com.juicyslew.moonstation14.ms14.player_body_control.movement.ExternalFlightPhysics.SCRIPTED_IMPULSE;

    void reset(int ticks) {
        reset(ticks, com.juicyslew.moonstation14.ms14.player_body_control.movement.ExternalFlightPhysics.SCRIPTED_IMPULSE);
    }

    void reset(int ticks, com.juicyslew.moonstation14.ms14.player_body_control.movement.ExternalFlightPhysics mode) {
        if (ticks < 0) throw new IllegalArgumentException("flight ticks must be nonnegative");
        this.mode = java.util.Objects.requireNonNull(mode, "mode");
        remaining = ticks;
    }

    com.juicyslew.moonstation14.ms14.player_body_control.movement.ExternalFlightPhysics mode() { return mode; }

    boolean advanceFrame() {
        boolean flying = remaining > 1;
        if (remaining > 0) remaining--;
        return flying;
    }

    void clear() {
        remaining = 0;
        mode = com.juicyslew.moonstation14.ms14.player_body_control.movement.ExternalFlightPhysics.SCRIPTED_IMPULSE;
    }
}
