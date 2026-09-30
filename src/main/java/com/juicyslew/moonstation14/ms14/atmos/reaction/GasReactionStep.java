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
                         Optional<HotspotKernel.HotspotState> nextState, boolean fireOccurred) {
        public Result {
            speciesDelta = Map.copyOf(speciesDelta);
            events = List.copyOf(events);
            nextState = Objects.requireNonNull(nextState, "nextState");
        }
    }

    /** The supplied catalog is the validated, complete server snapshot. No world state is mutated. */
    public static Result evaluate(PrototypeCatalog<GasReactionData> catalog, GasMixture original,
                                  HotspotKernel.HotspotState hotspot) {
        Objects.requireNonNull(catalog, "catalog");
        Objects.requireNonNull(original, "original");
        var nonfire = GasReactionEvaluator.evaluate(catalog, original,
                effect -> effect != GasReactionData.EffectType.TRITIUM_FIRE
                        && effect != GasReactionData.EffectType.PLASMA_FIRE);
        // A temperature label is not stored thermal energy. Reconcile it to the sampled
        // post-nonfire mixture, including external diffusion and cooling, before fire.
        var sampled = nonfire.mixture();
        var reconciled = hotspot == null ? null
                : new HotspotKernel.HotspotState(hotspot.fraction(), sampled.temperatureKelvin());
        var fire = HotspotKernel.evaluate(sampled, catalog, reconciled);
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
                events, fire.nextState(), !fire.events().isEmpty());
    }
}
