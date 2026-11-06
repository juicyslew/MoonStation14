package com.juicyslew.moonstation14.ms14.player_body_control.server;

/** Converts Sable's post-send interpolation counter into the key of its completed tracking pass. */
public final class SableFrameClock {
    private SableFrameClock() { }

    public static java.util.OptionalLong completedStep(long interpolationTick) {
        long completed = interpolationTick - 1L;
        return completed > 0 ? java.util.OptionalLong.of(completed) : java.util.OptionalLong.empty();
    }
}
