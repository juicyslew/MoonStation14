package com.juicyslew.moonstation14.ms14.disposal.unit.ui;

/** Pure minimum-delay/interruption predicate for self-entry. */
public final class DisposalUnitEntryPolicy {
    /** Minecraft mapping of SS14 BreakOnMove threshold: full 3D displacement, squared distance. */
    public static final double MOVEMENT_THRESHOLD_SQUARED = 0.3 * 0.3;
    public static final float DAMAGE_THRESHOLD = 1.0f;
    public static boolean damageCancels(float eventDamage) { return Float.isFinite(eventDamage) && eventDamage >= DAMAGE_THRESHOLD; }
    private DisposalUnitEntryPolicy(){}
    public static boolean withinMovementThreshold(net.minecraft.world.phys.Vec3 start, net.minecraft.world.phys.Vec3 current) {
        return start.distanceToSqr(current) < MOVEMENT_THRESHOLD_SQUARED;
    }
    public enum TickResult { WAIT, CANCEL, COMPLETE }
    public static TickResult tick(long elapsed,long requiredTicks,boolean exactMenu,boolean sameUnit,boolean sameBody,
                                      boolean authority,boolean canAct,boolean unmoved) {
        if(!exactMenu||!sameUnit||!sameBody||!authority||!canAct||!unmoved)return TickResult.CANCEL;
        return elapsed<requiredTicks?TickResult.WAIT:TickResult.COMPLETE;
    }
    public static boolean mayOperate(boolean menuAuthority,boolean reachable,boolean contained,boolean exactOwner,boolean otherOwner){
        return menuAuthority&&reachable&&!otherOwner&&(!contained||exactOwner);
    }
}

