package com.juicyslew.moonstation14.ms14.power.device;

import net.minecraft.nbt.CompoundTag;

/** Pure validated APC state and authorization rules. Joules are finite and capped. */
public final class PowerDeviceRules {
    public static final double APC_CAPACITY_JOULES = 1_000_000.0;
    public static final double APC_INITIAL_JOULES = 500_000.0;
    public static final double INTERACTION_RANGE_SQUARED = 64.0;
    public static final ApcProtectionConfig DEFAULT_APC_PROTECTION = new ApcProtectionConfig(20_000.0, 3.0);
    public static final int SERVER_TICKS_PER_SECOND = 20;
    public static final int APC_SOLVE_INTERVAL_TICKS = 20;

    private PowerDeviceRules() { }

    public static double validatedEnergy(double joules) {
        return Double.isFinite(joules) ? Math.clamp(joules, 0.0, APC_CAPACITY_JOULES) : 0.0;
    }

    public static boolean canToggle(boolean serverSide, boolean authorized, double distanceSquared) {
        return serverSide && authorized && Double.isFinite(distanceSquared)
                && distanceSquared >= 0.0 && distanceSquared <= INTERACTION_RANGE_SQUARED;
    }

    public static CompoundTag saveApc(double joules, boolean breakerClosed, boolean tripLatched) {
        CompoundTag tag = new CompoundTag();
        tag.putDouble("BatteryJoules", validatedEnergy(joules));
        tag.putBoolean("BreakerClosed", breakerClosed);
        tag.putBoolean("TripLatched", tripLatched);
        return tag;
    }

    /** Compatibility helper for callers persisting an APC without protection state. */
    public static CompoundTag saveApc(double joules, boolean breakerClosed) {
        return saveApc(joules, breakerClosed, false);
    }

    public static ApcState loadApc(CompoundTag tag) {
        double joules = tag.contains("BatteryJoules")
                ? validatedEnergy(tag.getDouble("BatteryJoules")) : APC_INITIAL_JOULES;
        boolean breaker = !tag.contains("BreakerClosed") || tag.getBoolean("BreakerClosed");
        boolean tripped = tag.getBoolean("TripLatched");
        // A persisted protection trip necessarily left the authoritative breaker open.
        if (tripped) breaker = false;
        return new ApcState(joules, breaker, tripped);
    }

    /** One server-tick observation of the latest actual per-APC delivered-output meter. */
    public static ApcProtectionState observeApcOutput(ApcProtectionState state,
                                                       long serverTick, long sampleTick,
                                                       boolean sampleKnown, double actualOutputWatts,
                                                       boolean breakerClosed,
                                                       ApcProtectionConfig config) {
        if (state == null) state = ApcProtectionState.initial();
        if (!breakerClosed || !sampleKnown || !Double.isFinite(actualOutputWatts)
                || actualOutputWatts <= config.maxLoadWatts()
                || serverTick < sampleTick || serverTick - sampleTick > APC_SOLVE_INTERVAL_TICKS) {
            return state.resetAt(serverTick);
        }

        // Only one tick of known, loaded server time is counted per call. Repeated calls
        // for the same tick cannot double-advance; skipped ticks break continuity rather
        // than crediting time that was not observed (including unloaded/work-deferred time).
        if (state.lastAdvancedTick() == serverTick) return state;
        long accumulated = state.overloadTicks();
        if (state.lastAdvancedTick() == Long.MIN_VALUE || serverTick != state.lastAdvancedTick() + 1) {
            accumulated = 0;
        }
        if (accumulated < Long.MAX_VALUE) accumulated++;
        long tripAfterTicks = (long) Math.floor(config.tripTimeSeconds() * SERVER_TICKS_PER_SECOND);
        boolean tripped = accumulated > tripAfterTicks;
        return new ApcProtectionState(accumulated, serverTick, tripped);
    }

    public static ApcProtectionState resetApcProtection(ApcProtectionState state) {
        return state == null ? ApcProtectionState.initial() : state.reset();
    }

    public record ApcState(double energyJoules, boolean breakerClosed, boolean tripLatched) { }

    /** Immutable typed defaults/configuration; units are watts and seconds. */
    public record ApcProtectionConfig(double maxLoadWatts, double tripTimeSeconds) {
        public ApcProtectionConfig {
            if (!Double.isFinite(maxLoadWatts) || maxLoadWatts <= 0
                    || !Double.isFinite(tripTimeSeconds) || tripTimeSeconds <= 0)
                throw new IllegalArgumentException("APC protection thresholds must be finite and positive");
        }
    }

    /** Transient only: reset on load/unload and never stored in NBT. */
    public record ApcProtectionState(long overloadTicks, long lastAdvancedTick, boolean tripped) {
        public static ApcProtectionState initial() { return new ApcProtectionState(0, Long.MIN_VALUE, false); }
        public ApcProtectionState reset() { return initial(); }
        public ApcProtectionState resetAt(long tick) { return new ApcProtectionState(0, tick, false); }
    }
}
