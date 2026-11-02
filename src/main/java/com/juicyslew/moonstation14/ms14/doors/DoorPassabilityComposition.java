package com.juicyslew.moonstation14.ms14.doors;

/** The composed doorway is traversable only when every installed leaf is passable. */
public final class DoorPassabilityComposition {
    private DoorPassabilityComposition() { }
    public static boolean passable(boolean airlockPassable, boolean firelockInstalled, boolean firelockPassable) {
        return airlockPassable && (!firelockInstalled || firelockPassable);
    }
}
