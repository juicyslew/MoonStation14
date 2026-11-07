package com.juicyslew.moonstation14.ms14.combat;

import com.juicyslew.moonstation14.ms14.ui.client.paint.OrderedPaint;
import net.minecraft.network.chat.Component;

/** Ordered, source-shaped painter for non-revolver gun ammo counters. */
public final class GunAmmoCounterPainter {
    private static final int YELLOW_A = 0xffd7df60, YELLOW_B = 0xffb68f0e;
    private GunAmmoCounterPainter() { }

    public static void paint(OrderedPaint paint, GunAmmoCounterProjection.Result result,
                             int x, int y, int width, int height) {
        if (paint == null || result == null || !result.visible() || width <= 0 || height <= 0
                || result.kind() == null || result.kind() == GunAmmoCounterProjection.Kind.REVOLVER) return;
        int right = x + width, bottom = y + height;
        if (result.kind() == GunAmmoCounterProjection.Kind.CELL_CHARGE) {
            // Cell charge is hand-detail metadata, not a large ammo counter. Keep
            // its natural bitmap font size and subordinate palette like hand text.
            float detailScale=com.juicyslew.moonstation14.ms14.hands.client.HandInfoLayout.DETAIL_TEXT_SCALE;
            int shownWidth=Math.round(paint.width(result.text())*detailScale);
            int shownHeight=Math.round(paint.lineHeight()*detailScale);
            if(shownWidth<=width&&shownHeight<=height) {
                paint.push();
                paint.translate(right-shownWidth,y,0);
                paint.scale(detailScale,detailScale,1);
                paint.text(Component.literal(result.text()),0,0,
                        com.juicyslew.moonstation14.ms14.hands.client.HandInfoLayout.DETAIL_MUTED_COLOR,true);
                paint.pop();
            }
            return;
        }
        if (result.kind() == GunAmmoCounterProjection.Kind.BATTERY) {
            int count = Math.max(0, result.count());
            int capacity = Math.max(0, result.capacity());
            int textWidth=paint.width(result.text()), available=Math.max(0,width-textWidth-4);
            int columns=Math.min(capacity, packing(available,10,14)), rows=Math.min(2,Math.min(height/14,columns==0?0:(capacity+columns-1)/columns));
            int done=0;
            for(int row=0;row<rows;row++){int n=Math.min(columns,capacity-done);if(row+1<rows&&capacity-done-n<3&&capacity-done-n>0)n-=3-(capacity-done-n);
              for(int col=0;col<n;col++) {int bx=right-textWidth-4-10-(columns-1-col)*14,by=bottom-10-row*14;
                if(bx<x||by<y||bx+10>right-textWidth-4||by+10>bottom)continue;
                int absolute=capacity-done-n+col; paint.fill(bx,by,bx+10,by+10,absolute>=capacity-count?0xffe00000:0xff000000);
              } done+=n; }
            if(textWidth<=width&&paint.lineHeight()<=height)paint.text(Component.literal(result.text()),right-textWidth,y,0xffffffff,true);
            return;
        }
        if (result.kind() == GunAmmoCounterProjection.Kind.MAGAZINE) {
            int glyphWidth=5, gap=2; String label=result.noMagazine()?"No magazine":Math.max(0,result.count())+"/"+Math.max(0,result.capacity());
            int tw=paint.width(label), th=paint.lineHeight();
            int availableLabel=Math.max(0,width-(result.chambers().isEmpty()?0:glyphWidth+gap));
            String shown=paint.plainSubstrByWidth(label,availableLabel);
            tw=paint.width(shown);
            int labelX=right-tw;
            if (!result.chambers().isEmpty() && width>=glyphWidth && height>=12) {
                var chamber=result.chambers().get(0);
                int tint=chamber==GunAmmoCounterProjection.Chamber.LIVE?YELLOW_A:0xff222222;
                paint.sprite(RevolverChamberPresentation.textureId("ss14/interface/itemstatus/bullets/normal"),
                        x,y,5,12,0,0,5,12,5,12,tint);
            }
            int cap=Math.max(0,result.capacity()), count=Math.max(0,Math.min(result.count(),cap));
            int reserve=tw+gap+(result.chambers().isEmpty()?0:glyphWidth+gap);
            int bulletWidth=Math.max(0,width-reserve);
            if(!result.noMagazine()) drawBullets(paint,x,y,bulletWidth,height,cap,count,5,3,0);
            if(tw>0&&th>0&&tw<=width&&th<=height)paint.text(Component.literal(shown),labelX,y,0xffffffff,true);
            return;
        }
        int cap=Math.max(0,result.capacity()), count=Math.max(0,Math.min(result.count(),cap));
        int sw=cap>50?2:5, step=cap>50?2:cap<=15?6:3;
        drawBullets(paint,x,y,width,height,cap,count,sw,step,7);
    }
    private static int packing(int width,int itemWidth,int step){return Math.max(0,(width-itemWidth+step)/step);}
    private static void drawBullets(OrderedPaint paint,int x,int y,int width,int height,int cap,int count,int sw,int step,int minRow){
        int right=x+width,bottom=y+height;
        int columns=packing(width,sw,step), rowLimit=Math.min(2,Math.max(0,(height+2)/14));
        int slots=Math.min(cap,columns*rowLimit); if(slots==0)return; int rowCount=Math.min(rowLimit,(slots+columns-1)/columns);
        String texture=sw==2?"ss14/interface/itemstatus/bullets/tiny":"ss14/interface/itemstatus/bullets/normal";
        int bulletsDone=0;
        for(int row=0;row<rowCount;row++){int n=Math.min(columns,slots-bulletsDone);int next=slots-bulletsDone-n;if(row+1<rowCount&&next>0&&next<minRow&&minRow<columns)n-=minRow-next;
            int rowWidth=n==0?0:(n-1)*step+sw;
            for(int col=0;col<n;col++){int absolute=cap-bulletsDone-n+col;int bx=right-rowWidth+col*step,by=bottom-12-row*14;
                if(bx<x||by<y||bx+sw>right||by+12>bottom)continue;
                boolean loaded=absolute>=0&&absolute>=cap-count;int tint=loaded?(absolute%2==0?YELLOW_A:YELLOW_B):(absolute%2==0?0xff000000:0xff222222);
                paint.sprite(RevolverChamberPresentation.textureId(texture),bx,by,sw,12,0,0,sw,12,sw,12,tint);
            } bulletsDone+=n;
        }
    }
}
