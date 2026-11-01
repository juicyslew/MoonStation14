package com.juicyslew.moonstation14.ms14.atmos.monitor;

import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.piping.prototype.VentilationDeviceData;
import com.juicyslew.moonstation14.ms14.devicenet.atmos.AtmosAlarmPayload;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeCatalog;
import java.util.Optional;

/** Shared production sampler adapter: an unavailable sample is absent, never a Normal reading. */
public final class AtmosMonitorSampler {
    private AtmosMonitorSampler() { }
    public static Optional<AtmosAlarmPayload.Measurement> observe(PrototypeCatalog<AtmosAlarmThresholdData> thresholds,
            VentilationDeviceData device, Optional<GasMixture> sample, int fireIntensity) {
        if (sample == null || sample.isEmpty() || device == null || device.atmosMonitor() == null
                || device.atmosAlarmable() == null || device.atmosAlarmable().sourceTags().isEmpty()) return Optional.empty();
        GasMixture mixture = sample.get();
        AtmosAlarmSeverity severity = AtmosMonitorClassification.classify(thresholds, device.atmosMonitor(), mixture);
        if (fireIntensity > 0) severity = AtmosAlarmSeverity.DANGER;
        return Optional.of(new AtmosAlarmPayload.Measurement(severity, mixture.pressureKpa(), mixture.temperatureKelvin(),
                mixture.totalMoles(), device.atmosAlarmable().sourceTags(), true, fireIntensity > 0, allGasAmounts(mixture)));
    }

    /** Includes explicit zeros for every supported species so vacuum differs from an unknown sample. */
    private static java.util.Map<com.juicyslew.moonstation14.ms14.atmos.core.GasType, Double> allGasAmounts(
            GasMixture mixture) {
        var amounts = new java.util.EnumMap<com.juicyslew.moonstation14.ms14.atmos.core.GasType, Double>(
                com.juicyslew.moonstation14.ms14.atmos.core.GasType.class);
        for (var gas : com.juicyslew.moonstation14.ms14.atmos.core.GasType.values()) amounts.put(gas, mixture.moles(gas));
        return java.util.Collections.unmodifiableMap(amounts);
    }
}
