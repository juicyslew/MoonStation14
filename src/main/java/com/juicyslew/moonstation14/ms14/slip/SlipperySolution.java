package com.juicyslew.moonstation14.ms14.slip;

import com.juicyslew.moonstation14.component.codec.json.ReagentData;
import com.juicyslew.moonstation14.component.codec.json.SlipData;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeCatalog;
import com.juicyslew.moonstation14.ms14.reagent.ReagentUnits;
import net.minecraft.resources.ResourceKey;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Pure SS14-compatible slip profile aggregation. Quantities are cent-backed units. */
public final class SlipperySolution {
    /** SS14 overflow volume (50 units) multiplied by the low threshold (0.3). */
    public static final long LOW_VOLUME_CUTOFF_CENTS = 1_500L;
    public static final double DEFAULT_SLIP_SPEED = 3.5d;
    public static final double NON_SLIPPERY_DEFAULT_SLIP_SPEED = 5.5d;
    public static final double DEFAULT_STUN_SECONDS = 0.5d;
    public static final double DEFAULT_KNOCKDOWN_SECONDS = 1.5d;
    public static final double DEFAULT_LAUNCH_VELOCITY_MULTIPLIER = 1.5d;
    public static final double DEFAULT_FRICTION = 1.0d;

    private SlipperySolution() {
    }

    /**
     * Calculates weighted slip properties without changing the provided solution.
     * Slip speed and friction are weighted over the entire mixture; launch/stun/
     * knockdown properties are weighted over slip-data reagents only.
     */
    public static Outcome calculate(Map<ResourceKey<ReagentData>, Long> centSnapshot,
                                    PrototypeCatalog<ReagentData> reagents) {
        Objects.requireNonNull(centSnapshot, "centSnapshot");
        Objects.requireNonNull(reagents, "reagents");

        Map<ResourceKey<ReagentData>, Long> ordered = new LinkedHashMap<>();
        centSnapshot.entrySet().stream()
                .peek(entry -> Objects.requireNonNull(entry.getKey(), "reagent key"))
                .sorted((left, right) -> left.getKey().location().compareTo(right.getKey().location()))
                .forEach(entry -> ordered.put(entry.getKey(), ReagentUnits.validateCents(
                        Objects.requireNonNull(entry.getValue(), "reagent quantity"), "reagent quantity")));
        long totalCents = ReagentUnits.total(ordered.values());
        double totalWeightedSpeed = 0d;
        double totalWeightedFriction = 0d;
        double slipperyWeightedStun = 0d;
        double slipperyWeightedKnockdown = 0d;
        double slipperyWeightedLaunch = 0d;
        long slipperyCents = 0L;
        long superSlipperyCents = 0L;

        for (Map.Entry<ResourceKey<ReagentData>, Long> entry : ordered.entrySet()) {
            ReagentData reagent = reagents.get(entry.getKey().location());
            if (reagent == null) {
                throw new IllegalArgumentException("Missing reagent prototype: " + entry.getKey().location());
            }
            long quantity = entry.getValue();
            SlipData slip = reagent.slipData().orElse(null);
            double speed = slip == null ? NON_SLIPPERY_DEFAULT_SLIP_SPEED : nonnegative(
                    slip.requiredSlipSpeed(), "required slip speed", reagent.id());
            double friction = reagent.friction().map(value -> nonnegative(value, "friction", reagent.id()))
                    .orElse(DEFAULT_FRICTION);
            totalWeightedSpeed += quantity * speed;
            totalWeightedFriction += quantity * friction;
            if (slip != null) {
                slipperyCents = Math.addExact(slipperyCents, quantity);
                if (slip.superSlippery().orElse(false)) {
                    superSlipperyCents = Math.addExact(superSlipperyCents, quantity);
                }
                slipperyWeightedStun += quantity * optionalNonnegative(slip.stunTime(), DEFAULT_STUN_SECONDS,
                        "stun time", reagent.id());
                slipperyWeightedKnockdown += quantity * optionalNonnegative(slip.knockdownTime(),
                        DEFAULT_KNOCKDOWN_SECONDS, "knockdown time", reagent.id());
                slipperyWeightedLaunch += quantity * optionalNonnegative(slip.launchVelocityMultiplier(),
                        DEFAULT_LAUNCH_VELOCITY_MULTIPLIER, "launch velocity multiplier", reagent.id());
            }
        }

        boolean inert = totalCents <= LOW_VOLUME_CUTOFF_CENTS;
        boolean slippery = !inert && slipperyCents > LOW_VOLUME_CUTOFF_CENTS;
        boolean superSlippery = !inert && superSlipperyCents >= LOW_VOLUME_CUTOFF_CENTS;
        double volume = totalCents / 100d;
        double slipperyVolume = slipperyCents / 100d;
        return new Outcome(totalCents, slipperyCents, superSlipperyCents, inert, slippery, superSlippery,
                totalCents == 0 ? DEFAULT_SLIP_SPEED : totalWeightedSpeed / totalCents,
                totalCents == 0 ? DEFAULT_FRICTION : totalWeightedFriction / totalCents,
                slipperyCents == 0 ? DEFAULT_STUN_SECONDS : slipperyWeightedStun / slipperyCents,
                slipperyCents == 0 ? DEFAULT_KNOCKDOWN_SECONDS : slipperyWeightedKnockdown / slipperyCents,
                slipperyCents == 0 ? DEFAULT_LAUNCH_VELOCITY_MULTIPLIER : slipperyWeightedLaunch / slipperyCents,
                volume, slipperyVolume);
    }

    private static double optionalNonnegative(java.util.Optional<Float> value, double fallback,
                                              String field, String reagent) {
        return value.map(number -> nonnegative(number, field, reagent)).orElse(fallback);
    }

    private static double nonnegative(float value, String field, String reagent) {
        if (!Float.isFinite(value) || value < 0f) {
            throw new IllegalArgumentException(field + " must be finite and nonnegative for " + reagent);
        }
        return value;
    }

    /** Speeds are blocks/second; friction is a dimensionless coefficient; durations are seconds. */
    public record Outcome(long totalCents, long slipperyCents, long superSlipperyCents,
                          boolean inert, boolean slippery, boolean superSlippery,
                          double requiredSlipSpeedBlocksPerSecond, double friction,
                          double stunSeconds, double knockdownSeconds,
                          double launchVelocityMultiplier, double totalUnits, double slipperyUnits) {
    }
}
