package com.juicyslew.moonstation14.ms14.atmos.piping.alarm.ui;

import com.juicyslew.moonstation14.ms14.atmos.piping.alarm.ui.client.AirAlarmPaint;
import com.juicyslew.moonstation14.ms14.atmos.piping.alarm.ui.client.AirAlarmPaintLayout;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AirAlarmPaintLayoutTest {
    @Test void contentLayoutsFitEachAlarmTab(){
        assertEquals(170,AirAlarmPaintLayout.forContent(320,240,1,1).height());
        assertEquals(180,AirAlarmPaintLayout.forContent(320,240,2,1).height());
        assertEquals(150,AirAlarmPaintLayout.forContent(320,240,0,1).height());
        assertTrue(AirAlarmPaintLayout.forContent(320,240,0,5).height()<=AirAlarmPaintLayout.HEIGHT);
        assertEquals(224,AirAlarmPaintLayout.forContent(320,240,3,1).height());
    }
    @Test void compactGuiPixelGeometryFitsAndSeparatesControls(){
        for(int[] viewport:new int[][]{{320,240},{640,360}}){var l=AirAlarmPaintLayout.forViewport(viewport[0],viewport[1]);
            assertTrue(l.width()<=240&&l.height()<=224);assertEquals(Math.min(240,viewport[0]),l.width());
            assertTrue(l.close().x()+l.close().width()<=l.width());assertTrue(l.footer().y()+l.footer().height()<=l.height());
            assertTrue(l.close().width()>=10,"close target remains usable at every GUI scale");
            for(int i=0;i<4;i++)assertTrue(l.tab(i).x()+l.tab(i).width()<=l.width());
            assertFalse(l.search().overlaps(l.device(0)),"search editor and first device row must not occlude each other");
            assertFalse(l.enabled().overlaps(l.mode()));
            for(int i=0;i<9;i++){assertTrue(l.gas(i).x()+l.gas(i).width()<=l.width());assertTrue(l.gas(i).y()+l.gas(i).height()<=l.height()-30);}
        }
    }
    @Test void controllerAndDeviceControlsHaveDistinctAnchorsAboveFooter(){
        for(int[] viewport:new int[][]{{320,240},{640,360}}){var l=AirAlarmPaintLayout.forContent(viewport[0],viewport[1],3,1);var footer=l.footer();
            var rows=new java.util.ArrayList<AirAlarmPaintLayout.Bounds>();
            for(int i=0;i<com.juicyslew.moonstation14.ms14.atmos.piping.alarm.AirAlarmMode.values().length;i++)rows.add(l.alarmMode(i));
            rows.add(l.autoMode());rows.add(new AirAlarmPaintLayout.Bounds(8,l.alarmSeverityY(),l.width()-16,10));rows.add(l.alarmStats());for(int i=0;i<9;i++)rows.add(l.gasReading(i));
            for(int i=0;i<rows.size();i++){var row=rows.get(i);assertTrue(row.x()>=0&&row.x()+row.width()<=l.width());assertTrue(row.y()>=0&&row.y()+row.height()<=footer.y(),"control anchor must fit before footer: "+row);for(int j=0;j<i;j++)assertFalse(row.overlaps(rows.get(j)),"control anchors must not overlap: "+row+" / "+rows.get(j));}
        }
    }
    @Test void alarmReadingsFitAndPaintCachedVacuumAndUnavailableWithoutInventingValues(){
        var layout=AirAlarmPaintLayout.forContent(320,240,3,1);for(int i=0;i<9;i++){var gas=layout.gasReading(i);assertTrue(gas.y()+gas.height()<=layout.footer().y());for(int j=0;j<i;j++)assertFalse(gas.overlaps(layout.gasReading(j)));}
        var controls=AirAlarmPaintLayout.forContent(320,240,1,1);var controlFooter=controls.footer();var deviceRows=List.of(new AirAlarmPaintLayout.Bounds(8,controls.deviceSummaryY(),controls.width()-16,10),controls.enabled(),controls.mode(),controls.numericLabel(),controls.numeric());for(int i=0;i<deviceRows.size();i++){var row=deviceRows.get(i);assertTrue(row.y()+row.height()<=controlFooter.y());for(int j=0;j<i;j++)assertFalse(row.overlaps(deviceRows.get(j)));}
        var id=java.util.UUID.randomUUID();var device=new com.juicyslew.moonstation14.ms14.atmos.piping.alarm.ui.AirAlarmControlSnapshot.Device(id,com.juicyslew.moonstation14.ms14.atmos.piping.VentilationDeviceConfig.Kind.VENT,new net.minecraft.core.BlockPos(1,2,3),true,true,false,0,0,1);
        var zero=java.util.Collections.nCopies(com.juicyslew.moonstation14.ms14.atmos.core.GasType.values().length,0.0);
        var cached=new com.juicyslew.moonstation14.ms14.atmos.piping.alarm.ui.AirAlarmControlSnapshot.MonitorReading(id,"addr",false,true,true,0,2.7,0,zero);
        var valid=new com.juicyslew.moonstation14.ms14.atmos.piping.alarm.ui.AirAlarmControlSnapshot(0,java.util.UUID.randomUUID(),0,true,true,com.juicyslew.moonstation14.ms14.atmos.monitor.AtmosAlarmSeverity.NORMAL,com.juicyslew.moonstation14.ms14.atmos.piping.alarm.AirAlarmMode.FILTERING,true,1,java.util.List.of(device),java.util.List.of(cached));
        var paint=new WidthPaint();int originX=17,originY=23;AirAlarmPaint.paint(paint,new AirAlarmPaint.View(valid,originX,originY,320,240,0,0,0,0,3,"","","",false,false));
        assertTrue(paint.hasKey("ui.moonstation14.air_alarm.cached_reading"));assertTrue(paint.summaryHasStatus("ui.moonstation14.air_alarm.summary_cached"));
        var stats=paint.components.stream().map(c->contents(c,"ui.moonstation14.air_alarm.reading_stats")).filter(java.util.Objects::nonNull).findFirst().orElseThrow();assertEquals(List.of("0.00","2.70","0.00"),java.util.Arrays.stream(stats.getArgs()).map(String::valueOf).toList());
        var gasRows=paint.components.stream().map(c->contents(c,"ui.moonstation14.air_alarm.gas_reading")).filter(java.util.Objects::nonNull).toList();assertEquals(9,gasRows.size(),"all nine gas rows must be painted");for(var row:gasRows){assertEquals("0.00",row.getArgs()[1]);assertEquals("0.00",row.getArgs()[2]);}
        for(int i=0;i<9;i++){var b=layout.gasReading(i);assertTrue(paint.clips.contains(new WidthPaint.Clip(originX+b.x(),originY+b.y(),originX+b.x()+b.width(),originY+b.y()+b.height())),"gas row must use its own bounded clip");}
        var statBounds=layout.alarmStats();assertTrue(paint.clips.contains(new WidthPaint.Clip(originX+statBounds.x(),originY+statBounds.y(),originX+statBounds.x()+statBounds.width(),originY+statBounds.y()+statBounds.height())));
        var current=new com.juicyslew.moonstation14.ms14.atmos.piping.alarm.ui.AirAlarmControlSnapshot.MonitorReading(id,"addr",true,true,false,101.3,293.15,2,java.util.Collections.nCopies(zero.size(),0.0));
        var currentSnapshot=new com.juicyslew.moonstation14.ms14.atmos.piping.alarm.ui.AirAlarmControlSnapshot(0,valid.session(),1,true,true,valid.aggregateSeverity(),valid.currentMode(),valid.autoMode(),2,List.of(device),List.of(current));paint.components.clear();paint.textYs.clear();paint.clips.clear();AirAlarmPaint.paint(paint,new AirAlarmPaint.View(currentSnapshot,originX,originY,320,240,0,0,0,0,3,"","","",false,false));
        assertTrue(paint.summaryHasStatus("ui.moonstation14.air_alarm.summary_current"));var currentStats=paint.components.stream().map(c->contents(c,"ui.moonstation14.air_alarm.reading_stats")).filter(java.util.Objects::nonNull).findFirst().orElseThrow();assertEquals("101.30",currentStats.getArgs()[0]);assertEquals("293.15",currentStats.getArgs()[1]);assertEquals("2.00",currentStats.getArgs()[2]);
        var unavailable=new com.juicyslew.moonstation14.ms14.atmos.piping.alarm.ui.AirAlarmControlSnapshot.MonitorReading(id,"addr",false,false,false,Double.NaN,Double.NaN,Double.NaN,java.util.Collections.nCopies(zero.size(),Double.NaN));
        var unknown=new com.juicyslew.moonstation14.ms14.atmos.piping.alarm.ui.AirAlarmControlSnapshot(0,valid.session(),1,true,true,valid.aggregateSeverity(),valid.currentMode(),valid.autoMode(),2,List.of(device),List.of(unavailable));
        paint.components.clear();paint.clips.clear();AirAlarmPaint.paint(paint,new AirAlarmPaint.View(unknown,originX,originY,320,240,0,0,0,0,3,"","","",false,false));
        assertTrue(paint.hasKey("ui.moonstation14.air_alarm.reading_unavailable"));assertTrue(paint.summaryHasStatus("ui.moonstation14.air_alarm.summary_unavailable"));
        assertFalse(paint.hasKey("ui.moonstation14.air_alarm.reading_stats"));assertFalse(paint.hasKey("ui.moonstation14.air_alarm.gas_reading"));
        assertFalse(paint.components.stream().anyMatch(c->c.getString().contains("NaN")),"unknown monitor/gas values must not be rendered");
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
        record Clip(int left,int top,int right,int bottom){}
        final java.util.List<String> lines=new java.util.ArrayList<>();final java.util.List<Integer> textYs=new java.util.ArrayList<>();final java.util.List<Component> components=new java.util.ArrayList<>();final java.util.List<Clip> clips=new java.util.ArrayList<>();
        boolean hasKey(String key){return components.stream().anyMatch(c->contents(c,key)!=null);}
        boolean summaryHasStatus(String status){var summary=components.stream().map(c->contents(c,"ui.moonstation14.air_alarm.summary")).filter(java.util.Objects::nonNull).findFirst().orElse(null);return summary!=null&&java.util.Arrays.stream(summary.getArgs()).filter(Component.class::isInstance).map(Component.class::cast).anyMatch(c->contents(c,status)!=null);}
        public int width(String s){return s.length();}public int width(net.minecraft.network.chat.Component c){return width(c.getString());}public int lineHeight(){return 9;}public String plainSubstrByWidth(String s,int w){return s.substring(0,Math.min(w,s.length()));}
        public void fill(int a,int b,int c,int d,int e){}public void text(net.minecraft.network.chat.Component c,int x,int y,int color,boolean shadow){lines.add(c.getString());textYs.add(y);components.add(c.copy());}
        public void push(){}public void pop(){}public void translate(float x,float y,float z){}public void scale(float x,float y,float z){}
        public void clip(int x,int y,int r,int b){clips.add(new Clip(x,y,r,b));}public void unclip(){}
        public void sprite(net.minecraft.resources.ResourceLocation t,int x,int y,int w,int h,int u,int v,int sw,int sh,int tw,int th,int tint){}
    }
    private static TranslatableContents contents(Component c,String key){return c.getContents() instanceof TranslatableContents t&&t.getKey().equals(key)?t:null;}
}
