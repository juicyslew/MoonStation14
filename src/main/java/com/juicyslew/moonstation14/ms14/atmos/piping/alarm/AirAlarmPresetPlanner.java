package com.juicyslew.moonstation14.ms14.atmos.piping.alarm;

import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import com.juicyslew.moonstation14.ms14.atmos.piping.VentilationDeviceConfig;
import com.juicyslew.moonstation14.ms14.devicenet.atmos.AtmosDevicePayload;
import com.juicyslew.moonstation14.ms14.atmos.piping.alarm.prototype.AirAlarmData;

/** Builds a full addressed replacement from the observed device state, preserving its pipe lane. */
public final class AirAlarmPresetPlanner {
    private AirAlarmPresetPlanner() { }

    public static AtmosDevicePayload.SetState plan(VentilationDeviceConfig.Kind kind, AirAlarmData.Preset preset,
            AtmosDevicePayload.State current) {
        if (kind == null || preset == null || current == null) return null;
        boolean vent = kind == VentilationDeviceConfig.Kind.VENT;
        double target = vent ? Math.min(preset.externalTargetKPa(), 4500.0) : current.targetPressureKPa();
        var mode = vent ? current.mode() : VentilationDeviceConfig.ScrubberMode.valueOf(preset.scrubberMode());
        var gases = vent ? current.filteredGases() : preset.filterGases().stream().map(GasType::fromId).collect(java.util.stream.Collectors.toSet());
        return new AtmosDevicePayload.SetState(current.revision(), vent ? preset.ventEnabled() : preset.scrubberEnabled(),
                vent ? target : current.externalPressureBoundKPa(), current.targetPressureChangeKPaPerSecond(), target,
                vent ? current.volumeRateM3PerSecond() : preset.scrubberRateM3PerSecond(), mode, gases, current.layer(),
                vent && preset.pressureLockoutOverride(), !vent && preset.wideNet());
    }
}
