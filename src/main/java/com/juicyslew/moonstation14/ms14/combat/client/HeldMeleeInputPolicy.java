package com.juicyslew.moonstation14.ms14.combat.client;

/** Pure held-input lifecycle. Cancelling a held gesture always requires a fresh physical press. */
public final class HeldMeleeInputPolicy {
    public record Source(com.juicyslew.moonstation14.ms14.combat.MeleeWeaponResolver.Kind kind,
                         String activeHand, String itemToken,
                         com.juicyslew.moonstation14.ms14.combat.MeleeWeaponProfile profile) { }
    public record Context(java.util.UUID body, long epoch, Source source) { }
    private Context context;
    private long lastDispatch = Long.MIN_VALUE;

    public boolean begin(boolean preference, boolean autoAttack, boolean combat, Context next, long tick) {
        context = preference || autoAttack ? next : null;
        lastDispatch = context == null ? Long.MIN_VALUE : tick;
        return context != null && combat;
    }
    public boolean eligible(boolean physicallyDown, boolean inputValid, boolean attackReady, boolean combat, Context current, long tick) {
        if (context == null) return false;
        if (!physicallyDown || !inputValid || !combat || !context.equals(current)) { cancel(); return false; }
        if (!attackReady) return false;
        return lastDispatch != tick;
    }
    public void dispatched(long tick) { if (context != null) lastDispatch = tick; }
    public void cancel() { context = null; lastDispatch = Long.MIN_VALUE; }
    public boolean active() { return context != null; }
}
