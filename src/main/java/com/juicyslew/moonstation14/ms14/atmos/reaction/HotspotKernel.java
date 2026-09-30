package com.juicyslew.moonstation14.ms14.atmos.reaction;

import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeCatalog;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeSet;
import net.minecraft.resources.ResourceLocation;

/** Detached one-step partial-volume fire. No cell ownership, persistence or spread is implied. */
public final class HotspotKernel {
    private static final double IGNITION_KELVIN = 373.15;
    private static final double SEED_FRACTION = 0.10;
    private static final double FULL_THRESHOLD = 0.95;
    // SS14's 40,000 L/mol growth divided by the local 1,000 L cell: provisional tuning.
    private static final double GROWTH_PER_MOLE = 40;

    private HotspotKernel() {}

    /** Temperature is informational, never an independent energy source. */
    public record HotspotState(double fraction, double temperatureKelvin) {
        public HotspotState {
            if (!Double.isFinite(fraction) || fraction <= 0 || fraction > 1
                    || !Double.isFinite(temperatureKelvin) || temperatureKelvin < 0)
                throw new IllegalArgumentException("invalid hotspot state");
        }
    }

    /** Empty nextState means quenched; events and extent contain only actual fire reactions. */
    public record Result(GasMixture mixture, List<GasReactionEvaluator.Event> events,
                         double totalFireExtentMoles, Optional<HotspotState> nextState) {
        public Result {
            events = List.copyOf(events);
            nextState = Objects.requireNonNull(nextState, "nextState");
        }
    }

    /** Null state seeds a new hotspot; an existing fraction is retained after external cooling/diffusion. */
    public static Result evaluate(GasMixture gas, PrototypeCatalog<GasReactionData> catalog, HotspotState state) {
        Objects.requireNonNull(gas, "gas");
        Objects.requireNonNull(catalog, "catalog");
        if (!viable(gas)) return quenched(gas);

        // Only full-cell eligible prototypes may ignite. Try the smallest fractions which
        // satisfy their portion gates, then verify actual effect viability on a detached portion.
        Map<ResourceLocation, GasReactionData> eligible = new LinkedHashMap<>();
        TreeSet<Double> candidates = new TreeSet<>();
        double base = state == null ? SEED_FRACTION : state.fraction();
        for (var entry : catalog.asMap().entrySet()) {
            var reaction = entry.getValue();
            if (gas.temperatureKelvin() < reaction.minimumTemperature()
                    || gas.temperatureKelvin() > reaction.maximumTemperature()
                    || gas.thermalEnergy() < reaction.minimumEnergy()) continue;
            double required = reaction.minimumEnergy() / gas.thermalEnergy();
            boolean allowed = true;
            for (var requirement : reaction.minimumRequirements().entrySet()) {
                double available = gas.moles(requirement.getKey());
                if (available < requirement.getValue()) { allowed = false; break; }
                if (requirement.getValue() > 0)
                    required = Math.max(required, requirement.getValue() / available);
            }
            if (!allowed) continue;
            boolean hasFire = false;
            for (var effect : reaction.effects()) {
                GasType fuel = switch (effect.type()) {
                    case TRITIUM_FIRE -> GasType.TRITIUM;
                    case PLASMA_FIRE -> GasType.PLASMA;
                    default -> null;
                };
                if (fuel == null || gas.moles(fuel) <= 0 || gas.moles(GasType.OXYGEN) <= 0) continue;
                hasFire = true;
                double candidate = Math.max(base, required);
                if (effect.type() == GasReactionData.EffectType.PLASMA_FIRE) {
                    double scale = Math.min(1, (gas.temperatureKelvin() - IGNITION_KELVIN) / 1270);
                    double preliminary = (gas.moles(GasType.OXYGEN) > gas.moles(fuel) * 10
                            ? gas.moles(fuel) : gas.moles(GasType.OXYGEN) / 10) * scale / 9;
                    if (preliminary <= 0.0003) continue;
                    // A small margin covers roundoff when the portion recomputes temperature
                    // from scaled heat capacity; the actual evaluator still decides viability.
                    candidate = Math.max(candidate, Math.min(1, Math.nextUp(
                            (0.0003 / preliminary) * (1 + 1e-12))));
                }
                if (candidate <= 1) candidates.add(candidate);
            }
            if (hasFire) eligible.put(entry.getKey(), reaction);
        }
        if (candidates.isEmpty()) return quenched(gas);
        // The final full-cell trial also handles roundoff at a strict effect threshold.
        candidates.add(1.0);
        var fireCatalog = new PrototypeCatalog<>(eligible);
        GasReactionEvaluator.Result fire = null;
        GasMixture portion = null;
        double fraction = 0;
        double reactingFraction = 0;
        for (double candidate : candidates) {
            double trialFraction = candidate >= FULL_THRESHOLD ? 1 : candidate;
            var trialPortion = gas.withScaledMoles(trialFraction);
            var trial = GasReactionEvaluator.evaluate(fireCatalog, trialPortion,
                    type -> type == GasReactionData.EffectType.TRITIUM_FIRE
                            || type == GasReactionData.EffectType.PLASMA_FIRE);
            if (trial.events().isEmpty()) continue;
            fraction = candidate;
            reactingFraction = trialFraction;
            portion = trialPortion;
            fire = trial;
            break;
        }
        if (fire == null) return quenched(gas);
        // Crossing the threshold this pass only changes nextState; full burn begins next due pass.

        double extent = 0;
        for (var event : fire.events()) extent += event.extentMoles();
        if (!Double.isFinite(extent) || extent <= 0) throw new IllegalArgumentException("invalid fire extent");

        EnumMap<GasType, Double> combined = new EnumMap<>(GasType.class);
        for (GasType type : GasType.values()) {
            double amount = (gas.moles(type) - portion.moles(type)) + fire.mixture().moles(type);
            if (!Double.isFinite(amount) || amount < 0) throw new IllegalArgumentException("invalid merged inventory");
            if (amount > 0) combined.put(type, amount);
        }
        double energy = (gas.thermalEnergy() - portion.thermalEnergy()) + fire.mixture().thermalEnergy();
        if (!Double.isFinite(energy) || energy < 0) throw new IllegalArgumentException("invalid merged energy");
        double capacity = 0;
        for (var entry : combined.entrySet()) capacity += entry.getValue() * entry.getKey().molarHeatCapacity();
        if (!Double.isFinite(capacity) || capacity <= 0) throw new IllegalArgumentException("invalid merged capacity");
        GasMixture merged = new GasMixture(combined, energy / capacity);
        if (!viable(merged)) return new Result(merged, fire.events(), extent, Optional.empty());
        double nextFraction = reactingFraction == 1 ? 1 : Math.min(1, fraction + GROWTH_PER_MOLE * extent);
        return new Result(merged, fire.events(), extent,
                Optional.of(new HotspotState(nextFraction, merged.temperatureKelvin())));
    }

    private static boolean viable(GasMixture gas) {
        return gas.temperatureKelvin() > IGNITION_KELVIN && gas.moles(GasType.OXYGEN) > 0
                && (gas.moles(GasType.TRITIUM) > 0 || gas.moles(GasType.PLASMA) > 0);
    }

    private static Result quenched(GasMixture gas) {
        return new Result(gas, List.of(), 0, Optional.empty());
    }
}
