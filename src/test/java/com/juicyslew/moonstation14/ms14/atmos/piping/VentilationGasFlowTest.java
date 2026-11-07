package com.juicyslew.moonstation14.ms14.atmos.piping;

import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import com.juicyslew.moonstation14.ms14.atmos.piping.prototype.ModVentilationDevices;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class VentilationGasFlowTest {
    @Test void sharedNetworkVolumeDrivesVentPressureAndCapacity() {
        var settings = new VentilationDeviceConfig(VentilationDeviceConfig.Kind.VENT).settings();
        var pump = new com.juicyslew.moonstation14.ms14.atmos.piping.prototype.VentilationDeviceData.GasVentPump(
                101.325, 101.325, 2,
                com.juicyslew.moonstation14.ms14.atmos.piping.prototype.VentilationDeviceData.PressureChecks.EXTERNAL, 0);
        // Multi-node shared network: whole-network volume is 1 m3, not the 0.2 m3 node volume.
        var shared = new GasMixture(Map.of(GasType.OXYGEN, 100.0), 300.0, 1.0);
        var room = GasMixture.vacuum();
        var plan = VentilationGasFlow.planVentRelease(shared, room, shared.volumeCubicMeters(), 1.0,
                1.0, settings, pump).orElseThrow();
        assertTrue(plan.packet().totalMoles() > 0);
        assertEquals(1.0, plan.sourceAfter().volumeCubicMeters());
        double expectedPressure=(float)((float)plan.sourceAfter().totalMoles()*8.3144626f*300f/1f/1000f);
        assertEquals(expectedPressure,plan.sourceAfter().pressureKpa(1.0),0,
                "source pressure is evaluated using shared network volume");
        assertTrue(plan.packet().totalMoles() <= shared.totalMoles());
    }

    @Test void upstreamExternalPumpPowerGateAndConditionalInternalFloor() {
        var settings = new VentilationDeviceConfig(VentilationDeviceConfig.Kind.VENT).settings();
        var low = new GasMixture(Map.of(GasType.OXYGEN, 0.4), 300, .2);
        var vacuum = GasMixture.vacuum();
        var external = new com.juicyslew.moonstation14.ms14.atmos.piping.prototype.VentilationDeviceData.GasVentPump(
                101.325, 101.325, 2, com.juicyslew.moonstation14.ms14.atmos.piping.prototype.VentilationDeviceData.PressureChecks.EXTERNAL, 0);
        var room20 = new GasMixture(Map.of(GasType.NITROGEN, 20 * 1000 / (8.31446261815324 * 300)), 300);
        var room21 = new GasMixture(Map.of(GasType.NITROGEN, 21 * 1000 / (8.31446261815324 * 300)), 300);
        var room15 = new GasMixture(Map.of(GasType.NITROGEN, 4 * 1000 / (8.31446261815324 * 300)), 300);
        assertTrue(VentilationGasFlow.planVentRelease(low, room20, .2, 1, 1, settings, external).isEmpty());
        assertTrue(VentilationGasFlow.planVentRelease(low, room21, .2, 1, 1, settings, external).isEmpty());
        var compressed = VentilationGasFlow.planVentRelease(low, room15, .2, 1, 1, settings, external).orElseThrow();
        assertTrue(compressed.destinationAfter().pressureKpa(1) > 4);
        assertTrue(VentilationGasFlow.planVentRelease(low, vacuum, .2, 1, 1, settings, external).isPresent());
        var source = new GasMixture(Map.of(GasType.OXYGEN, 100.0), 600, .2);
        var plan = VentilationGasFlow.planVentRelease(source, vacuum, .2, 1, 1, settings, external).orElseThrow();
        assertTrue(plan.packet().totalMoles() > 0);
        assertEquals(101.325 * 1000 / (8.31446261815324 * 600), plan.packet().totalMoles(), 1e-6,
                "release moles use source temperature, not room temperature");
        var internal = new com.juicyslew.moonstation14.ms14.atmos.piping.prototype.VentilationDeviceData.GasVentPump(
                101.325, 101.325, 2, com.juicyslew.moonstation14.ms14.atmos.piping.prototype.VentilationDeviceData.PressureChecks.INTERNAL, 10);
        var belowFloor = new GasMixture(Map.of(GasType.OXYGEN, .1), 300, .2);
        assertTrue(VentilationGasFlow.planVentRelease(belowFloor, vacuum, .2, 1, 1, settings, internal).isEmpty());
        var noChecks = new com.juicyslew.moonstation14.ms14.atmos.piping.prototype.VentilationDeviceData.GasVentPump(
                101.325, 101.325, 2, com.juicyslew.moonstation14.ms14.atmos.piping.prototype.VentilationDeviceData.PressureChecks.NO, 10);
        var restricted = new VentilationDeviceConfig.Settings(settings.revision(), true, 1, 101.325, 50,
                0, settings.scrubberMode(), settings.filteredGases());
        assertTrue(VentilationGasFlow.planVentRelease(low, vacuum, .2, 1, 1, restricted, noChecks).isPresent());
    }

    @Test void resolvedDefaultPressureVentMovesTracePlasmaFromLowPressurePipe() {
        var definition = PipeGasTransactionTest.resolvedVentilationDefinitions().get(ModVentilationDevices.VENT);
        assertNotNull(definition);
        var settings = new VentilationDeviceConfig(VentilationDeviceConfig.Kind.VENT, definition).settings();
        assertEquals(0.0, settings.volumeRateM3PerSecond(), "gasVentPump has no scrubber volume-rate field");
        assertEquals(101.325, settings.targetPressureChangeKPaPerSecond());
        var pipe = new GasMixture(Map.of(GasType.PLASMA, 0.4), 300.0, 0.2);
        var room = GasMixture.vacuum();

        var plan = VentilationGasFlow.planVentRelease(pipe, room, 0.2, 1.0, 1.0, settings, definition.gasVentPump()).orElseThrow(
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
        var plan = VentilationGasFlow.planVentRelease(pipe, room, 1, 1, 1, config.settings(), pump()).orElseThrow();
        // Independent ideal-gas expectation in the runtime's specified float representation.
        double pressureDelta = 101.325 - room.pressureKpa(1);
        double expected = (float) pressureDelta * 1.0f * 1000.0f / (300.0f * 8.3144626f);
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
        var plan = VentilationGasFlow.planVentRelease(pipe, room, 1, 1, 1, settings, pump()).orElseThrow();
        assertTrue(plan.packet().totalMoles() > 0.0);
        assertTrue(plan.destinationAfter().pressureKpa(1) <= settings.externalPressureBoundKPa());
        assertConserved(pipe, room, plan);
    }

    @Test void continuousExteriorBleedIsBoundedToAmbientPlusOffsetForVacuumAndPlanetaryAir() {
        var defaults=new VentilationDeviceConfig(VentilationDeviceConfig.Kind.VENT).settings();
        double r=8.31446261815324,temperature=293.15,volume=1d,offset=.05d;
        for(double ambient:new double[]{0d,101.325d}) {
            double target=ambient+offset;
            double bound=Math.max(defaults.externalPressureBoundKPa(),target);
            var settings=new VentilationDeviceConfig.Settings(defaults.revision(),true,bound,
                    defaults.targetPressureChangeKPaPerSecond(),target,defaults.volumeRateM3PerSecond(),
                    defaults.scrubberMode(),defaults.filteredGases(),defaults.selectedLayer());
            var room=ambient==0?GasMixture.vacuum():new GasMixture(Map.of(GasType.NITROGEN,ambient*1000*volume/(r*temperature)),temperature,volume);
            var hotPipe=new GasMixture(Map.of(GasType.NITROGEN,30d,GasType.CARBON_DIOXIDE,13d),564d,.2d);
            var plan=VentilationGasFlow.planVentRelease(hotPipe,room,.2d,volume,.6d,settings,pump()).orElseThrow();
            assertTrue(plan.packet().totalMoles()>0);
            assertTrue(plan.packet().totalMoles()<=.02051d+1e-6,"ambient offset bounds a 293.15 K, 1 m3 packet");
            assertTrue(plan.destinationAfter().pressureKpa(volume)<=bound+1e-4);
            assertEquals(plan.packet().totalMoles(),plan.packet().moles(GasType.NITROGEN)+plan.packet().moles(GasType.CARBON_DIOXIDE),1e-6);
            assertTrue(plan.packet().moles(GasType.NITROGEN)>0&&plan.packet().moles(GasType.CARBON_DIOXIDE)>0,"real mixed exhaust remains mixed");
            assertConserved(hotPipe,room,plan);
        }
    }

    @Test void ventAlreadyAtPressureBoundIsNoOp() {
        var defaults = new VentilationDeviceConfig(VentilationDeviceConfig.Kind.VENT).settings();
        var settings = new VentilationDeviceConfig.Settings(defaults.revision(), true, 101.325,
                defaults.targetPressureChangeKPaPerSecond(), 4500.0, defaults.volumeRateM3PerSecond(),
                defaults.scrubberMode(), defaults.filteredGases());
        GasMixture room = new GasMixture(Map.of(GasType.NITROGEN,
                101.325 * 1000 / (8.31446261815324 * 300)), 300.0);
        assertTrue(VentilationGasFlow.planVentRelease(
                new GasMixture(Map.of(GasType.OXYGEN, 10.0), 300.0), room, 1, 1, 1, settings, pump()).isEmpty());
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
    private static com.juicyslew.moonstation14.ms14.atmos.piping.prototype.VentilationDeviceData.GasVentPump pump() {
        return PipeGasTransactionTest.resolvedVentilationDefinitions().get(ModVentilationDevices.VENT).gasVentPump();
    }
}
