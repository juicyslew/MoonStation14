package com.juicyslew.moonstation14.ms14.blood;

import com.juicyslew.moonstation14.component.codec.json.CharacterData;
import com.juicyslew.moonstation14.component.codec.json.ReagentData;
import com.juicyslew.moonstation14.ms14.reagent.ReagentAttachment;
import com.juicyslew.moonstation14.ms14.reagent.ReagentUnits;
import net.minecraft.resources.ResourceKey;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.LinkedHashSet;

/** Pure composition-derived blood arithmetic, using the reagent cent representation. */
public final class BloodReducer {
    private BloodReducer() { }

    public static long scaledEffectUnits(float amount, float scale) {
        float scaled = amount * scale;
        if (!Float.isFinite(amount) || !Float.isFinite(scale) || !Float.isFinite(scaled))
            throw new IllegalArgumentException("effect amount and scale must be finite");
        return ReagentUnits.fromDouble(Math.abs((double) scaled));
    }
    public static double usableFraction(ReagentAttachment solution, Map<ResourceKey<ReagentData>, Long> reference) {
        return usableFraction(solution, reference, 1);
    }
    public static double usableFraction(ReagentAttachment solution, Map<ResourceKey<ReagentData>, Long> reference,
                                        double maxVolumeModifier) {
        if (reference == null || reference.isEmpty()) throw new IllegalArgumentException("reference blood must not be empty");
        if (!Double.isFinite(maxVolumeModifier) || maxVolumeModifier < 1)
            throw new IllegalArgumentException("invalid blood volume modifier");
        double minimum = maxVolumeModifier;
        for (var e : reference.entrySet()) {
            if (e.getValue() == null || e.getValue() <= 0) throw new IllegalArgumentException("reference amounts must be positive");
            minimum = Math.min(minimum, solution.snapshotUnits().getOrDefault(e.getKey(), 0L) / (double)e.getValue());
        }
        return minimum;
    }
    public static Map<ResourceKey<ReagentData>, Long> reference(CharacterData.BloodData policy) {
        Map<ResourceKey<ReagentData>, Long> result = new LinkedHashMap<>();
        policy.referenceSolution().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e -> {
            long units = ReagentUnits.fromDouble(e.getValue());
            if (units <= 0) throw new IllegalArgumentException("reference reagent must be positive");
            result.put(ResourceKey.create(com.juicyslew.moonstation14.ms14.reagent.ModReagents.REAGENT_REGISTRY_KEY,
                    net.minecraft.resources.ResourceLocation.parse(e.getKey())), units);
        });
        if (result.isEmpty()) throw new IllegalArgumentException("reference blood must not be empty");
        return Map.copyOf(result);
    }
    public static long capacity(CharacterData.BloodData policy) {
        if (policy == null || policy.referenceSolution() == null || policy.referenceSolution().isEmpty()
                || !Double.isFinite(policy.maxVolumeModifier()) || policy.maxVolumeModifier() < 0) {
            throw new IllegalArgumentException("invalid blood capacity policy");
        }
        long referenceCents = ReagentUnits.total(policy.referenceSolution().values().stream()
                .map(ReagentUnits::fromDouble).toList());
        // Keep capacity in the same explicit cent domain as storage. The final conversion
        // rejects non-finite or out-of-range policy results instead of overflowing later.
        double capacityUnits = referenceCents / 100d * policy.maxVolumeModifier();
        return ReagentUnits.fromDouble(capacityUnits);
    }

    /** Remove the actual, available bloodstream composition for one bounded bleed update. */
    public static Map<ResourceKey<ReagentData>, Long> removeBleedUnits(ReagentAttachment mixture, long requested) {
        ReagentUnits.validateCents(requested, "bleed removal");
        long bounded = Math.min(requested, mixture.totalUnits());
        Map<ResourceKey<ReagentData>, Long> removed = mixture.splitUnits(bounded);
        if (ReagentUnits.total(removed.values()) != bounded)
            throw new IllegalStateException("bleed removal did not match its bounded request");
        return removed;
    }

    /** Detached, full-accounting bleed staging into a temporary, uncapped batch. */
    public record BleedStage(ReagentAttachment bloodstream, ReagentAttachment pending, long removedUnits) { }

    public static BleedStage stageBleed(ReagentAttachment bloodstream, ReagentAttachment pending,
                                         long requestedUnits) {
        ReagentUnits.validateCents(requestedUnits, "requested bleed");
        ReagentAttachment stagedBlood = new ReagentAttachment(bloodstream.toComponent());
        ReagentAttachment stagedPending = new ReagentAttachment(pending.toComponent());
        long amount = Math.min(requestedUnits, stagedBlood.totalUnits());
        if (amount > ReagentUnits.MAX_CENTS - stagedPending.totalUnits())
            throw new IllegalArgumentException("temporary blood exceeds reagent representation");
        if (amount == 0) return new BleedStage(stagedBlood, stagedPending, 0);
        Map<ResourceKey<ReagentData>, Long> removed = removeBleedUnits(stagedBlood, amount);
        for (var entry : removed.entrySet()) {
            if (stagedPending.admitUnits(entry.getKey(), entry.getValue(), ReagentUnits.MAX_CENTS) != entry.getValue())
                throw new IllegalStateException("pending spill admission was not exact");
        }
        if (stagedPending.totalUnits() != pending.totalUnits() + amount)
            throw new IllegalStateException("pending spill staging lost mixture accounting");
        return new BleedStage(stagedBlood, stagedPending, amount);
    }

    /** Restore at most {@code requested} total units toward the configured composition. */
    public static long restoreTowardReference(ReagentAttachment mixture,
            Map<ResourceKey<ReagentData>, Long> reference, long requested, long capacity) {
        ReagentUnits.validateCents(requested, "requested refresh");
        ReagentUnits.validateCents(capacity, "blood capacity");
        long remaining = Math.min(requested, Math.max(0, capacity - mixture.totalUnits()));
        long initial = remaining;
        var eligible = new LinkedHashSet<ResourceKey<ReagentData>>();
        reference.keySet().stream().sorted().forEach(eligible::add);
        while (remaining > 0 && !eligible.isEmpty()) {
            var weights = new LinkedHashMap<ResourceKey<ReagentData>, Long>();
            for (var key : eligible) {
                long deficit = Math.max(0, reference.get(key) - mixture.snapshotUnits().getOrDefault(key, 0L));
                if (deficit > 0) weights.put(key, reference.get(key));
            }
            eligible.retainAll(weights.keySet());
            if (eligible.isEmpty()) break;
            long round = Math.min(remaining, ReagentUnits.total(weights.values()));
            var shares = ReagentUnits.split(weights, round);
            long admitted = 0;
            for (var key : eligible) {
                long deficit = reference.get(key) - mixture.snapshotUnits().getOrDefault(key, 0L);
                long amount = Math.min(deficit, shares.getOrDefault(key, 0L));
                if (amount > 0) admitted += mixture.admitUnits(key, amount, capacity);
            }
            if (admitted == 0) break;
            remaining -= admitted;
            // If rounding left a cent undistributed, loop and redistribute it among deficits.
        }
        return initial - remaining;
    }
    public static Map<String, Float> bloodloss(double fraction, CharacterData.BloodData policy, boolean healing) {
        if (!Double.isFinite(fraction) || fraction < 0 || fraction > policy.maxVolumeModifier())
            throw new IllegalArgumentException("invalid usable blood fraction");
        Map<String, Float> result = new LinkedHashMap<>();
        if (!healing && fraction < policy.bloodlossThresholdFraction()) {
            policy.bloodlossDamagePerUpdate().forEach((k,v) -> {
                if (v > 0) result.put(k, finiteFloat(v / (0.1 + fraction)));
            });
        } else if (healing && fraction >= policy.bloodlossThresholdFraction())
            policy.bloodlossHealPerUpdate().forEach((k,v) -> {
                if (v > 0) result.put(k, finiteFloat(-v * fraction));
            });
        return Map.copyOf(result);
    }
    private static float finiteFloat(double value) {
        if (!Double.isFinite(value) || !Float.isFinite((float)value))
            throw new IllegalArgumentException("bloodloss result must be finite");
        return (float)value;
    }
}
