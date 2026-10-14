package com.juicyslew.moonstation14.ms14.chemistry.chemmaster.client;

import com.juicyslew.moonstation14.ms14.ui.client.paint.OrderedPaint;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ChemMasterTextFieldPaintTest {
    @Test void paintsNativeVisibleSubstringAndSelectionFromSnapshot() {
        var text=new ArrayList<String>();var fills=new ArrayList<int[]>();
        OrderedPaint p=new OrderedPaint(){
            public void fill(int l,int t,int r,int b,int c){fills.add(new int[]{l,t,r,b,c});}
            public void text(Component c,int x,int y,int color,boolean shadow){text.add(c.getString());}
            public int width(Component c){return width(c.getString());} public int width(String s){return s.length();}
            public int lineHeight(){return 8;} public String plainSubstrByWidth(String s,int w){return s.substring(0,Math.min(s.length(),w));}
            public void push(){} public void pop(){} public void translate(float x,float y,float z){} public void scale(float x,float y,float z){}
            public void clip(int l,int t,int r,int b){} public void unclip(){}
            public void sprite(ResourceLocation t,int x,int y,int w,int h,int u,int v,int sw,int sh,int tw,int th,int tint){}
        };
        ChemMasterTextFieldPaint.paint(p,new ChemMasterOutputLayout.Rect(4,6,30,14),
                new ChemMasterTextFieldPaint.Snapshot("abcdef",4,2,1,true,true,true,1));
        assertEquals(List.of("bcdef"),text);
        assertTrue(fills.stream().anyMatch(f->f[4]==0xff526f9c));
        assertTrue(fills.stream().anyMatch(f->f[4]==0xfff0f4f5));
    }
    @Test void editorAndPainterShareEvenLogicalViewportCoordinates(){
        var rect=new ChemMasterOutputLayout.Rect(5,6,31,14);
        var viewport=ChemMasterTextFieldPaint.viewport(rect,8);
        assertEquals(0,viewport.x()%2,"screen scissor and text origin must share the same half-scale pixel edge");
        assertEquals(9,viewport.y());assertEquals(8,viewport.height());
        assertEquals(rect.x()+rect.width()-3-viewport.x(),viewport.width());
    }
}
