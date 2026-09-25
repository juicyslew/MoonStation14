package com.juicyslew.moonstation14.ms14.atmos.device;

import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.Optional;

/** Small, side-effect-free policy boundary for the placeable atmosphere test devices. */
public final class AtmosphereDeviceRules {
    public static final int TICK_CADENCE = 20;
    public static final double MOLES_PER_SECOND = 1.0;
    public static final double PRODUCER_PRESSURE_LIMIT_KPA = 202.65;
    public static final double HEATER_JOULES_PER_SECOND = 2000.0;
    public static final double COOLER_JOULES_PER_SECOND = 2000.0;
    public static final double MIN_TEMPERATURE_KELVIN = 2.7;
    public static final double PRODUCER_TEMPERATURE_KELVIN = 293.15;

    private AtmosphereDeviceRules() { }

    public static BlockPos target(BlockPos devicePos, Direction facing) {
        return devicePos.relative(facing);
    }

    public static boolean isDue(long gameTime) {
        return gameTime >= 0 && gameTime % TICK_CADENCE == 0;
    }

    /** Disabled devices return before even sampling their receiver. */
    public static void tick(boolean enabled, long gameTime, BlockPos target, Device device, Receiver receiver) {
        if (!enabled || !isDue(gameTime) || target == null || device == null || receiver == null) return;
        Optional<GasMixture> sampled = receiver.sample(target);
        if (sampled == null || sampled.isEmpty()) return;
        GasMixture mixture = sampled.get();
        switch (device) {
            case PRODUCER -> {
                try {
                    double pressure = mixture.pressureKpa(1.0);
                    GasMixture projected = mixture.withGasDelta(GasType.OXYGEN, MOLES_PER_SECOND * 0.21, PRODUCER_TEMPERATURE_KELVIN)
                            .withGasDelta(GasType.NITROGEN, MOLES_PER_SECOND * 0.79, PRODUCER_TEMPERATURE_KELVIN);
                    double afterPressure = projected.pressureKpa(1.0);
                    if (Double.isFinite(pressure) && Double.isFinite(afterPressure)
                            && pressure < PRODUCER_PRESSURE_LIMIT_KPA && afterPressure <= PRODUCER_PRESSURE_LIMIT_KPA)
                        receiver.addBreathableAir(target, MOLES_PER_SECOND, PRODUCER_TEMPERATURE_KELVIN);
                } catch (IllegalArgumentException ignored) {
                    // Invalid projected arithmetic is a safe no-op.
                }
            }
            case SINK -> {
                if (Double.isFinite(mixture.totalMoles()) && mixture.totalMoles() > 0.0)
                    receiver.removeGas(target, MOLES_PER_SECOND);
            }
            case HEATER -> {
                double capacity = mixture.heatCapacity();
                if (Double.isFinite(capacity) && capacity > 0.0)
                    receiver.addEnergy(target, HEATER_JOULES_PER_SECOND);
            }
            case COOLER -> {
                double capacity = mixture.heatCapacity();
                double temperature = mixture.temperatureKelvin();
                double removable = (temperature - MIN_TEMPERATURE_KELVIN) * capacity;
                if (Double.isFinite(capacity) && capacity > 0.0 && Double.isFinite(removable) && removable > 0.0)
                    receiver.addEnergy(target, -Math.min(COOLER_JOULES_PER_SECOND, removable));
            }
        }
    }

    public enum Device { PRODUCER, SINK, HEATER, COOLER }

    public interface Receiver {
        Optional<GasMixture> sample(BlockPos pos);
        boolean addBreathableAir(BlockPos pos, double moles, double temperatureKelvin);
        double removeGas(BlockPos pos, double moles);
        boolean addEnergy(BlockPos pos, double joules);
    }
}
