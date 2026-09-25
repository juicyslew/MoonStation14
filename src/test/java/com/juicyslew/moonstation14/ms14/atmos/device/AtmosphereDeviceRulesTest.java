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
    void frontCellAndFixedOneSecondCadence() {
        assertEquals(DEVICE_POS.relative(Direction.EAST), AtmosphereDeviceRules.target(DEVICE_POS, Direction.EAST));
        assertTrue(AtmosphereDeviceRules.isDue(0));
        assertTrue(AtmosphereDeviceRules.isDue(20));
        assertFalse(AtmosphereDeviceRules.isDue(19));
        assertFalse(AtmosphereDeviceRules.isDue(-20));
    }

    @Test
    void disabledAndNonDueDevicesDoNotSampleOrMutate() {
        FakeReceiver receiver = new FakeReceiver(GasMixture.breathableAir());
        AtmosphereDeviceRules.tick(false, 20, DEVICE_POS, AtmosphereDeviceRules.Device.PRODUCER, receiver);
        AtmosphereDeviceRules.tick(true, 19, DEVICE_POS, AtmosphereDeviceRules.Device.PRODUCER, receiver);
        assertEquals(0, receiver.samples);
        assertEquals(0, receiver.writes);
    }

    @Test
    void producerAddsOneBreathableDoseOnlyWhenProjectedPressureIsBelowCap() {
        FakeReceiver receiver = new FakeReceiver(GasMixture.vacuum());
        AtmosphereDeviceRules.tick(true, 20, DEVICE_POS, AtmosphereDeviceRules.Device.PRODUCER, receiver);
        assertEquals(1, receiver.writes);
        assertEquals(1.0, receiver.amount);
        assertEquals(293.15, receiver.temperature);

        double molesNearCap = 202000.0 / (8.31446261815324 * 293.15);
        receiver.reset(new GasMixture(Map.of(GasType.OXYGEN, molesNearCap * 0.21,
                GasType.NITROGEN, molesNearCap * 0.79), 293.15));
        AtmosphereDeviceRules.tick(true, 20, DEVICE_POS, AtmosphereDeviceRules.Device.PRODUCER, receiver);
        assertEquals(0, receiver.writes);
    }

    @Test
    void sinkAndThermalDevicesHaveBoundedOperationsAndRespectCoolerFloor() {
        GasMixture air = new GasMixture(Map.of(GasType.OXYGEN, 1.0, GasType.NITROGEN, 3.0), 300.0);
        FakeReceiver receiver = new FakeReceiver(air);
        AtmosphereDeviceRules.tick(true, 20, DEVICE_POS, AtmosphereDeviceRules.Device.SINK, receiver);
        assertEquals(1.0, receiver.amount);
        assertEquals(1, receiver.writes);

        receiver.reset(air);
        AtmosphereDeviceRules.tick(true, 20, DEVICE_POS, AtmosphereDeviceRules.Device.HEATER, receiver);
        assertEquals(2000.0, receiver.energy);

        receiver.reset(new GasMixture(air.gasMoles(), 2.71));
        AtmosphereDeviceRules.tick(true, 20, DEVICE_POS, AtmosphereDeviceRules.Device.COOLER, receiver);
        assertEquals(-Math.min(2000.0, (2.71 - 2.7) * air.heatCapacity()), receiver.energy, 1e-10);
        assertTrue(receiver.energy >= -2000.0);
    }

    @Test
    void emptyMixtureDoesNotReceiveThermalMutation() {
        FakeReceiver receiver = new FakeReceiver(GasMixture.vacuum());
        AtmosphereDeviceRules.tick(true, 20, DEVICE_POS, AtmosphereDeviceRules.Device.HEATER, receiver);
        AtmosphereDeviceRules.tick(true, 20, DEVICE_POS, AtmosphereDeviceRules.Device.COOLER, receiver);
        assertEquals(0, receiver.writes);
    }

    private static final class FakeReceiver implements AtmosphereDeviceRules.Receiver {
        private GasMixture mixture;
        private int samples;
        private int writes;
        private double amount;
        private double temperature;
        private double energy;

        private FakeReceiver(GasMixture mixture) { this.mixture = mixture; }
        private void reset(GasMixture mixture) {
            this.mixture = mixture;
            samples = writes = 0;
            amount = temperature = energy = 0;
        }
        @Override public Optional<GasMixture> sample(BlockPos pos) { samples++; return Optional.of(mixture); }
        @Override public boolean addBreathableAir(BlockPos pos, double moles, double temperatureKelvin) {
            writes++; amount = moles; temperature = temperatureKelvin; return true;
        }
        @Override public double removeGas(BlockPos pos, double moles) { writes++; amount = moles; return moles; }
        @Override public boolean addEnergy(BlockPos pos, double joules) { writes++; energy = joules; return true; }
    }
}
