package com.juicyslew.moonstation14.ms14.atmos.reaction;

import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeCatalog;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Explicit-volume local fire processing. */
public final class HotspotKernel {
    private static final double IGNITION_KELVIN = (double) 373.15f;
    private static final double SPREAD_MINIMUM_KELVIN = 423.15;
    private static final double FULL_THRESHOLD = 0.95;
    private static final double IGNITABLE_MOLES = (double) (float)
            com.juicyslew.moonstation14.ms14.atmos.core.AtmosphereUnits.referenceTileQuantity(0.5);

    private HotspotKernel() { }

    /** Only an already-past-first-process hotspot visit resets excited-group pacing. */
    public static boolean shouldResetExcitedGroupCooldowns(HotspotState state) {
        return state != null && state.skippedFirstProcess();
    }

    public record ProcessResult(GasMixture mixture, List<GasReactionEvaluator.Event> events,
                                double fireExtentMoles, Optional<HotspotState> nextState,
                                boolean spreadEligibleThisPass) {
        public ProcessResult { events = List.copyOf(events); Objects.requireNonNull(nextState); }
    }

    /** State lifecycle: first due visit only clears skip; subsequent visits process metadata volume. */
    public static ProcessResult process(PrototypeCatalog<GasReactionData> catalog, GasMixture mixture,
                                        HotspotState state, double priorFireResultMoles) {
        Objects.requireNonNull(catalog); Objects.requireNonNull(mixture);
        if (!Double.isFinite(priorFireResultMoles) || priorFireResultMoles < 0)
            throw new IllegalArgumentException("invalid prior fire extent");
        double cellLiters = mixture.volumeCubicMeters() * 1000;
        if (state == null) return new ProcessResult(mixture, List.of(), 0, Optional.empty(), false);
        if (!state.skippedFirstProcess()) return new ProcessResult(mixture, List.of(), 0,
                Optional.of(new HotspotState(state.volumeLiters(), state.temperatureKelvin(), true, state.bypass())), false);
        if (state.temperatureKelvin() < IGNITION_KELVIN || state.volumeLiters() <=
                (double) (float) com.juicyslew.moonstation14.ms14.atmos.core.AtmosphereUnits.referenceTileQuantity(1.0)
                || !mixtureIsIgnitable(mixture))
            return new ProcessResult(mixture, List.of(), 0, Optional.empty(), false);
        boolean bypass = state.volumeLiters() > cellLiters * FULL_THRESHOLD;
        if (bypass) {
            double volume = priorFireResultMoles * 40000;
            return new ProcessResult(mixture, List.of(), priorFireResultMoles,
                    Optional.of(new HotspotState(volume, mixture.temperatureKelvin(), true, true)),
                    mixture.temperatureKelvin() > SPREAD_MINIMUM_KELVIN);
        }
        var split = mixture.splitRatio(state.volumeLiters() / cellLiters);
        GasMixture hot = new GasMixture(split.removed().gasMoles(), state.temperatureKelvin(),
                split.removed().volumeCubicMeters());
        GasReactionEvaluator.Result fired = GasReactionEvaluator.evaluate(catalog, hot, 0.0);
        GasMixture merged = merge(split.remaining(), fired.mixture());
        double extent = fired.fireReactionResultMoles();
        double nextVolume = extent * 40000;
        // Zero-volume continuation is intentional: the next due pass performs canonical extinction.
        HotspotState next = new HotspotState(nextVolume, fired.mixture().temperatureKelvin(), true, false);
        return new ProcessResult(merged, fired.events(), extent, Optional.of(next),
                extent > 0 && fired.mixture().temperatureKelvin() > SPREAD_MINIMUM_KELVIN);
    }

    /** Adds exposure metadata only; volume is multiplied by 25 exactly once for a new exposure. */
    public static Optional<HotspotState> expose(GasMixture gas, HotspotState existing,
                                                 double exposedTemperatureKelvin, double exposedVolumeLiters,
                                                 boolean boost, boolean skippedFirstProcess) {
        Objects.requireNonNull(gas);
        exposedTemperatureKelvin = (double) (float) exposedTemperatureKelvin;
        if (!Double.isFinite(exposedTemperatureKelvin) || !Double.isFinite(exposedVolumeLiters)
                || exposedVolumeLiters <= 0 || (existing == null && exposedTemperatureKelvin <= IGNITION_KELVIN)
                || !mixtureIsIgnitable(gas)) return Optional.ofNullable(existing);
        double volume = existing == null ? exposedVolumeLiters * 25
                : boost ? Math.max(existing.volumeLiters(), exposedVolumeLiters) : existing.volumeLiters();
        double temperature = existing == null ? exposedTemperatureKelvin : boost
                ? Math.max(existing.temperatureKelvin(), exposedTemperatureKelvin) : existing.temperatureKelvin();
        return Optional.of(new HotspotState(volume, temperature,
                existing == null ? skippedFirstProcess : existing.skippedFirstProcess(), existing != null && existing.bypass()));
    }

    private static GasMixture merge(GasMixture a, GasMixture b) { return a.mergedWith(b); }
    private static boolean mixtureIsIgnitable(GasMixture gas) {
        return (double) (float) gas.moles(GasType.OXYGEN) > IGNITABLE_MOLES
                && (double) (float) (gas.moles(GasType.PLASMA) + gas.moles(GasType.TRITIUM)) > IGNITABLE_MOLES;
    }

    public static Optional<com.juicyslew.moonstation14.ms14.atmos.reaction.HotspotState> reconcileAfterHeatTransfer(
            GasMixture before, GasMixture after, com.juicyslew.moonstation14.ms14.atmos.reaction.HotspotState state) {
        Objects.requireNonNull(before); Objects.requireNonNull(after); Objects.requireNonNull(state);
        return Optional.of(state);
    }
}
