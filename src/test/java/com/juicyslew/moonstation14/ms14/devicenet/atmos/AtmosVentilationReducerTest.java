package com.juicyslew.moonstation14.ms14.devicenet.atmos;

import static org.junit.jupiter.api.Assertions.*;

import com.juicyslew.moonstation14.ms14.atmos.piping.VentilationDeviceConfig;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AtmosVentilationReducerTest {
    @Test void queuedStateIsFullAndUsesExistingRevisionCas() {
        var config = new VentilationDeviceConfig(VentilationDeviceConfig.Kind.VENT);
        var before = config.settings();
        var command = new AtmosDevicePayload.SetState(before.revision(), false, 77, 12, 44, 0,
                VentilationDeviceConfig.ScrubberMode.FILTER, Set.of(), VentilationDeviceConfig.Layer.SECONDARY);
        assertTrue(AtmosVentilationReducer.apply(config, command));
        assertFalse(config.settings().configuredEnabled());
        assertEquals(77, config.settings().externalPressureBoundKPa());
        assertEquals(44, config.settings().targetPressureKPa());
        assertEquals(VentilationDeviceConfig.Layer.SECONDARY, config.settings().selectedLayer());
        assertFalse(AtmosVentilationReducer.apply(config, command), "stale queued command must fail CAS");
    }

    @Test void deviceSpecificFieldsCannotBeAppliedToWrongFamily() {
        var config = new VentilationDeviceConfig(VentilationDeviceConfig.Kind.VENT);
        var command = new AtmosDevicePayload.SetState(0, true, 0, 0, 0, 2,
                VentilationDeviceConfig.ScrubberMode.FILTER, Set.of(), VentilationDeviceConfig.Layer.PRIMARY);
        assertFalse(AtmosVentilationReducer.apply(config, command));
    }
}
