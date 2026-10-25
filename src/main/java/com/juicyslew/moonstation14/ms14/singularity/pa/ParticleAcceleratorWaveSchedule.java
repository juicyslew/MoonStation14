package com.juicyslew.moonstation14.ms14.singularity.pa;

/** Runtime-only SS14-style firing phase: first wave is delayed, and ineligible time never accrues. */
public record ParticleAcceleratorWaveSchedule(long eligibleSinceTick, long nextWaveTick) {
    public static final ParticleAcceleratorWaveSchedule IDLE = new ParticleAcceleratorWaveSchedule(-1, -1);
    public record Advance(ParticleAcceleratorWaveSchedule schedule, boolean fire) {}

    public Advance advance(boolean eligible, long now, int intervalTicks) {
        if (intervalTicks <= 0) throw new IllegalArgumentException("intervalTicks must be positive");
        if (!eligible) return new Advance(IDLE, false);
        if (eligibleSinceTick < 0) return new Advance(new ParticleAcceleratorWaveSchedule(now, now + intervalTicks), false);
        if (now < nextWaveTick) return new Advance(this, false);
        return new Advance(new ParticleAcceleratorWaveSchedule(eligibleSinceTick, now + intervalTicks), true);
    }
}
