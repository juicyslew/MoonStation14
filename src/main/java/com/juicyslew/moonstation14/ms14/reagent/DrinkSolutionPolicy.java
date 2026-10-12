package com.juicyslew.moonstation14.ms14.reagent;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** Supported SS14 Edible component projection. Delay is personal delay in ticks. */
public record DrinkSolutionPolicy(boolean enabled, String solutionName, float consumeAmount, int personalDelayTicks) {
    public static final Codec<DrinkSolutionPolicy> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.BOOL.optionalFieldOf("enabled", false).forGetter(DrinkSolutionPolicy::enabled),
            Codec.STRING.optionalFieldOf("solutionName", "food").forGetter(DrinkSolutionPolicy::solutionName),
            Codec.FLOAT.optionalFieldOf("consumeAmount", 5f).forGetter(DrinkSolutionPolicy::consumeAmount),
            Codec.INT.optionalFieldOf("personalDelayTicks", 20).forGetter(DrinkSolutionPolicy::personalDelayTicks)
    ).apply(i, DrinkSolutionPolicy::new));
    public static final DrinkSolutionPolicy DISABLED = new DrinkSolutionPolicy(false, "food", 5f, 20);
    public DrinkSolutionPolicy {
        if (solutionName == null || solutionName.isBlank() || !Float.isFinite(consumeAmount) || consumeAmount <= 0 || personalDelayTicks < 0)
            throw new IllegalArgumentException("invalid drink policy");
    }
}
