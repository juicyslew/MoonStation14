package com.juicyslew.moonstation14.ms14.combat;

import java.util.OptionalLong;

/** Pure source-compatible cadence, empty-safety, and spread transitions for live firing. */
public final class GunFirePolicy {
    private GunFirePolicy() { }
    public static OptionalLong nextFireTick(long now, float shotsPerSecond) {
        return MeleeCooldownData.deadline(now, shotsPerSecond);
    }
    public static OptionalLong emptySafetyTick(long now) {
        return now > Long.MAX_VALUE - 10 ? OptionalLong.empty() : OptionalLong.of(now + 10);
    }
    public static OptionalLong emptyDeadline(long now, long advancedDeadline, float burstCooldownSeconds) {
        if (!Float.isFinite(burstCooldownSeconds) || burstCooldownSeconds < 0f) return OptionalLong.empty();
        double deltaDouble = Math.ceil(20d * burstCooldownSeconds);
        if (!Double.isFinite(deltaDouble) || deltaDouble > Long.MAX_VALUE) return OptionalLong.empty();
        long delta = (long) deltaDouble;
        if (advancedDeadline > Long.MAX_VALUE - delta) return OptionalLong.empty();
        var safety = emptySafetyTick(now);
        if (safety.isEmpty()) return OptionalLong.empty();
        return OptionalLong.of(Math.max(advancedDeadline + delta, safety.getAsLong()));
    }
    public static SpreadResult recoil(GunRuntimeState state, GunProfile.SpreadData spread, long now, float randomUnit) {
        if (!Float.isFinite(randomUnit) || randomUnit < -1f || randomUnit > 1f) throw new IllegalArgumentException("randomUnit must be in [-1,1]");
        double elapsedSeconds = (now - state.lastFireTick()) / 20d;
        float theta = (float) Math.max(spread.minimumDegrees(), Math.min(spread.maximumDegrees(),
                state.currentSpreadDegrees() + spread.angleIncreaseDegrees() - spread.angleDecayDegreesPerSecond() * elapsedSeconds));
        float angle = randomUnit * theta * .5f;
        return new SpreadResult(theta, angle);
    }
    public record SpreadResult(float currentSpreadDegrees, float angleDegrees) { }
}
