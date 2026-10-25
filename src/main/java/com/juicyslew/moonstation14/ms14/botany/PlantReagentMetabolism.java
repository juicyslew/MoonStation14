package com.juicyslew.moonstation14.ms14.botany;

import com.juicyslew.moonstation14.component.codec.json.PlantMetabolismData;
import com.juicyslew.moonstation14.component.codec.json.ReagentData;
import com.juicyslew.moonstation14.ms14.reagent.ReagentAttachment;
import net.minecraft.resources.ResourceKey;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/** Applies the supported fixed-dose reagent effects and consumes one unit per reagent each poll. */
public final class PlantReagentMetabolism {
    public static final long POLL_CENTS = 100;

    private PlantReagentMetabolism() { }

    public interface Target {
        void adjustWater(float amount);
        void adjustNutrition(float amount);
        default void adjustToxins(float amount) { }
        default void adjustWeeds(float amount) { }
        default void adjustPests(float amount) { }
        default void adjustHealth(float amount) { }
        default void adjustPotency(float amount) { }
        default void robustHarvest(PlantMetabolismData.RobustHarvest effect) { }
        void cryoxadone();
        void affectGrowth(int amount);
        void diethylamine();
        void phalanximine();
        void restoreSeeds();
        default void adjustMutationLevel(float amount) { }
        default void adjustMutationModifier(float amount) { }
        /** Targets must explicitly prove a living plant; unknown/default targets are inactive. */
        default boolean hasLivingPlant() { return false; }
        /** Source holder admission: all reagent effects and drains are skipped at mutation level 25+. */
        default boolean admitsMetabolismPoll() { return true; }
        /** Called once for an admitted poll with a nonempty source snapshot. */
        default void beginNonemptyPoll() { }
        default float nextFloat() { return 0f; }
        /** Stable per-holder/per-tick probability sample; independent of effect RNG draws. */
        double probabilitySample();
        default double nextDouble() { return nextFloat(); }
        default boolean hasChemicalMutationAdapter() { return false; }
        default void prepareChemicalMutation(String tableId) { }
        default boolean mutateChemicals(String tableId) { return false; }
    }

    public record UnsupportedEffect(String reagentId, String effectType) { }
    public record Result(List<UnsupportedEffect> unsupportedEffects) {
        public Result { unsupportedEffects = List.copyOf(unsupportedEffects); }
    }

    /** Resolve the whole snapshot before effects/removals, so stale catalog entries fail closed. */
    public static Result poll(ReagentAttachment source,
                              Function<ResourceKey<ReagentData>, ReagentData> resolvedLookup,
                              Target target) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(resolvedLookup, "resolvedLookup");
        Objects.requireNonNull(target, "target");
        if (!target.admitsMetabolismPoll()) return new Result(List.of());

        Map<ResourceKey<ReagentData>, Long> snapshot = source.snapshotUnits();
        Map<ResourceKey<ReagentData>, ReagentData> resolved = new LinkedHashMap<>();
        snapshot.forEach((key, cents) -> {
            if (cents > 0) resolved.put(key, Objects.requireNonNull(resolvedLookup.apply(key),
                    "Unknown reagent in metabolism source: " + key.location()));
        });

        // Preflight all descriptors before any effect or source drain. Missing support is reported,
        // whereas a configured adapter that rejects an unknown table/member fails closed.
        for (var entry : snapshot.entrySet()) if (entry.getValue() >= POLL_CENTS) {
            ReagentData reagent = resolved.get(entry.getKey());
            for (PlantMetabolismData effect : reagent.plantMetabolism())
                if (effect instanceof PlantMetabolismData.PlantMutateChemicals chemicals
                        && target.hasChemicalMutationAdapter() && target.hasLivingPlant())
                    target.prepareChemicalMutation(chemicals.randomPickBotanyReagent());
        }

        // COW only after every resolved reagent and adapter preflight has accepted the batch,
        // but before any effect, probability sample, or drain can observe/mutate plant genetics.
        if (!snapshot.isEmpty()) target.beginNonemptyPoll();

