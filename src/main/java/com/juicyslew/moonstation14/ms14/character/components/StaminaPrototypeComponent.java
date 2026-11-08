package com.juicyslew.moonstation14.ms14.character.components;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

/** Immutable StaminaComponent policy. Mutable damage/timers belong to StaminaState, not a prototype. */
public record StaminaPrototypeComponent(float baseCritThreshold, float decay, float cooldownSeconds,
                                        float stunSeconds, float afterCritDecayMultiplier,
                                        Map<Double, Double> slowdownThresholds) implements CharacterComponent {
    public static final String TYPE = "Stamina";
    public static final StaminaPrototypeComponent DEFAULT = new StaminaPrototypeComponent(100f, 3f, 3f, 6f, 5f,
            Map.of(0.0, 1.0, 0.6, 0.7, 0.8, 0.5));
    public static final Codec<StaminaPrototypeComponent> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("type").forGetter(StaminaPrototypeComponent::type),
            Codec.FLOAT.optionalFieldOf("base_crit_threshold", DEFAULT.baseCritThreshold).forGetter(StaminaPrototypeComponent::baseCritThreshold),
            Codec.FLOAT.optionalFieldOf("decay", DEFAULT.decay).forGetter(StaminaPrototypeComponent::decay),
            Codec.FLOAT.optionalFieldOf("cooldown_seconds", DEFAULT.cooldownSeconds).forGetter(StaminaPrototypeComponent::cooldownSeconds),
            Codec.FLOAT.optionalFieldOf("stun_seconds", DEFAULT.stunSeconds).forGetter(StaminaPrototypeComponent::stunSeconds),
            Codec.FLOAT.optionalFieldOf("after_crit_decay_multiplier", DEFAULT.afterCritDecayMultiplier).forGetter(StaminaPrototypeComponent::afterCritDecayMultiplier),
            Codec.unboundedMap(Codec.DOUBLE, Codec.DOUBLE).optionalFieldOf("slowdown_thresholds", DEFAULT.slowdownThresholds).forGetter(StaminaPrototypeComponent::slowdownThresholds)
    ).apply(i, (type, threshold, decay, cooldown, stun, multiplier, slowdowns) -> {
        if (!TYPE.equals(type)) throw new IllegalArgumentException("unknown component type: " + type);
        return new StaminaPrototypeComponent(threshold, decay, cooldown, stun, multiplier, slowdowns);
    }));

    public StaminaPrototypeComponent {
        if (!Float.isFinite(baseCritThreshold) || baseCritThreshold <= 0 || !Float.isFinite(decay) || decay < 0
                || !Float.isFinite(cooldownSeconds) || cooldownSeconds < 0 || !Float.isFinite(stunSeconds) || stunSeconds < 0
                || !Float.isFinite(afterCritDecayMultiplier) || afterCritDecayMultiplier < 0 || slowdownThresholds == null || slowdownThresholds.isEmpty())
            throw new IllegalArgumentException("invalid stamina policy");
        TreeMap<Double, Double> sorted = new TreeMap<>();
        slowdownThresholds.forEach((threshold, multiplier) -> {
            if (threshold == null || multiplier == null || !Double.isFinite(threshold) || !Double.isFinite(multiplier)
                    || threshold < 0 || threshold > 1 || multiplier < 0 || multiplier > 1)
                throw new IllegalArgumentException("invalid stamina slowdown threshold");
            sorted.put(threshold, multiplier);
        });
        if (!sorted.containsKey(0.0)) throw new IllegalArgumentException("stamina slowdown thresholds require zero baseline");
        slowdownThresholds = Collections.unmodifiableMap(sorted);
    }
    @Override public String type() { return TYPE; }
}
