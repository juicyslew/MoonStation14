package com.juicyslew.moonstation14.ms14.atmos.device;

import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class AtmosphereDeviceRulesTest {
    private static final BlockPos DEVICE_POS = new BlockPos(4, 70, 9);

    @Test
    void frontCellAndAtmosphereStepCadence() {
        assertEquals(DEVICE_POS.relative(Direction.EAST), AtmosphereDeviceRules.target(DEVICE_POS, Direction.EAST));
        assertTrue(AtmosphereDeviceRules.isDue(0));
        assertTrue(AtmosphereDeviceRules.isDue(2));
        assertTrue(AtmosphereDeviceRules.isDue(18));
        assertFalse(AtmosphereDeviceRules.isDue(1));
        assertFalse(AtmosphereDeviceRules.isDue(-2));
    }

    @Test
    void disabledAndNonDueDevicesDoNotSampleOrMutate() {
        FakeReceiver receiver = new FakeReceiver(GasMixture.breathableAir());
        AtmosphereDeviceRules.tick(false, 2, DEVICE_POS, AtmosphereDeviceRules.Device.PRODUCER, receiver);
        AtmosphereDeviceRules.tick(true, 1, DEVICE_POS, AtmosphereDeviceRules.Device.PRODUCER, receiver);
        assertEquals(0, receiver.samples);
        assertEquals(0, receiver.writes);
    }

    @Test
    void producerAddsTwoMolesPerStepAndUsesPartialDoseNearCap() {
        FakeReceiver receiver = new FakeReceiver(GasMixture.vacuum());
        AtmosphereDeviceRules.tick(true, 2, DEVICE_POS, AtmosphereDeviceRules.Device.PRODUCER, receiver);
        assertEquals(1, receiver.writes);
        assertEquals(2.0, receiver.amount);
        assertEquals(293.15, receiver.temperature);

        double molesNearCap = 200000.0 / (8.31446261815324 * 180.0);
        receiver.reset(new GasMixture(Map.of(GasType.OXYGEN, molesNearCap * 0.21,
                GasType.NITROGEN, molesNearCap * 0.79), 180.0));
        AtmosphereDeviceRules.tick(true, 2, DEVICE_POS, AtmosphereDeviceRules.Device.PRODUCER, receiver);
        assertEquals(1, receiver.writes);
        assertTrue(receiver.amount > 0.0 && receiver.amount < 2.0);
        GasMixture projected = receiver.mixture
                .withGasDelta(GasType.OXYGEN, receiver.amount * 0.21, AtmosphereDeviceRules.PRODUCER_TEMPERATURE_KELVIN)
                .withGasDelta(GasType.NITROGEN, receiver.amount * 0.79, AtmosphereDeviceRules.PRODUCER_TEMPERATURE_KELVIN);
        assertTrue(projected.pressureKpa(1.0) <= AtmosphereDeviceRules.PRODUCER_PRESSURE_LIMIT_KPA);
        assertEquals(AtmosphereDeviceRules.PRODUCER_PRESSURE_LIMIT_KPA, projected.pressureKpa(1.0), 1e-8);

        double aboveCapMoles = 203000.0 / (8.31446261815324 * 293.15);
        receiver.reset(new GasMixture(Map.of(GasType.OXYGEN, aboveCapMoles * 0.21,
                GasType.NITROGEN, aboveCapMoles * 0.79), 293.15));
        AtmosphereDeviceRules.tick(true, 2, DEVICE_POS, AtmosphereDeviceRules.Device.PRODUCER, receiver);
        assertEquals(0, receiver.writes);
    }

    @Test
    void pureGasProducerAddsOnlyRequestedSpeciesAndRespectsPressureCapForEveryGas() {
        for (GasType gas : GasType.values()) {
            FakeReceiver receiver = new FakeReceiver(GasMixture.vacuum());
            AtmosphereDeviceRules.tick(true, 2, DEVICE_POS, AtmosphereDeviceRules.Device.PRODUCER, gas, receiver);

            assertEquals(1, receiver.writes, gas.name());
            assertEquals(gas, receiver.addedGas, gas.name());
            assertEquals(2.0, receiver.amount, gas.name());
            assertEquals(AtmosphereDeviceRules.PRODUCER_TEMPERATURE_KELVIN, receiver.temperature, gas.name());
            assertEquals(2.0, receiver.mixture.moles(gas), gas.name());
            assertEquals(1, receiver.mixture.gasMoles().size(), gas.name());
            assertTrue(receiver.mixture.pressureKpa(1.0) <= AtmosphereDeviceRules.PRODUCER_PRESSURE_LIMIT_KPA,
                    gas.name());
        }
    }

    @Test
    void pureGasProducerUsesPartialDoseNearCapForFrezonAndTritium() {
        for (GasType gas : new GasType[]{GasType.FREZON, GasType.TRITIUM}) {
            double initialMoles = 200000.0 / (8.31446261815324 * 180.0);
            FakeReceiver receiver = new FakeReceiver(new GasMixture(Map.of(gas, initialMoles), 180.0));
            AtmosphereDeviceRules.tick(true, 2, DEVICE_POS, AtmosphereDeviceRules.Device.PRODUCER, gas, receiver);

            assertEquals(1, receiver.writes, gas.name());
            assertTrue(receiver.amount > 0.0 && receiver.amount < 2.0, gas.name());
            assertEquals(gas, receiver.addedGas, gas.name());
            assertEquals(1, receiver.mixture.gasMoles().size(), gas.name());
            assertTrue(receiver.mixture.pressureKpa(1.0) <= AtmosphereDeviceRules.PRODUCER_PRESSURE_LIMIT_KPA,
                    gas.name());
            assertEquals(AtmosphereDeviceRules.PRODUCER_PRESSURE_LIMIT_KPA, receiver.mixture.pressureKpa(1.0),
                    1e-8, gas.name());
        }
    }

    @Test
    void sinkAndThermalDevicesHaveBoundedOperationsAndRespectCoolerFloor() {
        GasMixture air = new GasMixture(Map.of(GasType.OXYGEN, 1.0, GasType.NITROGEN, 3.0), 300.0);
        FakeReceiver receiver = new FakeReceiver(air);
        AtmosphereDeviceRules.tick(true, 2, DEVICE_POS, AtmosphereDeviceRules.Device.SINK, receiver);
        assertEquals(2.0, receiver.amount);
        assertEquals(1, receiver.writes);

        receiver.reset(new GasMixture(Map.of(GasType.OXYGEN, 0.1), 300.0));
        AtmosphereDeviceRules.tick(true, 2, DEVICE_POS, AtmosphereDeviceRules.Device.SINK, receiver);
        assertEquals(0.1, receiver.amount);
        assertEquals(1, receiver.writes);

        receiver.reset(new GasMixture(Map.of(GasType.OXYGEN, 10.0, GasType.NITROGEN, 20.0), 300.0));
        AtmosphereDeviceRules.tick(true, 2, DEVICE_POS, AtmosphereDeviceRules.Device.SINK, receiver);
        assertEquals(2.0, receiver.amount);
        assertEquals(1, receiver.writes);

        receiver.reset(air);
        AtmosphereDeviceRules.tick(true, 2, DEVICE_POS, AtmosphereDeviceRules.Device.HEATER, receiver);
        assertEquals(40000.0, receiver.energy);

        receiver.reset(new GasMixture(air.gasMoles(), 2.71));
        AtmosphereDeviceRules.tick(true, 2, DEVICE_POS, AtmosphereDeviceRules.Device.COOLER, receiver);
        assertEquals(-Math.min(40000.0, (2.71 - 2.7) * air.heatCapacity()), receiver.energy, 1e-10);
        assertTrue(receiver.energy >= -40000.0);

        GasMixture hotMixture = new GasMixture(Map.of(GasType.OXYGEN, 1000.0, GasType.NITROGEN, 3000.0), 300.0);
        receiver.reset(hotMixture);
        AtmosphereDeviceRules.tick(true, 2, DEVICE_POS, AtmosphereDeviceRules.Device.COOLER, receiver);
        assertEquals(-40000.0, receiver.energy);
    }

    @Test
    void emptyMixtureDoesNotReceiveThermalMutation() {
        FakeReceiver receiver = new FakeReceiver(GasMixture.vacuum());
        AtmosphereDeviceRules.tick(true, 2, DEVICE_POS, AtmosphereDeviceRules.Device.HEATER, receiver);
        AtmosphereDeviceRules.tick(true, 2, DEVICE_POS, AtmosphereDeviceRules.Device.COOLER, receiver);
        assertEquals(0, receiver.writes);
    }

    @Test
    void tenAtmosphereStepsPreservePerSecondGasAndThermalRates() {
        FakeReceiver receiver = new FakeReceiver(GasMixture.vacuum());
        for (int tick = 0; tick < 20; tick += 2)
            AtmosphereDeviceRules.tick(true, tick, DEVICE_POS, AtmosphereDeviceRules.Device.PRODUCER, receiver);
        assertEquals(10, receiver.writes);
        assertEquals(20.0, receiver.totalAmount);
        assertEquals(2.0, receiver.amount, "producer sends small per-step events");

        receiver.reset(new GasMixture(Map.of(GasType.OXYGEN, 50.0, GasType.NITROGEN, 150.0), 300.0));
        for (int tick = 0; tick < 20; tick += 2)
            AtmosphereDeviceRules.tick(true, tick, DEVICE_POS, AtmosphereDeviceRules.Device.SINK, receiver);
        assertEquals(10, receiver.writes);
        assertEquals(20.0, receiver.totalAmount);
        assertEquals(2.0, receiver.amount);

        GasMixture thermalMixture = new GasMixture(Map.of(GasType.OXYGEN, 1000.0, GasType.NITROGEN, 3000.0), 300.0);
        receiver.reset(thermalMixture);
        for (int tick = 0; tick < 20; tick += 2)
            AtmosphereDeviceRules.tick(true, tick, DEVICE_POS, AtmosphereDeviceRules.Device.HEATER, receiver);
        assertEquals(10, receiver.writes);
        assertEquals(400000.0, receiver.energy);

        receiver.reset(thermalMixture);
        for (int tick = 0; tick < 20; tick += 2)
            AtmosphereDeviceRules.tick(true, tick, DEVICE_POS, AtmosphereDeviceRules.Device.COOLER, receiver);
        assertEquals(10, receiver.writes);
        assertEquals(-400000.0, receiver.energy);
    }

    private static final class FakeReceiver implements AtmosphereDeviceRules.Receiver {
        private GasMixture mixture;
        private int samples;
        private int writes;
        private double amount;
        private double totalAmount;
        private double temperature;
        private double energy;
        private GasType addedGas;

        private FakeReceiver(GasMixture mixture) { this.mixture = mixture; }
        private void reset(GasMixture mixture) {
            this.mixture = mixture;
            samples = writes = 0;
            amount = temperature = energy = 0;
            totalAmount = 0;
            addedGas = null;
        }
        @Override public Optional<GasMixture> sample(BlockPos pos) { samples++; return Optional.of(mixture); }
        @Override public boolean addBreathableAir(BlockPos pos, double moles, double temperatureKelvin) {
            writes++; amount = moles; totalAmount += moles; temperature = temperatureKelvin; return true;
        }
        @Override public boolean addGas(BlockPos pos, GasType gas, double moles, double temperatureKelvin) {
            writes++;
            amount = moles;
            totalAmount += moles;
            temperature = temperatureKelvin;
            addedGas = gas;
            mixture = mixture.withGasDelta(gas, moles, temperatureKelvin);
            return true;
        }
        @Override public double removeGas(BlockPos pos, double moles) { writes++; amount = moles; totalAmount += moles; return moles; }
        @Override public boolean addEnergy(BlockPos pos, double joules) { writes++; energy += joules; return true; }
    }
}
