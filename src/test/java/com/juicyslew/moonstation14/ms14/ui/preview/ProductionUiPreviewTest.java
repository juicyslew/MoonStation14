package com.juicyslew.moonstation14.ms14.ui.preview;

import com.google.gson.GsonBuilder;
import com.juicyslew.moonstation14.ms14.chemistry.chemmaster.ChemMasterControlRequest;
import com.juicyslew.moonstation14.ms14.chemistry.chemmaster.ChemMasterWindowState;
import com.juicyslew.moonstation14.ms14.chemistry.chemmaster.client.ChemMasterLayout;
import com.juicyslew.moonstation14.ms14.chemistry.chemmaster.client.ChemMasterPaint;
import com.juicyslew.moonstation14.ms14.doors.ui.AirlockWireIndicators;
import com.juicyslew.moonstation14.ms14.doors.ui.AirlockWireSnapshot;
import com.juicyslew.moonstation14.ms14.doors.ui.client.AirlockWirePaint;
import com.juicyslew.moonstation14.ms14.doors.ui.client.AirlockWireScreenLayout;
import com.juicyslew.moonstation14.ms14.reagent.ModReagents;
import com.juicyslew.moonstation14.ms14.ui.client.window.WindowChromeButtons;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.List;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.security.MessageDigest;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.*;

/** Offline diagnostics; invokes the exact production painters, never a parallel screen renderer. */
class ProductionUiPreviewTest {
    private PreviewEnglishLanguage activeLanguage;
    private static long renderedPixelVisits;
    private static long paintPasses;
    private static final UUID SESSION = UUID.fromString("00000000-0000-0000-0000-000000000014");
    private static final UUID MACHINE = UUID.fromString("00000000-0000-0000-0000-000000000015");
    private static final UUID BODY = UUID.fromString("00000000-0000-0000-0000-000000000016");

