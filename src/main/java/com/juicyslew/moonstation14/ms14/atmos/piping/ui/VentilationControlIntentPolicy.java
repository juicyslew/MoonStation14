package com.juicyslew.moonstation14.ms14.atmos.piping.ui;

import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import com.juicyslew.moonstation14.ms14.atmos.piping.VentilationDeviceConfig;

import java.util.HashSet;
import java.util.Optional;

/** Pure desired-state transformation for ventilation controls. */
public final class VentilationControlIntentPolicy {
    private VentilationControlIntentPolicy() { }

    public static Optional<VentilationDeviceConfig.Settings> apply(VentilationDeviceConfig.Settings current,
            VentilationDeviceConfig.Kind kind, VentilationControlRequest.Operation op,
            boolean desired, int enumIndex) {
        if (current == null || kind == null || op == null || op == VentilationControlRequest.Operation.QUERY)
            return Optional.empty();

        boolean enabled = current.configuredEnabled();
        var mode = current.scrubberMode();
        var filters = new HashSet<>(current.filteredGases());
        switch (op) {
            case SET_ENABLED -> enabled = desired;
            case SET_SCRUBBER_MODE -> {
                if (kind != VentilationDeviceConfig.Kind.SCRUBBER
                        || enumIndex < 0 || enumIndex >= VentilationDeviceConfig.ScrubberMode.values().length)
                    return Optional.empty();
                mode = VentilationDeviceConfig.ScrubberMode.values()[enumIndex];
            }
            case SET_FILTER_GAS -> {
                if (kind != VentilationDeviceConfig.Kind.SCRUBBER || enumIndex < 0 || enumIndex >= GasType.values().length)
                    return Optional.empty();
                var gas = GasType.values()[enumIndex];
                if (desired) filters.add(gas); else filters.remove(gas);
            }
            case QUERY -> { return Optional.empty(); }
        }

        if (enabled == current.configuredEnabled() && mode == current.scrubberMode()
                && filters.equals(current.filteredGases())) return Optional.empty();
        return Optional.of(new VentilationDeviceConfig.Settings(current.revision(), enabled,
                current.externalPressureBoundKPa(), current.targetPressureChangeKPaPerSecond(),
                current.targetPressureKPa(), current.volumeRateM3PerSecond(), mode, filters, current.selectedLayer()));
    }
}
