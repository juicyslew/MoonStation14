package com.juicyslew.moonstation14.ms14.ui.client.paint;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** Production adapter; coordinates and clipping retain GuiGraphics semantics. */
public final class GuiGraphicsPaint implements OrderedPaint {
    private final GuiGraphics graphics;
    private final Font font;
    public GuiGraphicsPaint(GuiGraphics graphics, Font font) { this.graphics=graphics; this.font=font; }
    public void fill(int l,int t,int r,int b,int c){graphics.fill(l,t,r,b,c);}
    public void text(Component s,int x,int y,int c,boolean shadow){graphics.drawString(font,s,x,y,c,shadow);}
    public int width(Component s){return font.width(s);} public int width(String s){return font.width(s);}
    public int lineHeight(){return font.lineHeight;}
    public String plainSubstrByWidth(String s,int w){return font.plainSubstrByWidth(s,w);}
    public void push(){graphics.pose().pushPose();} public void pop(){graphics.pose().popPose();}
    public void translate(float x,float y,float z){graphics.pose().translate(x,y,z);}
    public void scale(float x,float y,float z){graphics.pose().scale(x,y,z);}
    public void clip(int l,int t,int r,int b){graphics.enableScissor(l,t,r,b);} public void unclip(){graphics.disableScissor();}
    public void sprite(ResourceLocation texture,int x,int y,int w,int h,int u,int v,int sw,int sh,int tw,int th,int tint){
        RenderSystem.setShaderColor(((tint>>16)&255)/255f,((tint>>8)&255)/255f,(tint&255)/255f,((tint>>>24)&255)/255f);
        try { graphics.blit(texture,x,y,w,h,u,v,sw,sh,tw,th); } finally { RenderSystem.setShaderColor(1,1,1,1); }
    }
}
