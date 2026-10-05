package com.juicyslew.moonstation14.ms14.atmos.piping.alarm.ui;

import com.juicyslew.moonstation14.ms14.atmos.piping.VentilationDeviceConfig;

/** Numeric limits mapped from SS14: vent bound 4500 kPa; scrubber transfer cap 200 L/s = 0.2 m³/s.
 * Source: Content.Server/Atmos/Piping/Unary/Components/GasVentScrubberComponent.cs:35-44
 * (TransferRate clamps to MaxTransferRate, defaulting to Atmospherics.MaxTransferRate);
 * Content.Shared/Atmos/Atmospherics.cs:371 (MaxTransferRate = 200 L/s).
 */
public final class AirAlarmNumericBounds {
    public static final double MAX_PRESSURE_KPA = 4500.0;
    public static final double MAX_VOLUME_RATE_M3_PER_SECOND = 0.2;
    private AirAlarmNumericBounds() { }

    public static boolean valid(VentilationDeviceConfig.Kind kind, double value) {
        return kind != null && Double.isFinite(value) && value >= 0
                && value <= (kind == VentilationDeviceConfig.Kind.VENT
                    ? MAX_PRESSURE_KPA : MAX_VOLUME_RATE_M3_PER_SECOND);
    }
}
