package com.juicyslew.moonstation14.ms14.ui.client.paint;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class MachineTextFieldPaintTest {
    @Test void preservesFirstGlyphAndUsesNativeDisplayStartForNarrowField(){
        var texts=new ArrayList<String>();var clips=new ArrayList<int[]>();var fills=new ArrayList<int[]>();
        OrderedPaint p=new OrderedPaint(){public void fill(int l,int t,int r,int b,int c){fills.add(new int[]{l,t,r,b,c});}public void text(Component c,int x,int y,int color,boolean shadow){texts.add(c.getString());}public int width(Component c){return width(c.getString());}public int width(String s){return s.length();}public int lineHeight(){return 8;}public String plainSubstrByWidth(String s,int w){return s.substring(0,Math.min(s.length(),w));}public void push(){}public void pop(){}public void translate(float x,float y,float z){}public void scale(float x,float y,float z){}public void clip(int l,int t,int r,int b){clips.add(new int[]{l,t,r,b});}public void unclip(){}public void sprite(ResourceLocation t,int x,int y,int w,int h,int u,int v,int sw,int sh,int tw,int th,int tint){}};
        var wide=new MachineTextFieldPaint.Rect(4,6,30,14);var viewport=MachineTextFieldPaint.viewport(wide,8);
        MachineTextFieldPaint.paint(p,wide,new MachineTextFieldPaint.Snapshot("123.45",6,2,0,true,true,true,1));
        assertEquals("123.45",texts.get(0));assertEquals(viewport.x(),clips.get(0)[0]);assertTrue(fills.stream().anyMatch(f->f[4]==0xff526f9c));assertTrue(fills.stream().anyMatch(f->f[4]==0xfff0f4f5));
        texts.clear();clips.clear();var narrow=new MachineTextFieldPaint.Rect(4,6,12,14);
        MachineTextFieldPaint.paint(p,narrow,new MachineTextFieldPaint.Snapshot("123.45",6,6,3,false,true,true,1));
        assertEquals(".45",texts.get(0));assertEquals(MachineTextFieldPaint.viewport(narrow,8).x(),clips.get(0)[0]);
    }
}
