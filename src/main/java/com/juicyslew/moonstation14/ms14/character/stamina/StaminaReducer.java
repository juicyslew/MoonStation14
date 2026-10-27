package com.juicyslew.moonstation14.ms14.character.stamina;

import com.juicyslew.moonstation14.ms14.character.components.StaminaPrototypeComponent;
import java.util.Optional;

/** Pure transition math. Caller admits/modifies events before applying returned deltas. One tick is 1/20s. */
public final class StaminaReducer {
    private static final long TICKS_PER_SECOND = 20;
    private static final long CRIT_BUFFER_TICKS = 60; // SharedStaminaSystem constant, not prototype data.
    private StaminaReducer() { }
    public record DeltaResult(StaminaState state, boolean enteredCritical) { }
    public record RecoveryPlan(StaminaState state, float requestedDelta) { }

    public static DeltaResult applyDelta(StaminaPrototypeComponent policy, StaminaState state, float delta, long nowTick) {
        if (policy == null || state == null || !Float.isFinite(delta) || nowTick < 0) throw new IllegalArgumentException("invalid stamina transition input");
        if (state.critical()) return new DeltaResult(state, false);
        float nextDamage = Math.max(0, state.damage() + delta);
        long deadline = state.nextUpdateTick();
        if (nextDamage > state.damage()) deadline = Math.max(deadline, nowTick + secondsToTicks(policy.cooldownSeconds()));
        boolean afterCrit = state.afterCritical();
        if (afterCrit && nextDamage < state.damage() && nextDamage <= 0) afterCrit = false;
        boolean entered = nextDamage >= policy.baseCritThreshold();
        if (entered) {
            nextDamage = policy.baseCritThreshold();
            deadline = nowTick + secondsToTicks(policy.stunSeconds()) + CRIT_BUFFER_TICKS;
        }
        return new DeltaResult(new StaminaState(nextDamage, entered, afterCrit, deadline), entered);
    }

    /** Advances at most one recovery deadline and returns a delta for the ordinary event path to admit. */
    public static Optional<RecoveryPlan> recoveryPlan(StaminaPrototypeComponent policy, StaminaState state, long nowTick) {
        if (policy == null || state == null || nowTick < 0) throw new IllegalArgumentException("invalid stamina recovery input");
        if ((state.damage() <= 0 && !state.critical()) || nowTick < state.nextUpdateTick()) return Optional.empty();
        StaminaState advanced = state;
        if (state.critical()) advanced = new StaminaState(state.damage(), false, true, nowTick);
        long deadline = advanced.nextUpdateTick() + TICKS_PER_SECOND;
        advanced = new StaminaState(advanced.damage(), advanced.critical(), advanced.afterCritical(), deadline);
        float decay = policy.decay() * (advanced.afterCritical() ? policy.afterCritDecayMultiplier() : 1);
        return Optional.of(new RecoveryPlan(advanced, -decay));
    }

    public static double slowdownMultiplier(StaminaPrototypeComponent policy, StaminaState state) {
        double fraction = state.damage() / policy.baseCritThreshold();
        double result = 1;
        for (var entry : policy.slowdownThresholds().entrySet()) if (fraction >= entry.getKey()) result = entry.getValue(); else break;
        return result;
    }
    private static long secondsToTicks(float seconds) { return (long) (seconds * TICKS_PER_SECOND); }
}
