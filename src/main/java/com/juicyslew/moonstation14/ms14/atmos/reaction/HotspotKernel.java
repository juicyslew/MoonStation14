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
    private static final double SPREAD_MINIMUM_KELVIN = 423.15;
    private static final double SEED_FRACTION = 0.10;
    private static final double FULL_THRESHOLD = 0.95;
    // Local approximation: SS14 grows by volume (40,000 L/mol); this maps extent to
    // a fraction of the local 1,000 L cell. The >0.95 bypass is handled separately.
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
                          double totalFireExtentMoles, Optional<HotspotState> nextState,
                          double externalHeatJoules, boolean spreadEligibleThisPass) {
        public Result(GasMixture mixture, List<GasReactionEvaluator.Event> events,
                      double totalFireExtentMoles, Optional<HotspotState> nextState,
                      double externalHeatJoules) {
            this(mixture, events, totalFireExtentMoles, nextState, externalHeatJoules, false);
        }
        public Result(GasMixture mixture, List<GasReactionEvaluator.Event> events,
                      double totalFireExtentMoles, Optional<HotspotState> nextState) {
            this(mixture, events, totalFireExtentMoles, nextState, 0, false);
        }
        public Result {
            events = List.copyOf(events);
            nextState = Objects.requireNonNull(nextState, "nextState");
        }
    }

    /** Null state seeds a new hotspot; an existing fraction is retained after external cooling/diffusion. */
    public static Result evaluate(GasMixture gas, PrototypeCatalog<GasReactionData> catalog, HotspotState state) {
        Objects.requireNonNull(gas, "gas");
        Objects.requireNonNull(catalog, "catalog");
        if (state != null && !validStoredEnergy(gas, state)) return quenched(gas);
        if (state != null && state.fraction() <= FULL_THRESHOLD)
            return evaluatePartitioned(gas, catalog, state);
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
            double trialFraction = candidate > FULL_THRESHOLD ? 1 : candidate;
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
                Optional.of(new HotspotState(nextFraction, merged.temperatureKelvin())), 0,
                state != null && state.fraction() > FULL_THRESHOLD
                        && merged.temperatureKelvin() > SPREAD_MINIMUM_KELVIN);
    }

    private static Result evaluatePartitioned(GasMixture gas, PrototypeCatalog<GasReactionData> catalog,
                                               HotspotState state) {
        double fraction = state.fraction();
        double capacity = gas.heatCapacity();
        double hotEnergy = fraction * capacity * state.temperatureKelvin();
        double coldEnergy = gas.thermalEnergy() - hotEnergy;
        double partitionTolerance = 4 * Math.ulp(Math.max(gas.thermalEnergy(), hotEnergy));
        if (coldEnergy < 0 && coldEnergy >= -partitionTolerance) coldEnergy = 0;
        double coldCapacity = (1 - fraction) * capacity;
        if (!Double.isFinite(hotEnergy) || !Double.isFinite(coldEnergy) || coldEnergy < 0
                || !Double.isFinite(coldCapacity) || coldCapacity <= 0) return quenched(gas);
        double coldTemperature = coldEnergy / coldCapacity;
        if (!Double.isFinite(coldTemperature) || coldTemperature < 0) return quenched(gas);

        var hot = gas.withScaledMoles(fraction).withThermalEnergy(hotEnergy);
        var cold = gas.withScaledMoles(1 - fraction).withThermalEnergy(coldEnergy);
        if (!viable(hot)) return quenched(gas);
        // Treat the already-hot subcell as a complete detached mixture, without changing
        // the incoming whole-cell volume used to determine eligibility.
        var burned = evaluate(hot, catalog, new HotspotState(1, state.temperatureKelvin()));
        if (burned.events().isEmpty()) return quenched(gas);
        double grown = Math.min(1 - fraction, GROWTH_PER_MOLE * burned.totalFireExtentMoles());
        var swept = grown == 0 ? gas.withScaledMoles(0) : cold.withScaledMoles(grown / (1 - fraction));
        var remainingCold = grown == 1 - fraction ? gas.withScaledMoles(0)
                : cold.withScaledMoles(1 - grown / (1 - fraction));
        GasMixture active = merge(burned.mixture(), swept);
        GasMixture merged = merge(active, remainingCold);
        if (!viable(active)) return new Result(merged, burned.events(), burned.totalFireExtentMoles(),
                Optional.empty());
        double nextFraction = fraction + grown;
        // Merge homogenizes composition, while retaining the active subcell's energy budget.
        // This is a conservative projection, not an SS14-exact species partition.
        double projectedHotTemperature = active.thermalEnergy()
                / (nextFraction * merged.heatCapacity());
        if (!Double.isFinite(projectedHotTemperature) || projectedHotTemperature < 0)
            return new Result(merged, burned.events(), burned.totalFireExtentMoles(), Optional.empty());
        var projectedHot = merged.withScaledMoles(nextFraction)
                .withThermalEnergy(nextFraction * merged.heatCapacity() * projectedHotTemperature);
        if (!viable(projectedHot)) return new Result(merged, burned.events(), burned.totalFireExtentMoles(), Optional.empty());
        return new Result(merged, burned.events(), burned.totalFireExtentMoles(),
                Optional.of(new HotspotState(nextFraction, projectedHotTemperature)), 0,
                state.fraction() > FULL_THRESHOLD && projectedHotTemperature > SPREAD_MINIMUM_KELVIN);
    }

    /** Reconciles a stored partition across external heat transfer without creating energy.
     * Species are treated as uniformly mixed; the hot subcell retains its proportional energy budget.
     */
    public static Optional<HotspotState> reconcileAfterHeatTransfer(GasMixture preGas, GasMixture postGas,
                                                                     HotspotState state) {
        Objects.requireNonNull(preGas, "preGas");
        Objects.requireNonNull(postGas, "postGas");
        Objects.requireNonNull(state, "state");
        double beforeEnergy = preGas.thermalEnergy();
        double afterEnergy = postGas.thermalEnergy();
        double beforeHot = state.fraction() * preGas.heatCapacity() * state.temperatureKelvin();
        double tolerance = 4 * Math.ulp(Math.max(beforeEnergy, beforeHot));
        if (beforeHot > beforeEnergy && beforeHot <= beforeEnergy + tolerance) beforeHot = beforeEnergy;
        if (!Double.isFinite(beforeEnergy) || beforeEnergy <= 0 || !Double.isFinite(afterEnergy)
                || afterEnergy < 0 || !Double.isFinite(beforeHot) || beforeHot < 0 || beforeHot > beforeEnergy)
            return Optional.empty();
        if (state.fraction() == 1) return Optional.of(new HotspotState(1, postGas.temperatureKelvin()));
        double hotEnergy = beforeHot * (afterEnergy / beforeEnergy);
        double hotCapacity = state.fraction() * postGas.heatCapacity();
        double temperature = hotCapacity > 0 ? hotEnergy / hotCapacity : Double.NaN;
        if (!Double.isFinite(temperature) || temperature < 0 || hotEnergy > afterEnergy) return Optional.empty();
        return Optional.of(new HotspotState(state.fraction(), temperature));
    }

    private static GasMixture merge(GasMixture first, GasMixture second) {
        EnumMap<GasType, Double> species = new EnumMap<>(GasType.class);
        for (GasType type : GasType.values()) {
            double amount = first.moles(type) + second.moles(type);
            if (amount > 0) species.put(type, amount);
        }
        double energy = first.thermalEnergy() + second.thermalEnergy();
        double capacity = 0;
        for (var entry : species.entrySet()) capacity += entry.getValue() * entry.getKey().molarHeatCapacity();
        return new GasMixture(species, energy / capacity);
    }

    /** Consumes one pending explosion exposure, heating only its detached cell fraction. */
    public static Result evaluateExplosionExposure(GasMixture gas, PrototypeCatalog<GasReactionData> catalog,
                                                   HotspotState state, ExplosionHotspotExposure.Decision exposure) {
        Objects.requireNonNull(gas, "gas");
        Objects.requireNonNull(catalog, "catalog");
        if (exposure == null || !exposure.expose() || !Double.isFinite(exposure.cellFraction())
                || exposure.cellFraction() <= 0 || exposure.cellFraction() > 1
                || !Double.isFinite(exposure.temperatureKelvin()) || exposure.temperatureKelvin() < 0)
            return quenched(gas);
        double fraction = exposure.cellFraction();
        var cold = gas.withScaledMoles(1 - fraction);
        var hot = gas.withScaledMoles(fraction);
        double delta = Math.max(0, exposure.temperatureKelvin() - gas.temperatureKelvin());
        double supplied = hot.heatCapacity() * delta;
        if (!Double.isFinite(supplied) || !Double.isFinite(hot.thermalEnergy() + supplied))
            return quenched(gas);
        hot = hot.withThermalEnergy(hot.thermalEnergy() + supplied);
        // No viable reaction: exposure is not a persistent heat source in this detached model.
        if (!viable(hot)) return quenched(gas);
        // Exposure already selected the detached portion; preserve its prior full-portion
        // reaction policy rather than applying the stored ordinary-hotspot fraction again.
        var burned = evaluate(hot, catalog, new HotspotState(1, hot.temperatureKelvin()));
        if (burned.events().isEmpty()) return quenched(gas);
        EnumMap<GasType, Double> mergedSpecies = new EnumMap<>(GasType.class);
        for (GasType type : GasType.values()) {
            double amount = cold.moles(type) + burned.mixture().moles(type);
            if (!Double.isFinite(amount) || amount < 0) return quenched(gas);
            if (amount > 0) mergedSpecies.put(type, amount);
        }
        double energy = cold.thermalEnergy() + burned.mixture().thermalEnergy();
        double capacity = 0;
        for (var entry : mergedSpecies.entrySet()) capacity += entry.getValue() * entry.getKey().molarHeatCapacity();
        if (!Double.isFinite(energy) || !Double.isFinite(capacity) || capacity <= 0) return quenched(gas);
        var merged = new GasMixture(mergedSpecies, energy / capacity);
        Optional<HotspotState> next = Optional.empty();
        if (burned.nextState().isPresent()) {
            double nextFraction = Math.min(fraction,
                    fraction * burned.nextState().orElseThrow().fraction());
            var continuation = evaluate(merged, catalog, new HotspotState(nextFraction, merged.temperatureKelvin()));
            if (continuation.events().size() > 0)
                next = Optional.of(new HotspotState(nextFraction, merged.temperatureKelvin()));
        }
        // Explosion ignition is an exposure pass, never a hotspot-spread bypass pass.
        return new Result(merged, burned.events(), burned.totalFireExtentMoles(), next, supplied, false);
    }

    private static boolean viable(GasMixture gas) {
        return gas.temperatureKelvin() > IGNITION_KELVIN && gas.moles(GasType.OXYGEN) > 0
                && (gas.moles(GasType.TRITIUM) > 0 || gas.moles(GasType.PLASMA) > 0);
    }

    private static boolean validStoredEnergy(GasMixture gas, HotspotState state) {
        double hotEnergy = state.fraction() * gas.heatCapacity() * state.temperatureKelvin();
        double energy = gas.thermalEnergy();
        if (!Double.isFinite(hotEnergy) || !Double.isFinite(energy) || hotEnergy < 0 || energy < 0) return false;
        double tolerance = 4 * Math.ulp(Math.max(hotEnergy, energy));
        return hotEnergy <= energy || hotEnergy <= energy + tolerance;
    }

    private static Result quenched(GasMixture gas) {
        return new Result(gas, List.of(), 0, Optional.empty());
    }
}
