package com.juicyslew.moonstation14.ms14.ui.client.pointer;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;

/** Registry intentionally limited to current pointer-workspace overlay windows. */
public final class PointerOverlayRegistry {
    private static final List<PointerOverlayWindow> WINDOWS = new ArrayList<>();
    private static boolean overlayFront=true;
    private static boolean wireFront=true;
    private PointerOverlayRegistry() { }
    /** Shared production host admission for both rendering and input; vanilla modal screens are excluded. */
    public static boolean allowsHost(net.minecraft.client.gui.screens.Screen screen) {
        boolean project=screen instanceof com.juicyslew.moonstation14.ms14.ui.client.GameplayWindow
                || screen instanceof com.juicyslew.moonstation14.ms14.power.ui.client.ApcScreen
                || screen instanceof com.juicyslew.moonstation14.ms14.atmos.piping.alarm.ui.client.AirAlarmScreen
                || screen instanceof com.juicyslew.moonstation14.ms14.disposal.unit.ui.client.DisposalUnitScreen
                || screen instanceof com.juicyslew.moonstation14.ms14.disposal.ui.client.DisposalBuildScreen
                || screen instanceof com.juicyslew.moonstation14.ms14.ui.client.gallery.MachineUiGalleryScreen;
        return hostAllowed(screen==null,screen instanceof com.juicyslew.moonstation14.ms14.hands.client.StoragePopupScreen,project);
    }
    /** Storage canvas owns its complete input stream; its Screen routes against paint order. */
    public static boolean inputBelongsToScreen(net.minecraft.client.gui.screens.Screen screen) {
        return screen instanceof com.juicyslew.moonstation14.ms14.hands.client.StoragePopupScreen;
    }
    /** The workspace is painted once: after HUD on gameplay, after content on approved screens. */
    public static boolean shouldPaintWorkspace(boolean gameplayGuiPost, boolean screenPost,
                                               boolean currentScreen, boolean allowedHost) {
        if (gameplayGuiPost) return !screenPost;
        return screenPost && currentScreen && allowedHost;
    }
    /** Same admission predicate is used by production paint and input routing. */
    public static boolean hostAllowed(boolean gameplay,boolean pointerWorkspace,boolean projectMachineUi) {
        return gameplay||pointerWorkspace||projectMachineUi;
    }
    public static void register(PointerOverlayWindow window) { if (!WINDOWS.contains(window)) WINDOWS.add(window); }
    public static void unregister(PointerOverlayWindow window) { WINDOWS.remove(window); }
    /** Pin traversal order for one dispatch; new registrations join only the next dispatch. */
    private static List<PointerOverlayWindow> snapshot() { return List.copyOf(WINDOWS); }
    /** A dispatch uses its pinned membership/order; callbacks cannot remove peers mid-dispatch. */
    private static boolean active(PointerOverlayWindow window) { return window.visible(); }
    public static void bringToFront(PointerOverlayWindow window) { if(WINDOWS.remove(window)) WINDOWS.add(window); wireFront=false; }
    public static void bringWireToFront() { wireFront=true; }
    public static boolean wireFront() { return wireFront; }
    public static boolean anyVisible() { return snapshot().stream().anyMatch(PointerOverlayWindow::visible); }
    public static boolean textFocused() { return snapshot().stream().anyMatch(w -> active(w) && w.isSearchFocused()); }
    public static void clearTextFocus() { for (var w : snapshot()) if (active(w)) w.clearTextFocus(); }
    public static void resetOwnedContext() { for (var w : snapshot()) w.resetOwnedContext(); WINDOWS.clear(); overlayFront=true;wireFront=true; }
    public static boolean overlayFront() { return overlayFront; }
    /** Cursor-grabbed gameplay input stays with the outer world dispatcher, not this host. */
    public static boolean hostPointerAvailable(boolean mouseGrabbed) { return !mouseGrabbed; }
    public static void overlayFront(boolean front) { overlayFront=front; }
    public static boolean hit(double x,double y) { return snapshot().stream().anyMatch(w -> active(w)&&w.pointerAvailable()&&w.contains(x,y)); }
    public static boolean click(double x,double y,int button) { var ordered=paintOrder(); for(int i=ordered.size()-1;i>=0;i--){var w=ordered.get(i);if(active(w)&&w.pointerAvailable()&&w.contains(x,y)){bringToFront(w);return w.mouseClicked(x,y,button);}}return false; }
    /** Existing storage host's mixed wire/overlay click owner; same front choice drives paint layers. */
    public static boolean routeWorkspaceClick(double x,double y,int button,boolean storageWins,
                                              boolean wireHit,java.util.function.BooleanSupplier wireClick) {
        // A physical pointer click ends the previous text target; a clicked field can reclaim it.
        clearTextFocus();
        boolean overlayHit=hit(x,y);
        if(!storageWins&&wireHit&&(!overlayHit||wireFront)) {
            bringWireToFront();overlayFront(true);wireClick.getAsBoolean();return true;
        }
        if(!storageWins&&overlayHit) {
            overlayFront(true);
            click(x,y,button);return true;
        }
        return false;
    }
    public static boolean drag(double x,double y) { for(var w:snapshot())if(active(w)&&w.pointerAvailable()&&w.ownsPointerCapture()&&w.mouseDragged(x,y))return true;return false; }
    public static boolean hasPointerCapture() { return snapshot().stream().anyMatch(w->active(w)&&w.pointerAvailable()&&w.ownsPointerCapture()); }
    public static boolean release(int button) { var ordered=snapshot();for(int i=ordered.size()-1;i>=0;i--){var w=ordered.get(i);if(active(w)&&w.ownsPointerCapture()&&w.mouseReleased(button))return true;}return false; }
    /** Production host adapter: dispatch only to the window that began the pointer gesture. */
    public static boolean capturedDrag(double x,double y,java.util.function.BooleanSupplier wireCaptured,
                                       java.util.function.BooleanSupplier wireDrag) {
        if(wireCaptured.getAsBoolean()){wireDrag.getAsBoolean();return true;}
        if(!hasPointerCapture())return false;
        drag(x,y);
        return true;
    }
    /** Capture is checked before invoking release because release clears its owner's state. */
    public static boolean capturedRelease(int button,java.util.function.BooleanSupplier wireCaptured,
                                          java.util.function.BooleanSupplier wireRelease) {
        if(wireCaptured.getAsBoolean()){wireRelease.getAsBoolean();return true;}
        if(snapshot().stream().noneMatch(w->active(w)&&w.ownsPointerCapture()))return false;
        release(button);
        return true;
    }
    public static boolean scroll(double x,double y,double delta) { var ordered=snapshot();for(int i=ordered.size()-1;i>=0;i--){var w=ordered.get(i);if(active(w)&&w.pointerAvailable()&&w.contains(x,y))return w.mouseScrolled(x,y,delta);}return false; }
    public static boolean key(int key,int scan,int mods) { for(var w:snapshot())if(active(w)&&w.keyPressed(key,scan,mods))return true;return false; }
    public static boolean character(char c,int mods) { for(var w:snapshot())if(active(w)&&w.charTyped(c,mods))return true;return false; }
    /** Visible windows in back-to-front paint order; input reverses this exact order. */
    public static List<PointerOverlayWindow> paintOrder() { return snapshot().stream().filter(w->active(w)).toList(); }
    /** Traverse the pinned paint order, rechecking membership before each callback. */
    static void visitPaintOrder(List<PointerOverlayWindow> ordered,
                                java.util.function.BiConsumer<PointerOverlayWindow, Integer> visitor) {
        for (int i = 0; i < ordered.size(); i++) {
            var window = ordered.get(i);
            if (active(window)) visitor.accept(window, i);
        }
    }
    public static void render(GuiGraphics g,int mx,int my,float partial,int width,int height) {
        render(g,mx,my,partial,width,height,3);
    }
    public static void render(GuiGraphics g,int mx,int my,float partial,int width,int height,int storageSiblingCount) {
        var visible=paintOrder();
        int browserFamilySize=visible.size()+1; // registered windows plus the active-wire slot
        visitPaintOrder(visible, (window, i) -> {
            g.flush(); g.pose().pushPose();
            try {
                int layer=PointerWorkspaceLayers.browserLayer(i,overlayFront,storageSiblingCount,browserFamilySize);
                g.pose().translate(0,0,layer);
                window.renderIntoGui(g,mx,my,partial,width,height);
            } finally { g.pose().popPose(); g.flush(); }
        });
    }
    /** Single paint owner for the storage workspace and registered pointer windows. */
    public static void renderWorkspace(Runnable storagePaint, Runnable overlayPaint) {
        if (overlayFront) {
            storagePaint.run();
            overlayPaint.run();
        } else {
            overlayPaint.run();
            storagePaint.run();
        }
    }
}

