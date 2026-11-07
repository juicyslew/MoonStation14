package com.juicyslew.moonstation14.ms14.atmos.teg;

/** Pure offer smoothing and ramp tracking for a TEG supply device. */
public final class TegSupplyPolicy {
    public static final double DEFAULT_RAMP_FACTOR = 1.05;
    public static final double DEFAULT_RAMP_MINIMUM = 5000.0;
    public static final double DEFAULT_POWER_FACTOR = 1.05;
    public static final double DEFAULT_PREVIOUS_SUPPLY_WEIGHT = 0.8;

    private TegSupplyPolicy() { }

    public static Result update(State previous, double generatedEnergyJoules, double elapsedSeconds,
                                double actualDrawWatts, boolean eligible) {
        return update(previous, generatedEnergyJoules, elapsedSeconds, actualDrawWatts, eligible,
                DEFAULT_RAMP_FACTOR, DEFAULT_RAMP_MINIMUM, 1.0 - DEFAULT_PREVIOUS_SUPPLY_WEIGHT);
    }

    /** Read-only view of the persistent published supply snapshot, independent of the
     * transient offer consumed and cleared by the power allocator. */
    public static com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntimeNode.SupplierOffer snapshot(State state) {
        if (state == null) return null;
        return new com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntimeNode.SupplierOffer(
                state.maximumSupplyWatts(), state.maximumSupplyWatts());
    }

    /** Uses resolved prototype ramp and smoothing values for the live profile consumer. */
    public static Result update(State previous, double generatedEnergyJoules, double elapsedSeconds,
                                double actualDrawWatts, boolean eligible, double rampFactor,
                                double minimumPowerWatts, double smoothing) {
        if (previous == null) throw new NullPointerException("previous");
        finiteNonnegative(generatedEnergyJoules, "generatedEnergyJoules");
        if (!Double.isFinite(elapsedSeconds) || elapsedSeconds <= 0) throw new IllegalArgumentException("elapsedSeconds must be positive and finite");
        finiteNonnegative(actualDrawWatts, "actualDrawWatts");
        if (!Double.isFinite(rampFactor) || rampFactor < 1 || !Double.isFinite(minimumPowerWatts)
                || minimumPowerWatts < 0 || !Double.isFinite(smoothing) || smoothing < 0 || smoothing > 1)
            throw new IllegalArgumentException("invalid resolved TEG supply settings");
        if (!eligible) return new Result(State.zero(), 0, 0);
        double physicalPower = generatedEnergyJoules / elapsedSeconds;
        if (!Double.isFinite(physicalPower)) throw new IllegalArgumentException("generated power overflow");
        // Clamp actual draw to the prior ramp's multiplicative window, then apply upstream floor.
        double lower = previous.rampPositionWatts() / rampFactor;
        double upper = previous.rampPositionWatts() * rampFactor;
        double ramp = Math.max(minimumPowerWatts, Math.max(lower, Math.min(upper, actualDrawWatts)));
        // SS14 TegSystem: blend RampFactor-scaled physical generation with the previous MaxSupply.
        double offer = smoothing * (physicalPower * rampFactor)
                + (1.0 - smoothing) * previous.maximumSupplyWatts();
        if (!Double.isFinite(offer)) throw new IllegalArgumentException("supply offer overflow");
        return new Result(new State(ramp, offer), physicalPower, offer);
    }

    private static void finiteNonnegative(double value, String name) {
        if (!Double.isFinite(value) || value < 0) throw new IllegalArgumentException(name + " must be finite and nonnegative");
    }

    public record State(double rampPositionWatts, double maximumSupplyWatts) {
        public State {
            finiteNonnegative(rampPositionWatts, "rampPositionWatts");
            finiteNonnegative(maximumSupplyWatts, "maximumSupplyWatts");
        }
        public static State zero() { return new State(DEFAULT_RAMP_MINIMUM, 0); }
    }

    public record Result(State state, double physicalGenerationWatts, double offeredMaximumSupplyWatts) { }
}
