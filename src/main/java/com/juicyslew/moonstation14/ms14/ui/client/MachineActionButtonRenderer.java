package com.juicyslew.moonstation14.ms14.ui.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/** Compact toolkit command hit-region paint, matching the recessed machine field controls. */
public final class MachineActionButtonRenderer {
    private MachineActionButtonRenderer() {}
    public static void render(GuiGraphics g, String text, int x, int y, int width, int height, boolean hovered) {
        g.fill(x,y,x+width,y+height,0xff11191d);
        g.fill(x+1,y+1,x+width-1,y+height-1,hovered?0xff496b75:0xff26363d);
        var font=Minecraft.getInstance().font;
        g.drawString(font,text,x+(width-font.width(text))/2,y+(height-font.lineHeight)/2,MachineWindowRenderer.TEXT,true);
    }
}
