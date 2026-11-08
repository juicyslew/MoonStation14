package com.juicyslew.moonstation14.ms14.ui.preview;

import com.juicyslew.moonstation14.ms14.ui.client.paint.OrderedPaint;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import java.awt.image.BufferedImage;
import java.util.ArrayDeque;
import java.util.Deque;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** Test decorator: proves each production painter clip prevents pixels changing outside it. */
final class ClipAuditOrderedPaint implements OrderedPaint {
    private final CpuOrderedPaint target; private final int scale; private final Deque<Audit> audits=new ArrayDeque<>();
    ClipAuditOrderedPaint(CpuOrderedPaint target,int guiScale){this.target=target;this.scale=guiScale;}
    public void fill(int l,int t,int r,int b,int c){target.fill(l,t,r,b,c);}
    public void text(Component c,int x,int y,int color,boolean shadow){target.text(c,x,y,color,shadow);}
    public int width(Component c){return target.width(c);}public int width(String s){return target.width(s);}public int lineHeight(){return target.lineHeight();}
    public String plainSubstrByWidth(String s,int w){return target.plainSubstrByWidth(s,w);}
    public void push(){target.push();}public void pop(){target.pop();}public void translate(float x,float y,float z){target.translate(x,y,z);}public void scale(float x,float y,float z){target.scale(x,y,z);}
    public void clip(int l,int t,int r,int b){audits.push(new Audit(l*scale,t*scale,r*scale,b*scale,copy(target.image())));target.clip(l,t,r,b);}
    public void unclip(){target.unclip();Audit audit=audits.pop();var after=target.image();for(int y=0;y<after.getHeight();y++)for(int x=0;x<after.getWidth();x++)if(x<audit.l||x>=audit.r||y<audit.t||y>=audit.b)assertEquals(audit.before.getRGB(x,y),after.getRGB(x,y),"production clip leaked paint at device pixel "+x+","+y+" from GUI clip "+audit);}
    public void sprite(ResourceLocation texture,int x,int y,int width,int height,int u,int v,int sw,int sh,int tw,int th,int tint){target.sprite(texture,x,y,width,height,u,v,sw,sh,tw,th,tint);}
    private static BufferedImage copy(BufferedImage image){var copy=new BufferedImage(image.getWidth(),image.getHeight(),BufferedImage.TYPE_INT_ARGB);copy.setRGB(0,0,image.getWidth(),image.getHeight(),image.getRGB(0,0,image.getWidth(),image.getHeight(),null,0,image.getWidth()),0,image.getWidth());return copy;}
    private record Audit(int l,int t,int r,int b,BufferedImage before){}
}
