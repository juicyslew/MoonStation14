package com.juicyslew.moonstation14.ms14.atmos.exposure;

import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;

import java.util.Objects;

/** Pure body/atmosphere thermal exposure math; it does not mutate or update the gas mixture. */
public final class ThermalExposureMath {
    private ThermalExposureMath() {
    }

    /** Physical exchange independent of damage membership. */
    public static ExchangeResult exchange(double currentBodyKelvin, GasMixture gas,
                                          TemperatureProfile profile, double seconds) {
        requirePositiveFinite(currentBodyKelvin, "currentBodyKelvin");
        Objects.requireNonNull(gas, "gas");
        Objects.requireNonNull(profile, "profile");
        requirePositiveFinite(seconds, "seconds");
        double bodyCapacity = profile.bodyHeatCapacityJoulesPerKelvin();
        double gasCapacity = gas.heatCapacity();
        double next = currentBodyKelvin;
        double energy = 0;
        if (gasCapacity > 0) {
            requirePositiveFinite(gas.temperatureKelvin(), "gas temperature");
            double effective = gasCapacity * bodyCapacity / (gasCapacity + bodyCapacity);
            next = Math.max(Double.MIN_NORMAL, currentBodyKelvin +
                    (gas.temperatureKelvin() - currentBodyKelvin) * effective
                            * profile.atmosphereTransferEfficiency() * seconds / bodyCapacity);
            energy = -(next - currentBodyKelvin) * bodyCapacity;
        }
        if (!Double.isFinite(next) || !Double.isFinite(energy)) throw new IllegalArgumentException("invalid exchange");
        return new ExchangeResult(next, energy);
    }

    public record ExchangeResult(double bodyTemperatureKelvin, double environmentEnergyDeltaJoules) { }

    public record TemperatureProfile(double massKg, double specificHeatJoulesPerKgKelvin,
                                     double atmosphereTransferEfficiency) {
        public TemperatureProfile {
            requirePositiveFinite(massKg, "massKg");
            requirePositiveFinite(specificHeatJoulesPerKgKelvin, "specificHeatJoulesPerKgKelvin");
            if (!Double.isFinite(atmosphereTransferEfficiency) || atmosphereTransferEfficiency < 0
                    || atmosphereTransferEfficiency > 1 || !Double.isFinite(massKg * specificHeatJoulesPerKgKelvin))
                throw new IllegalArgumentException("invalid temperature profile");
        }
        public double bodyHeatCapacityJoulesPerKelvin() { return massKg * specificHeatJoulesPerKgKelvin; }
    }

    public record DamageProfile(double heatDamageThresholdKelvin, double coldDamageThresholdKelvin,
                                double heatDamagePerSecond, double coldDamagePerSecond, double damageCap) {
        public DamageProfile {
            requirePositiveFinite(heatDamageThresholdKelvin, "heatDamageThresholdKelvin");
            requirePositiveFinite(coldDamageThresholdKelvin, "coldDamageThresholdKelvin");
            requirePositiveFinite(heatDamagePerSecond, "heatDamagePerSecond");
            requirePositiveFinite(coldDamagePerSecond, "coldDamagePerSecond");
            requirePositiveFinite(damageCap, "damageCap");
            if (heatDamageThresholdKelvin <= coldDamageThresholdKelvin || !Double.isFinite(damageCap * damageCap))
                throw new IllegalArgumentException("invalid damage thresholds/cap");
        }
    }

    public static DamageAmounts damageAt(double bodyKelvin, DamageProfile profile, double seconds) {
        requirePositiveFinite(bodyKelvin, "bodyKelvin");
        Objects.requireNonNull(profile, "profile");
        requirePositiveFinite(seconds, "seconds");
        double heat = bodyKelvin <= profile.heatDamageThresholdKelvin() ? 0 :
                profile.heatDamagePerSecond() * (2 * profile.damageCap() /
                        (1 + Math.exp(-0.005 * (bodyKelvin - profile.heatDamageThresholdKelvin()))) - profile.damageCap()) * seconds;
        double cold = bodyKelvin >= profile.coldDamageThresholdKelvin() ? 0 :
                profile.coldDamagePerSecond() * Math.sqrt((profile.coldDamageThresholdKelvin() - bodyKelvin)
                        * profile.damageCap() * profile.damageCap() / profile.coldDamageThresholdKelvin()) * seconds;
        if (!Double.isFinite(heat) || heat < 0 || !Double.isFinite(cold) || cold < 0)
            throw new IllegalArgumentException("invalid damage amounts");
        return new DamageAmounts(heat, cold);
    }

