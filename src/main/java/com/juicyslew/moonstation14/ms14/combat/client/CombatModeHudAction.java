package com.juicyslew.moonstation14.ms14.combat.client;

import com.juicyslew.moonstation14.ms14.ui.client.pointer.PointerOverlayWindow;
import com.juicyslew.moonstation14.ms14.combat.CombatModeClient;
import com.juicyslew.moonstation14.ms14.combat.CombatModeHudPolicy;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** Compact action button hosted by the shared pointer overlay workspace. */
public final class CombatModeHudAction implements PointerOverlayWindow {
    public static final CombatModeHudAction INSTANCE = new CombatModeHudAction();
    private static final int SIZE = 32;
    private static final ResourceLocation OFF = ResourceLocation.fromNamespaceAndPath("moonstation14", "textures/ui/actions/harm_off.png");
    private static final ResourceLocation ON = ResourceLocation.fromNamespaceAndPath("moonstation14", "textures/ui/actions/harm.png");
    private CombatModeHudAction() { }
    private boolean ready() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !mc.player.isSpectator()) return false;
        var body = com.juicyslew.moonstation14.ms14.player_body_control.client.GhostControlClient.ownedCharacterForPresentation();
        return CombatModeClient.hasCapability(body);
    }
    @Override public boolean visible() { return ready(); }
    @Override public boolean contains(double x, double y) { Minecraft mc = Minecraft.getInstance(); var b=CombatModeHudPolicy.bounds(mc.getWindow().getGuiScaledWidth(),mc.getWindow().getGuiScaledHeight()); return visible() && b.contains(x,y); }
    @Override public boolean pointerAvailable() { Minecraft mc=Minecraft.getInstance(); return CombatModeHudPolicy.admitted(ready(),mc.mouseHandler.isMouseGrabbed(),mc.screen!=null,com.juicyslew.moonstation14.ms14.ui.client.pointer.PointerOverlayRegistry.inputBelongsToScreen(mc.screen)) && com.juicyslew.moonstation14.ms14.ui.client.pointer.PointerOverlayRegistry.hostPointerAvailable(mc.mouseHandler.isMouseGrabbed()); }
    @Override public void renderIntoGui(GuiGraphics g,int mx,int my,float partial,int width,int height) {
        if (!visible()) return;
        int x=CombatModeHudPolicy.bounds(width,height).x(), y=CombatModeHudPolicy.bounds(width,height).y();
        g.blit(CombatModeHudPolicy.icon(CombatModeClient.policy().mode()).equals("on")?ON:OFF,x,y,0,0,SIZE,SIZE,SIZE,SIZE);
        if (mx>=x && mx<x+SIZE && my>=y && my<y+SIZE) g.renderTooltip(Minecraft.getInstance().font, Component.literal("Enter combat mode"),mx,my);
    }
    @Override public boolean mouseClicked(double x,double y,int button) { if(button!=0||!pointerAvailable()||!contains(x,y))return false; return CombatModeClient.requestToggle(); }
    @Override public boolean mouseDragged(double x,double y){return false;} @Override public boolean mouseReleased(int b){return false;}
    @Override public boolean mouseScrolled(double x,double y,double d){return false;} @Override public boolean keyPressed(int k,int s,int m){return false;}
    @Override public boolean charTyped(char c,int m){return false;} @Override public boolean isSearchFocused(){return false;}
    @Override public void clearTextFocus(){} @Override public void resetOwnedContext(){}
}



