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
        class GameplayTestScreen extends net.minecraft.client.gui.screens.Screen
                implements com.juicyslew.moonstation14.ms14.ui.client.GameplayWindow {
            boolean textFocus;
            GameplayTestScreen() { super(net.minecraft.network.chat.Component.literal("window")); }
            public com.juicyslew.moonstation14.ms14.hands.client.PopupMovementIntent.State movementIntent() {
                return com.juicyslew.moonstation14.ms14.hands.client.PopupMovementIntent.State.IDLE;
            }
            public boolean capturesKeyboard() { return textFocus; }
        }
        var gameplay = new GameplayTestScreen();
        assertTrue(PointerOverlayRegistry.allowsHost(gameplay));
        assertTrue(com.juicyslew.moonstation14.ms14.ui.client.GameplayWindow.allowsBodyInput(gameplay,false));
        gameplay.textFocus=true;
        assertFalse(com.juicyslew.moonstation14.ms14.ui.client.GameplayWindow.allowsBodyInput(gameplay,true));
        assertFalse(com.juicyslew.moonstation14.ms14.ui.client.GameplayWindow.allowsBodyInput(null,false));
        assertTrue(com.juicyslew.moonstation14.ms14.ui.client.GameplayWindow.allowsBodyInput(null,true));
        assertFalse(PointerOverlayRegistry.allowsHost(new net.minecraft.client.gui.screens.Screen(
                net.minecraft.network.chat.Component.literal("modal")) { }));
    }
    @Test void keyDispatchUsesStableSnapshotAndSkipsRemovedWindows() {
        var added=new FakeWindow(0,0,20,20);added.visible=true;added.focus=true;
        browser.visible=true;other.visible=true;browser.focus=true;other.focus=true;
         browser.onKey=()->{PointerOverlayRegistry.unregister(browser);PointerOverlayRegistry.register(added);return false;};
         other.onKey=()->false; // keep dispatch moving so the newly added window is reached next time
        PointerOverlayRegistry.register(browser);PointerOverlayRegistry.register(other);
         assertFalse(PointerOverlayRegistry.key(1,1,0));
        assertEquals(1,browser.keys); // callback unregisters itself before a later turn
        assertEquals(1,other.keys);assertEquals(0,added.keys);
        assertTrue(PointerOverlayRegistry.key(1,1,0));assertEquals(1,added.keys);
    }
    @Test void clickAndRenderCallbacksCanMutateMembershipDuringSnapshotTraversal() {
        var first=new FakeWindow(0,0,20,20);var second=new FakeWindow(0,0,20,20);
        first.visible=true;second.visible=true;PointerOverlayRegistry.register(first);PointerOverlayRegistry.register(second);
        second.onClick=()->{PointerOverlayRegistry.unregister(first);return true;};
        assertTrue(PointerOverlayRegistry.click(5,5,0));assertEquals(0,first.clicks);assertEquals(1,second.clicks);
        first.visible=true;PointerOverlayRegistry.register(first);
        second.onRender=()->PointerOverlayRegistry.unregister(first);
         PointerOverlayRegistry.visitPaintOrder(PointerOverlayRegistry.paintOrder(), (window, index) ->
                 window.renderIntoGui(null,0,0,0,320,240));
        assertEquals(0,first.renders);
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
        int storage0=PointerWorkspaceLayers.storageLayer(0,false,3,2);
        int storage2=PointerWorkspaceLayers.storageLayer(2,false,3,2);
        int browser=PointerWorkspaceLayers.browserLayer(0,true,3,2);
        int raisedStorage=PointerWorkspaceLayers.storageLayer(0,true,3,2);
        assertTrue(storage0+PointerWorkspaceLayers.INTERNAL_ITEM_DEPTH<PointerWorkspaceLayers.storageLayer(1,false));
        assertTrue(storage2+PointerWorkspaceLayers.INTERNAL_ITEM_DEPTH<browser);
        assertTrue(PointerWorkspaceLayers.browserLayer(0,false,3,2)+PointerWorkspaceLayers.INTERNAL_ITEM_DEPTH<raisedStorage);
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
    @Test void lockedPointerWindowsRemainPaintedButAreAbsentFromHitAndInput() {
        var locked=new FakeWindow(0,0,20,20);locked.visible=true;locked.available=false;
        PointerOverlayRegistry.register(locked);
        assertEquals(List.of(locked),PointerOverlayRegistry.paintOrder());
        assertFalse(PointerOverlayRegistry.hit(5,5));
        assertFalse(PointerOverlayRegistry.click(5,5,0));
        assertEquals(0,locked.clicks);
        assertFalse(PointerOverlayRegistry.hostPointerAvailable(true)); // host returns unhandled to world dispatcher
        assertTrue(PointerOverlayRegistry.hostPointerAvailable(false));
    }
    @Test void latestWireOrChemClickMatchesPaintPriorityAndCaptureSurvivesCrossWindowDrag() {
        var chem=new FakeWindow(0,0,30,30);var second=new FakeWindow(10,0,30,30);
        chem.visible=true;second.visible=true;PointerOverlayRegistry.register(chem);PointerOverlayRegistry.register(second);
        assertTrue(PointerOverlayRegistry.click(15,5,0));
        assertEquals(List.of(chem,second),PointerOverlayRegistry.paintOrder());
        assertTrue(PointerOverlayRegistry.drag(35,5)); // capture is independent of current hit target
        assertTrue(PointerOverlayRegistry.release(0)); // release outside both bounds
        assertEquals(1,second.drags);assertEquals(1,second.releases);assertEquals(0,chem.drags);
        assertTrue(PointerOverlayRegistry.click(5,5,0));
        assertEquals(List.of(second,chem),PointerOverlayRegistry.paintOrder());
        assertTrue(PointerOverlayRegistry.drag(15,5));
        assertTrue(PointerOverlayRegistry.release(0));
        assertEquals(1,chem.drags);assertEquals(1,chem.releases);
    }
    @Test void wireAndOverlayUseOnePromotedDepthOrder() {
        int wireFront=PointerWorkspaceLayers.wireLayer(false,3,1,true);
        int overlayFront=PointerWorkspaceLayers.browserLayer(0,true,3,2);
        assertTrue(wireFront>overlayFront);
        int wireBack=PointerWorkspaceLayers.wireLayer(true,3,1,false);
        int overlayBack=PointerWorkspaceLayers.browserLayer(0,false,3,2);
        assertTrue(wireBack<overlayBack);
    }
    @Test void paintedStorageFrontRetainsMixedHitPriorityOverWireAndChem() {
        var chem=new FakeWindow(0,0,30,30);chem.visible=true;PointerOverlayRegistry.register(chem);
        PointerOverlayRegistry.overlayFront(false);
        var wireCalls=new java.util.concurrent.atomic.AtomicInteger();
        assertFalse(PointerOverlayRegistry.routeWorkspaceClick(5,5,0,true,true,()->{wireCalls.incrementAndGet();return true;}));
        assertEquals(0,wireCalls.get());assertEquals(0,chem.clicks);assertFalse(PointerOverlayRegistry.overlayFront());
    }
    @Test void productionRouterGeometryPointerPolicyAndMovementSurviveRefreshDragLockAndClose() {
        var movement=new com.juicyslew.moonstation14.ms14.hands.client.PopupMovementIntent();
        var controlled=new SequenceWindow(movement);
        PointerOverlayRegistry.register(controlled);
        controlled.visible=true;
        assertTrue(PointerOverlayRegistry.click(50,40,0));
        for(int i=0;i<3;i++) controlled.visible=true; // normal refresh retains the production window registration
        assertTrue(PointerOverlayRegistry.drag(-900,-900));
        assertEquals(0,controlled.geometry.x());assertEquals(0,controlled.geometry.y());
        assertTrue(PointerOverlayRegistry.release(0));
        assertFalse(controlled.geometry.isPointerCaptured());
        var state=movement.sample(true,true,false,false,false,false,false,false);
        assertEquals(1,state.forward());
        controlled.focus=true;
        assertTrue(PointerOverlayRegistry.textFocused());
        PointerOverlayRegistry.clearTextFocus();
        assertFalse(PointerOverlayRegistry.textFocused());assertTrue(controlled.visible);
        controlled.geometry.setScreenSize(120,90);
        assertEquals(0,controlled.geometry.x());
        controlled.resetOwnedContext();PointerOverlayRegistry.unregister(controlled);
        assertFalse(controlled.visible);
    }
    private static final class FakeWindow implements PointerOverlayWindow {
        final int x,y,w,h; boolean visible,focus,captured,available=true; int clicks,drags,releases,keys,consumed,renders;
        java.util.function.BooleanSupplier onKey,onClick;Runnable onRender;
        FakeWindow(int x,int y,int w,int h){this.x=x;this.y=y;this.w=w;this.h=h;}
        public boolean visible(){return visible;} public boolean contains(double px,double py){return visible&&px>=x&&px<x+w&&py>=y&&py<y+h;}
        public boolean pointerAvailable(){return available;}public boolean ownsPointerCapture(){return captured;}
         public void renderIntoGui(GuiGraphics g,int mx,int my,float p,int sw,int sh){renders++;if(onRender!=null)onRender.run();}
        public boolean mouseClicked(double a,double b,int button){clicks++;captured=true;return onClick==null||onClick.getAsBoolean();}
        public boolean mouseDragged(double a,double b){drags++;return captured;} public boolean mouseReleased(int b){releases++;boolean v=captured;captured=false;return v;}
        public boolean mouseScrolled(double a,double b,double d){return false;} public boolean keyPressed(int k,int s,int m){keys++;if(focus)consumed++;return onKey==null?focus:onKey.getAsBoolean();}
        public boolean charTyped(char c,int m){return focus;} public boolean isSearchFocused(){return focus;}
        public void clearTextFocus(){focus=false;} public void resetOwnedContext(){visible=false;focus=false;captured=false;}
    }
    private static final class SequenceWindow implements PointerOverlayWindow {
        final com.juicyslew.moonstation14.ms14.ui.client.window.WindowGeometryController geometry=
                new com.juicyslew.moonstation14.ms14.ui.client.window.WindowGeometryController(320,240,40,30,180,120,100,70,false);
        final com.juicyslew.moonstation14.ms14.hands.client.PopupMovementIntent movement;boolean visible,focus;
        SequenceWindow(com.juicyslew.moonstation14.ms14.hands.client.PopupMovementIntent movement){this.movement=movement;}
        public boolean visible(){return visible;}public boolean contains(double x,double y){return visible&&x>=geometry.x()&&x<geometry.x()+geometry.width()&&y>=geometry.y()&&y<geometry.y()+geometry.height();}
        public boolean ownsPointerCapture(){return geometry.isPointerCaptured();}
        public void renderIntoGui(GuiGraphics g,int mx,int my,float p,int w,int h){geometry.setScreenSize(w,h);}
        public boolean mouseClicked(double x,double y,int button){return geometry.beginPointer(x,y,button,27,46);}
        public boolean mouseDragged(double x,double y){return geometry.dragPointer(x,y);}
        public boolean mouseReleased(int button){return geometry.releasePointer(button);}
        public boolean mouseScrolled(double x,double y,double d){return false;}public boolean keyPressed(int k,int s,int m){return focus;}
        public boolean charTyped(char c,int m){return focus;}public boolean isSearchFocused(){return focus;}public void clearTextFocus(){focus=false;}
        public void resetOwnedContext(){visible=false;geometry.cancelPointer();movement.clear();focus=false;}
    }
}
