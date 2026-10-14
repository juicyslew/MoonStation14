package com.juicyslew.moonstation14.ms14.ui.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/** Compact toolkit command hit-region paint, matching the recessed machine field controls. */
public final class MachineActionButtonRenderer {
    private MachineActionButtonRenderer() {}
    public static void render(GuiGraphics g, String text, int x, int y, int width, int height, boolean hovered) {
        render(g,text,x,y,width,height,hovered,true,false);
    }
    public static void render(GuiGraphics g,String text,int x,int y,int width,int height,boolean hovered,boolean enabled,boolean selected) {
        g.fill(x,y,x+width,y+height,0xff11191d);
        g.fill(x+1,y+1,x+width-1,y+height-1,faceColor(hovered,enabled,selected));
        var font=Minecraft.getInstance().font;
        int color=enabled?MachineWindowRenderer.TEXT:0xff718187;
        g.drawString(font,text,x+(width-font.width(text))/2,y+(height-font.lineHeight)/2,color,true);
        if(selected&&enabled&&height>=3)g.fill(x+2,y+height-2,x+width-2,y+height-1,0xff8ce39a);
    }
    /** Same toolkit button with a measured compact label; existing consumers retain native sizing. */
    public static void render(GuiGraphics g,String text,int x,int y,int width,int height,boolean hovered,boolean enabled,boolean selected,float labelScale) {
        g.fill(x,y,x+width,y+height,0xff11191d);
        g.fill(x+1,y+1,x+width-1,y+height-1,faceColor(hovered,enabled,selected));
        var font=Minecraft.getInstance().font;
        int color=enabled?MachineWindowRenderer.TEXT:0xff718187;
        float scale=Math.max(0.1f,Math.min(1f,labelScale));
        g.pose().pushPose();try{g.pose().translate(x+width/2f,y+height/2f,0);g.pose().scale(scale,scale,1);
            g.drawString(font,text,-font.width(text)/2,-font.lineHeight/2,color,true);
        }finally{g.pose().popPose();}
        if(selected&&enabled&&height>=3)g.fill(x+2,y+height-2,x+width-2,y+height-1,0xff8ce39a);
        if(selected&&enabled&&height>=3)g.fill(x+2,y+height-2,x+width-2,y+height-1,0xff8ce39a);
    }
    public static int faceColor(boolean hovered,boolean enabled,boolean selected){return !enabled?0xff20282b:hovered?0xff496b75:selected?0xff3b555e:0xff26363d;}
}

