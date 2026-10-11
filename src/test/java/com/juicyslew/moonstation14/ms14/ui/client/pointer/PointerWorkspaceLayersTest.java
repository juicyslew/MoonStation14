package com.juicyslew.moonstation14.ms14.ui.client.pointer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PointerWorkspaceLayersTest {
    @Test void expandedStorageSiblingCountKeepsWireAndFrontOrdersAboveEveryWindow() {
        int count = 5;
        int backWire = PointerWorkspaceLayers.wireLayer(false, count);
        for (int rank = 0; rank < count; rank++)
            assertTrue(backWire > PointerWorkspaceLayers.storageLayer(rank, false, count));
        int browserFront = PointerWorkspaceLayers.browserLayer(0, true, count);
        assertTrue(browserFront > backWire);
        assertTrue(PointerWorkspaceLayers.storageLayer(count - 1, true, count)
                > PointerWorkspaceLayers.browserLayer(0, false, count));
        assertTrue(PointerWorkspaceLayers.wireLayer(true, count)
                > PointerWorkspaceLayers.storageLayer(count - 1, true, count));
    }

    @Test void browserWireAndStorageDepthsMatchBothFrontOrdersWithItemMargins() {
        int browserFront = PointerWorkspaceLayers.browserLayer(0, true);
        int backWire = PointerWorkspaceLayers.wireLayer(false);
        for (int rank = 0; rank < 3; rank++)
            assertTrue(backWire > PointerWorkspaceLayers.storageLayer(rank, false));
        assertTrue(PointerWorkspaceLayers.storageLayer(2, false) + PointerWorkspaceLayers.INTERNAL_ITEM_DEPTH < backWire);
        assertTrue(backWire + PointerWorkspaceLayers.INTERNAL_ITEM_DEPTH < browserFront);

        int frontWire = PointerWorkspaceLayers.wireLayer(true);
        for (int rank = 0; rank < 3; rank++)
            assertTrue(frontWire > PointerWorkspaceLayers.storageLayer(rank, true));
        assertTrue(PointerWorkspaceLayers.storageLayer(2, true) + PointerWorkspaceLayers.INTERNAL_ITEM_DEPTH < frontWire);
        assertTrue(PointerWorkspaceLayers.browserLayer(0, false) + PointerWorkspaceLayers.INTERNAL_ITEM_DEPTH
                < PointerWorkspaceLayers.storageLayer(0, true));
        assertTrue(PointerWorkspaceLayers.browserLayer(0, false) + PointerWorkspaceLayers.INTERNAL_ITEM_DEPTH < frontWire);
    }

    @Test void mixedWindowRanksKeepStorageChemWireAndFixedConfiguratorPaintHitPriorityAligned() {
        int storages=4,browsers=3,family=browsers+1; // browser registrations plus reserved wire slot
        int storageBack=PointerWorkspaceLayers.storageLayer(storages-1,false,storages,family);
        int browserFront=PointerWorkspaceLayers.browserLayer(0,true,storages,family);
        int wireFront=PointerWorkspaceLayers.wireLayer(false,storages,browsers,true);
        int fixedTop=PointerWorkspaceLayers.fixedPaneLayer(false,storages,family,browsers);
        assertTrue(storageBack<browserFront);
        assertTrue(browserFront<wireFront); // wire clicked last is above Chem, not above fixed panes
        assertTrue(wireFront<fixedTop);

        int browserBack=PointerWorkspaceLayers.browserLayer(0,false,storages,family);
        int wireBack=PointerWorkspaceLayers.wireLayer(true,storages,browsers,false);
        int storageFront=PointerWorkspaceLayers.storageLayer(0,true,storages,family);
        int fixedOverFrontStorage=PointerWorkspaceLayers.fixedPaneLayer(true,storages,family,browsers);
        assertTrue(wireBack<browserBack);
        assertTrue(browserBack<storageFront);
        assertTrue(storageFront<fixedOverFrontStorage);
    }
}
