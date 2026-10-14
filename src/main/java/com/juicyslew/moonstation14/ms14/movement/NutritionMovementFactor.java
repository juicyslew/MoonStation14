package com.juicyslew.moonstation14.ms14.movement;

import net.minecraft.world.entity.LivingEntity;

/** SS14 grounded nutrition movement projection: starvation and parched penalties stack multiplicatively. */
public final class NutritionMovementFactor {
    public static final double SLOWED_FACTOR = .75d;
    public static final double ATTRIBUTE_MODIFIER_AMOUNT = SLOWED_FACTOR - 1d;
    private NutritionMovementFactor() { }

    public static double resolve(boolean starving, boolean parched) {
        return (starving ? SLOWED_FACTOR : 1d) * (parched ? SLOWED_FACTOR : 1d);
    }

    public static double apply(double existingFactor, boolean starving, boolean parched) {
        if (!Double.isFinite(existingFactor) || existingFactor < 0d)
            throw new IllegalArgumentException("existing factor must be finite and nonnegative");
        return existingFactor * resolve(starving, parched);
    }

    /** The shared production seam used to author snapshot voluntary speed from authoritative body state. */
    public static double forActor(LivingEntity actor, double existingFactor) {
        return apply(existingFactor,
                com.juicyslew.moonstation14.ms14.hunger.HungerSystem.isStarving(actor),
                com.juicyslew.moonstation14.ms14.thirst.ThirstSystem.isParched(actor));
    }
}