    @Test void generateProductionDrivenPreviews() throws Exception {
        long started=System.nanoTime();renderedPixelVisits=0;paintPasses=0;
        List<MemoryPoolMXBean> heapPools=ManagementFactory.getMemoryPoolMXBeans().stream().filter(p->p.getType()==java.lang.management.MemoryType.HEAP).toList();
        for(var pool:heapPools)try{pool.resetPeakUsage();}catch(UnsupportedOperationException ignored){}
        Path root = Path.of(System.getProperty("moonstation14.projectDir", ".")).toAbsolutePath().normalize();
        Path fontJar = configuredGameResources(root);
        MinecraftBitmapFont font = MinecraftBitmapFont.load(fontJar);
        PreviewEnglishLanguage language = activeLanguage = PreviewEnglishLanguage.load(root, fontJar);
        assertEquals("Preview reagent",language.reagent("readiness.missing.reagent","Preview reagent").getString());
        assertEquals("Fallback 7",language.component("readiness.missing.format","Fallback %s",7).getString());
        assertEquals("Container",language.component("gui.moonstation14.chemmaster.container","fallback container").getString());
        assertEquals("Alex coughs.",language.component("moonstation14.emote.cough","%s coughs.","Alex").getString());
        assertEquals("Untranslated fixture",language.component("readiness.missing.key","Untranslated fixture").getString());
        Path output = root.resolve("build/reports/feature-readiness/previews");
        Files.createDirectories(output);
        List<Map<String,Object>> manifest = new ArrayList<>();
        for (int scale = 1; scale <= 3; scale++) {
            int[][] viewports = {{320,240},{640,360}};
            for (int[] viewport : viewports) {
                renderChem(root, output, manifest, font, language, viewport[0], viewport[1], scale);
                renderWire(root, output, manifest, font, language, viewport[0], viewport[1], scale);
            }
        }
        writeContactSheets(root,output,manifest);
        long elapsedNanos=System.nanoTime()-started;
        Path manifestPath=output.resolve("manifest.json");
        Map<String,Object> report=new LinkedHashMap<>();
        report.put("baseRevision", "695cab96f26fcb7a8343b5b1e0ff32638693aef1");
        report.put("source", "production ChemMasterPaint.paint and AirlockWirePaint.paint; deterministic immutable fixture states; animationTick=120");
        report.put("fontJar", fontJar.toString());
        report.put("sourceJars",List.of(root.resolve("build/moddev/artifacts/neoforge-21.1.224-sources.jar").toString(),fontJar.toString()));
        report.put("fontProviders", font.manifest());
        report.put("translations", Map.of("minecraft", "assets/minecraft/lang/en_us.json", "moonstation14", "assets/moonstation14/lang/en_us.json or literal fallback"));
        report.put("textScope","unstyled, left-to-right Components only; real Language instance uses loaded en_us maps, missing reagent keys use the production path fallback");
        report.put("supportedOperations", List.of("fill","text (unstyled Components)","sprite/UV","push/pop","translate/scale","clip/unclip"));
        report.put("unsupported", List.of("styled Components/tooltips, native GPU/scissor effects, resource-pack stacking beyond selected font jar"));
        report.put("glyphDiagnostics", "unavailable glyphs/providers throw UnsupportedOperationException; no fake font fallback");
        report.put("languageKeysLoaded", language.manifestEntries().size());
        report.put("cases",manifest);
        var peaks=new LinkedHashMap<String,Object>();long peakSum=0;
        for(var pool:heapPools){var usage=pool.getPeakUsage();long peak=usage==null?-1:usage.getUsed();peaks.put(pool.getName(),peak);if(peak>=0)peakSum+=peak;}
        var fullCases=manifest.stream().filter(c->c.containsKey("full")).toList();
        long outputPixels=0;int maxWidth=0,maxHeight=0;
        for(var c:fullCases){@SuppressWarnings("unchecked") List<Integer> viewport=(List<Integer>)c.get("viewport");int scale=(Integer)c.get("guiScale");int w=viewport.get(0)*scale,h=viewport.get(1)*scale;outputPixels+=(long)w*h;maxWidth=Math.max(maxWidth,w);maxHeight=Math.max(maxHeight,h);}
        report.put("cost",Map.ofEntries(Map.entry("elapsedMillisGenerationAndChecks",elapsedNanos/1_000_000),
                Map.entry("runtimeMaxMemoryBytes",Runtime.getRuntime().maxMemory()),
                Map.entry("heapPoolPeakUsageBytesByPool",peaks),Map.entry("sumOfHeapPoolPeaksBytesNoncontemporaneous",peakSum),
                Map.entry("memoryNote","Memory-pool peaks may occur at different times; the sum is not process RSS or a contemporaneous total. Process peak RSS and whole-Gradle/native peak remain unmeasured."),
                Map.entry("caseCount",fullCases.size()),Map.entry("fullPngPixelCount",outputPixels),
                Map.entry("maximumPngDimensions",List.of(maxWidth,maxHeight)),Map.entry("rasterPaintPasses",paintPasses),
                Map.entry("rasterWorkBoundPixelVisits",renderedPixelVisits),
                Map.entry("warmCacheProvenance","Measured in this test process; Gradle cache warmth not independently classified; no slow-host hard threshold.")));
        report.put("inputHashesSha256",inputHashes(root,fontJar));
        Files.writeString(manifestPath,new GsonBuilder().setPrettyPrinting().create().toJson(report),StandardCharsets.UTF_8);
    }
    @AfterEach void restoreLanguage(){if(activeLanguage!=null){activeLanguage.restore();activeLanguage=null;}}

