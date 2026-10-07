package com.juicyslew.moonstation14.ms14.explosion.grenade.core;

/** Immutable policy state for damage-triggered volatile grenades. This models typed damage only;
 * proximity, impact, and fuse timing are deliberately outside this policy.
 */
public record VolatileGrenadeDamage(double totalDamage, boolean armed, boolean terminal) {
    /** Reference values from the pinned upstream prototype; callers own actual gameplay thresholds. */
    public static final double REFERENCE_ARM_THRESHOLD = 10.0;
    public static final double REFERENCE_TERMINAL_THRESHOLD = 45.0;

    public enum Trigger { NONE, ARM, IMMEDIATE }

    public record Transition(VolatileGrenadeDamage state, Trigger trigger, boolean accepted) { }

    public VolatileGrenadeDamage {
        if (!Double.isFinite(totalDamage) || totalDamage < 0.0)
            throw new IllegalArgumentException("total damage must be finite and nonnegative");
    }

    public static VolatileGrenadeDamage safe() {
        return new VolatileGrenadeDamage(0.0, false, false);
    }

    /** Applies finite nonnegative typed damage; invalid input and arithmetic overflow leave state unchanged. */
    public Transition apply(double appliedDamage, double armThreshold, double terminalThreshold) {
        if (terminal) return new Transition(this, Trigger.NONE, false);
        if (!Double.isFinite(appliedDamage) || appliedDamage < 0.0
                || !Double.isFinite(armThreshold) || armThreshold < 0.0
                || !Double.isFinite(terminalThreshold) || terminalThreshold < 0.0
                || terminalThreshold < armThreshold)
            return new Transition(this, Trigger.NONE, false);
        double nextTotal = totalDamage + appliedDamage;
        if (!Double.isFinite(nextTotal)) return new Transition(this, Trigger.NONE, false);
        if (nextTotal >= terminalThreshold)
            return new Transition(new VolatileGrenadeDamage(nextTotal, armed, true), Trigger.IMMEDIATE, true);
        boolean newlyArmed = !armed && nextTotal >= armThreshold;
        return new Transition(new VolatileGrenadeDamage(nextTotal, armed || newlyArmed, false),
                newlyArmed ? Trigger.ARM : Trigger.NONE, true);
    }
}
