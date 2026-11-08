package com.juicyslew.moonstation14.ms14.combat;

/** Maps a gun prototype's use-key flag to the claimed combat mouse button. */
public final class GunInputRoute {
    private GunInputRoute() { }
    public static boolean matches(boolean useKey, boolean primaryButton) { return useKey == primaryButton; }
    /** A nominated gun input is claimed only while combat is active. */
    public static boolean claims(boolean combatMode, boolean useKey, boolean primaryButton) {
        return combatMode && matches(useKey, primaryButton);
    }
}