        List<UnsupportedEffect> unsupported = new ArrayList<>();
        for (var entry : snapshot.entrySet()) {
            if (entry.getValue() < POLL_CENTS) continue;
            ReagentData reagent = resolved.get(entry.getKey());
            float effectScale = entry.getValue() / 100f;
            for (PlantMetabolismData effect : reagent.plantMetabolism()) {
                if (effect instanceof PlantMetabolismData.PlantAdjustWater water) {
                    target.adjustWater(water.amount());
                } else if (effect instanceof PlantMetabolismData.PlantAdjustNutrition nutrition) {
                    target.adjustNutrition(nutrition.amount());
                } else if (effect instanceof PlantMetabolismData.PlantAdjustHealth health) {
                    if (passesProbability(target, health.probability()) && target.hasLivingPlant()) target.adjustHealth(health.amount());
                } else if (effect instanceof PlantMetabolismData.PlantAdjustToxins toxins) {
                    if (passesProbability(target, toxins.probability()) && target.hasLivingPlant()) target.adjustToxins(toxins.amount());
                } else if (effect instanceof PlantMetabolismData.PlantAdjustWeeds weeds) {
                    if (passesProbability(target, weeds.probability()) && target.hasLivingPlant()) target.adjustWeeds(weeds.amount());
                } else if (effect instanceof PlantMetabolismData.PlantAdjustPests pests) {
                    if (passesProbability(target, pests.probability()) && target.hasLivingPlant()) target.adjustPests(pests.amount());
                } else if (effect instanceof PlantMetabolismData.PlantAdjustPotency potency) {
                    if (passesProbability(target, potency.probability()) && target.hasLivingPlant()) target.adjustPotency(potency.amount());
                } else if (effect instanceof PlantMetabolismData.RobustHarvest robust) {
                    if (passesProbability(target, robust.probability()) && target.hasLivingPlant()) {
                        // SS14 only rolls when potency is already at/above the configured limit
                        // and yield exceeds one; target owns these state-dependent conditions.
                        target.robustHarvest(robust);
                    }
                } else if (effect instanceof PlantMetabolismData.PlantCryoxadone cryo) {
                    if (passesProbability(target, cryo.probability()) && target.hasLivingPlant()) target.cryoxadone();
                } else if (effect instanceof PlantMetabolismData.PlantAffectGrowth growth) {
                    if (passesProbability(target, growth.probability()) && target.hasLivingPlant())
                        target.affectGrowth((int) growth.amount());
                } else if (effect instanceof PlantMetabolismData.PlantDiethylamine diethylamine) {
                    if (passesProbability(target, diethylamine.probability()) && target.hasLivingPlant()) target.diethylamine();
                } else if (effect instanceof PlantMetabolismData.PlantPhalanximine phalanximine) {
                    // SS14's TryApplyEffect ordering is MinScale, probability, living-holder guard.
                    if (effectScale >= phalanximine.minScale()
                            && passesProbability(target, phalanximine.probability()) && target.hasLivingPlant()) target.phalanximine();
                } else if (effect instanceof PlantMetabolismData.PlantRestoreSeeds restore) {
                    if (passesProbability(target, restore.probability()) && target.hasLivingPlant()) target.restoreSeeds();
                } else if (effect instanceof PlantMetabolismData.PlantAdjustMutationLevel level) {
                    if (target.hasLivingPlant()) target.adjustMutationLevel(level.amount());
                } else if (effect instanceof PlantMetabolismData.PlantAdjustMutationMod modifier) {
                    if (passesProbability(target, modifier.probability()) && target.hasLivingPlant())
                        target.adjustMutationModifier(modifier.amount());
                } else if (effect instanceof PlantMetabolismData.PlantMutateChemicals chemicals) {
                    if (!target.hasLivingPlant()) continue;
                    if (!target.hasChemicalMutationAdapter()
                            || !target.mutateChemicals(chemicals.randomPickBotanyReagent()))
                        unsupported.add(new UnsupportedEffect(reagent.id(), effect.type()));
                } else {
                    unsupported.add(new UnsupportedEffect(reagent.id(), effect.type()));
                }
            }
        }
        for (ResourceKey<ReagentData> key : resolved.keySet()) source.removeUnits(key, POLL_CENTS);
        return new Result(unsupported);
    }
    private static boolean passesProbability(Target target, float probability) {
        return probability > 1f || target.probabilitySample() < probability;
    }
}
