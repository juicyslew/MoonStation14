package com.juicyslew.moonstation14.ms14.atmos.piping;

import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import com.juicyslew.moonstation14.ms14.atmos.piping.prototype.ModVentilationDevices;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class VentilationGasFlowTest {
    @Test void resolvedDefaultPressureVentMovesTracePlasmaFromLowPressurePipe() {
        var definition = PipeGasTransactionTest.resolvedVentilationDefinitions().get(ModVentilationDevices.VENT);
        assertNotNull(definition);
        var settings = new VentilationDeviceConfig(VentilationDeviceConfig.Kind.VENT, definition).settings();
        assertEquals(0.0, settings.volumeRateM3PerSecond(), "gasVentPump has no scrubber volume-rate field");
        assertEquals(101.325, settings.targetPressureChangeKPaPerSecond());
        var pipe = new GasMixture(Map.of(GasType.PLASMA, 0.4), 300.0, 0.2);
        var room = GasMixture.vacuum();

        var plan = VentilationGasFlow.planVentRelease(pipe, room, 0.2, 1.0, 1.0, settings).orElseThrow(
                () -> new AssertionError("resolved pressure-only pump must release low-pressure Plasma; pipe=" + pipe
                        + " room=" + room + " settings=" + settings));

        assertTrue(plan.packet().moles(GasType.PLASMA) > 0.0,
                "positive source-to-vacuum transfer despite zero scrubber volume rate; packet=" + plan.packet());
        assertTrue(plan.packet().totalMoles() <= pipe.totalMoles());
        assertConserved(pipe, room, plan);
    }

    @Test void ventUsesPressureChangeAndTargetBoundAndConservesInventory() {
        var config = new VentilationDeviceConfig(VentilationDeviceConfig.Kind.VENT);
        GasMixture pipe = new GasMixture(Map.of(GasType.OXYGEN, 100.0), 300.0);
        GasMixture room = new GasMixture(Map.of(GasType.NITROGEN, 1.0), 300.0);
        var plan = VentilationGasFlow.planVentRelease(pipe, room, 1, 1, 1, config.settings()).orElseThrow();
        double expected = (101.325 - room.pressureKpa(1)) * 1000 / (8.31446261815324 * 300);
        assertEquals(expected, plan.packet().totalMoles(), Math.ulp((float) expected) * 8);
        assertEquals(0, plan.packet().moles(GasType.NITROGEN), 0);
        assertConserved(pipe, room, plan);
    }

    @Test void hotVentGasCannotPushRoomPastConfiguredPressureBound() {
        var defaults = new VentilationDeviceConfig(VentilationDeviceConfig.Kind.VENT).settings();
        var settings = new VentilationDeviceConfig.Settings(defaults.revision(), true, 101.325,
                defaults.targetPressureChangeKPaPerSecond(), 4500.0, defaults.volumeRateM3PerSecond(),
                defaults.scrubberMode(), defaults.filteredGases());
        GasMixture pipe = new GasMixture(Map.of(GasType.OXYGEN, 100.0), 800.0);
        GasMixture room = new GasMixture(Map.of(GasType.NITROGEN, 4.0), 300.0);
        var plan = VentilationGasFlow.planVentRelease(pipe, room, 1, 1, 1, settings).orElseThrow();
        assertTrue(plan.packet().totalMoles() > 0.0);
        assertTrue(plan.destinationAfter().pressureKpa(1) <= settings.externalPressureBoundKPa());
        assertConserved(pipe, room, plan);
    }

    @Test void ventAlreadyAtPressureBoundIsNoOp() {
        var defaults = new VentilationDeviceConfig(VentilationDeviceConfig.Kind.VENT).settings();
        var settings = new VentilationDeviceConfig.Settings(defaults.revision(), true, 101.325,
                defaults.targetPressureChangeKPaPerSecond(), 4500.0, defaults.volumeRateM3PerSecond(),
                defaults.scrubberMode(), defaults.filteredGases());
        GasMixture room = new GasMixture(Map.of(GasType.NITROGEN,
                101.325 * 1000 / (8.31446261815324 * 300)), 300.0);
        assertTrue(VentilationGasFlow.planVentRelease(
                new GasMixture(Map.of(GasType.OXYGEN, 10.0), 300.0), room, 1, 1, 1, settings).isEmpty());
    }

    @Test void scrubberFiltersOrSiphonsAtRoomVolumeRate() {
        var cfg = new VentilationDeviceConfig(VentilationDeviceConfig.Kind.SCRUBBER);
        var selected = new GasMixture(Map.of(GasType.OXYGEN, 10.0, GasType.NITROGEN, 20.0,
                GasType.CARBON_DIOXIDE, 5.0), 300);
        var normal = VentilationGasFlow.planScrubberCapture(selected, GasMixture.vacuum(), 1, 1, 1,
                cfg.settings()).orElseThrow();
        assertEquals(1.0, normal.packet().moles(GasType.CARBON_DIOXIDE), 1e-12);
        assertEquals(0.0, normal.packet().moles(GasType.OXYGEN), 0);
        var s = cfg.settings();
        var siphonSettings = new VentilationDeviceConfig.Settings(s.revision(), true, s.externalPressureBoundKPa(),
                s.targetPressureChangeKPaPerSecond(), s.targetPressureKPa(), s.volumeRateM3PerSecond(),
                VentilationDeviceConfig.ScrubberMode.SIPHON, s.filteredGases());
        var siphon = VentilationGasFlow.planScrubberCapture(selected, GasMixture.vacuum(), 1, 1, 1,
                siphonSettings).orElseThrow();
        assertEquals(7.0, siphon.packet().totalMoles(), 1e-12);
        assertConserved(selected, GasMixture.vacuum(), siphon);
    }

    @Test void disabledVacuumSourceAndFullOutletAreNoOps() {
        var config = new VentilationDeviceConfig(VentilationDeviceConfig.Kind.SCRUBBER);
        var s = config.settings();
        var disabled = new VentilationDeviceConfig.Settings(s.revision(), false, s.externalPressureBoundKPa(),
                s.targetPressureChangeKPaPerSecond(), s.targetPressureKPa(), s.volumeRateM3PerSecond(),
                s.scrubberMode(), s.filteredGases());
        var room = GasMixture.breathableAir();
        assertTrue(VentilationGasFlow.planScrubberCapture(room, GasMixture.vacuum(), 1, 1, 1, disabled).isEmpty());
        assertTrue(VentilationGasFlow.planScrubberCapture(GasMixture.vacuum(), GasMixture.vacuum(), 1, 1, 1,
                s).isEmpty());
        var full = new GasMixture(Map.of(GasType.NITROGEN, 1000.0), 300);
        assertTrue(VentilationGasFlow.planScrubberCapture(room, full, 1, 1, 1, s).isEmpty());
    }

    private static void assertConserved(GasMixture source, GasMixture destination, PipeGasTransfer.Plan plan) {
        for (GasType gas : GasType.values()) {
            double expected = source.moles(gas) + destination.moles(gas);
            double actual = plan.sourceAfter().moles(gas) + plan.destinationAfter().moles(gas);
            assertEquals(expected, actual, Math.ulp((float) expected) * 8);
        }
        assertEquals(source.thermalEnergy() + destination.thermalEnergy(),
                plan.sourceAfter().thermalEnergy() + plan.destinationAfter().thermalEnergy(),
                Math.ulp((float) (source.thermalEnergy() + destination.thermalEnergy())) * 8);
    }
}