    private static void renderChem(Path root,Path out,List<Map<String,Object>> manifest,MinecraftBitmapFont font,
            PreviewEnglishLanguage language,int vw,int vh,int scale) throws Exception {
        var geometry=ChemMasterLayout.geometry(vw,vh);
        var rows=new ArrayList<ChemMasterWindowState.ReagentRow>();
        for(int i=0;i<15;i++) rows.add(new ChemMasterWindowState.ReagentRow(
                ResourceKey.create(ModReagents.REAGENT_REGISTRY_KEY,ResourceLocation.fromNamespaceAndPath("moonstation14", "preview_reagent_"+i)), (long)(i+1)*123456));
        for(String stateName:List.of("empty","populated-overflow","pending-disabled","rejected-hover-moved-clamped")) {
            boolean populated=!stateName.equals("empty");
            List<ChemMasterWindowState.ReagentRow> input=populated?rows:List.of();
            List<ChemMasterWindowState.ReagentRow> buffer=populated?rows:List.of();
            long inputVolume=input.stream().mapToLong(ChemMasterWindowState.ReagentRow::amountCents).sum();
            // Keep a valid immutable DTO; a deliberately large but legal capacity exposes quantity layout.
            var state=new ChemMasterWindowState(10,10,3,stateName.startsWith("rejected")?4:-1,
                    stateName.startsWith("rejected")?Optional.of("Cannot interact while stunned"):Optional.empty(),SESSION,MACHINE,BODY,1,
                    ResourceLocation.withDefaultNamespace("overworld"),0,64,0,true,Optional.empty(),
                    Optional.of(language.component("item.minecraft.glass_bottle", "Glass Bottle %s", "Fixture")),com.juicyslew.moonstation14.ms14.reagent.ReagentUnits.MAX_CENTS,inputVolume,input,buffer,
                    ChemMasterControlRequest.Mode.TRANSFER,ChemMasterControlRequest.Sort.OLDEST_FIRST);
            int x=Math.max(0,Math.min(vw-geometry.width()/2,20)),y=Math.max(0,Math.min(vh-geometry.height()/2,18));
            if(stateName.contains("moved")){x=Math.max(0,vw-geometry.width()/2);y=Math.max(0,vh-geometry.height()/2);}
            List<ChemMasterPaint.DisplayRow> inputDisplay=display(input,language),bufferDisplay=display(buffer,language);
            int scroll=stateName.contains("overflow")?ChemMasterLayout.maxScroll(inputDisplay.size(),ChemMasterLayout.ROW_HEIGHT,geometry.inputViewport().height()):0;
            String loaded=state.loadedItemName().map(Component::getString).orElse("No container");
            String caption=loaded+"  "+com.juicyslew.moonstation14.ms14.reagent.ReagentUnits.formatCents(state.inputVolumeCents())+" / "+
                    com.juicyslew.moonstation14.ms14.reagent.ReagentUnits.formatCents(state.inputCapacityCents())+"u";
            var view=new ChemMasterPaint.View(state,caption,inputDisplay,bufferDisplay,geometry,x,y,vw,vh,
                    stateName.contains("hover")?geometry.close().x()+1:Integer.MIN_VALUE,
                    stateName.contains("hover")?geometry.close().y()+1:Integer.MIN_VALUE,scroll,
                    stateName.contains("overflow")?ChemMasterLayout.maxScroll(bufferDisplay.size(),ChemMasterLayout.ROW_HEIGHT,geometry.bufferViewport().height()):0,
                    stateName.equals("pending-disabled"),false,language.component("gui.moonstation14.chemmaster.container","Container"),
                    stateName.startsWith("rejected")?"Cannot interact while stunned":null);
            var paint=new CpuOrderedPaint(vw*scale,vh*scale,font,scale); paint.fill(0,0,vw*scale,vh*scale,0xff5a1b7c);
            countPaint(vw*scale,vh*scale);
            paint.push();paint.scale(scale,scale,1);ChemMasterPaint.paint(new ClipAuditOrderedPaint(paint,scale),view);paint.pop();
            BufferedImage image=paint.image();
            var repeated=new CpuOrderedPaint(vw*scale,vh*scale,font,scale);repeated.fill(0,0,vw*scale,vh*scale,0xff5a1b7c);countPaint(vw*scale,vh*scale);repeated.push();repeated.scale(scale,scale,1);ChemMasterPaint.paint(new ClipAuditOrderedPaint(repeated,scale),view);repeated.pop();
            assertPixelsEqual(image,repeated.image(),"ChemMaster rerender must be pixel deterministic");
            assertOutsideWindowUnchanged(image,0xff5a1b7c,x*scale,y*scale,geometry.width()*scale/2,geometry.height()*scale/2);
            if(stateName.contains("hover")){
                var idle=new ChemMasterPaint.View(state,caption,inputDisplay,bufferDisplay,geometry,x,y,vw,vh,Integer.MIN_VALUE,Integer.MIN_VALUE,scroll,view.bufferScroll(),view.pending(),false,view.containerHeading(),view.rejectionReason());
                BufferedImage idlePixels=drawChem(vw*scale,vh*scale,font,scale,idle);
                assertPixelsDifferIn(image,idlePixels,(x*scale+geometry.close().x()*scale/2),(y*scale+geometry.close().y()*scale/2),Math.max(1,geometry.close().width()*scale/2),Math.max(1,geometry.close().height()*scale/2),"hovered ChemMaster close must change painted control pixels");
            }
            assertChemGeometry(geometry,inputDisplay,stateName,view,font,caption);
            writeCase(root,out,manifest,"chemmaster",stateName,vw,vh,scale,image,font,Map.of("window",List.of(x,y,geometry.width()/2,geometry.height()/2),"close",rect(geometry.close()),"inputViewport",rect(geometry.inputViewport()),"bufferViewport",rect(geometry.bufferViewport()),"scroll",List.of(view.inputScroll(),view.bufferScroll())));
        }
    }

