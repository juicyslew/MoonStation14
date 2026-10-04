package com.juicyslew.moonstation14.ms14.atmos.piping;

import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class Ss14VentilationDefaultsTest {
    @Test
    void recordsUpstreamUnitConversionsAndPressureDefaults() {
        assertEquals(0.2, Ss14VentilationDefaults.PIPE_NODE_VOLUME_M3, 0.0);
        assertEquals(0.2, Ss14VentilationDefaults.SCRUBBER_VOLUME_RATE_M3_PER_SECOND, 0.0);
        assertEquals(101.325, Ss14VentilationDefaults.VENT_TARGET_PRESSURE_CHANGE_KPA_PER_SECOND, 0.0);
        assertEquals(101.325, Ss14VentilationDefaults.VENT_EXTERNAL_PRESSURE_BOUND_KPA, 0.0);
        assertEquals(4500.0, Ss14VentilationDefaults.MAXIMUM_TARGET_PRESSURE_KPA, 0.0);
    }

    @Test
    void recordsExactDefaultScrubberFilterWithoutCommonGases() {
        assertEquals(Set.of(GasType.CARBON_DIOXIDE, GasType.PLASMA, GasType.TRITIUM,
                        GasType.WATER_VAPOR, GasType.AMMONIA, GasType.NITROUS_OXIDE, GasType.FREZON),
                Ss14VentilationDefaults.DEFAULT_SCRUBBED_GASES);
        assertFalse(Ss14VentilationDefaults.DEFAULT_SCRUBBED_GASES.contains(GasType.OXYGEN));
        assertFalse(Ss14VentilationDefaults.DEFAULT_SCRUBBED_GASES.contains(GasType.NITROGEN));
    }

    @Test
    void referencesExistingPowerDefaultWithoutApplyingAnyLocalVolumeOrCadence() {
        assertEquals(5.0, Ss14VentilationDefaults.APC_REQUESTED_POWER_WATTS, 0.0);
        // This value object deliberately contains no room-volume, tick-rate, or activation behavior.
    }
}
