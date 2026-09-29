package com.juicyslew.moonstation14.ms14.atmos.exposure;

import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class ThermalRegulatorMathTest {
    private static final ThermalRegulatorMath.Policy POLICY = new ThermalRegulatorMath.Policy(
            310, 40, 100, 20, 50, 20, 2);
    private static final ThermalExposureMath.ThermalProfile PROFILE = ThermalExposureMath.ThermalProfile.HUMAN;
    private static final ThermalExposureMath.VacuumPolicy SPACE = new ThermalExposureMath.VacuumPolicy(7000, 8, 2.7);

    @Test
    void sourceDerivedVacuumCoolingIsBodyOnlyAndRoomDoesNotUseSpaceCapacity() {
        double capacity = Math.PI * 0.35 * 0.35 * 185 * 42;
        double expected = 310.15 + (2.7 - 310.15) * 56000 / (capacity + 56000) * 0.1;
        assertEquals(expected, ThermalExposureMath.vacuumBodyKelvin(310.15, PROFILE, SPACE, 1), 1e-10);
        // With the specified efficiency and capacity this is ~29 K, not 2-3 K.
        assertTrue(310.15 - expected > 29 && 310.15 - expected < 30);
        var body = new BodyTemperatureAttachment(new BodyTemperatureComponent(310.15));
        var upstream = new ThermalRegulatorMath.Policy(310.15, 800, 100, 500, 2000, 2000, 2);
        AtomicInteger writes = new AtomicInteger();
        assertEquals(BodyTemperatureSystem.Outcome.APPLIED, BodyTemperatureSystem.transact(body,
                GasMixture.vacuum(), PROFILE, upstream, SPACE,
                energy -> { writes.incrementAndGet(); return true; }, ignored -> true));
        assertEquals(0, writes.get());
        assertEquals(ThermalRegulatorMath.regulate(expected, capacity, upstream, 1, true, true), body.kelvin(), 1e-9);
        var evacuatedRoom = new BodyTemperatureAttachment(new BodyTemperatureComponent(310.15));
        assertEquals(BodyTemperatureSystem.Outcome.APPLIED, BodyTemperatureSystem.transact(evacuatedRoom,
                GasMixture.vacuum(), PROFILE, upstream, ignored -> fail("no gas energy"), ignored -> true));
        assertEquals(ThermalRegulatorMath.regulate(310.15, capacity, upstream, 1, true, true),
                evacuatedRoom.kelvin(), 1e-9);
    }

    @Test
    void vacuumHasNoGasEnergyButCoolsThroughRadiationAndDamagesAtFinalTemperature() {
        var body = new BodyTemperatureAttachment(new BodyTemperatureComponent(260.001));
        AtomicInteger writes = new AtomicInteger();
        AtomicInteger damages = new AtomicInteger();
        assertEquals(BodyTemperatureSystem.Outcome.APPLIED, BodyTemperatureSystem.transact(body,
                GasMixture.vacuum(), PROFILE, POLICY, energy -> { writes.incrementAndGet(); return true; },
                typed -> { assertTrue(typed.containsKey("cold")); damages.incrementAndGet(); return true; }));
        assertEquals(0, writes.get());
        assertEquals(1, damages.get());
        assertTrue(body.kelvin() < 260);
        double previous = body.kelvin();
        for (int i = 0; i < 100; i++) {
            BodyTemperatureSystem.transact(body, GasMixture.vacuum(), PROFILE, POLICY, ignored -> fail("phantom gas write"),
                    ignored -> true);
        }
        assertTrue(body.kelvin() < previous);
    }

    @Test
    void implicitAndActiveRegulationAreBoundedByTargetAndCapability() {
        var equilibrium = new ThermalRegulatorMath.Policy(310, 60, 60, 20, 50, 20, 2);
        assertEquals(310, ThermalRegulatorMath.regulate(310, 2940, equilibrium, 1, true, true));
        var correcting = new ThermalRegulatorMath.Policy(310, 0, 0, 30000, 30000, 30000, 0);
        assertEquals(310, ThermalRegulatorMath.regulate(300, 2940, correcting, 1, true, true));
        assertEquals(310, ThermalRegulatorMath.regulate(320, 2940, correcting, 1, true, true));
        var active = new ThermalRegulatorMath.Policy(310, 0, 0, 0, 2940, 2940, 2);
        assertEquals(301, ThermalRegulatorMath.regulate(300, 2940, active, 1, true, true));
        assertEquals(300, ThermalRegulatorMath.regulate(300, 2940, active, 1, true, false));
        assertEquals(320, ThermalRegulatorMath.regulate(320, 2940, active, 1, false, true));
        assertEquals(310.5, ThermalRegulatorMath.regulate(310.5, 2940, active, 1, true, true));
    }

    @Test
    void roomGasExchangeIsConservativeAndOnlyGasContributionIsWritten() {
        var body = new BodyTemperatureAttachment(new BodyTemperatureComponent(310));
        GasMixture room = new GasMixture(Map.of(GasType.OXYGEN, 1.0), 290);
        double expectedGas = ThermalExposureMath.expose(310, room, PROFILE, 1).environmentEnergyDeltaJoules();
        double[] committed = {Double.NaN};
        assertEquals(BodyTemperatureSystem.Outcome.APPLIED, BodyTemperatureSystem.transact(body, room, PROFILE,
                POLICY, value -> { committed[0] = value; return true; }, ignored -> true));
        assertEquals(expectedGas, committed[0], 1e-9);
        assertTrue(body.kelvin() < 310);
        double afterGas = ThermalExposureMath.expose(310, room, PROFILE, 1).bodyTemperatureKelvin();
        assertEquals(ThermalRegulatorMath.regulate(afterGas, PROFILE.bodyHeatCapacityJoulesPerKelvin(), POLICY, 1, true, true), body.kelvin(), 1e-9);
    }

    @Test
    void unknownSampleCannotBeConfusedWithVacuum() {
        var body = new BodyTemperatureAttachment(new BodyTemperatureComponent(310));
        assertEquals(BodyTemperatureSystem.Outcome.SKIPPED, BodyTemperatureSystem.transact(body,
                null, PROFILE, POLICY, ignored -> fail("gas write"), ignored -> fail("damage")));
        assertEquals(310, body.kelvin());
        assertTrue(BodyTemperatureSystem.eligibleForThermal(true, false, true, true));
        assertFalse(BodyTemperatureSystem.eligibleForThermal(true, true, true, true));
    }

    @Test
    void heatAndColdThresholdsUseFinalTemperatureAndMinimumBodyTemperature() {
        assertEquals(0, ThermalExposureMath.damageAt(325, PROFILE, 1).heat());
        assertTrue(ThermalExposureMath.damageAt(326, PROFILE, 1).heat() > 0);
        assertEquals(0, ThermalExposureMath.damageAt(260, PROFILE, 1).cold());
        assertTrue(ThermalExposureMath.damageAt(259, PROFILE, 1).cold() > 0);
        var intense = new ThermalRegulatorMath.Policy(310, 0, 1_000_000, 0, 0, 0, 0);
        assertEquals(ThermalRegulatorMath.MIN_BODY_KELVIN,
                ThermalRegulatorMath.regulate(300, 2940, intense, 100, true, true));
    }
}
