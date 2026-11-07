package com.juicyslew.moonstation14.ms14.hands.client;

/** Admission rule for the direct Ctrl+Z wield toggle; deliberately excludes medical chords. */
public final class GunWieldInputPolicy {
    private GunWieldInputPolicy() { }
    public static boolean mayDispatch(boolean control, boolean shift, boolean alt,
                                      boolean bodyInputAllowed, boolean textFocused,
                                      boolean activeWieldableGun) {
        return control && !shift && !alt && bodyInputAllowed && !textFocused && activeWieldableGun;
    }
}
