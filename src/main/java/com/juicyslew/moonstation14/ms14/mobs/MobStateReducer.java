package com.juicyslew.moonstation14.ms14.mobs;

import com.juicyslew.moonstation14.ms14.character.components.MobStateComponent;
import com.juicyslew.moonstation14.ms14.character.components.MobThresholdsComponent;
import java.util.Objects;

/** Pure state transition rules driven by resolved prototype components. */
public final class MobStateReducer {
    private MobStateReducer() {}
    public record State(MobStateComponent.State value, long revision) {
        public State { Objects.requireNonNull(value); if (revision < 0) throw new IllegalArgumentException("revision must be nonnegative"); }
    }
    public record Transition(State state, boolean changed) {}

    public static Transition damageOrHealing(State current, double totalDamage, MobStateComponent config,
                                             MobThresholdsComponent thresholds) {
        return reduce(current, totalDamage, config, thresholds, false);
    }
    public static Transition explicitRevive(State current, double totalDamage, MobStateComponent config,
                                            MobThresholdsComponent thresholds) {
        return reduce(current, totalDamage, config, thresholds, true);
    }
    private static Transition reduce(State current, double totalDamage, MobStateComponent config,
                                     MobThresholdsComponent thresholds, boolean explicitRevive) {
        Objects.requireNonNull(current); Objects.requireNonNull(config); Objects.requireNonNull(thresholds);
        if (!Double.isFinite(totalDamage) || totalDamage < 0) throw new IllegalArgumentException("damage must be finite and nonnegative");
        var desired = thresholds.thresholds().entrySet().stream().filter(e -> e.getKey() <= totalDamage)
                .max(java.util.Map.Entry.comparingByKey()).orElseThrow().getValue();
        if (!config.allowedStates().contains(desired)) return new Transition(current, false);
        if (current.value() == MobStateComponent.State.DEAD && desired != MobStateComponent.State.DEAD
                && !explicitRevive && !thresholds.allowRevives()) return new Transition(current, false);
        if (current.value() == desired) return new Transition(current, false);
        return new Transition(new State(desired, Math.addExact(current.revision(), 1)), true);
    }
}
