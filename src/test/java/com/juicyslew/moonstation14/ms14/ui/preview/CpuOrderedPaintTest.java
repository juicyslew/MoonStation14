package com.juicyslew.moonstation14.ms14.ui.preview;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class CpuOrderedPaintTest {
    private static Path resources() {
        Path p=Path.of("").toAbsolutePath();
        while(p!=null){Path jar=p.resolve("build/moddev/artifacts/neoforge-21.1.224-client-extra-aka-minecraft-resources.jar");if(Files.isRegularFile(jar))return jar;p=p.getParent();}
        return Path.of("build/moddev/artifacts/neoforge-21.1.224-client-extra-aka-minecraft-resources.jar");
    }
    private static CpuOrderedPaint paint(){try{return new CpuOrderedPaint(80,60,MinecraftBitmapFont.load(resources()));}catch(Exception e){throw new AssertionError(e);}}
    @Test void transformOrderPopAndAbsoluteNestedClipAreStable(){
        var p=paint();p.fill(0,0,80,60,0xff000000);p.push();p.translate(10,10,0);p.scale(.5f,.5f,1);p.fill(0,0,20,20,0xffff0000);
        assertEquals(0xffff0000,p.image().getRGB(10,10));assertEquals(0xffff0000,p.image().getRGB(19,19));assertEquals(0xff000000,p.image().getRGB(20,19));
        p.clip(12,12,14,14);p.fill(0,0,40,40,0xff00ff00);p.unclip();assertEquals(0xff00ff00,p.image().getRGB(12,12));assertEquals(0xffff0000,p.image().getRGB(11,12));p.pop();
        p.fill(0,0,1,1,0xff0000ff);assertEquals(0xff0000ff,p.image().getRGB(0,0));
    }
    @Test void realBitmapFontMetricsTextAndRepeatedPixelsMatch(){
        var a=paint();var b=paint();assertEquals(a.width("ChemMaster 4000"),a.width(Component.literal("ChemMaster 4000")));
        a.text(Component.literal("ChemMaster 4000"),3,4,0xffe5ecee,true);b.text(Component.literal("ChemMaster 4000"),3,4,0xffe5ecee,true);
        assertArrayEquals(a.image().getRGB(0,0,80,60,null,0,80),b.image().getRGB(0,0,80,60,null,0,80));
        assertEquals(a.width("“12.34u…”"),a.width(Component.literal("“12.34u…”")));
    }
    @Test void transformedRasterKeepsProductionLogicalMetricsAndStylesRejectForBothMetricsAndRaster(){
        var p=paint();int width=p.width("ABC"),height=p.lineHeight();p.push();p.scale(.5f,.5f,1);
        assertEquals(width,p.width("ABC"));assertEquals(height,p.lineHeight());assertEquals("ABC",p.plainSubstrByWidth("ABC",width));
        p.text(Component.literal("ABC"),2,2,0xffffffff,true);p.pop();
        var styled=Component.literal("label").withStyle(net.minecraft.ChatFormatting.BOLD);
        assertThrows(UnsupportedOperationException.class,()->p.width(styled));assertThrows(UnsupportedOperationException.class,()->p.text(styled,0,0,-1,false));
    }
    private static MinecraftBitmapFont font(){try{return MinecraftBitmapFont.load(resources());}catch(Exception e){throw new AssertionError(e);}}
    @Test void spriteUvTintAndAlphaBlendArePixelDeterministic(){
        var p=paint();p.fill(0,0,8,8,0xff204060);var id=ResourceLocation.fromNamespaceAndPath("moonstation14","textures/gui/airlock/wire_1.png");
        p.sprite(id,1,1,4,4,0,0,16,50,16,50,0x80ffffff);
        var q=paint();q.fill(0,0,8,8,0xff204060);q.sprite(id,1,1,4,4,0,0,16,50,16,50,0x80ffffff);
        assertArrayEquals(p.image().getRGB(0,0,80,60,null,0,80),q.image().getRGB(0,0,80,60,null,0,80));
        assertEquals(0xff204060,p.image().getRGB(0,0));assertNotEquals(0xff204060,p.image().getRGB(2,2));
    }
    @Test void clipAndTransformUnderflowFailClearly(){var p=paint();assertThrows(IllegalStateException.class,p::unclip);assertThrows(IllegalStateException.class,p::pop);}
    @Test void halfScaleAndGuiScalesOneThroughThreeKeepWholeGlyphCellsAndRespectClip(){
        for(float scale:new float[]{.5f,1,2,3}){var p=paint();p.push();p.scale(scale,scale,1);p.clip(0,0,30,30);p.text(Component.literal("W"),2,2,0xffffffff,false);p.unclip();p.pop();
            int count=0;for(int y=0;y<60;y++)for(int x=0;x<80;x++)if(p.image().getRGB(x,y)!=0)count++;
            assertTrue(count>0,"glyph raster missing at scale "+scale);for(int y=30;y<60;y++)for(int x=0;x<80;x++)assertEquals(0,p.image().getRGB(x,y),"clip leaked at scale "+scale);
        }
    }
    @Test void bitmapTopUsesMinecraftSheetGlyphInfoGetTopDefault(){
        var font=font();int cp='W',ascent=font.ascent(cp);
        var sheet=new com.mojang.blaze3d.font.SheetGlyphInfo(){
            public int getPixelWidth(){return font.cellWidth(cp);}public int getPixelHeight(){return font.cellHeight(cp);}
            public void upload(int x,int y){}public boolean isColored(){return false;}public float getOversample(){return 1;}
            public float getBearingTop(){return ascent;}
        };
        assertEquals(7f-ascent,sheet.getTop()); // Minecraft 1.21.1 SheetGlyphInfo default, exercised directly.
        var cell=new BufferedImage(font.cellWidth(cp),font.cellHeight(cp),BufferedImage.TYPE_INT_ARGB);font.drawCell(cell,0,0,cp,0xffffffff,1,1);
        int inkTop=0;while(inkTop<cell.getHeight()){boolean ink=false;for(int x=0;x<cell.getWidth();x++)ink|=(cell.getRGB(x,inkTop)>>>24)!=0;if(ink)break;inkTop++;}
        var p=new CpuOrderedPaint(80,60,font);p.text(Component.literal("W"),2,8,0xffffffff,false);
        int actualTop=60;for(int y=0;y<60;y++)for(int x=0;x<80;x++)if((p.image().getRGB(x,y)>>>24)!=0)actualTop=Math.min(actualTop,y);
        assertEquals(8+(int)sheet.getTop()+inkTop,actualTop);
    }
    @Test void absoluteScissorUsesGuiScaleIndependentlyOfNestedPose(){
        for(int scale=1;scale<=3;scale++){
            var p=new CpuOrderedPaint(40*scale,30*scale,font(),scale);p.fill(0,0,40*scale,30*scale,0xff102030);
            p.push();p.scale(scale,scale,1);p.clip(4,3,10,9);p.push();p.scale(.5f,.5f,1);
            p.fill(0,0,40,30,0xffaa0000);
            var wire=ResourceLocation.fromNamespaceAndPath("moonstation14","textures/gui/airlock/wire_1.png");
            p.sprite(wire,0,0,12,12,0,0,16,50,16,50,0xffffffff);
            p.text(Component.literal("W"),0,0,0xffffffff,false);p.pop();p.unclip();p.pop();
            for(int y=0;y<30*scale;y++)for(int x=0;x<40*scale;x++){
                boolean clipped=x>=4*scale&&x<10*scale&&y>=3*scale&&y<9*scale;
                if(!clipped)assertEquals(0xff102030,p.image().getRGB(x,y),"absolute GUI clip leaked scale="+scale+" at "+x+","+y);
            }
            assertNotEquals(0xff102030,p.image().getRGB(5*scale,5*scale),"clip should allow device pixels within scaled GUI bounds");
        }
    }
}
