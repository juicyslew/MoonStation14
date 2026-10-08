package com.juicyslew.moonstation14.ms14.ui.client.pointer;

import net.minecraft.client.gui.GuiGraphics;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import static org.junit.jupiter.api.Assertions.*;

@ResourceLock("PointerOverlayRegistry")
class PointerOverlayRegistryTest {
    private final FakeWindow browser = new FakeWindow(0, 0, 20, 20);
    private final FakeWindow other = new FakeWindow(30, 0, 20, 20);
    @AfterEach void cleanup() { PointerOverlayRegistry.resetOwnedContext(); }
    @Test void routesToHitWindowAndCapturesDragAndReleaseOutside() {
        PointerOverlayRegistry.register(browser); PointerOverlayRegistry.register(other); browser.visible=true;
        PointerOverlayRegistry.register(browser);
        assertTrue(PointerOverlayRegistry.click(5, 5, 0)); assertEquals(1, browser.clicks); assertEquals(0, other.clicks);
        assertFalse(PointerOverlayRegistry.click(25, 5, 0));
        browser.captured=true; assertTrue(PointerOverlayRegistry.drag(80, 80)); assertTrue(PointerOverlayRegistry.release(0));
        assertEquals(1,browser.drags); assertEquals(1,browser.releases);
    }
    @Test void pointerRoutingUsesReverseOfActualPaintOrderForOverlappingWindows() {
        var under=new FakeWindow(0,0,20,20);var front=new FakeWindow(0,0,20,20);
        under.visible=true;front.visible=true;
        PointerOverlayRegistry.register(under);PointerOverlayRegistry.register(front);
        assertEquals(List.of(under,front),PointerOverlayRegistry.paintOrder());
        assertTrue(PointerOverlayRegistry.click(5,5,0));
        assertEquals(0,under.clicks);assertEquals(1,front.clicks);
    }
    @Test void focusClearPreservesWindowsButOwnedContextResetRemovesAndResetsThem() {
        PointerOverlayRegistry.register(browser); PointerOverlayRegistry.register(other);
        browser.visible=true; other.visible=true; browser.focus=true;
        assertTrue(PointerOverlayRegistry.textFocused());
        PointerOverlayRegistry.clearTextFocus();
        assertFalse(browser.focus); assertTrue(browser.visible); assertTrue(other.visible); assertFalse(PointerOverlayRegistry.textFocused());
        browser.focus=true; PointerOverlayRegistry.resetOwnedContext();
        assertFalse(browser.visible); assertFalse(browser.focus); assertFalse(other.visible);
        var reopened=new FakeWindow(0,0,20,20); reopened.visible=true; PointerOverlayRegistry.register(reopened);
        assertTrue(PointerOverlayRegistry.anyVisible()); assertFalse(PointerOverlayRegistry.hit(35,5));
    }
    @Test void keysAreConsumedOnlyByFocusedWindow() {
        PointerOverlayRegistry.register(browser); PointerOverlayRegistry.register(other);
        browser.visible=true; other.visible=true; other.focus=true;
        assertTrue(PointerOverlayRegistry.key(1,1,0)); assertEquals(0,browser.consumed); assertEquals(1,other.consumed);
    }
    @Test void productionHostAdmissionSuppressesPauseButSharesGameplayWorkspaceAndMachineUiPolicy() {
        assertTrue(PointerOverlayRegistry.hostAllowed(true,false,false));
        assertTrue(PointerOverlayRegistry.hostAllowed(false,true,false));
        assertTrue(PointerOverlayRegistry.hostAllowed(false,false,true));
        assertFalse(PointerOverlayRegistry.hostAllowed(false,false,false)); // pause/options/inventory/foreign modal
    }
    @Test void screenInputAdmissionLeavesStorageCanvasToItsSingleScreenRouter() {
        var canvas=new com.juicyslew.moonstation14.ms14.hands.client.StoragePopupScreen(
                new com.juicyslew.moonstation14.ms14.hands.client.StoragePopupWorkspace(320,240));
        assertTrue(PointerOverlayRegistry.inputBelongsToScreen(canvas));
        assertFalse(PointerOverlayRegistry.inputBelongsToScreen(null));
        assertFalse(PointerOverlayRegistry.inputBelongsToScreen(new net.minecraft.client.gui.screens.Screen(
                net.minecraft.network.chat.Component.literal("other")) { }));
    }
    @Test void productionWorkspacePhaseAdmissionSelectsExactlyOneHostPhase() {
        assertTrue(PointerOverlayRegistry.shouldPaintWorkspace(true,false,true,true)); // gameplay GuiPost
        assertFalse(PointerOverlayRegistry.shouldPaintWorkspace(true,true,true,true));
        assertFalse(PointerOverlayRegistry.shouldPaintWorkspace(false,false,true,true));
        assertTrue(PointerOverlayRegistry.shouldPaintWorkspace(false,true,true,true)); // canvas/project ScreenPost
        assertFalse(PointerOverlayRegistry.shouldPaintWorkspace(false,true,false,true)); // nested/noncurrent
        assertFalse(PointerOverlayRegistry.shouldPaintWorkspace(false,true,true,false)); // pause/modal
    }
    @Test void foregroundChoiceIsSharedStateForPaintAndInputHostAndResetsWithWorkspace() {
        PointerOverlayRegistry.register(browser);browser.visible=true;
        PointerOverlayRegistry.overlayFront(false);
        assertFalse(PointerOverlayRegistry.overlayFront()); // storage workspace is repainted after overlay by host
        PointerOverlayRegistry.overlayFront(true);
        assertTrue(PointerOverlayRegistry.overlayFront());
        PointerOverlayRegistry.resetOwnedContext();
        assertTrue(PointerOverlayRegistry.overlayFront());
    }
    @Test void centralDepthRanksSeparateItemBuffersAndForegroundFamilies() {
        int storage0=PointerWorkspaceLayers.storageLayer(0,false);
        int storage2=PointerWorkspaceLayers.storageLayer(2,false);
        int browser=PointerWorkspaceLayers.browserLayer(0,true);
        int raisedStorage=PointerWorkspaceLayers.storageLayer(0,true);
        assertTrue(storage0+PointerWorkspaceLayers.INTERNAL_ITEM_DEPTH<PointerWorkspaceLayers.storageLayer(1,false));
        assertTrue(storage2+PointerWorkspaceLayers.INTERNAL_ITEM_DEPTH<browser);
        assertTrue(browser+PointerWorkspaceLayers.INTERNAL_ITEM_DEPTH<raisedStorage);
    }
    @Test void workspaceDispatcherPaintsEachVisibleWindowOnceInEitherFrontOrder() {
        var painted=new FakeWindow(0,0,20,20);painted.visible=true;PointerOverlayRegistry.register(painted);
        for(boolean browserFront: new boolean[]{true,false}) {
            PointerOverlayRegistry.overlayFront(browserFront);
            var sequence=new java.util.ArrayList<String>();
            PointerOverlayRegistry.renderWorkspace(()->sequence.add("storage"),()->{
                sequence.add("overlay");
                for(var window:PointerOverlayRegistry.paintOrder()) {
                    window.renderIntoGui(null,0,0,0,320,240);
                    sequence.add("window");
                }
            });
            assertEquals(browserFront?List.of("storage","overlay","window"):List.of("overlay","window","storage"),sequence);
            assertEquals(browserFront?1:2,painted.renders);
        }
    }
    @Test void browserRemainsClickableOutsideStorageAndCanBeRaisedAfterStorageFront() {
        var storage=new FakeWindow(0,0,10,10);var browserWindow=new FakeWindow(20,0,10,10);
        storage.visible=true;browserWindow.visible=true;
        PointerOverlayRegistry.register(storage);PointerOverlayRegistry.register(browserWindow);
        PointerOverlayRegistry.overlayFront(false);
        assertTrue(PointerOverlayRegistry.click(25,5,0));
        assertEquals(1,browserWindow.clicks);
        PointerOverlayRegistry.overlayFront(true);
        assertTrue(PointerOverlayRegistry.click(25,5,0));
        assertEquals(2,browserWindow.clicks);
        assertTrue(storage.visible);assertTrue(browserWindow.visible);
    }
    private static final class FakeWindow implements PointerOverlayWindow {
        final int x,y,w,h; boolean visible,focus,captured; int clicks,drags,releases,keys,consumed,renders;
        FakeWindow(int x,int y,int w,int h){this.x=x;this.y=y;this.w=w;this.h=h;}
        public boolean visible(){return visible;} public boolean contains(double px,double py){return visible&&px>=x&&px<x+w&&py>=y&&py<y+h;}
         public void renderIntoGui(GuiGraphics g,int mx,int my,float p,int sw,int sh){renders++;}
        public boolean mouseClicked(double a,double b,int button){clicks++;captured=true;return true;}
        public boolean mouseDragged(double a,double b){drags++;return captured;} public boolean mouseReleased(int b){releases++;boolean v=captured;captured=false;return v;}
        public boolean mouseScrolled(double a,double b,double d){return false;} public boolean keyPressed(int k,int s,int m){keys++;if(focus)consumed++;return focus;}
        public boolean charTyped(char c,int m){return focus;} public boolean isSearchFocused(){return focus;}
        public void clearTextFocus(){focus=false;} public void resetOwnedContext(){visible=false;focus=false;captured=false;}
    }
}
