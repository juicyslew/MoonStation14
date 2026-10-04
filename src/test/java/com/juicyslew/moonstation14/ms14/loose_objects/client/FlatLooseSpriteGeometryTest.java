package com.juicyslew.moonstation14.ms14.loose_objects.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import com.mojang.blaze3d.vertex.PoseStack;
import org.joml.Vector3f;
import com.mojang.serialization.JsonOps;
import com.juicyslew.moonstation14.ms14.loose_objects.LooseObjectProfile;
import static org.junit.jupiter.api.Assertions.*;

/** Alpha-bound/profile geometry and a software diagnostic, not a Minecraft viewport. */
class FlatLooseSpriteGeometryTest {
    private static final String ASSET="/assets/moonstation14/";
    private static final String DATA="/data/moonstation14/moonstation14/loose_object_profile/";
    private record Sprite(String id,int minX,int minY,int maxX,int maxY) { }

    @Test void shippedFlatSpritesFitTheirProfilesAndProduceDiagnosticSheet() throws Exception {
        Sprite[] sprites={new Sprite("bag",5,8,25,26),new Sprite("steel",7,4,22,23),new Sprite("glass",9,6,24,25)};
        BufferedImage sheet=new BufferedImage(900,390,BufferedImage.TYPE_INT_ARGB);Graphics2D g=sheet.createGraphics();
        g.setColor(new Color(35,39,46));g.fillRect(0,0,sheet.getWidth(),sheet.getHeight());
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        for(int i=0;i<sprites.length;i++) {
            Sprite s=sprites[i];BufferedImage image;
            try(InputStream in=getClass().getResourceAsStream(ASSET+"textures/item/"+s.id+".png")){assertNotNull(in,"missing sprite");image=ImageIO.read(in);}
            int minX=image.getWidth(),minY=image.getHeight(),maxX=-1,maxY=-1;
            for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++)if((image.getRGB(x,y)>>>24)>0){minX=Math.min(minX,x);minY=Math.min(minY,y);maxX=Math.max(maxX,x);maxY=Math.max(maxY,y);}
            assertEquals(s.minX,minX,s.id+" minX");assertEquals(s.minY,minY,s.id+" minY");assertEquals(s.maxX,maxX,s.id+" maxX");assertEquals(s.maxY,maxY,s.id+" maxY");
            JsonObject profile=JsonParser.parseString(data(""+s.id+".json")).getAsJsonObject(),v=profile.getAsJsonObject("visual");
            double sx=v.get("sprite_scale_x").getAsDouble(),sy=v.get("sprite_scale_y").getAsDouble(),ox=v.get("sprite_offset_x").getAsDouble(),oy=v.get("sprite_offset_y").getAsDouble();
            double x0=(minX/32.0-.5)*sx+ox,x1=((maxX+1)/32.0-.5)*sx+ox,z0=(.5-minY/32.0)*sy+oy,z1=(.5-(maxY+1)/32.0)*sy+oy;
            double width=x1-x0,depth=z0-z1,physical=profile.get("physical_width").getAsDouble(),height=profile.get("physical_height").getAsDouble();
            assertEquals(0,(x0+x1)/2,.002,s.id+" horizontal center");assertEquals(0,(z0+z1)/2,.002,s.id+" plane center");
            assertEquals(.7,Math.max(width,depth),.002,s.id+" longest opaque dimension");assertTrue(physical+1e-9>=Math.max(width,depth),s.id+" physical width encloses sprite");
            JsonObject complete=JsonParser.parseString(data("generic.json")).getAsJsonObject();
            profile.entrySet().forEach(e->complete.add(e.getKey(),e.getValue()));
            LooseObjectProfile parsed=LooseObjectProfile.CODEC.parse(JsonOps.INSTANCE,complete).getOrThrow(IllegalStateException::new);
            PoseStack pose=new PoseStack();LooseItemRenderer.applyFlatPose(pose,parsed);
            var matrix=pose.last().pose();double minWorldY=Double.POSITIVE_INFINITY,maxWorldY=Double.NEGATIVE_INFINITY,sumX=0,sumZ=0;int vertices=0;
            for(double z:new double[]{-1.0/32,1.0/32})for(double x:new double[]{minX/32.0-.5,(maxX+1)/32.0-.5})for(double y:new double[]{.5-(maxY+1)/32.0,.5-minY/32.0}) {
                Vector3f p=matrix.transformPosition(new Vector3f((float)x,(float)y,(float)z));
                sumX+=p.x;sumZ+=p.z;vertices++;
                minWorldY=Math.min(minWorldY,p.y);maxWorldY=Math.max(maxWorldY,p.y);
            }
            assertEquals(0,sumX/vertices,.002,s.id+" world X center");assertEquals(0,sumZ/vertices,.002,s.id+" world Z center");
            assertEquals(.002,minWorldY,.00001,s.id+" world floor");assertTrue(maxWorldY<=height+1e-9,s.id+" world top within physical height");
            assertEquals(.7,Math.max(width,depth),.002,s.id+" opaque longest dimension");
            assertTrue(physical+1e-9>=Math.max(width,depth),s.id+" physical width encloses sprite");
            int left=20+i*290, center=left+135, centerY=155, pixels=280; Shape oldClip=g.getClip();
            g.setColor(Color.WHITE);g.setFont(new Font(Font.SANS_SERIF,Font.BOLD,18));g.drawString(s.id+" · .7 longest",left,32);
            g.setClip(left,42,270,330);
            int dx=center+(int)Math.round((-.5*sx+ox)*pixels),dy=centerY-(int)Math.round((.5*sy+oy)*pixels);
            int drawWidth=(int)Math.round(sx*pixels),drawHeight=(int)Math.round(sy*pixels);
            g.drawImage(image,dx,dy,drawWidth,drawHeight,null);
            int assetPixels=0,bg=new Color(35,39,46).getRGB();
            for(int py=Math.max(0,dy);py<Math.min(sheet.getHeight(),dy+drawHeight);py++)for(int px=Math.max(0,dx);px<Math.min(sheet.getWidth(),dx+drawWidth);px++)if(sheet.getRGB(px,py)!=bg)assetPixels++;
            assertTrue(assetPixels>=100,s.id+" rendered asset pixels");
            g.setColor(new Color(70,190,225));g.setStroke(new BasicStroke(2));int bw=(int)Math.round(physical*pixels),bh=(int)Math.round(physical*pixels);
            g.drawRect(center-bw/2,centerY-bh/2,bw,bh);
            int stripY=340, stripH=(int)Math.round((maxWorldY-minWorldY)*pixels);g.setColor(new Color(225,185,80));g.fillRect(left+35,stripY-stripH,200,stripH);
            g.setClip(oldClip);g.setColor(Color.LIGHT_GRAY);g.setFont(new Font(Font.SANS_SERIF,Font.PLAIN,12));g.drawString(String.format(java.util.Locale.ROOT,"software · floor %.3f",minWorldY),left,380);
        }
        g.dispose();Path root=Path.of("").toAbsolutePath();while(root!=null&&!Files.exists(root.resolve("settings.gradle"))&&!Files.exists(root.resolve("settings.gradle.kts")))root=root.getParent();assertNotNull(root,"workspace root");
        Path out=root.resolve("build/reports/loose-object-feedback-sprites.png");Files.createDirectories(out.getParent());assertTrue(ImageIO.write(sheet,"png",out.toFile()));assertTrue(Files.size(out)>0);
    }
    private static String data(String name)throws Exception{try(InputStream in=FlatLooseSpriteGeometryTest.class.getResourceAsStream(DATA+name)){assertNotNull(in,"missing profile "+name);return new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);}}
}
