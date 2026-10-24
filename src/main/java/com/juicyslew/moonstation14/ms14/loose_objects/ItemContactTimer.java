package com.juicyslew.moonstation14.ms14.loose_objects;

/** Per loose-source authoritative contact cooldown. */
public final class ItemContactTimer {
    private long nextInteractionTick;
    public long nextInteractionTick() { return nextInteractionTick; }
    public void setNextInteractionTick(long tick) { nextInteractionTick = Math.max(0, tick); }
}
