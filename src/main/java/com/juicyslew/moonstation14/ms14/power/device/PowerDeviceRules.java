package com.juicyslew.moonstation14.ms14.power.device;

import net.minecraft.nbt.CompoundTag;

/** Pure validated APC state and authorization rules. Joules are finite and capped. */
public final class PowerDeviceRules {
    public static final double APC_CAPACITY_JOULES = 1_000_000.0;
    public static final double APC_INITIAL_JOULES = 500_000.0;
    public static final double INTERACTION_RANGE_SQUARED = 64.0;

    private PowerDeviceRules() { }

    public static double validatedEnergy(double joules) {
        return Double.isFinite(joules) ? Math.clamp(joules, 0.0, APC_CAPACITY_JOULES) : 0.0;
    }

    public static boolean canToggle(boolean serverSide, boolean authorized, double distanceSquared) {
        return serverSide && authorized && Double.isFinite(distanceSquared)
                && distanceSquared >= 0.0 && distanceSquared <= INTERACTION_RANGE_SQUARED;
    }

    public static CompoundTag saveApc(double joules, boolean breakerClosed) {
        CompoundTag tag = new CompoundTag();
        tag.putDouble("BatteryJoules", validatedEnergy(joules));
        tag.putBoolean("BreakerClosed", breakerClosed);
        return tag;
    }

    public static ApcState loadApc(CompoundTag tag) {
        double joules = tag.contains("BatteryJoules")
                ? validatedEnergy(tag.getDouble("BatteryJoules")) : APC_INITIAL_JOULES;
        boolean breaker = !tag.contains("BreakerClosed") || tag.getBoolean("BreakerClosed");
        return new ApcState(joules, breaker);
    }

    public record ApcState(double energyJoules, boolean breakerClosed) { }
}
