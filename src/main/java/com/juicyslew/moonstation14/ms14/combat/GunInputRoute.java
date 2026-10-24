package com.juicyslew.moonstation14.ms14.combat;

/** Maps a gun prototype's use-key flag to the claimed combat mouse button. */
public final class GunInputRoute {
    private GunInputRoute() { }
    public static boolean matches(boolean useKey, boolean primaryButton) { return useKey == primaryButton; }
}
