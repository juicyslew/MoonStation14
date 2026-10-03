package com.juicyslew.moonstation14.ms14.atmos.reaction;

import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeCatalog;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.ArrayList;

/** Detached cell pass: whole-cell nonfire chemistry, followed by one partial-hotspot fire pass. */
public final class GasReactionStep {
    private GasReactionStep() { }

    public record Result(GasMixture mixture, Map<GasType, Double> speciesDelta,
                          double energyDeltaJoules, List<GasReactionEvaluator.Event> events,
                          Optional<HotspotKernel.HotspotState> nextState, boolean fireOccurred,
                          double externalHeatJoules, boolean spreadEligibleThisPass) {
        public Result(GasMixture mixture, Map<GasType, Double> speciesDelta, double energyDeltaJoules,
                      List<GasReactionEvaluator.Event> events,
                      Optional<HotspotKernel.HotspotState> nextState, boolean fireOccurred,
                      double externalHeatJoules) {
            this(mixture, speciesDelta, energyDeltaJoules, events, nextState, fireOccurred,
                    externalHeatJoules, false);
        }
        public Result(GasMixture mixture, Map<GasType, Double> speciesDelta, double energyDeltaJoules,
                      List<GasReactionEvaluator.Event> events,
                      Optional<HotspotKernel.HotspotState> nextState, boolean fireOccurred) {
            this(mixture, speciesDelta, energyDeltaJoules, events, nextState, fireOccurred, 0, false);
        }
        public Result {
            speciesDelta = Map.copyOf(speciesDelta);
            events = List.copyOf(events);
            nextState = Objects.requireNonNull(nextState, "nextState");
        }
    }

    /** The supplied catalog is the validated, complete server snapshot. No world state is mutated. */
    public static Result evaluate(PrototypeCatalog<GasReactionData> catalog, GasMixture original,
                                  HotspotKernel.HotspotState hotspot) {
        return evaluateInternal(catalog, original, hotspot, null);
    }

    public static Result evaluate(PrototypeCatalog<GasReactionData> catalog, GasMixture original,
                                  HotspotKernel.HotspotState hotspot,
                                  ExplosionHotspotExposure.Decision exposure) {
        return evaluateInternal(catalog, original, hotspot, exposure);
    }

    private static Result evaluateInternal(PrototypeCatalog<GasReactionData> catalog, GasMixture original,
                                          HotspotKernel.HotspotState hotspot,
                                          ExplosionHotspotExposure.Decision exposure) {
        Objects.requireNonNull(catalog, "catalog");
        Objects.requireNonNull(original, "original");
        var nonfire = GasReactionEvaluator.evaluate(catalog, original,
                effect -> effect != GasReactionData.EffectType.TRITIUM_FIRE
                        && effect != GasReactionData.EffectType.PLASMA_FIRE);
        // Reconciliation can validate the observed pre/post energy budget, but cannot
        // reconstruct unobserved external diffusion that happened between samples.
        var sampled = nonfire.mixture();
        var reconciled = hotspot == null ? Optional.<HotspotKernel.HotspotState>empty()
                : HotspotKernel.reconcileAfterHeatTransfer(original, sampled, hotspot);
        // A pending explosion exposure is independent fresh heat, not ordinary hotspot
        // continuation. A stale stored partition must not suppress that exposure.
        var fire = exposure != null
                ? HotspotKernel.evaluateExplosionExposure(sampled, catalog, reconciled.orElse(null), exposure)
                : hotspot != null && reconciled.isEmpty()
                ? new HotspotKernel.Result(sampled, List.of(), 0, Optional.empty())
                : HotspotKernel.evaluate(sampled, catalog, reconciled.orElse(null));
        List<GasReactionEvaluator.Event> events = new ArrayList<>(nonfire.events());
        events.addAll(fire.events());
        EnumMap<GasType, Double> delta = new EnumMap<>(GasType.class);
        for (GasType species : GasType.values()) {
            double change = fire.mixture().moles(species) - original.moles(species);
            if (change != 0) delta.put(species, change);
        }
        return new Result(fire.mixture(), delta,
                nonfire.energyDeltaJoules() + fire.events().stream()
                        .mapToDouble(GasReactionEvaluator.Event::energyDeltaJoules).sum(),
                events, fire.nextState(), !fire.events().isEmpty(), fire.externalHeatJoules(),
                fire.spreadEligibleThisPass());
    }
}