    /**
     * Applies one exposure interval. The reported environment energy delta is the negative of
     * body sensible-energy gain, for a future caller to apply if/when atmosphere coupling is wired.
     * It is diagnostic only: the exposure calculation does not update the supplied gas.
     */
    public static ExposureResult expose(double currentBodyKelvin, GasMixture authoritativeGas,
                                        ThermalProfile profile, double seconds) {
        requirePositiveFinite(currentBodyKelvin, "currentBodyKelvin");
        Objects.requireNonNull(authoritativeGas, "authoritativeGas");
        Objects.requireNonNull(profile, "profile");
        requirePositiveFinite(seconds, "seconds");

        double bodyHeatCapacity = profile.bodyHeatCapacityJoulesPerKelvin();
        double gasHeatCapacity = authoritativeGas.heatCapacity();
        double newBodyKelvin = currentBodyKelvin;
        double environmentEnergyDeltaJoules = 0.0;
        if (gasHeatCapacity > 0.0) {
            double gasKelvin = authoritativeGas.temperatureKelvin();
            requirePositiveFinite(gasKelvin, "gas temperature");
            double combinedCapacity = gasHeatCapacity + bodyHeatCapacity;
            double effectiveCapacity = gasHeatCapacity * bodyHeatCapacity / combinedCapacity;
            double transferredEnergy = (gasKelvin - currentBodyKelvin) * effectiveCapacity
                    * profile.atmosphereTransferEfficiency() * seconds;
            double bodyDeltaKelvin = transferredEnergy / bodyHeatCapacity;
            newBodyKelvin = Math.max(Double.MIN_NORMAL, currentBodyKelvin + bodyDeltaKelvin);
            transferredEnergy = (newBodyKelvin - currentBodyKelvin) * bodyHeatCapacity;
            environmentEnergyDeltaJoules = -transferredEnergy;
        }
        if (!Double.isFinite(newBodyKelvin) || newBodyKelvin <= 0.0
                || !Double.isFinite(environmentEnergyDeltaJoules)) {
            throw new IllegalArgumentException("Exposure would produce non-finite thermal values");
        }

        return new ExposureResult(newBodyKelvin, environmentEnergyDeltaJoules,
                damageAt(newBodyKelvin, profile, seconds));
    }

    /** SS14's empty-space heat capacity is a gameplay coefficient, not gas inventory.
     * The local equivalent transfers heat out of the body only, never into a mixture. */
    public static double vacuumBodyKelvin(double bodyKelvin, ThermalProfile body,
                                           VacuumPolicy space, double seconds) {
        return vacuumBodyKelvin(bodyKelvin, new TemperatureProfile(body.massKg(), body.specificHeatJoulesPerKgKelvin(),
                body.atmosphereTransferEfficiency()), space, seconds);
    }

    public static double vacuumBodyKelvin(double bodyKelvin, TemperatureProfile body,
                                           VacuumPolicy space, double seconds) {
        requirePositiveFinite(bodyKelvin, "bodyKelvin");
        Objects.requireNonNull(body, "body");
        Objects.requireNonNull(space, "space");
        requirePositiveFinite(seconds, "seconds");
        double cBody = body.bodyHeatCapacityJoulesPerKelvin();
        double cSpace = space.spaceHeatCapacityJoulesPerKelvin() * space.spaceHeatScale();
        double joules = (space.spaceTemperatureKelvin() - bodyKelvin)
                * (cBody * cSpace / (cBody + cSpace)) * body.atmosphereTransferEfficiency() * seconds;
        double next = Math.max(space.spaceTemperatureKelvin(), bodyKelvin + joules / cBody);
        if (!Double.isFinite(next)) throw new IllegalArgumentException("non-finite vacuum cooling");
        return next;
    }

    public record VacuumPolicy(double spaceHeatCapacityJoulesPerKelvin, double spaceHeatScale,
                               double spaceTemperatureKelvin) {
        public VacuumPolicy {
            requirePositiveFinite(spaceHeatCapacityJoulesPerKelvin, "spaceHeatCapacityJoulesPerKelvin");
            requirePositiveFinite(spaceHeatScale, "spaceHeatScale");
            requirePositiveFinite(spaceTemperatureKelvin, "spaceTemperatureKelvin");
            if (!Double.isFinite(spaceHeatCapacityJoulesPerKelvin * spaceHeatScale))
                throw new IllegalArgumentException("non-finite space capacity");
        }
    }

