package com.juicyslew.moonstation14.ms14.metabolism;

import com.juicyslew.moonstation14.component.codec.json.MetabolismData;
import com.juicyslew.moonstation14.component.codec.json.ReagentData;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeCatalog;
import com.juicyslew.moonstation14.ms14.reagent.ReagentAttachment;
import com.juicyslew.moonstation14.ms14.reagent.ReagentUnits;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Pure orchestration for staged metabolism of one reagent compartment. */
public final class MetabolismSystem {
    private static final Comparator<ResourceKey<ReagentData>> CANONICAL_KEY_ORDER =
            Comparator.comparing(key -> key.location().toString());

    public MetabolismSystem() {
    }

    public static MetabolismReport process(
            ReagentAttachment compartment,
            PrototypeCatalog<ReagentData> catalog,
            MetabolizerProfile profile,
            float capacity,
            RandomSource random,
            MetabolismEffectCallback effectCallback) {
        Objects.requireNonNull(catalog, "catalog");
        return process(compartment, catalog.asMap(), profile, capacity, random, effectCallback);
    }

    public static MetabolismReport process(
            ReagentAttachment compartment,
            Map<ResourceLocation, ReagentData> catalog,
            MetabolizerProfile profile,
            float capacity,
            RandomSource random,
            MetabolismEffectCallback effectCallback) {
        return process(compartment, compartment, catalog, profile, capacity, random, effectCallback);
    }

    /** Processes one source compartment while routing generated metabolites to a separate destination. */
    public static MetabolismReport process(
            ReagentAttachment compartment,
            ReagentAttachment metaboliteDestination,
            Map<ResourceLocation, ReagentData> catalog,
            MetabolizerProfile profile,
            float capacity,
            RandomSource random,
            MetabolismEffectCallback effectCallback) {
        return process(compartment, metaboliteDestination, catalog, profile, capacity, capacity,
                random, effectCallback);
    }

    /** Processes a source with its own capacity and routes products to a separately bounded destination. */
    public static MetabolismReport process(
            ReagentAttachment compartment,
            ReagentAttachment metaboliteDestination,
            Map<ResourceLocation, ReagentData> catalog,
            MetabolizerProfile profile,
            float sourceCapacity,
            float destinationCapacity,
            RandomSource random,
            MetabolismEffectCallback effectCallback) {
        validateInputs(compartment, catalog, profile, sourceCapacity, random, effectCallback);
        Objects.requireNonNull(metaboliteDestination, "metaboliteDestination");
        validateCompartment(metaboliteDestination, destinationCapacity, "destination");
        long destinationCapacityUnits = ReagentUnits.fromFloat(destinationCapacity);

        List<MetabolismReport.MetabolismAttempt> attempts = new ArrayList<>();
        for (MetabolismStage stage : profile.stages()) {
            List<ResourceKey<ReagentData>> candidates = currentCandidates(compartment, random);
            int processed = 0;
            for (ResourceKey<ReagentData> key : candidates) {
                if (processed >= profile.perStageProcessCap()) {
                    break;
                }

                // Both the amount and the prototype are intentionally looked up
                // again here. Earlier attempts in this and prior stages may have
                // changed the live compartment.
                long currentUnits = compartment.snapshotUnits().getOrDefault(key, 0L);
                if (currentUnits <= 0L) {
                    continue;
                }
                ReagentData reagent = catalog.get(key.location());
                if (reagent == null) {
                    continue;
                }
                MetabolismData metabolism = reagent.metabolisms().get(stage);
                if (metabolism == null) {
                    continue;
                }

                // The snapshot is taken at the last possible moment, directly
                // before the ordinary removal.
                SourceSnapshot sourceSnapshot = SourceSnapshot.capture(compartment, key, reagent);
                MetabolismMath.CentMetabolismPlan<ResourceKey<ReagentData>> plan = MetabolismMath.metabolizeUnits(
                        currentUnits, metabolism.rate(), metabolism.metabolites());
                long actualRemovedUnits = compartment.removeUnits(key, plan.actualRemovedUnits());
                if (actualRemovedUnits <= 0L) {
                    continue;
                }
                if (actualRemovedUnits != plan.actualRemovedUnits())
                    throw new IllegalStateException("cent-native metabolism removal differed from its preflight");
                MetabolismResult<ResourceKey<ReagentData>> result = plan.toFloatResult(plan.requestedProductionUnits());
                MetabolismEffectInvocation invocation = new MetabolismEffectInvocation(
                        key, stage, sourceSnapshot, metabolism, result);

                ReagentAttachment.UnitsCapacityAddResult<ResourceKey<ReagentData>> exactCapacityResult;
                try {
                    effectCallback.execute(invocation);
                } catch (RuntimeException | Error callbackFailure) {
                    // Product addition is part of the attempt and must still be
                    // tried when effect execution fails. Preserve the callback
                    // failure as the primary exception if that addition fails too.
                    try {
                         metaboliteDestination.addUnitsCapacitySafe(plan.requestedProductionUnits(), destinationCapacityUnits);
                    } catch (RuntimeException | Error additionFailure) {
                        callbackFailure.addSuppressed(additionFailure);
                    }
                    throw callbackFailure;
                }
                exactCapacityResult = metaboliteDestination.addUnitsCapacitySafe(
                        plan.requestedProductionUnits(), destinationCapacityUnits);
                ReagentAttachment.CapacityAddResult<ResourceKey<ReagentData>> capacityResult = floatCapacityResult(exactCapacityResult);

                attempts.add(new MetabolismReport.MetabolismAttempt(
                        key, stage, sourceSnapshot, metabolism, result, capacityResult, exactCapacityResult));
                processed++;
            }
        }
        return new MetabolismReport(attempts);
    }

