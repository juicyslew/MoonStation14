package com.juicyslew.moonstation14.ms14.atmos.piping;

import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import com.juicyslew.moonstation14.ms14.power.runtime.PowerLiveLoop;

import java.util.Set;

/**
 * Immutable, non-activated reference values observed in SS14 at c9df5ef5d675b0d1d226828bddf6b78c28502d91.
 * These are references, not approved local simulation settings. Provenance is recorded in
 * docs/powered-ventilation-sprint/audits/ss14-ventilation-values-and-power.md:7-17.
 */
public final class Ss14VentilationDefaults {
    /** PipeNode volume: PipeNode.cs:107-110; converted from 200 L. Per node, not per network. */
    public static final double PIPE_NODE_VOLUME_M3 = 0.2;
    /** Scrubber default volume rate: SharedVentScrubberComponent.cs:12-16; converted from 200 L/s. */
    public static final double SCRUBBER_VOLUME_RATE_M3_PER_SECOND = 0.2;
    /** GasVentPumpComponent.cs:111-122; reference default in kPa/s. */
    public static final double VENT_TARGET_PRESSURE_CHANGE_KPA_PER_SECOND = 101.325;
    /** SharedVentPumpComponent.cs:13-26 and GasVentPumpComponent.cs:83-104. */
    public static final double VENT_EXTERNAL_PRESSURE_BOUND_KPA = 101.325;
    /** Atmospherics.cs:364-371; maximum target pressure. */
    public static final double MAXIMUM_TARGET_PRESSURE_KPA = 4500.0;
    /** SharedVentScrubberComponent.cs:12-26; normal selected scrubber gases (not siphon mode). */
    public static final Set<GasType> DEFAULT_SCRUBBED_GASES = Set.of(
            GasType.CARBON_DIOXIDE, GasType.PLASMA, GasType.TRITIUM, GasType.WATER_VAPOR,
            GasType.AMMONIA, GasType.NITROUS_OXIDE, GasType.FREZON);
    /** ApcPowerReceiverComponent.cs:53-58, exposed by the existing power-loop reference. */
    public static final double APC_REQUESTED_POWER_WATTS = PowerLiveLoop.SS14_UNARY_ATMOS_POWER_WATTS;

    private Ss14VentilationDefaults() { }
}

