package com.juicyslew.moonstation14.ms14.singularity.emitter;

/** Deterministic firing cadence state; a power interruption discards both timers and burst progress. */
public record EmitterBurstSchedule(int remainingDelay, int shotsRemaining, boolean wasPowered) {
    public EmitterBurstSchedule { if (remainingDelay < 0 || shotsRemaining < 0) throw new IllegalArgumentException(); }
    public static EmitterBurstSchedule idle() { return new EmitterBurstSchedule(0, 0, false); }
    public Tick tick(EmitterProfile profile, boolean requested, boolean powered, int randomizedFirstDelay) {
        if (!requested || !powered) return new Tick(idle(), false);
        if (!wasPowered) return new Tick(new EmitterBurstSchedule(profile.firstDelayMaxTicks(), 0, true), false);
        if (remainingDelay > 1) return new Tick(new EmitterBurstSchedule(remainingDelay - 1, shotsRemaining, true), false);
        int left = shotsRemaining == 0 ? profile.burstSize() - 1 : shotsRemaining - 1;
        return new Tick(new EmitterBurstSchedule(left > 0 ? profile.burstDelayTicks()
                : Math.max(profile.firstDelayMinTicks(), Math.min(profile.firstDelayMaxTicks(), randomizedFirstDelay)),
                left, true), true);
    }
    public record Tick(EmitterBurstSchedule schedule, boolean fire) { }
}

