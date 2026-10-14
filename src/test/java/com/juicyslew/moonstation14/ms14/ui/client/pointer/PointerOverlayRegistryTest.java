package com.juicyslew.moonstation14.ms14.ui.client.pointer;

import net.minecraft.client.gui.GuiGraphics;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import static org.junit.jupiter.api.Assertions.*;

@ResourceLock("PointerOverlayRegistry")
class PointerOverlayRegistryTest {
    private final FakeWindow browser = new FakeWindow(0, 0, 20, 20);
    private final FakeWindow other = new FakeWindow(30, 0, 20, 20);
    private List<PointerOverlayWindow> originalWindows;
    private boolean originalFront;
    private List<PointerOverlayWindow> windows() throws Exception {
        var windowsField = PointerOverlayRegistry.class.getDeclaredField("WINDOWS");
        windowsField.setAccessible(true);
        @SuppressWarnings("unchecked") var value = (List<PointerOverlayWindow>) windowsField.get(null);
        return value;
    }
    @BeforeEach void isolateRegistry() throws Exception {
        originalWindows=List.copyOf(windows());originalFront=PointerOverlayRegistry.overlayFront();
        windows().clear();PointerOverlayRegistry.overlayFront(true);
    }
    @AfterEach void restoreRegistry() throws Exception {
        var live=windows();
        for(var window:List.copyOf(live)) if(window instanceof FakeWindow fake) fake.resetOwnedContext();
        live.clear();live.addAll(originalWindows);PointerOverlayRegistry.overlayFront(originalFront);
    }
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
        browser.focus=true;
        // A process-wide registry can contain production overlays whose context reset
        // sends packets. Exercise the fake windows' reset contract locally instead.
        browser.resetOwnedContext(); other.resetOwnedContext();
        PointerOverlayRegistry.unregister(browser); PointerOverlayRegistry.unregister(other);
        assertFalse(browser.visible); assertFalse(browser.focus); assertFalse(other.visible);
        var reopened=new FakeWindow(0,0,20,20); reopened.visible=true; PointerOverlayRegistry.register(reopened);
        assertTrue(PointerOverlayRegistry.anyVisible()); assertFalse(PointerOverlayRegistry.hit(35,5));
    }
    @Test void keysAreConsumedOnlyByFocusedWindow() {
        PointerOverlayRegistry.register(browser); PointerOverlayRegistry.register(other);
        browser.visible=true; other.visible=true; other.focus=true;
        assertTrue(PointerOverlayRegistry.key(1,1,0)); assertEquals(0,browser.consumed); assertEquals(1,other.consumed);
    }
    @Test void keyDispatchUsesEntrySnapshotWhenCallbackUnregistersItself() {
        var first=new FakeWindow(0,0,20,20);var second=new FakeWindow(0,0,20,20);
        first.visible=true;first.focus=true;second.visible=true;
        first.keyAction=()->PointerOverlayRegistry.unregister(first);
        PointerOverlayRegistry.register(first);PointerOverlayRegistry.register(second);
        assertTrue(PointerOverlayRegistry.key(1,1,0));
        assertEquals(1,first.keys);assertEquals(0,second.keys);
        assertFalse(PointerOverlayRegistry.key(1,1,0));assertEquals(1,second.keys);
    }
    @Test void keyDispatchDefersRegistrationAndPreservesSnapshotOrderDuringReorder() {
        var first=new FakeWindow(0,0,20,20);var second=new FakeWindow(0,0,20,20);var third=new FakeWindow(0,0,20,20);var added=new FakeWindow(0,0,20,20);
        first.visible=true;second.visible=true;third.visible=true;added.visible=true;
        first.keyAction=()->{PointerOverlayRegistry.bringToFront(second);PointerOverlayRegistry.register(added);};
        PointerOverlayRegistry.register(first);PointerOverlayRegistry.register(second);PointerOverlayRegistry.register(third);
        assertFalse(PointerOverlayRegistry.key(1,1,0));
        assertEquals(List.of(first,third,second,added),List.copyOf(PointerOverlayRegistry.paintOrder()));
        assertEquals(1,first.keys);assertEquals(1,second.keys);assertEquals(1,third.keys);assertEquals(0,added.keys);
        PointerOverlayRegistry.key(1,1,0);assertEquals(1,added.keys);
    }
    @Test void unconsumedKeyRemovalStillDispatchesRemainingSnapshotOnce() {
        var first=new FakeWindow(0,0,20,20);var second=new FakeWindow(0,0,20,20);first.visible=true;second.visible=true;
        first.keyAction=()->PointerOverlayRegistry.unregister(first);
        PointerOverlayRegistry.register(first);PointerOverlayRegistry.register(second);
        assertFalse(PointerOverlayRegistry.key(1,1,0));assertEquals(1,first.keys);assertEquals(1,second.keys);
    }
    @Test void otherCallbackDispatchesAlsoTolerateReentrantUnregister() {
        var window=new FakeWindow(0,0,20,20);window.visible=true;window.focus=true;window.captured=true;
        PointerOverlayRegistry.register(window);
        window.charAction=()->PointerOverlayRegistry.unregister(window);
        assertTrue(PointerOverlayRegistry.character('x',0));
        PointerOverlayRegistry.register(window);window.clearAction=()->PointerOverlayRegistry.unregister(window);
        assertDoesNotThrow(PointerOverlayRegistry::clearTextFocus);
        PointerOverlayRegistry.register(window);window.dragAction=()->PointerOverlayRegistry.unregister(window);
        assertTrue(PointerOverlayRegistry.drag(1,1));
        PointerOverlayRegistry.register(window);window.releaseAction=()->PointerOverlayRegistry.unregister(window);
        assertTrue(PointerOverlayRegistry.release(0));
        PointerOverlayRegistry.register(window);window.scrollAction=()->PointerOverlayRegistry.unregister(window);
        assertFalse(PointerOverlayRegistry.scroll(1,1,1));
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
        Runnable keyAction=()->{},charAction=()->{},clearAction=()->{},dragAction=()->{},releaseAction=()->{},scrollAction=()->{};
        FakeWindow(int x,int y,int w,int h){this.x=x;this.y=y;this.w=w;this.h=h;}
        public boolean visible(){return visible;} public boolean contains(double px,double py){return visible&&px>=x&&px<x+w&&py>=y&&py<y+h;}
         public void renderIntoGui(GuiGraphics g,int mx,int my,float p,int sw,int sh){renders++;}
         public boolean mouseClicked(double a,double b,int button){clicks++;captured=true;return true;}
        public boolean mouseDragged(double a,double b){drags++;dragAction.run();return captured;} public boolean mouseReleased(int b){releases++;releaseAction.run();boolean v=captured;captured=false;return v;}
         public boolean mouseScrolled(double a,double b,double d){scrollAction.run();return false;} public boolean keyPressed(int k,int s,int m){keys++;keyAction.run();if(focus)consumed++;return focus;}
        public boolean charTyped(char c,int m){charAction.run();return focus;} public boolean isSearchFocused(){return focus;}
        public void clearTextFocus(){clearAction.run();focus=false;} public void resetOwnedContext(){visible=false;focus=false;captured=false;}
    }
}
