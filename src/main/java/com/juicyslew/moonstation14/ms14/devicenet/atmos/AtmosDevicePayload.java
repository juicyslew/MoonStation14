package com.juicyslew.moonstation14.ms14.devicenet.atmos;

import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import com.juicyslew.moonstation14.ms14.atmos.piping.VentilationDeviceConfig;
import com.juicyslew.moonstation14.ms14.devicenet.DevicePayload;
import java.util.Set;

/** Addressed AtmosDevices command/reply family. Commands are applied by the device owner. */
public sealed interface AtmosDevicePayload extends DevicePayload
        permits AtmosDevicePayload.Register, AtmosDevicePayload.Deregister, AtmosDevicePayload.Sync,
                AtmosDevicePayload.SetState, AtmosDevicePayload.State {
    record Register() implements AtmosDevicePayload { }
    record Deregister() implements AtmosDevicePayload { }
    record Sync() implements AtmosDevicePayload { }

    /** Full supported manual state, rather than a partial patch that can lose concurrent settings. */
    record SetState(long expectedRevision, boolean enabled, double externalPressureBoundKPa,
                    double targetPressureChangeKPaPerSecond, double targetPressureKPa,
                    double volumeRateM3PerSecond, VentilationDeviceConfig.ScrubberMode mode,
                    Set<GasType> filteredGases, VentilationDeviceConfig.Layer layer) implements AtmosDevicePayload {
        public SetState { filteredGases = Set.copyOf(filteredGases); }
        public VentilationDeviceConfig.Settings settings() {
            return new VentilationDeviceConfig.Settings(expectedRevision, enabled, externalPressureBoundKPa,
                    targetPressureChangeKPaPerSecond, targetPressureKPa, volumeRateM3PerSecond, mode, filteredGases, layer);
        }
    }

    /** Observed state is keyed by the sender address at the alarm adapter, not entity position. */
    record State(long revision, boolean enabled, double externalPressureBoundKPa,
                 double targetPressureChangeKPaPerSecond, double targetPressureKPa,
                 double volumeRateM3PerSecond, VentilationDeviceConfig.ScrubberMode mode,
                 Set<GasType> filteredGases, VentilationDeviceConfig.Layer layer) implements AtmosDevicePayload {
        public State { filteredGases = Set.copyOf(filteredGases); }
        public static State from(VentilationDeviceConfig.Settings s) {
            return new State(s.revision(), s.configuredEnabled(), s.externalPressureBoundKPa(),
                    s.targetPressureChangeKPaPerSecond(), s.targetPressureKPa(), s.volumeRateM3PerSecond(),
                    s.scrubberMode(), s.filteredGases(), s.selectedLayer());
        }
    }
}
