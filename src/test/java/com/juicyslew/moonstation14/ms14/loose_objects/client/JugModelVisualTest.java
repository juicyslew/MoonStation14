package com.juicyslew.moonstation14.ms14.loose_objects.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

/** Software orthographic diagnostic of shipped block meshes, not a Minecraft viewport render. */
class JugModelVisualTest {
    private static final String ASSET="/assets/moonstation14/";

    @Test void shippedFillModelsHaveFloorPivotAndRenderInspectionSheet() throws Exception {
        BufferedImage sheet=new BufferedImage(960,672,BufferedImage.TYPE_INT_ARGB); Graphics2D g=sheet.createGraphics();
        g.setColor(new Color(34,38,44));g.fillRect(0,0,sheet.getWidth(),sheet.getHeight());
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.setColor(new Color(205,210,220));g.setFont(new Font(Font.SANS_SERIF,Font.PLAIN,12));g.drawString("wireframe overlay: mesh/contact diagnostic (not a Minecraft viewport)",12,660);
        for(int fill=0;fill<=6;fill++) {
            var json=JsonParser.parseString(resource("models/block/jug_"+fill+".json")).getAsJsonObject(); var elements=json.getAsJsonArray("elements");
            double minY=Double.POSITIVE_INFINITY,maxY=Double.NEGATIVE_INFINITY,minX=Double.POSITIVE_INFINITY,maxX=Double.NEGATIVE_INFINITY;
            for(var e:elements){var el=e.getAsJsonObject();var from=el.getAsJsonArray("from");var to=el.getAsJsonArray("to");minY=Math.min(minY,from.get(1).getAsDouble());maxY=Math.max(maxY,to.get(1).getAsDouble());minX=Math.min(minX,from.get(0).getAsDouble());maxX=Math.max(maxX,to.get(0).getAsDouble());}
            assertEquals(0,minY,1e-9,"actual floor contact");assertEquals(.5,(maxX-minX)/16,1e-9,"model width");assertEquals(.75,(maxY-minY)/16,1e-9,"model height");
            for(int cardinal=0;cardinal<4;cardinal++){int ox=cardinal*240,oy=fill*96;g.setColor(Color.WHITE);g.setFont(new Font(Font.SANS_SERIF,Font.PLAIN,14));g.drawString("fill "+fill+" / "+cardinal*90+"°",ox+8,oy+18);g.setColor(new Color(190,190,190));double a=Math.toRadians(cardinal*90),c=Math.cos(a),s=Math.sin(a);Path2D ground=new Path2D.Double();for(int i=0;i<4;i++){double x=(i==0||i==3?-4:4),z=(i<2?-4:4),rx=c*x-s*z,rz=c*z+s*x,px=ox+120+(rx-rz)*4,py=oy+76+(rx+rz)*2.2; if(i==0)ground.moveTo(px,py);else ground.lineTo(px,py);}ground.closePath();g.draw(ground);render(g,elements,json.getAsJsonObject("textures"),ox,oy,cardinal*90);}
        }
        g.dispose();Path root=Path.of("").toAbsolutePath();while(root!=null&&!Files.exists(root.resolve("settings.gradle"))&&!Files.exists(root.resolve("settings.gradle.kts")))root=root.getParent();assertNotNull(root,"workspace root");
        Path output=root.resolve("build/reports/loose-object-visuals/jug.png");Files.createDirectories(output.getParent());assertTrue(ImageIO.write(sheet,"png",output.toFile()));assertTrue(Files.size(output)>0);
    }
    private static void render(Graphics2D g,JsonArray elements,JsonObject textures,int ox,int oy,int degrees)throws Exception{
        double a=Math.toRadians(degrees),c=Math.cos(a),s=Math.sin(a);
        for(var element:elements){var el=element.getAsJsonObject();var from=el.getAsJsonArray("from");var to=el.getAsJsonArray("to");double x0=n(from,0),y0=n(from,1),z0=n(from,2),x1=n(to,0),y1=n(to,1),z1=n(to,2);var f=el.getAsJsonObject("faces");
            // Rotating the exposed side pair gives a coherent cardinal orthographic view.
            // Render both projected side faces plus the top; a cardinal rotation must not
            // collapse the inspection to a single edge-on face.
            drawFace(g,textures,f.getAsJsonObject("south"),new double[][]{{x0,y0,z1},{x1,y0,z1},{x1,y1,z1},{x0,y1,z1}},ox,oy,c,s);
            drawFace(g,textures,f.getAsJsonObject("east"),new double[][]{{x1,y0,z0},{x1,y0,z1},{x1,y1,z1},{x1,y1,z0}},ox,oy,c,s);
            drawFace(g,textures,f.getAsJsonObject("up"),new double[][]{{x0,y1,z0},{x1,y1,z0},{x1,y1,z1},{x0,y1,z1}},ox,oy,c,s);
        }
    }
    private static double n(JsonArray a,int i){return a.get(i).getAsDouble();}
    private static void drawFace(Graphics2D g,JsonObject textures,JsonObject face,double[][] vertices,int ox,int oy,double c,double s)throws Exception{
        if(face==null)return;String key=face.get("texture").getAsString().substring(1),texture=textures.get(key).getAsString().replace("moonstation14:","");BufferedImage image;
        try(InputStream in=JugModelVisualTest.class.getResourceAsStream(ASSET+"textures/"+texture+".png")){assertNotNull(in,"missing texture "+texture);image=ImageIO.read(in);}
        double[][] p=new double[4][2];Path2D path=new Path2D.Double();for(int i=0;i<4;i++){double x=vertices[i][0]-8,z=vertices[i][2]-8,rotatedX=c*x-s*z,rotatedZ=c*z+s*x;p[i][0]=ox+120+(rotatedX-rotatedZ)*4;p[i][1]=oy+76+(rotatedX+rotatedZ)*2.2-vertices[i][1]*4;if(i==0)path.moveTo(p[i][0],p[i][1]);else path.lineTo(p[i][0],p[i][1]);}path.closePath();Shape old=g.getClip();g.clip(path);
        JsonArray uv=face.getAsJsonArray("uv");double u0=n(uv,0)*image.getWidth()/32,v0=n(uv,1)*image.getHeight()/32,u1=n(uv,2)*image.getWidth()/32,v1=n(uv,3)*image.getHeight()/32,du=u1-u0,dv=v1-v0;
        if(du!=0&&dv!=0){double ax=(p[1][0]-p[0][0])/du,ay=(p[1][1]-p[0][1])/du,bx=(p[3][0]-p[0][0])/dv,by=(p[3][1]-p[0][1])/dv;BufferedImage source=image;
            if(face.has("tintindex")&&face.get("tintindex").getAsInt()>=0){JsonObject water=JsonParser.parseString(dataResource("/data/moonstation14/moonstation14/reagent/water.json")).getAsJsonObject();int rgb=(int)Long.decode(water.get("color").getAsString()).longValue();source=new BufferedImage(image.getWidth(),image.getHeight(),BufferedImage.TYPE_INT_ARGB);for(int yy=0;yy<image.getHeight();yy++)for(int xx=0;xx<image.getWidth();xx++){int pixel=image.getRGB(xx,yy),alpha=pixel>>>24;source.setRGB(xx,yy,(alpha<<24)|(((pixel>>16&255)*(rgb>>16&255)/255)<<16)|(((pixel>>8&255)*(rgb>>8&255)/255)<<8)|((pixel&255)*(rgb&255)/255));}}
            g.drawImage(source,new AffineTransform(ax,ay,bx,by,p[0][0]-ax*u0-bx*v0,p[0][1]-ay*u0-by*v0),null);}g.setClip(old);g.setColor(new Color(15,20,25,150));g.setStroke(new BasicStroke(.7f));g.draw(path);
    }
    private static String resource(String path)throws Exception{try(InputStream in=JugModelVisualTest.class.getResourceAsStream(ASSET+path)){assertNotNull(in,"missing model "+path);return new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);}}
    private static String dataResource(String path)throws Exception{try(InputStream in=JugModelVisualTest.class.getResourceAsStream(path)){assertNotNull(in,"missing data "+path);return new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);}}
}
