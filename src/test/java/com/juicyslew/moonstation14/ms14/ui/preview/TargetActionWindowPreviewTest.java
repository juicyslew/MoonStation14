package com.juicyslew.moonstation14.ms14.ui.preview;

import com.juicyslew.moonstation14.ms14.hands.network.BodyTargetActionSnapshot;
import com.juicyslew.moonstation14.ms14.item_processing.client.TargetActionClientPolicy;
import com.juicyslew.moonstation14.ms14.item_processing.client.TargetActionWindowRenderer;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

/** CPU preview invokes the exact ordered production window painter used by the live overlay. */
class TargetActionWindowPreviewTest {
    private PreviewEnglishLanguage language;
    private static Path resources(){Path p=Path.of("").toAbsolutePath();while(p!=null){Path jar=p.resolve("build/moddev/artifacts/neoforge-21.1.224-client-extra-aka-minecraft-resources.jar");if(Files.isRegularFile(jar))return jar;p=p.getParent();}return Path.of("build/moddev/artifacts/neoforge-21.1.224-client-extra-aka-minecraft-resources.jar");}
    @AfterEach void restore(){if(language!=null){language.restore();language=null;}}
    private static final class StringPaint implements com.juicyslew.moonstation14.ms14.ui.client.paint.OrderedPaint {
        public void fill(int l,int t,int r,int b,int c){}public void text(Component c,int x,int y,int color,boolean shadow){}
        public int width(Component c){return c.getString().length();}public int width(String s){return s.length();}public int lineHeight(){return 1;}
        public String plainSubstrByWidth(String s,int width){return s.substring(0,Math.min(s.length(),width));}
        public void push(){}public void pop(){}public void translate(float x,float y,float z){}public void scale(float x,float y,float z){}
        public void clip(int l,int t,int r,int b){}public void unclip(){}
        public void sprite(net.minecraft.resources.ResourceLocation id,int x,int y,int w,int h,int u,int v,int sw,int sh,int tw,int th,int tint){}
    }
    @Test void titleAndReasonFittingHaveExactWordAwareBoundaries(){
        var p=new StringPaint();
        assertEquals("A long targ…",TargetActionWindowRenderer.fitTitle(p,"A long target name",12));
        assertTrue(p.width(TargetActionWindowRenderer.fitTitle(p,"A very long title",8))<=8);
        assertEquals(java.util.List.of("one two","three…"),TargetActionWindowRenderer.wrapReason(p,"one two three four five",9,2));
        assertEquals(java.util.List.of("abcde","fghi…"),TargetActionWindowRenderer.wrapReason(p,"abcdefghijkl",5,2));
    }
    @Test void productionPainterPreviewsEnabledDisabledLongTextAndNarrowViewport() throws Exception {
        var root=Path.of(System.getProperty("moonstation14.projectDir",System.getProperty("user.dir"))).toAbsolutePath();var out=root.resolve("build/agent-workflow/botany-genetics-sprint/target-action-preview");Files.createDirectories(out);
        var jar=resources();var font=MinecraftBitmapFont.load(jar);language=PreviewEnglishLanguage.load(root,jar);
        var target=UUID.fromString("00000000-0000-0000-0000-000000000404");
        var cases=new Object[][]{
                {"enabled-long-title",320,180,new BodyTargetActionSnapshot(1,1,1,"right","item-token",target,Component.literal("A very long target display name for title clipping"),true,true,null)},
                {"disabled-long-reason",640,360,new BodyTargetActionSnapshot(1,2,1,"right","item-token",target,Component.literal("Mouse"),true,false,Component.literal("This target action is disabled because the held object cannot butcher this creature safely. Please select a sharp object before attempting to butcher this creature."))},
                {"disabled-narrow",160,90,new BodyTargetActionSnapshot(1,3,1,"right","item-token",target,Component.literal("Narrow viewport target"),true,false,Component.literal("Use a sharp object to butcher this."))}
        };
        for(var fixture:cases){String name=(String)fixture[0];int width=(int)fixture[1],height=(int)fixture[2];var state=(BodyTargetActionSnapshot)fixture[3];
            var paint=new CpuOrderedPaint(width,height,font);paint.fill(0,0,width,height,0xff5a1b7c);var layout=new TargetActionClientPolicy.Layout(width-8,height-6,width,height);
            assertTrue(layout.usable());assertTrue(layout.width()<=width&&layout.height()<=height);
            for(var rect:java.util.List.of(layout.window(),layout.action(),layout.reason(),layout.close()))assertTrue(layout.rectWithinViewport(rect));
            assertTrue(layout.action().within(layout.window()));assertTrue(layout.reason().within(layout.window()));assertTrue(layout.close().within(layout.window()));
            TargetActionWindowRenderer.paint(paint,state,layout,layout.action().x(),layout.action().y());
            var title=paint.paintedText().get(0);assertTrue(font.width(title)<=TargetActionClientPolicy.Layout.WIDTH-44);
            if(name.equals("enabled-long-title"))assertTrue(title.endsWith("…"),"truncated target title must show ellipsis");
            if(!state.enabled()){
                var reasonLines=paint.paintedText().subList(3,paint.paintedText().size());
                assertTrue(!reasonLines.isEmpty());
                if(name.equals("disabled-long-reason")){assertTrue(reasonLines.size()>=3);assertTrue(reasonLines.get(reasonLines.size()-1).endsWith("…"));}
                for(String line:reasonLines)assertTrue(font.width(line)<=layout.reason().width());
                assertTrue(reasonLines.stream().noneMatch(s->s.endsWith("disab")||s.endsWith("butch")),"reason lines must wrap on word boundaries");
            }
            assertTrue(paint.paintedSprites().contains(TargetActionWindowRenderer.CUTLERY));
            assertTrue(paint.spriteDraws().stream().anyMatch(s->s.id().equals(TargetActionWindowRenderer.CUTLERY)&&s.width()==16&&s.height()==16
                    &&s.u()==0&&s.v()==0&&s.sourceWidth()==64&&s.sourceHeight()==64&&s.textureWidth()==64&&s.textureHeight()==64),
                    "production paint must sample the full 64px cutlery image");
            int iconPixels=0;for(int y=layout.action().y()+3;y<layout.action().y()+19&&y<height;y++)for(int x=layout.action().x()+8;x<layout.action().x()+24&&x<width;x++)if(paint.image().getRGB(x,y)!=0xff26363d)iconPixels++;
            assertTrue(iconPixels>20,"full scaled cutlery source should paint substantial icon pixels: "+name);
            var file=out.resolve(name+".png");javax.imageio.ImageIO.write(paint.image(),"PNG",file.toFile());assertTrue(Files.size(file)>0);
        }
    }
}
