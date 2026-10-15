package com.juicyslew.moonstation14.ms14.hands.client;

/** Captured physical binding identity, retained through cancellation until its matching up event. */
final class AimReleasePhysicalInput {
    private boolean active;
    private int button = -1;
    private boolean legacyFallback;
    void beginMouse(int button, boolean legacyFallback) { clear(); active = true; this.button = button; this.legacyFallback = legacyFallback; }
    boolean matchesMouseRelease(int releasedButton) { return active && button == releasedButton; }
    boolean legacyFallback() { return legacyFallback; }
    void clear() { active = false; button = -1; legacyFallback = false; }
}

