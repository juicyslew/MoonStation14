package com.juicyslew.moonstation14.ms14.hands.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PointerWorkspaceRoutingTest {
    @Test void outsideClickDismissesAndConsumesOnlyWhenNoKnownUiOwnsIt() {
        assertTrue(StoragePopupScreen.shouldDismissPointerWorkspace(0,true,false));
        assertFalse(StoragePopupScreen.shouldDismissPointerWorkspace(0,true,true)); // browser/storage/hand UI
        assertFalse(StoragePopupScreen.shouldDismissPointerWorkspace(0,false,false)); // captured drag remains owner
        assertFalse(StoragePopupScreen.shouldDismissPointerWorkspace(1,true,false));
    }

    @Test void transparentWorkspaceOwnsInputBeforeGlobalWorldActionRouting() {
        assertFalse(BodyHandClientPolicy.routeGlobalMouseActions(true));
        assertTrue(BodyHandClientPolicy.routeGlobalMouseActions(false)); // locked gameplay path is unchanged
    }
}
