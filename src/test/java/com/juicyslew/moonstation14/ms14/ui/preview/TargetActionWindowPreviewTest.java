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
        var root=Path.of(System.getProperty("moonstation14.projectDir",System.getProperty("user.dir"))).toAbsolutePath();var out=root.resolve("build/guns-sprint/owner-feedback/ui-preview");Files.createDirectories(out);
        var jar=resources();var font=MinecraftBitmapFont.load(jar);language=PreviewEnglishLanguage.load(root,jar);
        var target=UUID.fromString("00000000-0000-0000-0000-000000000404");
        var cases=new Object[][]{
                {"enabled-long-title",320,180,new BodyTargetActionSnapshot(1,1,1,"right","item-token",target,Component.literal("A very long target display name for title clipping"),true,true,null)},
                {"disabled-long-reason",640,360,new BodyTargetActionSnapshot(1,2,1,"right","item-token",target,Component.literal("Mouse"),true,false,Component.literal("This target action is disabled because the held object cannot butcher this creature safely. Please select a sharp object before attempting to butcher this creature."))},
                {"disabled-narrow",160,90,new BodyTargetActionSnapshot(1,3,1,"right","item-token",target,Component.literal("Narrow viewport target"),true,false,Component.literal("Use a sharp object to butcher this."))}
        };
        for(var fixture:cases){String name=(String)fixture[0];int width=(int)fixture[1],height=(int)fixture[2];var state=(BodyTargetActionSnapshot)fixture[3];
            var paint=new CpuOrderedPaint(width,height,font);paint.fill(0,0,width,height,0xff5a1b7c);var layout=new TargetActionClientPolicy.Layout(width-8,height-6,width,height,state.actions().size(),TargetActionClientPolicy.Layout.WIDTH,!state.enabled());
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
        var manager=new com.juicyslew.moonstation14.ms14.prototype.PrototypeManager();manager.register(com.juicyslew.moonstation14.ms14.combat.ModGunProfiles.TYPE);var gunProfiles=new java.util.LinkedHashMap<net.minecraft.resources.ResourceLocation,com.juicyslew.moonstation14.ms14.combat.GunProfile>();manager.stage(java.util.Map.of(com.juicyslew.moonstation14.ms14.combat.ModGunProfiles.TYPE,com.juicyslew.moonstation14.ms14.prototype.BotanyCatalogCandidateTest.mainResourcesAll("gun_profile")),catalogs->catalogs.get(com.juicyslew.moonstation14.ms14.combat.ModGunProfiles.TYPE.typeId()).forEach((key,raw)->gunProfiles.put(key,com.juicyslew.moonstation14.ms14.combat.GunProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,raw).getOrThrow())));
        var mk58=gunProfiles.get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("moonstation14","weapon_pistol_mk58"));var lecter=gunProfiles.get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("moonstation14","weapon_rifle_lecter"));assertNotNull(mk58);assertNotNull(lecter);
        var physicalMk58=new net.minecraft.world.item.ItemStack(com.juicyslew.moonstation14.item.ModItems.WEAPON_PISTOL_MK58.get());physicalMk58.set(com.juicyslew.moonstation14.component.ModDataComponents.MAGAZINE_CHAMBER_STATE.get(),new com.juicyslew.moonstation14.ms14.combat.MagazineChamberState(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_INGOT),net.minecraft.world.item.ItemStack.EMPTY,true));var openMk58=new net.minecraft.world.item.ItemStack(com.juicyslew.moonstation14.item.ModItems.WEAPON_PISTOL_MK58.get());openMk58.set(com.juicyslew.moonstation14.component.ModDataComponents.MAGAZINE_CHAMBER_STATE.get(),com.juicyslew.moonstation14.ms14.combat.MagazineChamberState.empty(false));var physicalLecter=new net.minecraft.world.item.ItemStack(com.juicyslew.moonstation14.item.ModItems.WEAPON_RIFLE_LECTER.get());physicalLecter.set(com.juicyslew.moonstation14.component.ModDataComponents.MAGAZINE_CHAMBER_STATE.get(),new com.juicyslew.moonstation14.ms14.combat.MagazineChamberState(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_INGOT),net.minecraft.world.item.ItemStack.EMPTY,true));
        var mk58Loaded=com.juicyslew.moonstation14.ms14.item_processing.client.GunHeldControlProjection.rows(mk58,physicalMk58);var mk58Empty=com.juicyslew.moonstation14.ms14.item_processing.client.GunHeldControlProjection.rows(mk58,openMk58);var lecterRows=com.juicyslew.moonstation14.ms14.item_processing.client.GunHeldControlProjection.rows(lecter,physicalLecter);
        java.util.function.Function<java.util.List<com.juicyslew.moonstation14.ms14.item_processing.client.BodyTargetActionClient.HeldRow>,java.util.List<TargetActionWindowRenderer.Row>> renderRows=source->source.stream().map(r->new TargetActionWindowRenderer.Row(r.label(),r.enabled(),r.disabledReason(),java.util.Optional.empty())).toList();
        var gunCases=java.util.List.of(new Object[]{"mk58-mag-present-320x240",320,240,renderRows.apply(mk58Loaded)},new Object[]{"mk58-mag-present-640x360",640,360,renderRows.apply(mk58Loaded)},new Object[]{"mk58-no-mag-open-320x240",320,240,renderRows.apply(mk58Empty)},new Object[]{"mk58-no-mag-open-640x360",640,360,renderRows.apply(mk58Empty)},new Object[]{"lecter-mag-present-320x240",320,240,renderRows.apply(lecterRows)},new Object[]{"lecter-mag-present-640x360",640,360,renderRows.apply(lecterRows)});
        var manifest=new java.util.ArrayList<String>();
        for(var fixture:gunCases){String name=(String)fixture[0];int w=(int)fixture[1],h=(int)fixture[2];@SuppressWarnings("unchecked") var rows=(java.util.List<TargetActionWindowRenderer.Row>)fixture[3];var title=language.component(name.startsWith("lecter")?"item.moonstation14.weapon_rifle_lecter":"item.moonstation14.weapon_pistol_mk58",name.startsWith("lecter")?"Lecter rifle":"Mk58 pistol");var paint=new CpuOrderedPaint(w,h,font);paint.fill(0,0,w,h,0xff5a1b7c);int requested=TargetActionWindowRenderer.measuredWidth(title,rows,c->font.width(c.getString()));boolean footer=rows.stream().anyMatch(r->!r.enabled()&&r.disabledReason()!=null);var l=new TargetActionClientPolicy.Layout(24,24,w,h,rows.size(),requested,footer);assertTrue(l.usable());assertTrue(l.width()<=148);assertTrue(l.height()<=127);assertTrue(l.width()>=font.width(title.getString())+44||font.width(title.getString())>l.width()-44);for(int i=0;i<rows.size();i++){assertTrue(l.action(i).within(l.window()));if(i>0)assertTrue(l.action(i).y()>=l.action(i-1).y()+l.action(i-1).height());}assertTrue(l.close().within(l.window()));TargetActionWindowRenderer.paint(paint,title,rows,l,l.close().x(),l.close().y());assertTrue(paint.paintedText().stream().allMatch(s->font.width(s)<=l.width()));var png=out.resolve(name+".png");javax.imageio.ImageIO.write(paint.image(),"PNG",png.toFile());assertTrue(Files.size(png)>0);manifest.add(name+" "+w+"x"+h+" "+png.getFileName());}
        var highlightRows=renderRows.apply(mk58Loaded);var highlightTitle=language.component("item.moonstation14.weapon_pistol_mk58","Mk58 pistol");var hl=new TargetActionClientPolicy.Layout(24,24,320,240,highlightRows.size(),TargetActionWindowRenderer.measuredWidth(highlightTitle,highlightRows,c->font.width(c.getString())),false);for(String hot:new String[]{"close","title","action"}){var p=new CpuOrderedPaint(320,240,font);p.fill(0,0,320,240,0xff5a1b7c);int mx=hot.equals("close")?hl.close().x():hot.equals("title")?hl.x()+4:hl.action(0).x()+2;int my=hot.equals("close")?hl.close().y():hot.equals("title")?hl.y()+4:hl.action(0).y()+2;TargetActionWindowRenderer.paint(p,highlightTitle,highlightRows,hl,mx,my);var png=out.resolve("mk58-mag-present-320x240-"+hot+"-highlight.png");javax.imageio.ImageIO.write(p.image(),"PNG",png.toFile());assertTrue(Files.size(png)>0);manifest.add("mk58 highlight "+hot+" "+png.getFileName());}
         var tinyRows=java.util.List.of(new TargetActionWindowRenderer.Row(language.component("item.moonstation14.weapon_pistol_mk58","Mk58 pistol"),false,language.component("gui.moonstation14.target_action.unavailable","Unavailable"),java.util.Optional.empty()));var tiny=new TargetActionClientPolicy.Layout(0,0,320,TargetActionClientPolicy.Layout.heightFor(1,true),1,148,true);assertTrue(tiny.usable());assertEquals(TargetActionClientPolicy.Layout.FOOTER_HEIGHT,tiny.reason().height());assertTrue(tiny.reason().within(tiny.window()));TargetActionWindowRenderer.paint(new CpuOrderedPaint(320,tiny.height(),font),highlightTitle,tinyRows,tiny,0,0);
         Files.write(out.resolve("manifest.txt"),manifest);
    }
}
