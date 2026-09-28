package com.juicyslew.moonstation14.ms14.station.debug;

import net.minecraft.core.BlockPos;

/** Small pure state machine shared by the new-world hook and placement service. */
public final class StarterStationPlacementState {
    public enum State { DISABLED, PENDING, SELECTED, COMPLETED }

    private StarterStationPlacementState() { }

    /** CreateSpawnPosition is vanilla's new-level-only initialization signal. */
    public static State afterNewWorldSpawnInitialization(State current, boolean overworld) {
        return overworld && current == State.DISABLED ? State.PENDING : current;
    }

    public static State afterSelection(State current, BlockPos origin) {
        return current == State.PENDING && origin != null ? State.SELECTED : current;
    }

    public static State afterPlacement(State current, boolean allWritesSucceeded) {
        return current == State.SELECTED && allWritesSucceeded ? State.COMPLETED : current;
    }

    public static boolean shouldAttempt(State state) {
        return state == State.PENDING || state == State.SELECTED;
    }
}
