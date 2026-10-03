package com.juicyslew.moonstation14.ms14.hands.client;

import org.junit.jupiter.api.Test;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import com.juicyslew.moonstation14.ms14.storage.grid.GridGeometry;
import com.juicyslew.moonstation14.ms14.storage.grid.ItemFootprints;
import static org.junit.jupiter.api.Assertions.*;

class GridEntrySpriteRendererTest {
    @Test void opaquePixelFitMatchesEveryCardinalAndWritesInspectionSheet() throws Exception {
        var stream = GridEntrySpriteRendererTest.class.getResourceAsStream(
                "/assets/moonstation14/textures/item/pickaxe.png");
        assertNotNull(stream, "pickaxe PNG must be present on the test classpath");
        var image = ImageIO.read(stream);
        assertNotNull(image); assertEquals(32, image.getWidth()); assertEquals(32, image.getHeight());
        double c=Math.cos(Math.toRadians(-45)), s=Math.sin(Math.toRadians(-45));
        double minX=Double.POSITIVE_INFINITY,minY=minX,maxX=Double.NEGATIVE_INFINITY,maxY=maxX;
        for(int y=0;y<32;y++) for(int x=0;x<32;x++) if((image.getRGB(x,y)>>>24)!=0) {
            for(double px:new double[]{x,x+1}) for(double py:new double[]{y,y+1}) {
                double rx=c*(px-16)-s*(py-16), ry=s*(px-16)+c*(py-16);
                minX=Math.min(minX,rx);maxX=Math.max(maxX,rx);minY=Math.min(minY,ry);maxY=Math.max(maxY,ry);
            }
        }
        assertEquals(-17/Math.sqrt(2),minX,1e-9); assertEquals(17/Math.sqrt(2),maxX,1e-9);
        assertEquals(-15/Math.sqrt(2),minY,1e-9); assertEquals(22/Math.sqrt(2),maxY,1e-9);
        var sheet=new BufferedImage(960,400,BufferedImage.TYPE_INT_ARGB); var g=sheet.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.scale(4,4);
        var footprint=ItemFootprints.forId(net.minecraft.resources.ResourceLocation.parse("moonstation14:pickaxe"));
        for(int panel=0;panel<8;panel++) {
            int col=panel%4,row=panel/4, ox=col*60, oy=row*50; boolean after=panel>=4; int cardinal=(col*90);
            g.setColor(Color.WHITE);g.setFont(new Font(Font.SANS_SERIF,Font.PLAIN,3));g.drawString((after?"after ":"before ")+cardinal,ox+2,oy+8);
            int gx=ox+15,gy=oy+12;
            var rotation=GridGeometry.Rotation.values()[col];
            for(int yy=0;yy<3;yy++) for(int xx=0;xx<3;xx++) {
                boolean occupied=footprint.cells(rotation).contains(new GridGeometry.Cell(xx,yy));
                g.setColor(occupied?new Color(105,125,145):new Color(35,35,35));g.fillRect(gx+xx*10,gy+yy*10,10,10);
                g.setColor(Color.LIGHT_GRAY);g.drawRect(gx+xx*10,gy+yy*10,9,9);
            }
            AffineTransform old=g.getTransform();
            double canvas=after?32:38, centerX=gx+15,centerY=gy+15;
            g.translate(centerX,centerY);
            if(after) {
                var fit=StorageSpriteGeometry.fit(cardinal);
                assertEquals(0,(fit.minX()+fit.maxX())/2,1e-8);
                assertEquals(0,(fit.minY()+fit.maxY())/2,1e-8);
                assertTrue(fit.minX()>=-14-1e-8&&fit.minY()>=-14-1e-8&&fit.maxX()<=14+1e-8&&fit.maxY()<=14+1e-8);
                assertEquals(28,Math.max(fit.maxX()-fit.minX(),fit.maxY()-fit.minY()),1e-8);
                if(cardinal==0) { assertEquals(1.07021566882288,fit.scale(),1e-12); assertEquals(0,fit.offsetX(),1e-10); assertEquals(-2.64864864864865,fit.offsetY(),1e-10); }
                g.translate(fit.offsetX(),fit.offsetY());g.scale(fit.scale(),fit.scale());g.rotate(Math.toRadians(-45+cardinal));
                double cr=Math.cos(Math.toRadians(cardinal)),sr=Math.sin(Math.toRadians(cardinal));
                for(int py=0;py<32;py++) for(int px=0;px<32;px++) if((image.getRGB(px,py)>>>24)!=0)
                    for(double ax:new double[]{px,px+1}) for(double ay:new double[]{py,py+1}) {
                        double rx=c*(ax-16)-s*(ay-16),ry=s*(ax-16)+c*(ay-16);
                        double tx=15+fit.offsetX()+fit.scale()*(cr*rx-sr*ry),ty=15+fit.offsetY()+fit.scale()*(sr*rx+cr*ry);
                        assertTrue(tx>=1-1e-8&&tx<=29+1e-8&&ty>=1-1e-8&&ty<=29+1e-8,"transformed alpha corner outside placement");
                    }
            } else { g.scale(canvas/32,canvas/32);g.rotate(Math.toRadians(-45+cardinal)); }
            g.drawImage(image,-16,-16,null);g.setTransform(old);
        }
        g.dispose();var output=Path.of("build/reports/storage-sprite-fit/pickaxe.png");
        Files.createDirectories(output.getParent());ImageIO.write(sheet,"png",output.toFile());
        assertTrue(Files.size(output)>0);
    }
}
