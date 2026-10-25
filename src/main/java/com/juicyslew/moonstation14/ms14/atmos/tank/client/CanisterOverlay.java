package com.juicyslew.moonstation14.ms14.atmos.tank.client;

import com.juicyslew.moonstation14.ms14.atmos.tank.CanisterControlRequest;
import com.juicyslew.moonstation14.ms14.atmos.tank.CanisterSessionSnapshot;
import com.juicyslew.moonstation14.ms14.atmos.tank.client.CanisterWorkspace;
import com.juicyslew.moonstation14.ms14.hands.client.BodyHandClient;
import com.juicyslew.moonstation14.ms14.ui.client.MachineActionButtonRenderer;
import com.juicyslew.moonstation14.ms14.ui.client.MachineWindowRenderer;
import com.juicyslew.moonstation14.ms14.ui.client.pointer.PointerOverlayRegistry;
import com.juicyslew.moonstation14.ms14.ui.client.pointer.PointerOverlayWindow;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/** Compact controls for the currently selected authoritative canister session. */
public final class CanisterOverlay implements PointerOverlayWindow {
    private static final int WIDTH = 176, HEIGHT = 146, SCALE = 2;
    private final CanisterWorkspace workspace;
    private int x, y, screenWidth, screenHeight;
    private boolean dragging;

    public CanisterOverlay(CanisterWorkspace workspace) { this.workspace = java.util.Objects.requireNonNull(workspace); }

    @Override public boolean visible() { return workspace.session() != null; }
    @Override public boolean contains(double px, double py) {
        return visible() && px >= x && py >= y && px < x + WIDTH / (double) SCALE && py < y + HEIGHT / (double) SCALE;
    }

    @Override public void renderIntoGui(GuiGraphics g, int mx, int my, float partial, int sw, int sh) {
        screenWidth = sw; screenHeight = sh;
        if (x == 0 && y == 0) { x = Math.max(0, (sw - WIDTH) / 2); y = Math.max(0, (sh - HEIGHT) / 2); }
        CanisterSessionSnapshot s = workspace.session(); if (s == null) return;
        var font = Minecraft.getInstance().font;
        boolean enabled = enabled(s);
        int lx = toLocal(mx - x), ly = toLocal(my - y);
        g.pose().pushPose();
        try {
            g.pose().translate(x, y, 0); g.pose().scale(.5f, .5f, 1);
            MachineWindowRenderer.window(g, font, 0, 0, WIDTH, HEIGHT, net.minecraft.network.chat.Component.literal("Gas Canister"));
            g.drawString(font, "Pressure: " + String.format(java.util.Locale.ROOT, "%.1f kPa", s.pressureKpa()), 8, 27, MachineWindowRenderer.TEXT, true);
            g.drawString(font, "Tank: " + (s.activeTank().isEmpty() ? "Empty" : s.activeTank().getHoverName().getString()), 8, 42, MachineWindowRenderer.TEXT, true);
            g.drawString(font, "Gas: " + gas(s), 8, 57, MachineWindowRenderer.MUTED_TEXT, true);
            button(g, "−", 8, 72, 32, 18, lx, ly, enabled && s.pressureKpa() > s.minimumPressureKpa());
            button(g, "+", 44, 72, 32, 18, lx, ly, enabled && s.pressureKpa() < s.maximumPressureKpa());
            button(g, s.valveClosed() ? "Open valve" : "Close valve", 82, 72, 86, 18, lx, ly, enabled);
            button(g, "Insert", 8, 96, 54, 18, lx, ly, enabled);
            button(g, "Eject", 68, 96, 54, 18, lx, ly, enabled && !s.activeTank().isEmpty());
            button(g, "×", 148, 5, 20, 16, lx, ly, true);
            g.drawString(font, "World venting deferred.", 8, 126, MachineWindowRenderer.MUTED_TEXT, true);
        } finally { g.pose().popPose(); }
    }

    private static int toLocal(double screenDelta) { return (int)(screenDelta * SCALE); }

    private static String gas(CanisterSessionSnapshot s) {
        if (s.gasMoles().isEmpty()) return "No gas";
        return s.gasMoles().entrySet().stream().map(e -> e.getKey() + " " + String.format(java.util.Locale.ROOT, "%.2f mol", e.getValue())).reduce((a,b) -> a + ", " + b).orElse("No gas");
    }
    private static void button(GuiGraphics g, String text, int x, int y, int w, int h, int mx, int my, boolean enabled) {
        MachineActionButtonRenderer.render(g, text, x, y, w, h, mx >= x && mx < x+w && my >= y && my < y+h, enabled, false, .7f);
    }
    private boolean enabled(CanisterSessionSnapshot s) {
        var proof = BodyHandClient.currentHandProof();
        return proof != null && workspace.requestsEnabled(proof.snapshot().bodyId(), proof.snapshot().epoch())
                && s.bodyId().equals(proof.snapshot().bodyId()) && s.authorityEpoch() == proof.snapshot().epoch();
    }
    private boolean send(CanisterControlRequest.Operation op, CanisterSessionSnapshot s, boolean valve, double pressure) {
        var proof = BodyHandClient.currentHandProof(); if (proof == null || !enabled(s)) return false;
        return workspace.send(proof.snapshot().bodyId(), proof.snapshot().epoch(), op, 0, valve, pressure);
    }
    @Override public boolean mouseClicked(double px, double py, int button) {
        if (button != 0 || !contains(px,py) || Minecraft.getInstance().mouseHandler.isMouseGrabbed()) return false;
        PointerOverlayRegistry.bringToFront(this);
        int lx=toLocal(px-x), ly=toLocal(py-y); var s=workspace.session();
        if (lx>=148&&ly<21) { workspace.close(); return true; }
        if (ly<24) { dragging=true; return true; }
        if(s==null)return true;
        if(ly>=72&&ly<90&&lx<40)send(CanisterControlRequest.Operation.SET_PRESSURE,s,s.valveClosed(),Math.max(s.minimumPressureKpa(),s.pressureKpa()-10));
        else if(ly>=72&&ly<90&&lx<80)send(CanisterControlRequest.Operation.SET_PRESSURE,s,s.valveClosed(),Math.min(s.maximumPressureKpa(),s.pressureKpa()+10));
        else if(ly>=72&&ly<90&&lx>=82)send(CanisterControlRequest.Operation.SET_VALVE,s,!s.valveClosed(),s.pressureKpa());
        else if(ly>=96&&ly<114&&lx<64)send(CanisterControlRequest.Operation.INSERT_ACTIVE,s,s.valveClosed(),s.pressureKpa());
        else if(ly>=96&&ly<114&&lx<124)send(CanisterControlRequest.Operation.EJECT,s,s.valveClosed(),s.pressureKpa());
        return true;
    }
    @Override public boolean mouseDragged(double px,double py){if(!dragging)return false;x=Math.max(0,Math.min(screenWidth-WIDTH/SCALE,(int)px-WIDTH/(2*SCALE)));y=Math.max(0,Math.min(screenHeight-HEIGHT/SCALE,(int)py-13));return true;}
    @Override public boolean mouseReleased(int button){if(button==0&&dragging){dragging=false;return true;}return false;}
    @Override public boolean mouseScrolled(double x,double y,double delta){return false;}
    @Override public boolean keyPressed(int key,int scan,int modifiers){return false;}
    @Override public boolean charTyped(char value,int modifiers){return false;}
    @Override public boolean isSearchFocused(){return false;}
    @Override public void clearTextFocus(){}
    @Override public void resetOwnedContext(){workspace.close();}
}

