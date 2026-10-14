package com.juicyslew.moonstation14.ms14.ui.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import com.juicyslew.moonstation14.ms14.ui.client.paint.OrderedPaint;
import com.juicyslew.moonstation14.ms14.ui.client.paint.GuiGraphicsPaint;

/** Compact toolkit command hit-region paint, matching the recessed machine field controls. */
public final class MachineActionButtonRenderer {
    private MachineActionButtonRenderer() {}
    public static void render(GuiGraphics g, String text, int x, int y, int width, int height, boolean hovered) {
        render(g,text,x,y,width,height,hovered,true,false);
    }
    public static void render(GuiGraphics g,String text,int x,int y,int width,int height,boolean hovered,boolean enabled,boolean selected) {
        render(new GuiGraphicsPaint(g,Minecraft.getInstance().font),text,x,y,width,height,hovered,enabled,selected,1f);
    }
    /** Same toolkit button with a measured compact label; existing consumers retain native sizing. */
    public static void render(GuiGraphics g,String text,int x,int y,int width,int height,boolean hovered,boolean enabled,boolean selected,float labelScale) {
        render(new GuiGraphicsPaint(g,Minecraft.getInstance().font),text,x,y,width,height,hovered,enabled,selected,labelScale);
    }
    public static void render(OrderedPaint g,String text,int x,int y,int width,int height,boolean hovered,boolean enabled,boolean selected,float labelScale) {
        g.fill(x,y,x+width,y+height,0xff11191d);
        g.fill(x+1,y+1,x+width-1,y+height-1,faceColor(hovered,enabled,selected));
        int color=enabled?MachineWindowRenderer.TEXT:0xff718187;
        float scale=Math.max(0.1f,Math.min(1f,labelScale));
        if(scale==1f) {
            g.text(net.minecraft.network.chat.Component.literal(text),x+(width-g.width(text))/2,
                    y+(height-g.lineHeight())/2,color,true);
        } else {
            g.push();try{g.translate(x+width/2f,y+height/2f,0);g.scale(scale,scale,1);
                g.text(net.minecraft.network.chat.Component.literal(text),-g.width(text)/2,-g.lineHeight()/2,color,true);
            }finally{g.pop();}
        }
        if(selected&&enabled&&height>=3)g.fill(x+2,y+height-2,x+width-2,y+height-1,0xff8ce39a);
    }
    public static int faceColor(boolean hovered,boolean enabled,boolean selected){return !enabled?0xff20282b:hovered?0xff496b75:selected?0xff3b555e:0xff26363d;}
}