    private static List<ChemMasterPaint.DisplayRow> display(List<ChemMasterWindowState.ReagentRow> rows,PreviewEnglishLanguage language){
        var result=new ArrayList<ChemMasterPaint.DisplayRow>();for(var row:rows){String id=row.reagent().location().getPath();String fallback=id.replace('_',' ');result.add(new ChemMasterPaint.DisplayRow(row,language.reagent("reagent-name-"+id,fallback),0xff82959b));}return List.copyOf(result);
    }

    private static void renderWire(Path root,Path out,List<Map<String,Object>> manifest,MinecraftBitmapFont font,
            PreviewEnglishLanguage language,int vw,int vh,int scale) throws Exception {
        for(String stateName:List.of("empty","populated","no-power-rejected","hover-moved-clamped")) {
            var rows=new ArrayList<AirlockWireSnapshot.Row>();
            if(!stateName.equals("empty"))for(int i=0;i<7;i++)rows.add(new AirlockWireSnapshot.Row(i,0xff95aeb1,"ABCDEFG".substring(i,i+1),i%3==0));
            boolean rejection=stateName.contains("rejected");
            var snapshot=new AirlockWireSnapshot(0,SESSION,1,!rejection,rejection?AirlockWireSnapshot.Rejection.STUNNED:AirlockWireSnapshot.Rejection.NONE,1,
                    !stateName.contains("no-power"),AirlockWireIndicators.Light.ON,AirlockWireIndicators.Light.OFF,AirlockWireIndicators.Light.ON,
                    AirlockWireIndicators.Light.ON,AirlockWireIndicators.Light.OFF,AirlockWireIndicators.Light.ON,AirlockWireIndicators.Light.OFF,true,true,rows);
            int x=Math.max(0,Math.min(vw-AirlockWireScreenLayout.WIDTH,24)),y=Math.max(0,Math.min(vh-AirlockWireScreenLayout.HEIGHT,20));
            if(stateName.contains("moved")){x=vw-AirlockWireScreenLayout.WIDTH;y=vh-AirlockWireScreenLayout.HEIGHT;}
            Component title=Component.literal("Airlock — Wires");
            var view=new AirlockWirePaint.View(x,y,snapshot,120,rejection?"Cannot interact while stunned":null,
                    stateName.contains("hover")?x+AirlockWireScreenLayout.WIDTH-8:Integer.MIN_VALUE,
                    stateName.contains("hover")?y+8:Integer.MIN_VALUE,title);
            var paint=new CpuOrderedPaint(vw*scale,vh*scale,font,scale);paint.fill(0,0,vw*scale,vh*scale,0xff5a1b7c);
            countPaint(vw*scale,vh*scale);
            paint.push();paint.scale(scale,scale,1);AirlockWirePaint.paint(paint,view);paint.pop();
            var repeated=new CpuOrderedPaint(vw*scale,vh*scale,font,scale);repeated.fill(0,0,vw*scale,vh*scale,0xff5a1b7c);countPaint(vw*scale,vh*scale);repeated.push();repeated.scale(scale,scale,1);AirlockWirePaint.paint(repeated,view);repeated.pop();
            assertPixelsEqual(paint.image(),repeated.image(),"wire rerender must be pixel deterministic");
            assertOutsideWindowUnchanged(paint.image(),0xff5a1b7c,x*scale,y*scale,AirlockWireScreenLayout.WIDTH*scale,AirlockWireScreenLayout.HEIGHT*scale);
            if(stateName.contains("hover")){
                var idle=new AirlockWirePaint.View(x,y,snapshot,120,view.message(),Integer.MIN_VALUE,Integer.MIN_VALUE,title);
                var idlePaint=new CpuOrderedPaint(vw*scale,vh*scale,font,scale);idlePaint.fill(0,0,vw*scale,vh*scale,0xff5a1b7c);countPaint(vw*scale,vh*scale);idlePaint.push();idlePaint.scale(scale,scale,1);AirlockWirePaint.paint(idlePaint,idle);idlePaint.pop();
                var cb=WindowChromeButtons.closeBounds(x,y,AirlockWireScreenLayout.WIDTH,1f);
                assertPixelsDifferIn(paint.image(),idlePaint.image(),cb.left()*scale,cb.top()*scale,cb.width()*scale,cb.height()*scale,"hovered wire close must change painted control pixels");
            }
            assertWireGeometry(x,y,vw,vh,view,font);
            var close=WindowChromeButtons.closeBounds(x,y,AirlockWireScreenLayout.WIDTH,1f);
            writeCase(root,out,manifest,"airlock-wire",stateName,vw,vh,scale,paint.image(),font,Map.of("window",List.of(x,y,AirlockWireScreenLayout.WIDTH,AirlockWireScreenLayout.HEIGHT),"close",List.of(close.left(),close.top(),close.width(),close.height())));
        }
    }

