package com.juicyslew.moonstation14.ms14.construction.client;

/** Production gate for nonmodal construction guidance while the controlled body remains aimable. */
public final class ConstructionGuidanceAdmission {
    private ConstructionGuidanceAdmission() { }
    public static boolean allowed(boolean paused,boolean screenOpen,boolean storagePopup,boolean searchFocused) {
        return !paused&&(!screenOpen||storagePopup)&&!searchFocused;
    }
}
