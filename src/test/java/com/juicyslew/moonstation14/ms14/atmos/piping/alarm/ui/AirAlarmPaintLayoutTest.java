package com.juicyslew.moonstation14.ms14.atmos.piping.alarm.ui;

import com.juicyslew.moonstation14.ms14.atmos.piping.alarm.ui.client.AirAlarmPaint;
import com.juicyslew.moonstation14.ms14.atmos.piping.alarm.ui.client.AirAlarmPaintLayout;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AirAlarmPaintLayoutTest {
    @Test void contentLayoutsFitEachAlarmTab(){
        assertEquals(170,AirAlarmPaintLayout.forContent(320,240,1,1).height());
        assertEquals(180,AirAlarmPaintLayout.forContent(320,240,2,1).height());
        assertEquals(150,AirAlarmPaintLayout.forContent(320,240,0,1).height());
        assertTrue(AirAlarmPaintLayout.forContent(320,240,0,5).height()<=AirAlarmPaintLayout.HEIGHT);
    }
    @Test void compactGuiPixelGeometryFitsAndSeparatesControls(){
        for(int[] viewport:new int[][]{{320,240},{640,360}}){var l=AirAlarmPaintLayout.forViewport(viewport[0],viewport[1]);
            assertTrue(l.width()<=240&&l.height()<=224);assertEquals(Math.min(240,viewport[0]),l.width());
            assertTrue(l.close().x()+l.close().width()<=l.width());assertTrue(l.footer().y()+l.footer().height()<=l.height());
            assertTrue(l.close().width()>=10,"close target remains usable at every GUI scale");
            for(int i=0;i<3;i++)assertTrue(l.tab(i).x()+l.tab(i).width()<=l.width());
            assertFalse(l.search().overlaps(l.device(0)),"search editor and first device row must not occlude each other");
            assertFalse(l.enabled().overlaps(l.mode()));
            for(int i=0;i<9;i++){assertTrue(l.gas(i).x()+l.gas(i).width()<=l.width());assertTrue(l.gas(i).y()+l.gas(i).height()<=l.height()-30);}
        }
    }
    @Test void presentationActionsHaveStableIdsAndGuiPixelHitCoordinates(){
        var l=AirAlarmPaintLayout.forViewport(320,240);var v=new AirAlarmPaint.View(null,0,0,320,240,0,0,-1,0,0,"","","",false,false);
        var hits=AirAlarmPaint.hitRegions(l,v);assertEquals("close",hits.get(0).id());
        assertTrue(hits.stream().anyMatch(h->h.id().equals("tab_devices")));
        assertTrue(hits.stream().anyMatch(h->h.id().equals("device_search")));
        assertTrue(hits.stream().noneMatch(h->h.id().equals("enabled")));
        assertEquals("a…",AirAlarmPaint.fit(new WidthPaint(),"abcd",2));
    }
    private static final class WidthPaint implements com.juicyslew.moonstation14.ms14.ui.client.paint.OrderedPaint {
        public int width(String s){return s.length();}public int width(net.minecraft.network.chat.Component c){return width(c.getString());}public int lineHeight(){return 9;}public String plainSubstrByWidth(String s,int w){return s.substring(0,Math.min(w,s.length()));}
        public void fill(int a,int b,int c,int d,int e){}public void text(net.minecraft.network.chat.Component c,int x,int y,int color,boolean shadow){}
        public void push(){}public void pop(){}public void translate(float x,float y,float z){}public void scale(float x,float y,float z){}
        public void clip(int x,int y,int r,int b){}public void unclip(){}
        public void sprite(net.minecraft.resources.ResourceLocation t,int x,int y,int w,int h,int u,int v,int sw,int sh,int tw,int th,int tint){}
    }
}
