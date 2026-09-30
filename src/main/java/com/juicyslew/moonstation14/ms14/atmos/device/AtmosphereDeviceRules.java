package com.juicyslew.moonstation14.ms14.atmos.device;

import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.Optional;

/** Small, side-effect-free policy boundary for the placeable atmosphere test devices. */
public final class AtmosphereDeviceRules {
    public static final int TICK_CADENCE = AtmosphereService.TICK_CADENCE;
    public static final double MOLES_PER_SECOND = 20.0;
    public static final double PRODUCER_PRESSURE_LIMIT_KPA = 202.65;
    public static final double HEATER_JOULES_PER_SECOND = 400000.0;
    public static final double COOLER_JOULES_PER_SECOND = 400000.0;
    public static final double MIN_TEMPERATURE_KELVIN = 2.7;
    public static final double HEATER_MIN_MOLES = 1.0e-6;
    public static final double HEATER_MIN_HEAT_CAPACITY = 0.0003;
    public static final double HEATER_MAX_TEMPERATURE_KELVIN = 262144.0;
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
        tick(enabled, gameTime, target, device, null, receiver);
    }

    /** A non-null pureGas selects single-species output for producers; other devices ignore it. */
    public static void tick(boolean enabled, long gameTime, BlockPos target, Device device,
                            GasType pureGas, Receiver receiver) {
        if (!enabled || !isDue(gameTime) || target == null || device == null || receiver == null) return;
        Optional<GasMixture> sampled = receiver.sample(target);
        if (sampled == null || sampled.isEmpty()) return;
        GasMixture mixture = sampled.get();
        switch (device) {
            case PRODUCER -> {
                try {
                    double pressure = mixture.pressureKpa(1.0);
                    if (!Double.isFinite(pressure) || pressure >= PRODUCER_PRESSURE_LIMIT_KPA) return;

                    double dose = dosePerStep();
                    if (projectedPressure(mixture, dose, pureGas) > PRODUCER_PRESSURE_LIMIT_KPA) {
                        double low = 0.0;
                        double high = dose;
                        for (int i = 0; i < 80; i++) {
                            double middle = low + (high - low) * 0.5;
                            if (projectedPressure(mixture, middle, pureGas) <= PRODUCER_PRESSURE_LIMIT_KPA)
                                low = middle;
                            else
                                high = middle;
                        }
                        dose = low;
                    }
                    if (Double.isFinite(dose) && dose > 0.0) {
                        if (pureGas == null) {
                            receiver.addBreathableAir(target, dose, PRODUCER_TEMPERATURE_KELVIN);
                        } else {
                            receiver.addGas(target, pureGas, dose, PRODUCER_TEMPERATURE_KELVIN);
                        }
                    }
                } catch (IllegalArgumentException ignored) {
                    // Invalid projected arithmetic is a safe no-op.
                }
            }
            case SINK -> {
                if (Double.isFinite(mixture.totalMoles()) && mixture.totalMoles() > 0.0)
                    receiver.removeGas(target, Math.min(dosePerStep(), mixture.totalMoles()));
            }
            case HEATER -> {
                double offer = heaterOffer(mixture, energyPerStep(HEATER_JOULES_PER_SECOND));
                if (offer > 0.0) receiver.addHeaterEnergy(target, offer);
            }
            case COOLER -> {
                double capacity = mixture.heatCapacity();
                double temperature = mixture.temperatureKelvin();
                double removable = (temperature - MIN_TEMPERATURE_KELVIN) * capacity;
                if (Double.isFinite(capacity) && capacity > 0.0 && Double.isFinite(removable) && removable > 0.0)
                    receiver.addEnergy(target, -Math.min(energyPerStep(COOLER_JOULES_PER_SECOND), removable));
            }
        }
    }

    private static double dosePerStep() { return MOLES_PER_SECOND * TICK_CADENCE / 20.0; }

    private static double energyPerStep(double joulesPerSecond) { return joulesPerSecond * TICK_CADENCE / 20.0; }

    /** Positive heater-only input policy; other sources and signed energy paths remain unchanged. */
    public static double heaterOffer(GasMixture mixture, double requestedJoules) {
        if (mixture == null || !Double.isFinite(requestedJoules) || requestedJoules <= 0.0
                || !Double.isFinite(mixture.totalMoles()) || mixture.totalMoles() < HEATER_MIN_MOLES)
            return 0.0;
        double capacity = mixture.heatCapacity();
        double temperature = mixture.temperatureKelvin();
        if (!Double.isFinite(capacity) || capacity < HEATER_MIN_HEAT_CAPACITY
                || !Double.isFinite(temperature) || temperature >= HEATER_MAX_TEMPERATURE_KELVIN)
            return 0.0;
        double headroom = capacity * (HEATER_MAX_TEMPERATURE_KELVIN - temperature);
        if (!Double.isFinite(headroom) || headroom <= 0.0) return 0.0;
        double offer = Math.min(requestedJoules, headroom);
        double accepted = acceptedHeaterEnergy(mixture, offer);
        if (accepted > 0.0) return accepted;

        // Search representable positive offers rather than trusting the algebraic headroom:
        // the detached mixture uses its actual rounded energy and temperature calculations.
        double low = 0.0;
        double high = offer;
        for (int i = 0; i < 64; i++) {
            double middle = low + (high - low) * 0.5;
            if (middle <= low || middle >= high) break;
            if (acceptedHeaterEnergy(mixture, middle) > 0.0) low = middle;
            else high = middle;
        }
        double result = low > 0.0 ? acceptedHeaterEnergy(mixture, low) : 0.0;
        return result <= offer ? result : 0.0;
    }

    private static double acceptedHeaterEnergy(GasMixture mixture, double offer) {
        try {
            GasMixture projected = mixture.withEnergyDelta(offer);
            double energyChange = projected.thermalEnergy() - mixture.thermalEnergy();
            return Double.isFinite(energyChange) && energyChange > 0.0 && energyChange <= offer
                    && projected.temperatureKelvin() <= HEATER_MAX_TEMPERATURE_KELVIN ? energyChange : 0.0;
        } catch (IllegalArgumentException | IllegalStateException ignored) {
            return 0.0;
        }
    }

    private static double projectedPressure(GasMixture mixture, double dose, GasType pureGas) {
        GasMixture projected = pureGas == null
                ? mixture.withGasDelta(GasType.OXYGEN, dose * 0.21, PRODUCER_TEMPERATURE_KELVIN)
                    .withGasDelta(GasType.NITROGEN, dose * 0.79, PRODUCER_TEMPERATURE_KELVIN)
                : mixture.withGasDelta(pureGas, dose, PRODUCER_TEMPERATURE_KELVIN);
        double pressure = projected.pressureKpa(1.0);
        if (!Double.isFinite(pressure)) throw new IllegalArgumentException("Projected pressure must be finite");
        return pressure;
    }

    public enum Device { PRODUCER, SINK, HEATER, COOLER }

    public interface Receiver {
        Optional<GasMixture> sample(BlockPos pos);
        boolean addBreathableAir(BlockPos pos, double moles, double temperatureKelvin);
        default boolean addGas(BlockPos pos, GasType gas, double moles, double temperatureKelvin) { return false; }
        double removeGas(BlockPos pos, double moles);
        boolean addEnergy(BlockPos pos, double joules);
        default boolean addHeaterEnergy(BlockPos pos, double requestedJoules) { return addEnergy(pos, requestedJoules); }
    }
}
