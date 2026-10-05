package com.juicyslew.moonstation14.ms14.atmos.piping.ui;

import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import com.juicyslew.moonstation14.ms14.atmos.piping.VentilationDeviceConfig;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class VentilationControlIntentPolicyTest {
    @Test void enablingChangesOnlyEnabledAndDoesNotBumpRevision() {
        var current = settings();
        var result = VentilationControlIntentPolicy.apply(current, VentilationDeviceConfig.Kind.VENT,
                VentilationControlRequest.Operation.SET_ENABLED, false, 0).orElseThrow();
        assertFalse(result.configuredEnabled());
        assertEquals(current.revision(), result.revision());
        assertEquals(current.externalPressureBoundKPa(), result.externalPressureBoundKPa());
        assertEquals(current.selectedLayer(), result.selectedLayer());
        assertTrue(current.configuredEnabled());
    }

    @Test void filterTogglePreservesOtherGasAndSiphonIsSeparateFromFilterMode() {
        var co2 = GasType.CARBON_DIOXIDE;
        var oxygen = GasType.OXYGEN;
        var current = settings(Set.of(co2, oxygen));
        var result = VentilationControlIntentPolicy.apply(current, VentilationDeviceConfig.Kind.SCRUBBER,
                VentilationControlRequest.Operation.SET_FILTER_GAS, false, co2.ordinal()).orElseThrow();
        assertEquals(Set.of(oxygen), result.filteredGases());
        assertEquals(Set.of(co2, oxygen), current.filteredGases());

        var siphon = VentilationControlIntentPolicy.apply(current, VentilationDeviceConfig.Kind.SCRUBBER,
                VentilationControlRequest.Operation.SET_SCRUBBER_MODE, true,
                VentilationDeviceConfig.ScrubberMode.SIPHON.ordinal()).orElseThrow();
        assertEquals(VentilationDeviceConfig.ScrubberMode.SIPHON, siphon.scrubberMode());
        assertEquals(current.filteredGases(), siphon.filteredGases());
    }

    @Test void rejectsWrongKindAndOutOfRangeAndTreatsQueryAndNoopAsEmpty() {
        var current = settings();
        assertTrue(VentilationControlIntentPolicy.apply(current, VentilationDeviceConfig.Kind.VENT,
                VentilationControlRequest.Operation.SET_FILTER_GAS, true, 0).isEmpty());
        assertTrue(VentilationControlIntentPolicy.apply(current, VentilationDeviceConfig.Kind.SCRUBBER,
                VentilationControlRequest.Operation.SET_FILTER_GAS, true, GasType.values().length).isEmpty());
        assertTrue(VentilationControlIntentPolicy.apply(current, VentilationDeviceConfig.Kind.SCRUBBER,
                VentilationControlRequest.Operation.SET_SCRUBBER_MODE, true,
                VentilationDeviceConfig.ScrubberMode.values().length).isEmpty());
        assertTrue(VentilationControlIntentPolicy.apply(current, VentilationDeviceConfig.Kind.VENT,
                VentilationControlRequest.Operation.SET_ENABLED, true, 0).isEmpty());
        assertTrue(VentilationControlIntentPolicy.apply(current, VentilationDeviceConfig.Kind.VENT,
                VentilationControlRequest.Operation.QUERY, true, 0).isEmpty());
    }

    private static VentilationDeviceConfig.Settings settings() { return settings(Set.of()); }
    private static VentilationDeviceConfig.Settings settings(Set<GasType> gases) {
        return new VentilationDeviceConfig.Settings(7, true, 12, 3, 100, 4,
                VentilationDeviceConfig.ScrubberMode.FILTER, gases, VentilationDeviceConfig.Layer.SECONDARY);
    }
}