    public static MetabolismReport process(
            ReagentAttachment compartment,
            Map<ResourceLocation, ReagentData> catalog,
            MetabolizerProfile profile,
            RandomSource random,
            float capacity,
            MetabolismEffectCallback effectCallback) {
        return process(compartment, catalog, profile, capacity, random, effectCallback);
    }

    public static MetabolismReport process(
            ReagentAttachment compartment,
            PrototypeCatalog<ReagentData> catalog,
            float capacity,
            RandomSource random,
            MetabolizerProfile profile,
            MetabolismEffectCallback effectCallback) {
        return process(compartment, catalog, profile, capacity, random, effectCallback);
    }

    public static MetabolismReport metabolize(
            ReagentAttachment compartment,
            Map<ResourceLocation, ReagentData> catalog,
            MetabolizerProfile profile,
            float capacity,
            RandomSource random,
            MetabolismEffectCallback effectCallback) {
        return process(compartment, catalog, profile, capacity, random, effectCallback);
    }

    /** Immutable point-in-time quantities visible to an effect callback. */
    public record SourceSnapshot(
            ResourceKey<ReagentData> reagent,
            ReagentData prototype,
            float amount,
            Map<ResourceKey<ReagentData>, Float> quantities
    ) {
        public SourceSnapshot {
            Objects.requireNonNull(reagent, "reagent");
            Objects.requireNonNull(prototype, "prototype");
            if (!Float.isFinite(amount) || amount <= 0f) {
                throw new IllegalArgumentException("amount must be finite and strictly positive");
            }
            quantities = immutableQuantities(quantities);
        }

        private static Map<ResourceKey<ReagentData>, Float> immutableQuantities(
                Map<ResourceKey<ReagentData>, Float> source) {
            Objects.requireNonNull(source, "quantities");
            Map<ResourceKey<ReagentData>, Float> copy = new LinkedHashMap<>();
            for (Map.Entry<ResourceKey<ReagentData>, Float> entry : source.entrySet()) {
                Objects.requireNonNull(entry.getKey(), "quantity key");
                Float value = entry.getValue();
                if (value == null || !Float.isFinite(value) || value < 0f) {
                    throw new IllegalArgumentException("quantities must be finite and nonnegative");
                }
                copy.put(entry.getKey(), value);
            }
            return Map.copyOf(copy);
        }

        public Map<ResourceKey<ReagentData>, Float> getMap() {
            return quantities;
        }

        private static SourceSnapshot capture(
                ReagentAttachment compartment,
                ResourceKey<ReagentData> reagent,
                ReagentData prototype) {
            Map<ResourceKey<ReagentData>, Float> quantities = new LinkedHashMap<>(compartment.getMap());
            Float amount = quantities.get(reagent);
            if (amount == null || !Float.isFinite(amount) || amount <= 0f) {
                throw new IllegalArgumentException("source reagent must have a positive amount");
            }
            return new SourceSnapshot(reagent, prototype, amount, quantities);
        }
    }

