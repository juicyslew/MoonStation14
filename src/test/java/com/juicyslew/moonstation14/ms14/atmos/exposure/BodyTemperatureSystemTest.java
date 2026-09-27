package com.juicyslew.moonstation14.ms14.atmos.exposure;

import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereReading;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BodyTemperatureSystemTest {
    @Test
    void acceptedTransactionCommitsEnergyThenBodyThenOneTypedDamageAttempt() {
        var body = new BodyTemperatureAttachment(new BodyTemperatureComponent(400));
        var order = new ArrayList<String>();
        var gas = new GasMixture(Map.of(GasType.OXYGEN, 1.0), 500.0);
        var result = BodyTemperatureSystem.transact(body, gas, ThermalExposureMath.ThermalProfile.HUMAN,
                energy -> { assertTrue(energy < 0); order.add("energy"); return true; },
                damage -> { assertTrue(damage.containsKey("heat")); order.add("damage"); return true; });

        assertEquals(BodyTemperatureSystem.Outcome.APPLIED, result);
        assertTrue(body.kelvin() > 400);
        assertEquals(java.util.List.of("energy", "damage"), order);
    }

    @Test
    void rejectedEnergyWriteLeavesBodyAloneAndDoesNotApplyDamage() {
        var body = new BodyTemperatureAttachment(new BodyTemperatureComponent(400));
        var damageCalled = new boolean[1];
        var result = BodyTemperatureSystem.transact(body,
                new GasMixture(Map.of(GasType.OXYGEN, 1.0), 500.0),
                ThermalExposureMath.ThermalProfile.HUMAN,
                BodyTemperatureSystem.energyCommitFor(AtmosphereReading.Status.FINITE, energy -> false),
                damage -> { damageCalled[0] = true; return true; });

        assertEquals(BodyTemperatureSystem.Outcome.SKIPPED, result);
        assertEquals(400, body.kelvin());
        assertFalse(damageCalled[0]);
    }

    @Test
    void exteriorReservoirAcceptsExchangeWithoutAttemptingWorldWriteAndDamageRunsOnce() {
        var body = new BodyTemperatureAttachment(new BodyTemperatureComponent(400));
        var gasWrites = new int[1];
        var damageCalls = new int[1];
        var result = BodyTemperatureSystem.transact(body,
                new GasMixture(Map.of(GasType.OXYGEN, 1.0), 500.0),
                ThermalExposureMath.ThermalProfile.HUMAN,
                BodyTemperatureSystem.energyCommitFor(AtmosphereReading.Status.EXTERIOR,
                        energy -> { gasWrites[0]++; return false; }),
                damage -> { damageCalls[0]++; assertTrue(damage.containsKey("heat")); return true; });

        assertEquals(BodyTemperatureSystem.Outcome.APPLIED, result);
        assertTrue(body.kelvin() > 400);
        assertEquals(0, gasWrites[0]);
        assertEquals(1, damageCalls[0]);
    }

    @Test
    void provisionalOwnershipCannotDriveThermalPhysics() {
        var body = new BodyTemperatureAttachment(new BodyTemperatureComponent(400));
        var damageCalls = new int[1];
        var result = BodyTemperatureSystem.transact(body,
                new GasMixture(Map.of(GasType.OXYGEN, 1.0), 500.0),
                ThermalExposureMath.ThermalProfile.HUMAN,
                BodyTemperatureSystem.energyCommitFor(AtmosphereReading.Status.PROVISIONAL, energy -> true),
                damage -> { damageCalls[0]++; return true; });

        assertEquals(BodyTemperatureSystem.Outcome.SKIPPED, result);
        assertEquals(400, body.kelvin());
        assertEquals(0, damageCalls[0]);
    }

    @Test
    void unavailableSampleFallbackDamagesOverheatedBodyOnceAndNormalBodyNotAtAll() {
        var calls = new int[1];
        assertTrue(BodyTemperatureSystem.applyBodyDamage(326.0, ThermalExposureMath.ThermalProfile.HUMAN,
                1.0, damage -> { calls[0]++; assertTrue(damage.containsKey("heat")); return true; }));
        assertEquals(1, calls[0]);
        assertTrue(BodyTemperatureSystem.applyBodyDamage(310.15, ThermalExposureMath.ThermalProfile.HUMAN,
                1.0, damage -> { calls[0]++; return true; }));
        assertEquals(1, calls[0]);
    }

    @Test
    void spectatorControllerAndUnboundOrDisabledActorPoliciesAreInert() {
        assertFalse(BodyTemperatureSystem.eligibleForThermal(true, true, true, true));
        assertFalse(BodyTemperatureSystem.eligibleForThermal(true, false, false, true));
        assertFalse(BodyTemperatureSystem.eligibleForThermal(true, false, true, false));
        assertTrue(BodyTemperatureSystem.eligibleForThermal(true, false, true, true));
        // A corporeal villager is not rejected by the spectator-player policy.
        assertTrue(BodyTemperatureSystem.eligibleForThermal(true, false, true, true));
    }

    @Test
    void noEnergyDeltaDoesNotCallGasWriterAndThresholdsProduceNoDamage() {
        var body = new BodyTemperatureAttachment(new BodyTemperatureComponent(310.15));
        var gasWrites = new boolean[1];
        var damageCalls = new int[1];
        var result = BodyTemperatureSystem.transact(body, GasMixture.vacuum(),
                ThermalExposureMath.ThermalProfile.HUMAN,
                energy -> { gasWrites[0] = true; return true; },
                damage -> { damageCalls[0]++; return true; });
        assertEquals(BodyTemperatureSystem.Outcome.APPLIED, result);
        assertFalse(gasWrites[0]);
        assertEquals(0, damageCalls[0]);
        assertEquals(310.15, body.kelvin());
    }
}
