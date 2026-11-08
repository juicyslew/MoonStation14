package com.juicyslew.moonstation14.ms14.chemistry.chemmaster.client;

import com.juicyslew.moonstation14.ms14.chemistry.chemmaster.ChemMasterControlRequest;
import com.juicyslew.moonstation14.ms14.chemistry.chemmaster.ChemMasterWindowState;
import com.juicyslew.moonstation14.ms14.ui.client.paint.OrderedPaint;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ChemMasterOutputPaintTest {
    private static final class Paint implements OrderedPaint {
        final List<ResourceLocation> sprites=new ArrayList<>();final List<String> text=new ArrayList<>();final List<int[]> uv=new ArrayList<>();
        public void fill(int l,int t,int r,int b,int c){} public void text(Component s,int x,int y,int c,boolean sh){text.add(s.getString());}
        public int width(Component c){return width(c.getString());} public int width(String s){return s.length();} public int lineHeight(){return 8;}
        public String plainSubstrByWidth(String s,int w){return s.substring(0,Math.min(s.length(),w));}
        public void push(){} public void pop(){} public void translate(float x,float y,float z){} public void scale(float x,float y,float z){}
        public void clip(int l,int t,int r,int b){} public void unclip(){}
        public void sprite(ResourceLocation t,int x,int y,int w,int h,int u,int v,int sw,int sh,int tw,int th,int tint){sprites.add(t);uv.add(new int[]{u,v,sw,sh,w,h});}
    }
    private static ChemMasterWindowState state(){
        var pill=new ChemMasterWindowState.PillDescriptor(0,0,0,Optional.of("label"),List.of());
        var output=new ChemMasterWindowState.OutputSlot(2,ChemMasterWindowState.OutputKind.PILL_CANISTER,Optional.of(Component.literal("Canister")),0,0,List.of(),5,2,9,Optional.empty(),List.of(pill));
        return state(output);
    }
    private static ChemMasterWindowState state(ChemMasterWindowState.OutputSlot output){
        return new ChemMasterWindowState(1,1,1,1,-1,Optional.empty(),UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),1,
                ResourceLocation.withDefaultNamespace("overworld"),0,64,0,true,Optional.empty(),Optional.empty(),0,0,List.of(),List.of(),
                ChemMasterControlRequest.Mode.TRANSFER,ChemMasterControlRequest.Sort.OLDEST_FIRST,output,ChemMasterControlRequest.DrawSource.BUFFER,0,20,false);
    }
    @Test void sharedPainterUsesTwentyRealPillTexturesAndAuthoritativeCanisterContents(){
        var state=state();var form=new ChemMasterOutputForm();form.authoritativeState(20,9,20,0);
        var layout=ChemMasterLayout.geometry(640,360);var geometry=ChemMasterOutputLayout.geometry(new ChemMasterOutputLayout.Rect(layout.outputPlaceholder().x(),layout.outputPlaceholder().y(),layout.outputPlaceholder().width(),layout.outputPlaceholder().height()),9);var p=new Paint();
        var snapshot=new ChemMasterTextFieldPaint.Snapshot("",0,0,0,false,true,false,0);
        var presentation=new ChemMasterPaint.OutputPresentation(form.state(20,9,20,0),geometry,snapshot,snapshot,snapshot,snapshot,0,ChemMasterControlRequest.DrawSource.BUFFER);
        var view=new ChemMasterPaint.View(state,"No container",List.of(),List.of(),layout,0,0,640,360,-1,-1,0,0,false,true,
                Component.literal("Container"),null,Optional.of(presentation));
        ChemMasterPaint.paint(p,view);
        assertEquals(21,p.sprites.size());
        for(int i=1;i<=20;i++)assertTrue(p.sprites.contains(ResourceLocation.fromNamespaceAndPath("moonstation14","textures/item/pill/pill"+i+".png")));
        assertTrue(p.text.stream().anyMatch(s->s.contains("1 pills")&&s.contains("9 free")));
        assertTrue(p.text.contains("label"));
        assertArrayEquals(new int[]{10,15,11,5},Arrays.copyOf(p.uv.get(0),4));
        double sharedPaletteScale=(double)p.uv.get(0)[4]/11;
        for(int i=0;i<20;i++){
            int[] crop=i<6?new int[]{10,15,11,5}:i<17?new int[]{12,14,7,7}:new int[]{11,15,11,5};
            assertArrayEquals(crop,Arrays.copyOf(p.uv.get(i),4),"visible source-art crop for pill "+(i+1));
            var cell=geometry.pills().get(i);int[] sprite=p.uv.get(i);
            assertEquals(geometry.pills().get(0).width(),cell.width(),"uniform palette button width");
            assertEquals(geometry.pills().get(0).height(),cell.height(),"uniform palette button height");
            assertEquals(sharedPaletteScale,(double)sprite[4]/crop[2],0.1,"one sprite scale for every palette type");
            assertEquals((double)crop[2]/crop[3],(double)sprite[4]/sprite[5],0.15,"preserve each pill's source-art aspect");
        }
        assertTrue(p.uv.get(20)[4]>10,"5x2 contents pill icon uses available cell width, not the old fixed 10px cap");
    }
    @Test void canisterContentsUseAllTwentyTypeCropsAtRowSizedDimensions(){
        var pills=new ArrayList<ChemMasterWindowState.PillDescriptor>();
        for(int i=0;i<20;i++)pills.add(new ChemMasterWindowState.PillDescriptor(i%5,i/5,i,Optional.of("Type "+(i+1)),List.of()));
        var output=new ChemMasterWindowState.OutputSlot(2,ChemMasterWindowState.OutputKind.PILL_CANISTER,Optional.of(Component.literal("Canister")),0,0,List.of(),5,4,0,Optional.empty(),pills);
        var state=state(output);var layout=ChemMasterLayout.geometry(640,360);
        var geometry=ChemMasterOutputLayout.geometry(new ChemMasterOutputLayout.Rect(layout.outputPlaceholder().x(),layout.outputPlaceholder().y(),layout.outputPlaceholder().width(),layout.outputPlaceholder().height()),9);
        var snapshot=new ChemMasterTextFieldPaint.Snapshot("",0,0,0,false,true,false,0);var paint=new Paint();
        var presentation=new ChemMasterPaint.OutputPresentation(new ChemMasterOutputForm().state(20,0,20,0),geometry,snapshot,snapshot,snapshot,snapshot,0,ChemMasterControlRequest.DrawSource.BUFFER);
        ChemMasterPaint.paint(paint,new ChemMasterPaint.View(state,"No container",List.of(),List.of(),layout,0,0,640,360,-1,-1,0,0,false,true,
                Component.literal("Container"),null,Optional.of(presentation)));
        assertEquals(40,paint.uv.size());
        for(int i=0;i<20;i++){
            int[] crop=i<6?new int[]{10,15,11,5}:i<17?new int[]{12,14,7,7}:new int[]{11,15,11,5};
            assertArrayEquals(crop,Arrays.copyOf(paint.uv.get(20+i),4),"canister art source for descriptor type "+i);
            assertTrue(paint.uv.get(20+i)[4]>0&&paint.uv.get(20+i)[5]>0);
        }
        assertTrue(paint.uv.get(20)[4]>10,"wide canister pill scales from intrinsic aspect and row height");
        double sharedContentsScale=(double)paint.uv.get(20)[4]/11;
        for(int i=0;i<20;i++){
            int[] crop=i<6?new int[]{10,15,11,5}:i<17?new int[]{12,14,7,7}:new int[]{11,15,11,5};
            assertEquals(sharedContentsScale,(double)paint.uv.get(20+i)[4]/crop[2],0.1,"one shared contents scale across types");
            assertEquals((double)crop[2]/crop[3],(double)paint.uv.get(20+i)[4]/paint.uv.get(20+i)[5],0.15);
        }
    }
}
