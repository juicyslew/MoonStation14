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

/** Detached cell pass: shared whole-air reaction priority over the full catalog, then partial-hotspot fire.
 * The whole-air pass carries the prior final fire-effect result so bypass rules can reuse it.
 */
public final class GasReactionStep {
    private GasReactionStep() { }

    public record Result(GasMixture mixture, Map<GasType, Double> speciesDelta,
                          double energyDeltaJoules, List<GasReactionEvaluator.Event> events,
                           Optional<HotspotState> nextState, boolean fireOccurred,
                          double externalHeatJoules, boolean spreadEligibleThisPass) {
        public Result(GasMixture mixture, Map<GasType, Double> speciesDelta, double energyDeltaJoules,
                      List<GasReactionEvaluator.Event> events,
                       Optional<HotspotState> nextState, boolean fireOccurred,
                      double externalHeatJoules) {
            this(mixture, speciesDelta, energyDeltaJoules, events, nextState, fireOccurred,
                    externalHeatJoules, false);
        }
        public Result(GasMixture mixture, Map<GasType, Double> speciesDelta, double energyDeltaJoules,
                      List<GasReactionEvaluator.Event> events,
                       Optional<HotspotState> nextState, boolean fireOccurred) {
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
                                  HotspotState hotspot) {
        return evaluateInternal(catalog, original, hotspot, null, 0);
    }

    public static Result evaluate(PrototypeCatalog<GasReactionData> catalog, GasMixture original,
                                  HotspotState hotspot, double priorFireReactionResultMoles) {
        return evaluateInternal(catalog, original, hotspot, null, priorFireReactionResultMoles);
    }

    public static Result evaluate(PrototypeCatalog<GasReactionData> catalog, GasMixture original,
                                  HotspotState hotspot,
                                  ExplosionHotspotExposure.Decision exposure) {
        return evaluateInternal(catalog, original, hotspot, exposure, 0);
    }

    public static Result evaluate(PrototypeCatalog<GasReactionData> catalog, GasMixture original,
                                  HotspotState hotspot, ExplosionHotspotExposure.Decision exposure,
                                  double priorFireReactionResultMoles) {
        return evaluateInternal(catalog, original, hotspot, exposure, priorFireReactionResultMoles);
    }

    private static Result evaluateInternal(PrototypeCatalog<GasReactionData> catalog, GasMixture original,
                                           HotspotState hotspot,
                                          ExplosionHotspotExposure.Decision exposure,
                                          double priorFireReactionResultMoles) {
        Objects.requireNonNull(catalog, "catalog");
        Objects.requireNonNull(original, "original");
        var whole = evaluateWholeAir(catalog, original, priorFireReactionResultMoles);
        var sampled = whole.mixture();
        Optional<HotspotState> nextState = Optional.ofNullable(hotspot);
        if (exposure != null && exposure.expose()) {
            nextState = HotspotKernel.expose(sampled, hotspot, exposure.temperatureKelvin(),
                    exposure.exposedVolumeLiters(), true, false);
        } else if (whole.autoExposureVolumeLiters() > 0) {
            nextState = HotspotKernel.expose(sampled, hotspot, sampled.temperatureKelvin(),
                    whole.autoExposureVolumeLiters(), false, false);
        }
        // Exposure is metadata; it never heats or burns the cell during this pass.
        var fire = exposure != null && exposure.expose()
                ? new HotspotKernel.ProcessResult(sampled, List.of(), 0, nextState, false)
                : processHotspot(catalog, sampled, hotspot, whole.fireExtentMoles());
        List<GasReactionEvaluator.Event> events = new ArrayList<>(whole.events());
        events.addAll(fire.events());
        EnumMap<GasType, Double> delta = new EnumMap<>(GasType.class);
        for (GasType species : GasType.values()) {
            double change = fire.mixture().moles(species) - original.moles(species);
            if (change != 0) delta.put(species, change);
        }
        Optional<HotspotState> publishedState = hotspot == null || (exposure != null && exposure.expose())
                ? nextState : fire.nextState();
        boolean fireEvent = events.stream().anyMatch(e -> e.effect() == GasReactionData.EffectType.TRITIUM_FIRE
                || e.effect() == GasReactionData.EffectType.PLASMA_FIRE);
        return new Result(fire.mixture(), delta, fire.mixture().thermalEnergy() - original.thermalEnergy(),
                events, publishedState, fireEvent, 0,
                fire.spreadEligibleThisPass());
    }

    /** Runs the shared whole-air reaction priority pass, before any local hotspot processing. */
    public static WholeAirResult evaluateWholeAir(PrototypeCatalog<GasReactionData> catalog, GasMixture gas) {
        return evaluateWholeAir(catalog, gas, 0);
    }

    /** Carries the per-cell final fire-effect slot through a whole-air priority pass. */
    public static WholeAirResult evaluateWholeAir(PrototypeCatalog<GasReactionData> catalog, GasMixture gas,
                                                   double priorFireReactionResultMoles) {
        Objects.requireNonNull(catalog, "catalog");
        Objects.requireNonNull(gas, "gas");
        var result = GasReactionEvaluator.evaluate(catalog, gas, priorFireReactionResultMoles);
        double extent = result.fireReactionResultMoles();
        boolean fireOccurred = result.events().stream().anyMatch(e -> e.effect() == GasReactionData.EffectType.TRITIUM_FIRE
                || e.effect() == GasReactionData.EffectType.PLASMA_FIRE);
        double exposure = !fireOccurred || result.mixture().temperatureKelvin() <= 373.15
                ? 0 : gas.volumeCubicMeters() * 1000.0;
        return new WholeAirResult(result.mixture(), result.events(), extent, exposure);
    }

    public record WholeAirResult(GasMixture mixture, List<GasReactionEvaluator.Event> events,
                                 double fireExtentMoles, double autoExposureVolumeLiters) {
        public WholeAirResult { events = List.copyOf(events); }
    }

    /** Applies a previously exposed local fire state against the result of the whole-air pass. */
    public static HotspotKernel.ProcessResult processHotspot(PrototypeCatalog<GasReactionData> catalog,
                                                               GasMixture gas, HotspotState state,
                                                               double priorWholeFireExtent) {
        return HotspotKernel.process(catalog, gas, state, priorWholeFireExtent);
    }
}

