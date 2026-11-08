package com.juicyslew.moonstation14.ms14.ui.preview;

import com.google.gson.GsonBuilder;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import com.juicyslew.moonstation14.ms14.atmos.machine.*;
import com.juicyslew.moonstation14.ms14.atmos.machine.ui.AtmosMachineControlSnapshot;
import com.juicyslew.moonstation14.ms14.atmos.machine.ui.client.*;
import com.juicyslew.moonstation14.ms14.atmos.piping.VentilationDeviceConfig;
import com.juicyslew.moonstation14.ms14.atmos.piping.alarm.ui.AirAlarmControlSnapshot;
import com.juicyslew.moonstation14.ms14.atmos.piping.alarm.ui.client.*;
import com.juicyslew.moonstation14.ms14.ui.client.paint.MachineTextFieldPaint;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Independent CPU evidence generator for the production atmosphere UI painters. */
class AtmosUiPreviewTest {
    private static final UUID SESSION=UUID.fromString("00000000-0000-0000-0000-000000000014");
    private PreviewEnglishLanguage language;
    @Test void generateAtmosProductionPreviews() throws Exception {
        Path root=Path.of(System.getProperty("moonstation14.projectDir",".")).toAbsolutePath().normalize();
        String configured=System.getProperty("moonstation14.gameResourcesJar",System.getenv().getOrDefault("MOONSTATION14_GAME_RESOURCES_JAR","build/moddev/artifacts/neoforge-21.1.224-client-extra-aka-minecraft-resources.jar"));
        Path jar=Path.of(configured);if(!jar.isAbsolute())jar=root.resolve(jar).normalize();
        MinecraftBitmapFont font=MinecraftBitmapFont.load(jar);
        language=PreviewEnglishLanguage.load(root,jar);
        String outputRoot=System.getProperty("moonstation14.atmosPreviewOutput", "build/reports/atmos-ui-preview");
        Path out=root.resolve(outputRoot).normalize();Files.createDirectories(out);
        List<Map<String,Object>> cases=new ArrayList<>();
        for(int scale=1;scale<=3;scale++)for(int[] size:new int[][]{{320,240},{640,360}}){
            int w=size[0],h=size[1];
            for(var kind:List.of(AtmosMachineProfile.Kind.PRESSURE_PUMP,AtmosMachineProfile.Kind.VOLUME_PUMP,AtmosMachineProfile.Kind.MIXER,AtmosMachineProfile.Kind.FILTER,AtmosMachineProfile.Kind.HEATER,AtmosMachineProfile.Kind.FREEZER)){
                var controls=machineControls(kind);var settings=machineSettings(kind);
                BufferedImage normal=null;
                 var states=new ArrayList<>(List.of("normal","pending","rejected","numeric-focused"));if(kind==AtmosMachineProfile.Kind.MIXER)states.add("mixer-PERCENT-focused");if(kind==AtmosMachineProfile.Kind.FILTER){states.add("filter-search");states.add("filter-list");}
                for(String state:states){
                    var snapshot=new AtmosMachineControlSnapshot(1,SESSION,1,true,settings,controls,0,false);
                    boolean focusedNumeric=state.equals("numeric-focused")||state.equals("mixer-PERCENT-focused");
                    String expanded=focusedNumeric?(state.equals("mixer-PERCENT-focused")?"ratio":controls.pressure()?"pressure":controls.rate()?"rate":controls.mixerRatio()?"ratio":"thermostat"):state.startsWith("filter-")?"filterGas":null;
                     var gases=state.equals("filter-search")?List.of(AtmosMachineClientModel.gasLabel(Optional.of(GasType.OXYGEN))):state.equals("filter-list")?java.util.Arrays.stream(GasType.values()).limit(5).map(g->AtmosMachineClientModel.gasLabel(Optional.of(g))).toList():List.<String>of();
                     var layout=AtmosMachineLayout.forControls(w,h,controls,focusedNumeric,state.equals("rejected"),expanded,state.startsWith("filter-")?gases.size():5);int x=18,y=16;
                    var numericKind=expanded==null?null:switch(expanded){case "pressure"->AtmosMachineClientModel.Numeric.PRESSURE_KPA;case "rate"->AtmosMachineClientModel.Numeric.RATE_LPS;case "ratio"->AtmosMachineClientModel.Numeric.MIXER_PERCENT;default->AtmosMachineClientModel.Numeric.THERMOSTAT_K;};
                    double canonical=expanded==null?0:switch(numericKind){case PRESSURE_KPA->settings.targetPressureKpa().orElse(0d);case RATE_LPS->settings.transferRateM3PerSecond().orElse(0d);case MIXER_PERCENT->settings.mixerFractionA().orElse(0d);case THERMOSTAT_K->settings.thermostatSetpointK().orElse(0d);};
                    String fieldValue=numericKind==null?"":AtmosMachineClientModel.format(numericKind,canonical);
                    var field=new MachineTextFieldPaint.Snapshot(fieldValue,fieldValue.length(),Math.max(0,fieldValue.length()-1),0,focusedNumeric,true,true,120);
                    var edit=layout.edit();var submit=layout.submit();
                    var numeric=focusedNumeric?new AtmosMachinePaint.NumericPresentation(field,"Set",false):null;
                    if(focusedNumeric){var fieldBounds=new AtmosMachineLayout.Bounds(edit.x(),edit.y(),edit.width(),edit.height());assertEquals(edit.width(),fieldBounds.width());assertTrue(edit.y()+edit.height()<=layout.height());assertTrue(submit.y()+submit.height()<=layout.height());assertTrue(fieldBounds.x()>=0&&fieldBounds.y()>=0&&fieldBounds.x()+fieldBounds.width()<=layout.width()&&fieldBounds.y()+fieldBounds.height()<=layout.height());String titleKey="block.moonstation14."+controls.profileId();assertTrue(language.manifestEntries().containsKey(titleKey),"missing production title translation "+titleKey);}
                    var view=new AtmosMachinePaint.View(snapshot,x,y,w,h,0,0,0,state.equals("pending"),state.equals("rejected")?"Request rejected":null,numeric,expanded,state.equals("filter-list")?"":"oxy",gases,null);
                    BufferedImage image=render(w,h,scale,font,p->AtmosMachinePaint.paint(p,view));
                  var paintedLayout=AtmosMachineLayout.forControls(w,h,controls,focusedNumeric,state.equals("rejected"),expanded,state.startsWith("filter-")?view.gases().size():5);assertEquals(layout.height(),paintedLayout.height(),"preview layout must match production painter content height");assertTrue(x+paintedLayout.width()<=w&&y+paintedLayout.height()<=h,"compact machine panel within viewport");if(state.equals("filter-search"))assertTrue(paintedLayout.gasRow(0).y()+paintedLayout.gasRow(0).height()<=paintedLayout.height());
                    write(out,cases,"atmos-machine-"+kind.name().toLowerCase(Locale.ROOT)+"-"+state,w,h,scale,image,"AtmosMachinePaint.paint",state);
                    if(state.equals("normal"))normal=image;
                    if(state.equals("numeric-focused")){assertTrue(field.focused()&&field.cursor()!=field.anchor());assertTrue(hasColor(image,0xffe5ecee),"production editor must paint field glyphs");assertTrue(hasColor(image,0xfff0f4f5),"production editor must paint caret");assertFalse(Arrays.equals(hash(normal),hash(image)),"focused machine editor must change rendered image");}
                }
            }
            var alarmImages=new HashMap<String,BufferedImage>();
            for(String state:List.of("vent-controls","scrubber-filters","disabled","pending","rejected","search-idle","search-focused","numeric-focused")){
                boolean scrub=state.contains("scrubber")||state.contains("filters");
                var kind=scrub?VentilationDeviceConfig.Kind.SCRUBBER:VentilationDeviceConfig.Kind.VENT;
                var device=new AirAlarmControlSnapshot.Device(UUID.fromString("00000000-0000-0000-0000-000000000021"),kind,new BlockPos(12,64,-3),true,true,!state.equals("disabled"),1,0x55,2,101.325,.075);
                var snapshot=new AirAlarmControlSnapshot(1,SESSION,1,!state.equals("rejected"),true,List.of(device));
                int tab=state.equals("scrubber-filters")?2:(state.startsWith("search-")?0:1);
                 var layout=AirAlarmPaintLayout.forContent(w,h,tab,snapshot.devices().size());int x=18,y=16;
                String search=state.startsWith("search-")?"vent":"";String draft=state.equals("numeric-focused")?"101.325":null;
                 var searchField=new MachineTextFieldPaint.Snapshot("vent",4,1,0,state.equals("search-focused"),true,true,120);
                 var numericField=new MachineTextFieldPaint.Snapshot("101.325",7,2,0,state.equals("numeric-focused"),true,true,120);
                 var view=new AirAlarmPaint.View(snapshot,x,y,w,h,0,0,0,0,tab,search,draft,state.equals("rejected")?"Request rejected":"",state.equals("pending"),state.equals("search-focused"),state.startsWith("search-")?new MachineTextFieldPaint.Snapshot("vent",4,4,1,state.equals("search-focused"),true,state.equals("search-focused"),120):null,state.equals("numeric-focused")?numericField:null);
                 if(state.equals("search-focused"))assertTrue(view.searchEditor().focused(),"search-focused fixture must reach the production painter as focused");
                BufferedImage image=render(w,h,scale,font,p->AirAlarmPaint.paint(p,view));
                 assertTrue(x+layout.width()<=w&&y+layout.height()<=h,"compact alarm panel within viewport");
                write(out,cases,"air-alarm-"+state,w,h,scale,image,"AirAlarmPaint.paint",state);
                alarmImages.put(state,image);
                if(state.equals("search-focused")){assertTrue(searchField.focused()&&searchField.cursor()!=searchField.anchor());}
                if(state.equals("numeric-focused")){assertTrue(numericField.focused()&&numericField.cursor()!=numericField.anchor());}
            }
            assertFalse(Arrays.equals(hash(alarmImages.get("search-idle")),hash(alarmImages.get("search-focused"))),"focused search editor must change rendered image");
            assertFalse(Arrays.equals(hash(alarmImages.get("vent-controls")),hash(alarmImages.get("numeric-focused"))),"focused numeric editor must change rendered image");
        }
        contactSheet(out,cases);
        var images=cases.stream().filter(c->c.containsKey("caseId")).toList();
        assertEquals(210,images.size(),"six machine kinds and one alarm fixture across 35 states, two viewports, and three GUI scales");
        assertEquals(35,images.stream().map(c->c.get("caseId")).distinct().count(),"every Atmos state has one stable case ID");
        assertEquals(Set.of("AtmosMachinePaint.paint","AirAlarmPaint.paint"),images.stream().map(c->(String)c.get("painter")).collect(java.util.stream.Collectors.toSet()));
        Map<String,Object> manifest=new LinkedHashMap<>();manifest.put("producer",getClass().getName()+"#generateAtmosProductionPreviews");
        manifest.put("source","production AtmosMachinePaint.paint and AirAlarmPaint.paint; authoritative immutable control snapshot fixtures; CPU OrderedPaint; not native GPU evidence");
        manifest.put("previewRoot",outputRoot.replace('\\','/'));manifest.put("fontProviders",font.manifest());manifest.put("images",cases);manifest.put("contactSheet",root.relativize(out.resolve("atmos-contact-sheet.png")).toString().replace('\\','/'));
        Files.writeString(out.resolve("manifest.json"),new GsonBuilder().setPrettyPrinting().create().toJson(manifest),StandardCharsets.UTF_8);
    }
    private static AtmosMachineControlSnapshot.Controls machineControls(AtmosMachineProfile.Kind k){
        boolean p=k==AtmosMachineProfile.Kind.PRESSURE_PUMP||k==AtmosMachineProfile.Kind.MIXER,r=k==AtmosMachineProfile.Kind.VOLUME_PUMP||k==AtmosMachineProfile.Kind.FILTER,m=k==AtmosMachineProfile.Kind.MIXER,t=k==AtmosMachineProfile.Kind.HEATER||k==AtmosMachineProfile.Kind.FREEZER;
        String id=switch(k){case PRESSURE_PUMP->"gas_pressure_pump";case VOLUME_PUMP->"gas_volume_pump";case MIXER->"gas_mixer";case FILTER->"gas_filter";case HEATER->"gas_thermo_heater";case FREEZER->"gas_thermo_freezer";default->k.name().toLowerCase(Locale.ROOT);};
        return new AtmosMachineControlSnapshot.Controls(id,k,true,p,r,m,k==AtmosMachineProfile.Kind.FILTER,t,p?4500:0,r?0.2:0,t?0:0,t?1000:0);
    }
    private static AtmosMachineSettings.Values machineSettings(AtmosMachineProfile.Kind k){
        boolean p=k==AtmosMachineProfile.Kind.PRESSURE_PUMP||k==AtmosMachineProfile.Kind.MIXER,r=k==AtmosMachineProfile.Kind.VOLUME_PUMP||k==AtmosMachineProfile.Kind.FILTER,m=k==AtmosMachineProfile.Kind.MIXER,t=k==AtmosMachineProfile.Kind.HEATER||k==AtmosMachineProfile.Kind.FREEZER;
        return new AtmosMachineSettings.Values(2,Optional.of(true),p?Optional.of(101.325):Optional.empty(),r?Optional.of(.075):Optional.empty(),m?Optional.of(.5):Optional.empty(),k==AtmosMachineProfile.Kind.FILTER?Optional.of(GasType.OXYGEN):Optional.empty(),t?Optional.of(293.15):Optional.empty(),Optional.empty());
    }
    @FunctionalInterface private interface Painter {void paint(com.juicyslew.moonstation14.ms14.ui.client.paint.OrderedPaint paint);}
    private static BufferedImage render(int w,int h,int scale,MinecraftBitmapFont font,Painter painter){var p=new CpuOrderedPaint(w*scale,h*scale,font,scale);p.fill(0,0,w*scale,h*scale,0xff5a1b7c);p.push();p.scale(scale,scale,1);painter.paint(new ClipAuditOrderedPaint(p,scale));p.pop();return p.image();}
    private static void write(Path out,List<Map<String,Object>> cases,String id,int w,int h,int scale,BufferedImage image,String painter,String state)throws Exception{
        String file=id+"-"+w+"x"+h+"-scale"+scale+".png";Path path=out.resolve(file);ImageIO.write(image,"png",path.toFile());
        String base=System.getProperty("moonstation14.atmosPreviewOutput", "build/reports/atmos-ui-preview").replace('\\','/');
        cases.add(Map.of("caseId",id,"viewport",List.of(w,h),"guiScale",scale,"state",state,"painter",painter,"source","immutable protocol snapshot fixture","image",base+"/"+file,"sha256",sha(path)));
    }
    private static String sha(Path p)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(p)));}
    private static byte[] hash(BufferedImage image)throws Exception{var digest=MessageDigest.getInstance("SHA-256");for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++){int p=image.getRGB(x,y);digest.update((byte)(p>>>24));digest.update((byte)(p>>>16));digest.update((byte)(p>>>8));digest.update((byte)p);}return digest.digest();}
    private static boolean hasColor(BufferedImage image,int color){for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++)if(image.getRGB(x,y)==color)return true;return false;}
    @AfterEach void restoreLanguage(){if(language!=null){language.restore();language=null;}}
    private static void contactSheet(Path out,List<Map<String,Object>> cases)throws Exception{
        final int columns=4,tileWidth=320,tileHeight=200,imageWidth=296,imageHeight=164,labelHeight=28;
        int rows=(cases.size()+columns-1)/columns;
        var sheet=new BufferedImage(columns*tileWidth,rows*tileHeight,BufferedImage.TYPE_INT_ARGB);var g=sheet.createGraphics();
        try{
            g.setColor(new java.awt.Color(0xff182329,true));g.fillRect(0,0,sheet.getWidth(),sheet.getHeight());
            g.setFont(new java.awt.Font(java.awt.Font.SANS_SERIF,java.awt.Font.PLAIN,9));g.setColor(java.awt.Color.WHITE);
            for(int index=0;index<cases.size();index++){
                var item=cases.get(index);var source=out.resolve(Path.of((String)item.get("image")).getFileName());
                BufferedImage image=ImageIO.read(source.toFile());if(image==null)throw new java.io.IOException("Unable to decode preview image "+source);
                int x=(index%columns)*tileWidth,y=(index/columns)*tileHeight;
                double scale=Math.min((double)imageWidth/image.getWidth(),(double)imageHeight/image.getHeight());
                int w=Math.max(1,(int)Math.round(image.getWidth()*scale)),h=Math.max(1,(int)Math.round(image.getHeight()*scale));
                g.drawImage(image,x+(tileWidth-w)/2,y+(imageHeight-h)/2,w,h,null);image.flush();
                g.drawString((String)item.get("caseId"),x+8,y+imageHeight+12);
                g.drawString(item.get("viewport")+" gui"+item.get("guiScale")+" · "+item.get("state"),x+8,y+imageHeight+25);
            }
        }finally{g.dispose();}
        ImageIO.write(sheet,"png",out.resolve("atmos-contact-sheet.png").toFile());sheet.flush();
    }
}
