package com.juicyslew.moonstation14.ms14.devicenet.atmos;

import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import com.juicyslew.moonstation14.ms14.atmos.piping.VentilationDeviceConfig;
import com.juicyslew.moonstation14.ms14.devicenet.DevicePayload;
import java.util.Set;

/** Addressed AtmosDevices command/reply family. Commands are applied by the device owner. */
public sealed interface AtmosDevicePayload extends DevicePayload
        permits AtmosDevicePayload.Register, AtmosDevicePayload.Deregister, AtmosDevicePayload.Sync,
                AtmosDevicePayload.SetState, AtmosDevicePayload.State, AtmosDevicePayload.CommandResult {
    record Register() implements AtmosDevicePayload { }
    record Deregister() implements AtmosDevicePayload { }
    record Sync() implements AtmosDevicePayload { }

    /** Full supported manual state, rather than a partial patch that can lose concurrent settings. */
    record SetState(long expectedRevision, boolean enabled, double externalPressureBoundKPa,
                    double targetPressureChangeKPaPerSecond, double targetPressureKPa,
                    double volumeRateM3PerSecond, VentilationDeviceConfig.ScrubberMode mode,
                    Set<GasType> filteredGases, VentilationDeviceConfig.Layer layer,
                    boolean pressureLockoutOverride, boolean wideNet, long requestId) implements AtmosDevicePayload {
        public SetState(long expectedRevision, boolean enabled, double externalPressureBoundKPa,
                        double targetPressureChangeKPaPerSecond, double targetPressureKPa,
                        double volumeRateM3PerSecond, VentilationDeviceConfig.ScrubberMode mode,
                        Set<GasType> filteredGases, VentilationDeviceConfig.Layer layer) {
            this(expectedRevision, enabled, externalPressureBoundKPa, targetPressureChangeKPaPerSecond,
                    targetPressureKPa, volumeRateM3PerSecond, mode, filteredGases, layer, false, false, 0);
        }
        public SetState(long expectedRevision, boolean enabled, double externalPressureBoundKPa,
                        double targetPressureChangeKPaPerSecond, double targetPressureKPa,
                        double volumeRateM3PerSecond, VentilationDeviceConfig.ScrubberMode mode,
                        Set<GasType> filteredGases, VentilationDeviceConfig.Layer layer,
                        boolean pressureLockoutOverride, boolean wideNet) {
            this(expectedRevision, enabled, externalPressureBoundKPa, targetPressureChangeKPaPerSecond,
                    targetPressureKPa, volumeRateM3PerSecond, mode, filteredGases, layer,
                    pressureLockoutOverride, wideNet, 0);
        }
        public SetState { filteredGases = Set.copyOf(filteredGases); }
        public VentilationDeviceConfig.Settings settings() {
            return new VentilationDeviceConfig.Settings(expectedRevision, enabled, externalPressureBoundKPa,
                    targetPressureChangeKPaPerSecond, targetPressureKPa, volumeRateM3PerSecond, mode, filteredGases, layer,
                    pressureLockoutOverride, wideNet);
        }
    }

    /** Observed state is keyed by the sender address at the alarm adapter, not entity position. */
    record State(long revision, boolean enabled, double externalPressureBoundKPa,
                 double targetPressureChangeKPaPerSecond, double targetPressureKPa,
                 double volumeRateM3PerSecond, VentilationDeviceConfig.ScrubberMode mode,
                  Set<GasType> filteredGases, VentilationDeviceConfig.Layer layer,
                  boolean pressureLockoutOverride, boolean wideNet) implements AtmosDevicePayload {
        public State(long revision, boolean enabled, double externalPressureBoundKPa,
                     double targetPressureChangeKPaPerSecond, double targetPressureKPa,
                     double volumeRateM3PerSecond, VentilationDeviceConfig.ScrubberMode mode,
                     Set<GasType> filteredGases, VentilationDeviceConfig.Layer layer) {
            this(revision, enabled, externalPressureBoundKPa, targetPressureChangeKPaPerSecond,
                    targetPressureKPa, volumeRateM3PerSecond, mode, filteredGases, layer, false, false);
        }
        public State { filteredGases = Set.copyOf(filteredGases); }
        public static State from(VentilationDeviceConfig.Settings s) {
            return new State(s.revision(), s.configuredEnabled(), s.externalPressureBoundKPa(),
                    s.targetPressureChangeKPaPerSecond(), s.targetPressureKPa(), s.volumeRateM3PerSecond(),
                    s.scrubberMode(), s.filteredGases(), s.selectedLayer(), s.pressureLockoutOverride(), s.wideNet());
        }
    }

    /** Reply to one queued command, bound to its request and carrying authoritative current state. */
    record CommandResult(long requestId, boolean applied, State current) implements AtmosDevicePayload { }
}
