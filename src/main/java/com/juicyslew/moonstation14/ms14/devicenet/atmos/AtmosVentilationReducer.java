package com.juicyslew.moonstation14.ms14.devicenet.atmos;

import com.juicyslew.moonstation14.ms14.atmos.piping.VentilationDeviceConfig;

/** Domain adapter for addressed atmos commands; keeps the existing config CAS as sole reducer. */
public final class AtmosVentilationReducer {
    private AtmosVentilationReducer() { }

    public static boolean apply(VentilationDeviceConfig config, AtmosDevicePayload.SetState command) {
        if (config == null || command == null) return false;
        try {
            var next = command.settings();
            // Validate device-specific supported fields without changing the established CAS semantics.
            if (config.kind() == VentilationDeviceConfig.Kind.VENT
                    && (next.volumeRateM3PerSecond() != 0 || !next.filteredGases().isEmpty())) return false;
            if (config.kind() == VentilationDeviceConfig.Kind.SCRUBBER
                    && (next.externalPressureBoundKPa() != 0 || next.targetPressureChangeKPaPerSecond() != 0
                    || next.targetPressureKPa() != 0)) return false;
            return config.compareAndSet(command.expectedRevision(), next);
        } catch (IllegalArgumentException invalid) {
            return false;
        }
    }
}
