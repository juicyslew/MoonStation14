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
        return storageLayer(paintIndex,storageFront,storageSiblingCount,DEFAULT_STORAGE_SIBLINGS+1);
    }
    public static int storageLayer(int paintIndex,boolean storageFront,int storageSiblingCount,int browserFamilySize) {
        int count = Math.max(DEFAULT_STORAGE_SIBLINGS, storageSiblingCount);
        if (paintIndex < 0 || paintIndex >= count) throw new IllegalArgumentException("storage window index");
        int storageBase=storageFront?STORAGE_BASE+Math.max(1,browserFamilySize)*WINDOW_STRIDE:STORAGE_BASE;
        return storageBase+paintIndex*WINDOW_STRIDE;
    }

    /** Wire and browser share one z-order. Most recently clicked family paints last. */
    public static int wireLayer(boolean storageFront) {
        return wireLayer(storageFront, DEFAULT_STORAGE_SIBLINGS);
    }
    public static int wireLayer(boolean storageFront, int storageSiblingCount) {
        int count = Math.max(DEFAULT_STORAGE_SIBLINGS, storageSiblingCount);
        int browserBase = STORAGE_BASE + (count + 1) * WINDOW_STRIDE;
        int storageBase = storageFront ? browserBase + WINDOW_STRIDE : STORAGE_BASE;
        return storageBase + count * WINDOW_STRIDE;
    }
    public static int wireLayer(boolean storageFront,int storageSiblingCount,boolean wireFront) {
        return wireLayer(storageFront,storageSiblingCount,DEFAULT_STORAGE_SIBLINGS,wireFront);
    }
    public static int wireLayer(boolean storageFront,int storageSiblingCount,int browserWindowCount,boolean wireFront) {
        int count=Math.max(DEFAULT_STORAGE_SIBLINGS,storageSiblingCount);
        int browserBase=storageFront?STORAGE_BASE:STORAGE_BASE+(count+1)*WINDOW_STRIDE;
        return wireFront?browserBase+Math.max(0,browserWindowCount)*WINDOW_STRIDE:browserBase-WINDOW_STRIDE;
    }

    public static int browserLayer(int paintIndex, boolean browserFront) {
        return browserLayer(paintIndex, browserFront, DEFAULT_STORAGE_SIBLINGS);
    }
    public static int browserLayer(int paintIndex, boolean browserFront, int storageSiblingCount) {
        return browserLayer(paintIndex,browserFront,storageSiblingCount,DEFAULT_STORAGE_SIBLINGS+1);
    }
    public static int browserLayer(int paintIndex,boolean browserFront,int storageSiblingCount,int browserFamilySize) {
        int count = Math.max(DEFAULT_STORAGE_SIBLINGS, storageSiblingCount);
        if (paintIndex < 0) throw new IllegalArgumentException("window index");
        int browserBase = STORAGE_BASE + (count + 1) * WINDOW_STRIDE;
        return (browserFront?browserBase:STORAGE_BASE)+paintIndex*WINDOW_STRIDE;
    }
    public static int fixedPaneLayer(boolean storageFront,int storageCount,int browserFamilySize,int browserWindowCount) {
        int storage=storageLayer(Math.max(0,storageCount-1),storageFront,Math.max(DEFAULT_STORAGE_SIBLINGS,storageCount),browserFamilySize);
        int browser=browserLayer(Math.max(0,browserWindowCount-1),!storageFront,
                Math.max(DEFAULT_STORAGE_SIBLINGS,storageCount),browserFamilySize);
        int wire=wireLayer(storageFront,storageCount,browserWindowCount,true);
        return Math.max(storage,Math.max(browser,wire))+WINDOW_STRIDE;
    }
}