    private static void assertChemGeometry(ChemMasterLayout.Geometry g,List<ChemMasterPaint.DisplayRow> rows,String state,ChemMasterPaint.View view,MinecraftBitmapFont font,String caption){
        assertTrue(g.close().x()>=0&&g.close().x()+g.close().width()<=g.width(),"ChemMaster close bound");
        assertTrue(font.width("ChemMaster 4000")/2f<=g.close().x()-ChemMasterLayout.MARGIN,"ChemMaster title remains left of close control");
        assertTrue(g.inputTab().x()+g.inputTab().width()<=g.width()&&g.outputTab().x()+g.outputTab().width()<=g.width(),"tab bounds");
        assertFalse(intersects(g.inputTab(),g.outputTab()),"ChemMaster tabs overlap");
        assertFalse(intersects(g.inputViewport(),g.bufferViewport()),"ChemMaster row viewports overlap");
        assertFalse(intersects(g.sort(),g.transferMode()),"sort/transfer controls overlap");assertFalse(intersects(g.transferMode(),g.discardMode()),"mode controls overlap");
        var close=WindowChromeButtons.closeBounds(g.close().x(),g.close().y(),g.close().width(),g.close().height());
        assertTrue(WindowChromeButtons.contains(close,view.mouseX(),view.mouseY())==state.contains("hover"),"painted close hover equals close hit geometry");
        var metrics=new CpuOrderedPaint(1,1,font);metrics.push();metrics.scale(.5f,.5f,1);
        String fitting=ChemMasterPaint.fitText(metrics,caption,g.caption().width());
        assertTrue(metrics.width(fitting)<=g.caption().width(),"actual production caption fit result must fit bitmap metrics");
        if(!rows.isEmpty()){String label=rows.get(0).name().getString();String fittedRow=ChemMasterPaint.fitText(metrics,label,64);assertTrue(metrics.width(fittedRow)<=64,"actual production reagent fit result must fit bitmap metrics");}
        metrics.pop();
        if(!rows.isEmpty()&&g.inputViewport().width()>0&&g.inputViewport().height()>0){
            assertTrue(ChemMasterLayout.visibleRowAt(g.inputViewport(),view.inputScroll(),g.inputViewport().x()+1,g.inputViewport().y()+1,rows.size())>=0,"visible clipped row has matching hit geometry");
            var clip=ChemMasterLayout.toScreenClip(g.inputViewport(),view.x(),view.y());
            var bounds=ChemMasterLayout.toScreen(g.inputViewport(),view.x(),view.y());
            assertTrue(clip.left()>=bounds.left()&&clip.top()>=bounds.top()&&clip.right()<=bounds.right()&&clip.bottom()<=bounds.bottom(),"paint clip is contained by hit viewport");
        }
        if(state.contains("overflow")){assertEquals(ChemMasterLayout.maxScroll(rows.size(),ChemMasterLayout.ROW_HEIGHT,g.inputViewport().height()),view.inputScroll());assertEquals(ChemMasterLayout.maxScroll(rows.size(),ChemMasterLayout.ROW_HEIGHT,g.bufferViewport().height()),view.bufferScroll());}
        // Negative geometry probes prove defects are rejected rather than blessed as image baselines.
        assertThrows(AssertionError.class,()->assertWithin(new ChemMasterLayout.Rect(g.width()+1,g.close().y(),g.close().width(),g.close().height()),g.width(),g.height()));
        var movedTab=new ChemMasterLayout.Rect(g.outputTab().x(),g.inputTab().y(),g.inputTab().width(),g.inputTab().height());
        assertThrows(AssertionError.class,()->assertNoOverlap(movedTab,g.outputTab()));
        assertThrows(AssertionError.class,()->assertClosePresent(new ChemMasterLayout.Rect(g.close().x(),g.close().y(),0,g.close().height()),g.width(),g.height()));
        assertWithin(g.close(),g.width(),g.height());
    }
    private static void assertWireGeometry(int x,int y,int vw,int vh,AirlockWirePaint.View view,MinecraftBitmapFont font){
        var bounds=WindowChromeButtons.closeBounds(x,y,AirlockWireScreenLayout.WIDTH,1f);
        var close=new Rectangle(bounds.left(),bounds.top(),bounds.width(),bounds.height());
        assertTrue(font.width(view.title().getString())<=bounds.left()-(x+8),"wire title remains left of close control");
        assertTrue(close.x>=0&&close.y>=0&&close.x+close.width<=vw&&close.y+close.height<=vh,"wire chrome bounds");
        assertThrows(AssertionError.class,()->assertWithin(new Rectangle(vw+1,bounds.top(),bounds.width(),bounds.height()),vw,vh));
        assertThrows(AssertionError.class,()->assertClosePresent(new Rectangle(bounds.left(),bounds.top(),0,bounds.height()),vw,vh));
        var titleBounds=new Rectangle(x+8,y+8,font.width(view.title().getString()),9);
        assertThrows(AssertionError.class,()->assertNoOverlap(titleBounds,new Rectangle(x+8,y+5,bounds.width(),bounds.height())));
        assertTrue(WindowChromeButtons.contains(bounds,view.mouseX(),view.mouseY())||view.mouseX()<0,"wire close hover is the actual hit rectangle");
    }
    private static void assertWithin(ChemMasterLayout.Rect r,int w,int h){assertTrue(r.x()>=0&&r.y()>=0&&r.x()+r.width()<=w&&r.y()+r.height()<=h,"out-of-bounds geometry: "+r);}
    private static void assertWithin(Rectangle r,int w,int h){assertTrue(r.x>=0&&r.y>=0&&r.x+r.width<=w&&r.y+r.height<=h,"out-of-bounds geometry: "+r);}
    private static void assertClosePresent(ChemMasterLayout.Rect r,int w,int h){assertTrue(r.width()>0&&r.height()>0,"close control missing");assertWithin(r,w,h);}
    private static void assertClosePresent(Rectangle r,int w,int h){assertTrue(r.width>0&&r.height>0,"close control missing");assertWithin(r,w,h);}
    private static void assertNoOverlap(Rectangle a,Rectangle b){assertFalse(a.intersects(b),"unexpected overlap: "+a+" / "+b);}
    private static void assertNoOverlap(ChemMasterLayout.Rect a,ChemMasterLayout.Rect b){assertFalse(intersects(a,b),"unexpected overlap: "+a+" / "+b);}
    private static boolean intersects(ChemMasterLayout.Rect a,ChemMasterLayout.Rect b){return a.x()<b.x()+b.width()&&b.x()<a.x()+a.width()&&a.y()<b.y()+b.height()&&b.y()<a.y()+a.height();}
    private static void assertPixelsEqual(BufferedImage a,BufferedImage b,String message){assertEquals(a.getWidth(),b.getWidth());assertEquals(a.getHeight(),b.getHeight());assertArrayEquals(a.getRGB(0,0,a.getWidth(),a.getHeight(),null,0,a.getWidth()),b.getRGB(0,0,b.getWidth(),b.getHeight(),null,0,b.getWidth()),message);}
    private static void assertPixelsDifferIn(BufferedImage a,BufferedImage b,int x,int y,int w,int h,String message){int right=Math.min(Math.min(a.getWidth(),b.getWidth()),x+w),bottom=Math.min(Math.min(a.getHeight(),b.getHeight()),y+h),left=Math.max(0,x),top=Math.max(0,y);for(int py=top;py<bottom;py++)for(int px=left;px<right;px++)if(a.getRGB(px,py)!=b.getRGB(px,py))return;fail(message);}
    private static void assertOutsideWindowUnchanged(BufferedImage image,int sentinel,int x,int y,int w,int h){for(int py=0;py<image.getHeight();py++)for(int px=0;px<image.getWidth();px++)if(px<x||py<y||px>=x+w||py>=y+h)assertEquals(sentinel,image.getRGB(px,py),"paint escaped the selected window/clip bounds at "+px+","+py);}
     private static BufferedImage drawChem(int w,int h,MinecraftBitmapFont font,int scale,ChemMasterPaint.View view){countPaint(w,h);var p=new CpuOrderedPaint(w,h,font,scale);p.fill(0,0,w,h,0xff5a1b7c);p.push();p.scale(scale,scale,1);ChemMasterPaint.paint(new ClipAuditOrderedPaint(p,scale),view);p.pop();return p.image();}
    private static void countPaint(int width,int height){paintPasses++;renderedPixelVisits+=(long)width*height;}
    private static Map<String,String> inputHashes(Path root,Path fontJar) throws Exception {
        var hashes=new LinkedHashMap<String,String>();hashes.put(fontJar.toString(),sha256(fontJar));
        Path sourceJar=root.resolve("build/moddev/artifacts/neoforge-21.1.224-sources.jar");if(Files.isRegularFile(sourceJar))hashes.put(root.relativize(sourceJar).toString(),sha256(sourceJar));
        Path translations=root.resolve("src/main/resources/assets/moonstation14/lang/en_us.json");if(Files.isRegularFile(translations))hashes.put(root.relativize(translations).toString(),sha256(translations));
        for(String texture:List.of("wire_1","wire_1_cut","wire_2","wire_2_cut")){Path asset=root.resolve("src/main/resources/assets/moonstation14/textures/gui/airlock/"+texture+".png");if(Files.isRegularFile(asset))hashes.put(root.relativize(asset).toString(),sha256(asset));}
        return hashes;
    }
    private static String sha256(Path path) throws Exception {var digest=MessageDigest.getInstance("SHA-256");try(InputStream input=Files.newInputStream(path)){byte[] buffer=new byte[65536];int read;while((read=input.read(buffer))>=0)digest.update(buffer,0,read);}return HexFormat.of().formatHex(digest.digest());}

