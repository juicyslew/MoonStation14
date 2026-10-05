package com.juicyslew.moonstation14.ms14.atmos.piping;

import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;

import java.util.Objects;
import java.util.Optional;

/** Pure SS14-derived transfer planning for room-facing vents and scrubbers. */
public final class VentilationGasFlow {
    private static final double GAS_CONSTANT = 8.31446261815324;
    // GasVentScrubberSystem.cs destination guard: 50 atmospheres. This is not pump burst pressure.
    private static final double SCRUBBER_OUTLET_PRESSURE_CAP_KPA = 5_066.25;

    private VentilationGasFlow() { }

    /** Plan release from pipe to room, bounded by the configured pressure target and rate. */
    public static Optional<PipeGasTransfer.Plan> planVentRelease(
            GasMixture pipeBefore, GasMixture roomBefore, double pipeVolumeM3, double roomVolumeM3,
            double elapsedSeconds, VentilationDeviceConfig.Settings settings) {
        validate(pipeBefore, roomBefore, pipeVolumeM3, roomVolumeM3, elapsedSeconds, settings);
        if (!settings.configuredEnabled() || pipeBefore.totalMoles() == 0.0) return Optional.empty();
        double roomPressure = roomBefore.pressureKpa(roomVolumeM3);
        double remainingPressure = Math.min(settings.externalPressureBoundKPa(), settings.targetPressureKPa()) - roomPressure;
        if (!(remainingPressure > Math.ulp((float) Math.min(settings.externalPressureBoundKPa(), settings.targetPressureKPa())))
                || roomBefore.temperatureKelvin() <= 0.0) return Optional.empty();
        double maxMoles = Math.min(settings.targetPressureChangeKPaPerSecond() * elapsedSeconds,
                remainingPressure) * 1000.0 * roomVolumeM3
                / (GAS_CONSTANT * roomBefore.temperatureKelvin());
        double destinationMaxPressureKpa = Math.min(settings.externalPressureBoundKPa(), settings.targetPressureKPa());
        return PipeGasTransfer.plan(pipeBefore, roomBefore, pipeVolumeM3, roomVolumeM3, maxMoles,
                destinationMaxPressureKpa, java.util.Set.of(com.juicyslew.moonstation14.ms14.atmos.core.GasType.values()));
    }

    /** Plan room capture, filtering selected gases unless configured for siphon mode. */
    public static Optional<PipeGasTransfer.Plan> planScrubberCapture(
            GasMixture roomBefore, GasMixture wastePipeBefore, double roomVolumeM3, double pipeVolumeM3,
            double elapsedSeconds, VentilationDeviceConfig.Settings settings) {
        validate(roomBefore, wastePipeBefore, roomVolumeM3, pipeVolumeM3, elapsedSeconds, settings);
        if (!settings.configuredEnabled() || roomBefore.totalMoles() == 0.0
                || wastePipeBefore.pressureKpa(pipeVolumeM3) >= SCRUBBER_OUTLET_PRESSURE_CAP_KPA)
            return Optional.empty();
        double fraction = Math.min(1.0, elapsedSeconds * settings.volumeRateM3PerSecond() / roomVolumeM3);
        double eligibleMoles = settings.gasesToScrub().stream().mapToDouble(roomBefore::moles).sum();
        double maximumMoles = eligibleMoles * fraction;
        return PipeGasTransfer.plan(roomBefore, wastePipeBefore, roomVolumeM3, pipeVolumeM3, maximumMoles,
                SCRUBBER_OUTLET_PRESSURE_CAP_KPA, settings.gasesToScrub());
    }

    private static void validate(GasMixture source, GasMixture destination, double sourceVolume,
                                 double destinationVolume, double elapsed, VentilationDeviceConfig.Settings settings) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(destination, "destination");
        Objects.requireNonNull(settings, "settings");
        positiveFinite(sourceVolume, "sourceVolumeM3");
        positiveFinite(destinationVolume, "destinationVolumeM3");
        positiveFinite(elapsed, "elapsedSeconds");
    }

    private static void positiveFinite(double value, String name) {
        if (!Double.isFinite(value) || value <= 0.0)
            throw new IllegalArgumentException(name + " must be positive and finite");
    }
}