    /** Computes threshold damage from body state alone, independent of an atmosphere sample. */
    public static DamageAmounts damageAt(double bodyKelvin, ThermalProfile profile, double seconds) {
        requirePositiveFinite(bodyKelvin, "bodyKelvin");
        Objects.requireNonNull(profile, "profile");
        requirePositiveFinite(seconds, "seconds");
        double heatDamage = heatDamage(bodyKelvin, profile) * seconds;
        double coldDamage = coldDamage(bodyKelvin, profile) * seconds;
        if (!Double.isFinite(heatDamage) || heatDamage < 0.0
                || !Double.isFinite(coldDamage) || coldDamage < 0.0) {
            throw new IllegalArgumentException("Exposure would produce invalid damage amounts");
        }
        return new DamageAmounts(heatDamage, coldDamage);
    }

    private static double heatDamage(double bodyKelvin, ThermalProfile profile) {
        if (bodyKelvin <= profile.heatDamageThresholdKelvin()) return 0.0;
        double difference = bodyKelvin - profile.heatDamageThresholdKelvin();
        double multiplier = (2.0 * profile.damageCap()) / (1.0 + Math.exp(-0.005 * difference))
                - profile.damageCap();
        return profile.heatDamagePerSecond() * multiplier;
    }

    private static double coldDamage(double bodyKelvin, ThermalProfile profile) {
        if (bodyKelvin >= profile.coldDamageThresholdKelvin()) return 0.0;
        double difference = profile.coldDamageThresholdKelvin() - bodyKelvin;
        double multiplier = Math.sqrt(difference * profile.damageCap() * profile.damageCap()
                / profile.coldDamageThresholdKelvin());
        return profile.coldDamagePerSecond() * multiplier;
    }

    private static void requirePositiveFinite(double value, String name) {
        if (!Double.isFinite(value) || value <= 0.0) {
            throw new IllegalArgumentException(name + " must be finite and greater than zero");
        }
    }

    public record ThermalProfile(double massKg, double specificHeatJoulesPerKgKelvin,
                                 double atmosphereTransferEfficiency,
                                 double heatDamageThresholdKelvin, double coldDamageThresholdKelvin,
                                 double heatDamagePerSecond, double coldDamagePerSecond,
                                 double damageCap) {
        public static final ThermalProfile HUMAN = new ThermalProfile(
                 Math.PI * 0.35 * 0.35 * 185.0, 42.0, 0.1, 325.0, 260.0, 1.5, 0.1, 8.0);

        public ThermalProfile {
            requirePositiveFinite(massKg, "massKg");
            requirePositiveFinite(specificHeatJoulesPerKgKelvin, "specificHeatJoulesPerKgKelvin");
            if (!Double.isFinite(atmosphereTransferEfficiency) || atmosphereTransferEfficiency < 0.0
                    || atmosphereTransferEfficiency > 1.0) {
                throw new IllegalArgumentException("atmosphereTransferEfficiency must be between zero and one");
            }
            requirePositiveFinite(heatDamageThresholdKelvin, "heatDamageThresholdKelvin");
            requirePositiveFinite(coldDamageThresholdKelvin, "coldDamageThresholdKelvin");
            requirePositiveFinite(heatDamagePerSecond, "heatDamagePerSecond");
            requirePositiveFinite(coldDamagePerSecond, "coldDamagePerSecond");
            requirePositiveFinite(damageCap, "damageCap");
            if (!Double.isFinite(damageCap * damageCap)) {
                throw new IllegalArgumentException("damageCap squared must be finite");
            }
            if (!Double.isFinite(massKg * specificHeatJoulesPerKgKelvin)) {
                throw new IllegalArgumentException("body heat capacity must be finite");
            }
        }

        public double bodyHeatCapacityJoulesPerKelvin() {
            return massKg * specificHeatJoulesPerKgKelvin;
        }
    }

    public record DamageAmounts(double heat, double cold) {
    }

    public record ExposureResult(double bodyTemperatureKelvin, double environmentEnergyDeltaJoules,
                                 DamageAmounts damage) {
    }
}