    private static void writeCase(Path root,Path out,List<Map<String,Object>> manifest,String screen,String state,int vw,int vh,int scale,
                                  BufferedImage image,MinecraftBitmapFont font,Map<String,Object> geometry) throws Exception {
        String stem=screen+"-"+state+"-"+vw+"x"+vh+"-scale"+scale;
        Path full=out.resolve(stem+"-full.png");ImageIO.write(image,"png",full.toFile());
        @SuppressWarnings("unchecked") List<Integer> window=(List<Integer>)geometry.get("window");
        int sx=window.get(0)*scale,sy=window.get(1)*scale,sw=window.get(2)*scale,sh=window.get(3)*scale;
        sx=Math.max(0,Math.min(image.getWidth()-1,sx));sy=Math.max(0,Math.min(image.getHeight()-1,sy));sw=Math.max(1,Math.min(sw,image.getWidth()-sx));sh=Math.max(1,Math.min(sh,image.getHeight()-sy));
        Path detail=out.resolve(stem+"-detail.png");ImageIO.write(image.getSubimage(sx,sy,sw,sh),"png",detail.toFile());
        BufferedImage decoded=ImageIO.read(full.toFile());
        assertNotNull(decoded,"PNG decoder must be able to read generated output");
        assertEquals(image.getWidth(),decoded.getWidth());assertEquals(image.getHeight(),decoded.getHeight());
        assertArrayEquals(image.getRGB(0,0,image.getWidth(),image.getHeight(),null,0,image.getWidth()),
                decoded.getRGB(0,0,decoded.getWidth(),decoded.getHeight(),null,0,decoded.getWidth()),"decoded PNG pixels must match deterministic raster");
        Map<String,Object> item=new LinkedHashMap<>();item.put("screen",screen);item.put("state",state);item.put("viewport",List.of(vw,vh));item.put("guiScale",scale);
        item.put("fonts",font.manifest());item.put("geometry",geometry);item.put("full",root.relativize(full).toString());item.put("detail",root.relativize(detail).toString());
        item.put("tooltipDiagnostics","unsupported; no tooltip painted");item.put("textFit","ChemMaster actual fitText result measured with bitmap metrics; sentinel pixels verify window clip; glyph/provider failures are fatal");manifest.add(item);
    }
    private static void writeContactSheets(Path root,Path out,List<Map<String,Object>> cases) throws Exception {
        for(String screen:List.of("chemmaster","airlock-wire")) {
            var selected=cases.stream().filter(c->screen.equals(c.get("screen"))).toList();
            if(selected.isEmpty())continue;
            List<BufferedImage> tiles=new ArrayList<>();int tileW=0,tileH=0;
            for(var c:selected){@SuppressWarnings("unchecked") var detail=(String)c.get("detail");BufferedImage tile=ImageIO.read(root.resolve(detail).toFile());tiles.add(tile);tileW=Math.max(tileW,tile.getWidth());tileH=Math.max(tileH,tile.getHeight());}
            int columns=4,rows=(tiles.size()+columns-1)/columns;BufferedImage sheet=new BufferedImage(columns*tileW,rows*tileH,BufferedImage.TYPE_INT_ARGB);Graphics2D g=sheet.createGraphics();
            try{g.setColor(new Color(0xff182329,true));g.fillRect(0,0,sheet.getWidth(),sheet.getHeight());for(int i=0;i<tiles.size();i++){int x=(i%columns)*tileW,y=(i/columns)*tileH;g.drawImage(tiles.get(i),x,y,null);}}
            finally{g.dispose();}
            Path sheetPath=out.resolve(screen+"-contact-sheet.png");ImageIO.write(sheet,"png",sheetPath.toFile());
            cases.add(Map.of("screen",screen,"contactSheet",root.relativize(sheetPath).toString(),"tiles",selected.stream().map(c->c.get("state")).toList()));
        }
    }
    private static List<Integer> rect(ChemMasterLayout.Rect r){return List.of(r.x(),r.y(),r.width(),r.height());}
    private static Path configuredGameResources(Path root){
        String configured=System.getProperty("moonstation14.gameResourcesJar");
        if(configured==null||configured.isBlank())configured=System.getenv("MOONSTATION14_GAME_RESOURCES_JAR");
        if(configured==null||configured.isBlank())configured="build/moddev/artifacts/neoforge-21.1.224-client-extra-aka-minecraft-resources.jar";
        Path path=Path.of(configured);return path.isAbsolute()?path:root.resolve(path).normalize();
    }
}
