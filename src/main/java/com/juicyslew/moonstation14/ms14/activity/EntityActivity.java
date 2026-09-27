package com.juicyslew.moonstation14.ms14.activity;

/** Derived, staggered work which may be required by an entity attachment. */
public enum EntityActivity {
    STATUS_EFFECT(1),
    ALERT(1),
    REAGENT_METABOLISM(20),
    FIRE_DRYING(20),
    THIRST(20),
    HUNGER(20),
    BODY_TEMPERATURE(20),
    RESPIRATION_EXPOSURE(40);

    private final int tickInterval;

    EntityActivity(int tickInterval) {
        this.tickInterval = tickInterval;
    }

    public int tickInterval() {
        return tickInterval;
    }

    public int interval() {
        return tickInterval;
    }

    /** Returns whether this activity's deterministic entity bucket is due. */
    public boolean isDue(long gameTime, int entityId) {
        long timeResidue = Math.floorMod(gameTime, (long) tickInterval);
        return Math.floorMod(timeResidue + entityId, tickInterval) == 0;
    }
}
