package com.juicyslew.moonstation14.ms14.atmos.machine.thermal;

import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;

/** Pure SS14 thermomachine step; power is the electrical energy actually paid this step. */
public final class GasThermostatPolicy {
    public record Profile(double ratedWatts, double coefficientOfPerformance, double minimumKelvin,
                          double maximumKelvin, double toleranceKelvin) {
        public Profile {
            if (!Double.isFinite(ratedWatts) || ratedWatts < 0 || !Double.isFinite(coefficientOfPerformance)
                    || coefficientOfPerformance == 0 || !Double.isFinite(minimumKelvin)
                    || !Double.isFinite(maximumKelvin) || minimumKelvin > maximumKelvin
                    || !Double.isFinite(toleranceKelvin) || toleranceKelvin < 0)
                throw new IllegalArgumentException("invalid thermostat profile");
        }
        public static Profile heater() { return new Profile(5000, .95, 73.15, 593.15, 2); }
        public static Profile freezer() { return new Profile(5000, -3.9, 73.15, 593.15, 2); }
    }
    public record State(double targetKelvin, boolean on) {
        public State { if (!Double.isFinite(targetKelvin)) throw new IllegalArgumentException("target must be finite"); }
    }
    public record Result(GasMixture gasAfter, State stateAfter, double gasEnergyDeltaJoules,
                         double electricalEnergyRequiredJoules) { }
    private GasThermostatPolicy() { }

    public static Result step(GasMixture gas, Profile profile, State state, double dtSeconds,
                              double electricalEnergyPaidJoules) {
        if (gas == null || profile == null || state == null || !Double.isFinite(dtSeconds) || dtSeconds < 0
                || !Double.isFinite(electricalEnergyPaidJoules) || electricalEnergyPaidJoules < 0)
            throw new IllegalArgumentException("invalid thermostat input");
        double target = Math.max(profile.minimumKelvin(), Math.min(profile.maximumKelvin(), state.targetKelvin()));
        double sign = Math.signum(profile.coefficientOfPerformance());
        double high = target + sign * profile.toleranceKelvin();
        boolean on = state.on();
        if (sign * gas.temperatureKelvin() >= sign * high) on = false;
        else if (sign * gas.temperatureKelvin() < sign * target) on = true;
        if (!on || gas.heatCapacity() == 0 || dtSeconds == 0 || electricalEnergyPaidJoules == 0)
            return new Result(gas, new State(target, on), 0, 0);
        double requested = Math.min(profile.ratedWatts() * dtSeconds,
                electricalEnergyPaidJoules) * profile.coefficientOfPerformance();
        double bound = (high - gas.temperatureKelvin()) * gas.heatCapacity();
        double delta = Math.copySign(Math.min(Math.abs(requested), Math.abs(bound)), requested);
        GasMixture after = gas.withThermalEnergy(Math.max(0, gas.thermalEnergy() + delta));
        if (Math.signum(delta) == sign && Math.abs(delta) >= Math.abs(bound)) on = false;
        double electric = Math.abs(delta / profile.coefficientOfPerformance());
        return new Result(after, new State(target, on), after.thermalEnergy() - gas.thermalEnergy(), electric);
    }
}
