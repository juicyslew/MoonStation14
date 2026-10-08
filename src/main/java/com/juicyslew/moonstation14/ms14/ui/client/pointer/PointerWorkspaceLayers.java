package com.juicyslew.moonstation14.ms14.ui.client.pointer;

/** Shared depth ranks for the existing storage-workspace and registered pointer-window families. */
public final class PointerWorkspaceLayers {
    /** Covers GUI item quads (~200) and vanilla tooltip quads (~400) inside window passes. */
    public static final int INTERNAL_ITEM_DEPTH = 400;
    public static final int WINDOW_STRIDE = 1000;
    private static final int STORAGE_BASE = 1000;
    private static final int BROWSER_BASE = STORAGE_BASE + 4 * WINDOW_STRIDE;
    private PointerWorkspaceLayers() { }

    public static int storageLayer(int paintIndex, boolean storageFront) {
        if (paintIndex < 0 || paintIndex >= 3) throw new IllegalArgumentException("storage window index");
        return (storageFront ? BROWSER_BASE + WINDOW_STRIDE : STORAGE_BASE) + paintIndex * WINDOW_STRIDE;
    }

    /** Wire input is routed before storage input, so its window must paint above every storage window. */
    public static int wireLayer(boolean storageFront) {
        return STORAGE_BASE + (storageFront ? 8 : 3) * WINDOW_STRIDE;
    }

    public static int browserLayer(int paintIndex, boolean browserFront) {
        if (paintIndex < 0) throw new IllegalArgumentException("window index");
        return (browserFront ? BROWSER_BASE : STORAGE_BASE) + paintIndex * WINDOW_STRIDE;
    }
}
