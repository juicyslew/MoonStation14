package com.juicyslew.moonstation14.ms14.chemistry.chemmaster.client;

import com.juicyslew.moonstation14.ms14.ui.client.paint.OrderedPaint;
import net.minecraft.resources.ResourceLocation;

/** Verified opaque source rectangles for the existing SS14-derived pill sprites. */
final class ChemMasterPillArt {
    record Crop(int u,int v,int width,int height) { }
    private static final Crop WIDE=new Crop(10,15,11,5);
    private static final Crop ROUND=new Crop(12,14,7,7);
    private static final Crop LAST_WIDE=new Crop(11,15,11,5);
    private ChemMasterPillArt() { }

    static Crop crop(int type){
        if(type<0||type>=20)throw new IllegalArgumentException("Unknown pill type: "+type);
        return type<6?WIDE:type<17?ROUND:LAST_WIDE;
    }
    static void paintUniform(OrderedPaint p,int type,int x,int y,int maxWidth,int maxHeight,int tint){
        if(maxWidth<=0||maxHeight<=0)return;
        var crop=crop(type);
        // Every pill shares one scale based on the union bounds of all 20 source-art footprints.
        double scale=Math.min((double)maxWidth/11.0,(double)maxHeight/7.0);
        int width=Math.max(1,(int)Math.round(crop.width()*scale)),height=Math.max(1,(int)Math.round(crop.height()*scale));
        var texture=ResourceLocation.fromNamespaceAndPath("moonstation14","textures/item/pill/pill"+(type+1)+".png");
        p.sprite(texture,x+(maxWidth-width)/2,y+(maxHeight-height)/2,width,height,crop.u(),crop.v(),crop.width(),crop.height(),32,32,tint);
    }
}
