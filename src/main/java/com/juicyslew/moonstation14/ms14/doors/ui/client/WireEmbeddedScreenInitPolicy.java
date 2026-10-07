package com.juicyslew.moonstation14.ms14.doors.ui.client;

/** Decides when the retained, embedded screen needs the regular Screen initialization lifecycle. */
final class WireEmbeddedScreenInitPolicy {
    private WireEmbeddedScreenInitPolicy() { }

    static boolean needsInit(boolean initialized, boolean fontBound, int currentWidth, int currentHeight,
                             int width, int height) {
        return !initialized || !fontBound || currentWidth != width || currentHeight != height;
    }
}
