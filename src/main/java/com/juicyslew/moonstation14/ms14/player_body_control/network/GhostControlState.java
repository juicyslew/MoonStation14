package com.juicyslew.moonstation14.ms14.player_body_control.network;

/** Shared immutable wire representation of a complete control state. */
public record GhostControlState(short wishX, short wishZ, byte verticalWish, int buttons, float yaw, float pitch) {
    public GhostControlState {
        if (Math.abs((int) wishX) > 1000 || Math.abs((int) wishZ) > 1000) throw new IllegalArgumentException("wish axes out of range");
        if (verticalWish < -1 || verticalWish > 1) throw new IllegalArgumentException("vertical wish out of range");
        if ((buttons & ~GhostControlPayloads.BUTTON_MASK) != 0) throw new IllegalArgumentException("unknown ghost control buttons");
        if (!Float.isFinite(yaw) || yaw < -180 || yaw > 180) throw new IllegalArgumentException("yaw out of range");
        if (!Float.isFinite(pitch) || pitch < -90 || pitch > 90) throw new IllegalArgumentException("pitch out of range");
    }
    public static GhostControlState neutral() { return new GhostControlState((short) 0, (short) 0, (byte) 0, 0, 0, 0); }
    public boolean jumpHeld() { return (buttons & GhostControlPayloads.BUTTON_JUMP) != 0; }
}
