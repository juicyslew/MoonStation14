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
    void temperatureOnlyExchangesAndAdjustsWithoutDamageOrRegulation() {
        var profile = new ThermalExposureMath.TemperatureProfile(71.1963435119787, 42, 0.1);
        var body = new BodyTemperatureAttachment(new BodyTemperatureComponent(400));
        var gas = new GasMixture(Map.of(GasType.OXYGEN, 1.0), 500);
        assertEquals(BodyTemperatureSystem.Outcome.APPLIED, BodyTemperatureSystem.transact(body, gas,
                profile, null, null, null, energy -> true, damage -> { throw new AssertionError("no Damage component"); }));
        assertTrue(body.kelvin() > 400);
        double exchanged = body.kelvin();
        assertEquals(com.juicyslew.moonstation14.ms14.effect.EffectResult.APPLIED,
                BodyTemperatureSystem.adjustHeat(body, profile, 42));
        assertTrue(body.kelvin() > exchanged);
    }

    @Test
    void temperatureAndRegulatorRunWithoutTemperatureDamage() {
        var temperature = new ThermalExposureMath.TemperatureProfile(10, 100, 0);
        var regulation = new ThermalRegulatorMath.Policy(310, 100, 0, 0, 0, 0, 1);
        var body = new BodyTemperatureAttachment(new BodyTemperatureComponent(300));
        assertEquals(BodyTemperatureSystem.Outcome.APPLIED, BodyTemperatureSystem.transact(body,
                GasMixture.vacuum(), temperature, null, regulation, null,
                energy -> { throw new AssertionError("no gas energy exchanged"); },
                damage -> { throw new AssertionError("no TemperatureDamage component"); }));
        assertEquals(300.1, body.kelvin(), 1e-12);
    }

    @Test
    void unknownAndProvisionalSamplesLeaveTemperatureOnlySavedStateUnchanged() {
        var temperature = new ThermalExposureMath.TemperatureProfile(10, 100, 0.1);
        var body = new BodyTemperatureAttachment(new BodyTemperatureComponent(400));
        var gas = new GasMixture(Map.of(GasType.OXYGEN, 1.0), 500);
        var noDamage = (java.util.function.Predicate<Map<String, Float>>) amounts -> {
            throw new AssertionError("no TemperatureDamage component");
        };
        assertEquals(BodyTemperatureSystem.Outcome.SKIPPED, BodyTemperatureSystem.transact(body,
                null, temperature, null, null, null, energy -> true, noDamage));
        assertEquals(BodyTemperatureSystem.Outcome.SKIPPED, BodyTemperatureSystem.transact(body,
                gas, temperature, null, null, null,
                BodyTemperatureSystem.energyCommitFor(AtmosphereReading.Status.PROVISIONAL,
                        energy -> { throw new AssertionError("provisional gas write"); }), noDamage));
        assertEquals(400, body.kelvin());
    }

    @Test
    void optionalDamageOnStoredStateAndFailedEnergyPreflight() {
        var profile = new ThermalExposureMath.TemperatureProfile(71.1963435119787, 42, 0.1);
        var damage = new ThermalExposureMath.DamageProfile(325, 260, 1.5, 0.1, 8);
        assertTrue(BodyTemperatureSystem.thresholdDamage(326, damage, 1).containsKey("heat"));
        var body = new BodyTemperatureAttachment(new BodyTemperatureComponent(400));
        int[] calls = {0};
        assertEquals(BodyTemperatureSystem.Outcome.SKIPPED, BodyTemperatureSystem.transact(body,
                new GasMixture(Map.of(GasType.OXYGEN, 1.0), 500), profile, damage, null, null,
                energy -> false, amounts -> { calls[0]++; return true; }));
        assertEquals(400, body.kelvin());
        assertEquals(0, calls[0]);
    }
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
