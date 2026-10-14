package com.juicyslew.moonstation14.ms14.ui.client.pointer;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;

/** Registry intentionally limited to current pointer-workspace overlay windows. */
public final class PointerOverlayRegistry {
    private static final List<PointerOverlayWindow> WINDOWS = new ArrayList<>();
    private static boolean overlayFront=true;
    private PointerOverlayRegistry() { }
    /** Shared production host admission for both rendering and input; vanilla modal screens are excluded. */
    public static boolean allowsHost(net.minecraft.client.gui.screens.Screen screen) {
        boolean project=screen instanceof com.juicyslew.moonstation14.ms14.power.ui.client.ApcScreen
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
    public static void bringToFront(PointerOverlayWindow window) { if(WINDOWS.remove(window)) WINDOWS.add(window); }
    /** Membership/order is fixed for one dispatch; visibility remains live as callbacks run. */
    private static List<PointerOverlayWindow> snapshot() { return List.copyOf(WINDOWS); }
    public static boolean anyVisible() { return snapshot().stream().anyMatch(PointerOverlayWindow::visible); }
    public static boolean textFocused() { return snapshot().stream().anyMatch(w -> w.visible() && w.isSearchFocused()); }
    public static void clearTextFocus() { for (var w : snapshot()) w.clearTextFocus(); }
    public static void resetOwnedContext() { for (var w : List.copyOf(WINDOWS)) w.resetOwnedContext(); WINDOWS.clear(); overlayFront=true; }
    public static boolean overlayFront() { return overlayFront; }
    public static void overlayFront(boolean front) { overlayFront=front; }
    public static boolean hit(double x,double y) { return snapshot().stream().anyMatch(w -> w.visible() && w.contains(x,y)); }
    public static boolean click(double x,double y,int button) { var ordered=paintOrder(); for(int i=ordered.size()-1;i>=0;i--){var w=ordered.get(i);if(w.contains(x,y))return w.mouseClicked(x,y,button);}return false; }
    public static boolean drag(double x,double y) { for(var w:snapshot())if(w.visible()&&w.mouseDragged(x,y))return true;return false; }
    public static boolean release(int button) { for(var w:snapshot())if(w.visible()&&w.mouseReleased(button))return true;return false; }
    public static boolean scroll(double x,double y,double delta) { var ordered=snapshot();for(int i=ordered.size()-1;i>=0;i--){var w=ordered.get(i);if(w.visible()&&w.contains(x,y))return w.mouseScrolled(x,y,delta);}return false; }
    public static boolean key(int key,int scan,int mods) { for(var w:snapshot())if(w.visible()&&w.keyPressed(key,scan,mods))return true;return false; }
    public static boolean character(char c,int mods) { for(var w:snapshot())if(w.visible()&&w.charTyped(c,mods))return true;return false; }
    /** Visible windows in back-to-front paint order; input reverses this exact order. */
    public static List<PointerOverlayWindow> paintOrder() { return snapshot().stream().filter(PointerOverlayWindow::visible).toList(); }
    public static void render(GuiGraphics g,int mx,int my,float partial,int width,int height) {
        render(g,mx,my,partial,width,height,3);
    }
    public static void render(GuiGraphics g,int mx,int my,float partial,int width,int height,int storageSiblingCount) {
        var visible=paintOrder();
        for(int i=0;i<visible.size();i++) {
            var window=visible.get(i);
            g.flush(); g.pose().pushPose();
            try {
                int layer=PointerWorkspaceLayers.browserLayer(i,overlayFront,storageSiblingCount);
                g.pose().translate(0,0,layer);
                window.renderIntoGui(g,mx,my,partial,width,height);
            } finally { g.pose().popPose(); g.flush(); }
        }
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