    private static List<ResourceKey<ReagentData>> currentCandidates(
            ReagentAttachment compartment, RandomSource random) {
        List<ResourceKey<ReagentData>> candidates = new ArrayList<>();
        for (Map.Entry<ResourceKey<ReagentData>, Long> entry : compartment.snapshotUnits().entrySet()) {
            Long amount = entry.getValue();
            if (amount != null && amount > 0L) {
                candidates.add(entry.getKey());
            }
        }
        candidates.sort(CANONICAL_KEY_ORDER);
        // Fisher-Yates, deliberately driven only by the injected source.
        for (int i = candidates.size() - 1; i > 0; i--) {
            int swap = random.nextInt(i + 1);
            ResourceKey<ReagentData> value = candidates.get(i);
            candidates.set(i, candidates.get(swap));
            candidates.set(swap, value);
        }
        return candidates;
    }

    private static void validateInputs(
            ReagentAttachment compartment,
            Map<ResourceLocation, ReagentData> catalog,
            MetabolizerProfile profile,
            float capacity,
            RandomSource random,
            MetabolismEffectCallback effectCallback) {
        Objects.requireNonNull(compartment, "compartment");
        Objects.requireNonNull(catalog, "catalog");
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(random, "random");
        Objects.requireNonNull(effectCallback, "effectCallback");
        if (!Float.isFinite(capacity) || capacity < 0f) {
            throw new IllegalArgumentException("capacity must be finite and nonnegative");
        }

        validateCompartment(compartment, capacity, "compartment");
        for (Map.Entry<ResourceLocation, ReagentData> entry : catalog.entrySet()) {
            Objects.requireNonNull(entry.getKey(), "catalog key");
            Objects.requireNonNull(entry.getValue(), "catalog prototype");
        }
    }

    private static void validateCompartment(ReagentAttachment compartment, float capacity, String label) {
        if (!Float.isFinite(capacity) || capacity < 0f) {
            throw new IllegalArgumentException(label + " capacity must be finite and nonnegative");
        }
        long cap = ReagentUnits.fromFloat(capacity);
        long total = 0L;
        for (Map.Entry<ResourceKey<ReagentData>, Long> entry : compartment.snapshotUnits().entrySet()) {
            Objects.requireNonNull(entry.getKey(), "compartment key");
            Long amount = entry.getValue();
            if (amount == null || amount < 0L) throw new IllegalArgumentException("compartment amounts must be nonnegative");
            total = Math.addExact(total, amount);
        }
        if (total > cap) {
            throw new IllegalArgumentException(label + " is already over capacity");
        }
    }

    private static ReagentAttachment.CapacityAddResult<ResourceKey<ReagentData>> floatCapacityResult(
            ReagentAttachment.UnitsCapacityAddResult<ResourceKey<ReagentData>> result) {
        Map<ResourceKey<ReagentData>, Float> requested = new LinkedHashMap<>(), retained = new LinkedHashMap<>(), excess = new LinkedHashMap<>();
        result.requested().forEach((key, value) -> requested.put(key, ReagentUnits.toFloat(value)));
        result.retained().forEach((key, value) -> retained.put(key, ReagentUnits.toFloat(value)));
        result.excess().forEach((key, value) -> excess.put(key, ReagentUnits.toFloat(value)));
        return new ReagentAttachment.CapacityAddResult<>(requested, retained, excess);
    }

    /** Convenience overload for the normal shared-living schedule. */
    public static MetabolismReport process(
            ReagentAttachment compartment,
            PrototypeCatalog<ReagentData> catalog,
            float capacity,
            RandomSource random,
            MetabolismEffectCallback effectCallback) {
        return process(compartment, catalog, MetabolizerProfile.SHARED_LIVING, capacity, random, effectCallback);
    }
}
