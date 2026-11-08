package com.juicyslew.moonstation14.ms14.combat;

import com.juicyslew.moonstation14.ms14.ui.client.paint.OrderedPaint;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class GunAmmoCounterPainterTest {
    private static final class Recorder implements OrderedPaint {
        record Box(int x,int y,int r,int b,int color){} record Sprite(ResourceLocation id,int x,int y,int w,int h,int tint){} record Label(String s,int x,int y){}
        final List<Box> boxes=new ArrayList<>(); final List<Sprite> sprites=new ArrayList<>(); final List<Label> labels=new ArrayList<>();
        public void fill(int l,int t,int r,int b,int c){boxes.add(new Box(l,t,r,b,c));} public void text(Component s,int x,int y,int c,boolean sh){labels.add(new Label(s.getString(),x,y));}
        public int width(Component s){return width(s.getString());} public int width(String s){return s.length()*6;} public int lineHeight(){return 9;} public String plainSubstrByWidth(String s,int w){return s.substring(0,Math.min(s.length(),Math.max(0,w/6)));}
        public void push(){} public void pop(){} public void translate(float x,float y,float z){} public void scale(float x,float y,float z){} public void clip(int l,int t,int r,int b){} public void unclip(){}
        public void sprite(ResourceLocation id,int x,int y,int w,int h,int u,int v,int sw,int sh,int tw,int th,int tint){sprites.add(new Sprite(id,x,y,w,h,tint));}
    }
    @Test void ballisticCapsUseExpectedSpritesLoadedColorsAndStayBounded(){
        for(int cap:new int[]{10,20,80}) for(int count:new int[]{0,3,10}) {
            if(count>cap)continue;
            var p=new Recorder(); GunAmmoCounterPainter.paint(p,result(GunAmmoCounterProjection.Kind.BALLISTIC,count,cap,"",List.of(),false),0,0,80,40);
            assertEquals(cap,p.sprites.size());
            assertEquals(count,p.sprites.stream().filter(s->s.tint()==0xffd7df60||s.tint()==0xffb68f0e).count());
            assertTrue(p.sprites.stream().allMatch(s->s.x()>=0&&s.y()>=0&&s.x()+s.w()<=80&&s.y()+s.h()<=40));
            var expected=cap>50?"tiny":"normal";
            assertTrue(p.sprites.stream().allMatch(s->s.id().equals(ResourceLocation.fromNamespaceAndPath("moonstation14","textures/ss14/interface/itemstatus/bullets/"+expected+".png"))));
            if(cap==80){assertTrue(p.sprites.stream().allMatch(s->s.w()==2));assertTrue(p.sprites.stream().mapToInt(Recorder.Sprite::y).distinct().count()<=2);}
        }
    }
    private static GunAmmoCounterProjection.Result result(GunAmmoCounterProjection.Kind kind,int count,int cap,String text,List<GunAmmoCounterProjection.Chamber> chamber,boolean noMag){return new GunAmmoCounterProjection.Result(true,count,cap,text,chamber,-1,noMag,kind);}
    @Test void batteryBoxesAndTextAndBoundedBulletRows(){var p=new Recorder();GunAmmoCounterPainter.paint(p,result(GunAmmoCounterProjection.Kind.BATTERY,4,6,"x04",List.of(),false),0,0,60,40);
        assertEquals(6,p.boxes.size());assertEquals(4,p.boxes.stream().filter(b->b.color()==0xffe00000).count());assertEquals(2,p.boxes.stream().filter(b->b.color()==0xff000000).count());assertEquals("x04",p.labels.get(0).s());
        int labelX=p.labels.get(0).x(); assertTrue(p.boxes.stream().allMatch(b->b.r()<=labelX));
        for(int count:new int[]{1,20,80}){p=new Recorder();GunAmmoCounterPainter.paint(p,result(GunAmmoCounterProjection.Kind.BALLISTIC,count,count,"",List.of(),false),0,0,80,40);for(var s:p.sprites){assertTrue(s.x()>=0&&s.y()>=0&&s.x()+s.w()<=80&&s.y()+s.h()<=40);}}
        p=new Recorder();GunAmmoCounterPainter.paint(p,result(GunAmmoCounterProjection.Kind.BALLISTIC,100,100,"",List.of(),false),20,20,4,3);assertTrue(p.sprites.isEmpty());
    }
    @Test void magazineCountAndChamberRemainIndependentEvenWithoutMagazine(){var p=new Recorder();GunAmmoCounterPainter.paint(p,result(GunAmmoCounterProjection.Kind.MAGAZINE,3,10,"3",List.of(GunAmmoCounterProjection.Chamber.LIVE),false),0,0,80,28);assertEquals(11,p.sprites.size());assertEquals("3/10",p.labels.get(0).s());
        p=new Recorder();GunAmmoCounterPainter.paint(p,result(GunAmmoCounterProjection.Kind.MAGAZINE,0,10,"No magazine",List.of(GunAmmoCounterProjection.Chamber.SPENT),true),0,0,20,20);assertEquals(1,p.sprites.size());assertFalse(p.labels.isEmpty());assertTrue(p.labels.get(0).x()>=5);
        p=new Recorder();GunAmmoCounterPainter.paint(p,result(GunAmmoCounterProjection.Kind.MAGAZINE,10,10,"",List.of(GunAmmoCounterProjection.Chamber.LIVE),false),0,0,4,3);assertTrue(p.sprites.isEmpty());assertTrue(p.labels.isEmpty());}
    @Test void ratioLabelAndChamberReserveDoNotOverlapBulletBoundsAtCounterSize(){
        var p=new Recorder(); GunAmmoCounterPainter.paint(p,result(GunAmmoCounterProjection.Kind.MAGAZINE,3,10,"3/10",List.of(),false),0,0,96,13);
        assertEquals("3/10",p.labels.get(0).s()); assertTrue(p.sprites.stream().noneMatch(s->s.x()+s.w()>p.labels.get(0).x()));
        assertTrue(p.sprites.stream().allMatch(s->s.y()>=0&&s.y()+s.h()<=13));
        var chamber=new Recorder(); GunAmmoCounterPainter.paint(chamber,result(GunAmmoCounterProjection.Kind.MAGAZINE,3,10,"3/10",List.of(GunAmmoCounterProjection.Chamber.LIVE),false),0,0,96,13);
        assertEquals(1,chamber.sprites.stream().filter(s->s.id().getPath().contains("normal")).mapToInt(s->s.w()==5&&s.x()==0?1:0).sum());
        assertTrue(chamber.sprites.stream().filter(s->s.x()>0).allMatch(s->s.x()+s.w()<=chamber.labels.get(0).x()));
    }
}
