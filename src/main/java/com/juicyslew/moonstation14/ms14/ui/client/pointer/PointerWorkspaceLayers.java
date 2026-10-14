package com.juicyslew.moonstation14.ms14.ui.client.pointer;

/** Shared depth ranks for the existing storage-workspace and registered pointer-window families. */
public final class PointerWorkspaceLayers {
    /** Covers GUI item quads (~200) and vanilla tooltip quads (~400) inside window passes. */
    public static final int INTERNAL_ITEM_DEPTH = 400;
    public static final int WINDOW_STRIDE = 1000;
    private static final int STORAGE_BASE = 1000;
    private static final int DEFAULT_STORAGE_SIBLINGS = 3;
    private PointerWorkspaceLayers() { }

    public static int storageLayer(int paintIndex, boolean storageFront) {
        return storageLayer(paintIndex, storageFront, DEFAULT_STORAGE_SIBLINGS);
    }
    public static int storageLayer(int paintIndex, boolean storageFront, int storageSiblingCount) {
        int count = Math.max(DEFAULT_STORAGE_SIBLINGS, storageSiblingCount);
        if (paintIndex < 0 || paintIndex >= count) throw new IllegalArgumentException("storage window index");
        int browserBase = STORAGE_BASE + (count + 1) * WINDOW_STRIDE;
        return (storageFront ? browserBase + WINDOW_STRIDE : STORAGE_BASE) + paintIndex * WINDOW_STRIDE;
    }

    /** Wire input is routed before storage input, so its window must paint above every storage window. */
    public static int wireLayer(boolean storageFront) {
        return wireLayer(storageFront, DEFAULT_STORAGE_SIBLINGS);
    }
    public static int wireLayer(boolean storageFront, int storageSiblingCount) {
        int count = Math.max(DEFAULT_STORAGE_SIBLINGS, storageSiblingCount);
        int browserBase = STORAGE_BASE + (count + 1) * WINDOW_STRIDE;
        int storageBase = storageFront ? browserBase + WINDOW_STRIDE : STORAGE_BASE;
        return storageBase + count * WINDOW_STRIDE;
    }

    public static int browserLayer(int paintIndex, boolean browserFront) {
        return browserLayer(paintIndex, browserFront, DEFAULT_STORAGE_SIBLINGS);
    }
    public static int browserLayer(int paintIndex, boolean browserFront, int storageSiblingCount) {
        int count = Math.max(DEFAULT_STORAGE_SIBLINGS, storageSiblingCount);
        if (paintIndex < 0) throw new IllegalArgumentException("window index");
        int browserBase = STORAGE_BASE + (count + 1) * WINDOW_STRIDE;
        return (browserFront ? browserBase : STORAGE_BASE) + paintIndex * WINDOW_STRIDE;
    }
}

