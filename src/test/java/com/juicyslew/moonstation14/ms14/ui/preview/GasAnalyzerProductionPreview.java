package com.juicyslew.moonstation14.ms14.ui.preview;

import com.google.gson.GsonBuilder;
import com.juicyslew.moonstation14.ms14.atmos.analyzer.GasAnalyzerSample;
import com.juicyslew.moonstation14.ms14.atmos.analyzer.GasAnalyzerSnapshot;
import com.juicyslew.moonstation14.ms14.atmos.analyzer.GasAnalyzerPipeSamples;
import com.juicyslew.moonstation14.ms14.atmos.piping.PipeTopology;
import com.juicyslew.moonstation14.ms14.atmos.piping.Ss14VentilationDefaults;
import com.juicyslew.moonstation14.ms14.atmos.analyzer.client.GasAnalyzerLayout;
import com.juicyslew.moonstation14.ms14.atmos.analyzer.client.GasAnalyzerPaint;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.analyzer.ModGasAnalyzers;
import com.juicyslew.moonstation14.ms14.atmos.machine.ModAtmosMachines;
import com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachineProfile;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeManager;
import com.juicyslew.moonstation14.ms14.ui.client.window.WindowChromeButtons;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.*;
import java.util.List;
import javax.imageio.ImageIO;

/** Adds deterministic cases by invoking the exact production gas analyzer painter. */
final class GasAnalyzerProductionPreview {
    private static final ResourceLocation PROFILE=ResourceLocation.fromNamespaceAndPath("moonstation14","atmosphere_analyzer");
    private static final UUID SESSION=UUID.fromString("00000000-0000-0000-0000-000000000041");
    private static final UUID BODY=UUID.fromString("00000000-0000-0000-0000-000000000042");
    private GasAnalyzerProductionPreview() { }

    static List<Map<String,Object>> generate(Path root,MinecraftBitmapFont font,PreviewEnglishLanguage language) throws Exception {
        Path output=root.resolve("build/reports/feature-readiness/previews/analyzer");Files.createDirectories(output);
        String sourceHash=sourceHash(root);
        var profile=resolvedProfile();
        var mixer=resolvedMixerProfile();
        if(mixer.ports().size()!=3||!mixer.ports().stream().map(AtmosMachineProfile.Port::name).collect(java.util.stream.Collectors.toSet())
                .equals(java.util.Set.of(AtmosMachineProfile.PortName.INLET,AtmosMachineProfile.PortName.FILTER,AtmosMachineProfile.PortName.OUTLET)))
            throw new AssertionError("resolved gas_mixer ports differ from typed gas-trinary composition: "+mixer.ports());
        for(var port:mixer.ports())if(Double.compare(port.volumeM3(),.2)!=0)
            throw new AssertionError("resolved gas_mixer default port volume differs from gas_pipe_base 200 L: "+port);
        if(profile.maximumRange()!=6||profile.defaultSampleDistance()!=1||profile.updateIntervalTicks()!=20)
            throw new AssertionError("preview profile defaults differ from analyzer source contract: "+profile);
        List<Map<String,Object>> manifest=new ArrayList<>();Map<String,BufferedImage> images=new LinkedHashMap<>();
        List<String> states=List.of("distance-0","remote-graph","empty","blocked","waiting","retarget-waiting","distance-6","long-gas-list",
                "pipe-vacuum","pipe-mixed","device-mixer","device-mixer-scrolled","unary-vent");
        List<Integer> scales=List.of(1,2,3);List<List<Integer>> viewports=List.of(List.of(320,240),List.of(640,360));
        for(int scale:scales)for(List<Integer> viewport:viewports)for(String state:states) {
            int vw=viewport.get(0),vh=viewport.get(1);var layout=GasAnalyzerLayout.calculate(vw,vh,state.startsWith("pipe-")||state.startsWith("device-")||state.startsWith("unary-"));
            if(layout.width()*layout.height()>18_000)throw new AssertionError("analyzer exceeds compact quarter-screen footprint");
            if(layout.gasList().x()<0||layout.gasList().y()<0||layout.gasList().x()+layout.gasList().width()>layout.width()
                    ||layout.gasList().y()+layout.gasList().height()>layout.height())throw new AssertionError("gas list leaves window bounds");
            if(layout.close().x()+layout.close().width()>layout.width()||layout.close().y()+layout.close().height()>layout.height())throw new AssertionError("close control leaves window bounds");
            int x=Math.max(0,(vw-layout.width())/2),y=Math.max(0,(vh-layout.height())/2);
            if(x+layout.width()>vw||y+layout.height()>vh)throw new AssertionError("analyzer window leaves logical viewport");
            var local=sample(false);var remote=state.equals("empty")?empty():sample(state.equals("long-gas-list"));
            GasAnalyzerSnapshot snapshot=switch(state) {
                case "waiting" -> null;
                case "retarget-waiting" -> snapshot(GasAnalyzerSnapshot.Status.READY,local,remote,1,new BlockPos(4,65,6));
                case "blocked" -> snapshot(GasAnalyzerSnapshot.Status.BLOCKED,local,null,2,new BlockPos(4,65,6));
                case "empty" -> snapshot(GasAnalyzerSnapshot.Status.READY,empty(),empty(),1,new BlockPos(4,65,6));
                case "distance-6" -> snapshot(GasAnalyzerSnapshot.Status.READY,local,remote,6,new BlockPos(9,65,6));
                case "distance-0" -> snapshot(GasAnalyzerSnapshot.Status.READY,local,local,0,new BlockPos(4,65,5));
                case "pipe-vacuum" -> named(GasAnalyzerPipeSamples.Source.VISIBLE_PIPE,new BlockPos(4,65,6),List.of(
                        namedEntry(PipeTopology.Layer.PRIMARY,"",new GasMixture(Map.of(),GasMixture.MIN_TEMPERATURE_KELVIN,.2),.2,profile)));
                case "pipe-mixed" -> named(GasAnalyzerPipeSamples.Source.VISIBLE_PIPE,new BlockPos(4,65,6),List.of(
                        namedEntry(PipeTopology.Layer.PRIMARY,"",mixture(3,2,1,.2,1),.2,profile)));
                case "device-mixer","device-mixer-scrolled" -> named(GasAnalyzerPipeSamples.Source.DEVICE,new BlockPos(4,65,6),
                        mixer.ports().stream().map(port->{
                            GasMixture gas=switch(port.name()){
                                case INLET->mixture(4,3,2,port.volumeM3(),1);
                                case FILTER->mixture(2,5,3,port.volumeM3(),2);
                                case OUTLET->mixture(6,2,4,port.volumeM3(),3);
                                default->throw new IllegalStateException("unexpected resolved mixer port "+port.name());};
                            String portId=port.name().name().toLowerCase(Locale.ROOT);
                            String label=GasAnalyzerPipeSamples.deviceLabel(mixer.kind(),portId,PipeTopology.Layer.PRIMARY,
                                    net.minecraft.core.Direction.valueOf(port.direction().name()));
                            return namedEntry(PipeTopology.Layer.PRIMARY,portId,label,gas,port.volumeM3(),profile);
                        }).toList());
                case "unary-vent" -> unary(new BlockPos(4,65,6),new GasMixture(
                        Map.of(GasType.OXYGEN,4d,GasType.NITROGEN,2d),293.15,Ss14VentilationDefaults.PIPE_NODE_VOLUME_M3),profile);
                default -> snapshot(GasAnalyzerSnapshot.Status.READY,local,remote,1,new BlockPos(4,65,6));
            };
            var view=new GasAnalyzerPaint.View(snapshot,x,y,vw,vh,
                    state.equals("blocked")?x+layout.close().x()+1:Integer.MIN_VALUE,
                    state.equals("blocked")?y+layout.close().y()+1:Integer.MIN_VALUE,
                    state.equals("distance-6")?profile.maximumRange():snapshot==null?profile.defaultSampleDistance():snapshot.requestedDistance(),profile.maximumRange(),
                    state.equals("long-gas-list")||state.equals("device-mixer-scrolled")?5:0,state.equals("retarget-waiting"));
            var paint=new CpuOrderedPaint(vw*scale,vh*scale,font,scale);
            paint.fill(0,0,vw*scale,vh*scale,0xff18242a);paint.push();paint.scale(scale,scale,1);
            GasAnalyzerPaint.paint(new ClipAuditOrderedPaint(paint,scale),view);paint.pop();
            BufferedImage image=paint.image();String id=state+"-"+vw+"x"+vh+"-scale"+scale;
            var labels=paint.paintedText();
            assertContains(labels,"Gas Analyzer","production title is painted");
            if(layout.width()!=152&&vw==320)throw new AssertionError("320px viewport should use compact 152px analyzer width");
            if(state.equals("waiting")||state.equals("retarget-waiting"))
                assertContains(labels,"Waiting for current cell sample","stale/no target data never paints as current graph");
            if(state.equals("remote-graph"))assertContains(labels,"Oxygen","localized species label reaches the production painter");
            if(state.equals("blocked"))assertContains(labels,"Opaque obstruction","blocked status is painted from authoritative state");
            if(state.equals("remote-graph")){
                assertContains(labels,"101.33 kPa","pressure uses two-decimal precision");
                assertContains(labels,"293.15 K","temperature uses two-decimal precision");
                assertContains(labels,"mol","species amount remains visible alongside its percentage");
                assertContains(labels,"%","species percentage is painted");
            }
            if(state.equals("long-gas-list")){
                assertContains(labels,"e120 mol","oversized finite moles use compact scientific notation with units");
                if(labels.stream().anyMatch(s->s.contains("e+120")||s.contains("e0120")))
                    throw new AssertionError("scientific exponent must omit redundant plus/zero padding: "+labels);
            }
            assertGasTextRows(paint.textRuns(),x,layout,scale,id);
            if(state.equals("pipe-mixed")||state.equals("device-mixer")){
                assertContains(labels,"Water vapor","full water-vapor gas name is visible");
                assertContains(labels,"Carbon dioxide","full carbon-dioxide gas name is visible");
                assertContains(labels,"Nitrous oxide","full nitrous-oxide gas name is visible");
                assertContains(labels,"mol","moles and percentages remain visible");
            }
            if(state.equals("device-mixer")){
                assertContains(labels,"First inlet","resolved mixer inlet receives its semantic name");
                assertContains(labels,"Second inlet","resolved mixer filter port is the second inlet");
                if(labels.contains("Filtered outlet"))throw new AssertionError("mixer second inlet mislabeled as filter output");
            }
            if(state.equals("device-mixer-scrolled")){
                assertContains(labels,"Second inlet","scroll reveals the second mixer inlet label");
                assertContains(labels,"Outlet","scroll reaches the third mixer port");
            }
            if(state.equals("unary-vent")){
                assertContains(labels,"Primary pipe","visible vent reports its named connected pipe node");
                assertContains(labels,"200 L","unary source displays actual pipe-node volume, not component volume");
            }
            if(labels.stream().anyMatch(s->s.contains("Distance:")||s.contains("Wheel:")||s.contains("Selected cell")||s.matches(".*\\(-?\\d+,.*")))
                throw new AssertionError("player-facing analyzer UI exposes control instructions or coordinates");
            var close=WindowChromeButtons.closeBounds(0,0,layout.close().width(),layout.close().height());
            if(layout.close().width()!=close.width())throw new AssertionError("production close hitbox width differs from analyzer layout");
            Path png=output.resolve(id+".png");ImageIO.write(image,"png",png.toFile());images.put(id,image);
            var entry=new LinkedHashMap<String,Object>();entry.put("caseId",id);entry.put("state",state);
            entry.put("viewportGui",List.of(vw,vh));entry.put("guiScale",scale);entry.put("canvasPx",List.of(vw*scale,vh*scale));
            entry.put("prototypeIds",List.of(PROFILE.toString()));entry.put("window",List.of(x,y,layout.width(),layout.height()));
            entry.put("mode",state);entry.put("distance",view.distance());entry.put("png",root.relativize(png).toString());
            entry.put("pngSha256",sha256(Files.readAllBytes(png)));manifest.add(entry);
        }
        int cols=6,tileW=160,tileH=120,labelH=18,rows=(images.size()+cols-1)/cols;
        BufferedImage sheet=new BufferedImage(cols*tileW,rows*(tileH+labelH),BufferedImage.TYPE_INT_ARGB);
        Graphics2D g=sheet.createGraphics();try{g.setColor(new Color(0xff10191d,true));g.fillRect(0,0,sheet.getWidth(),sheet.getHeight());
            int i=0;for(var entry:images.entrySet()){int col=i%cols,row=i/cols,px=col*tileW,py=row*(tileH+labelH);g.drawImage(entry.getValue(),px,py,tileW,tileH,null);g.setColor(Color.WHITE);g.drawString(entry.getKey(),px+3,py+tileH+13);i++;}}
        finally{g.dispose();}
        Path contact=output.resolve("contact-sheet.png");ImageIO.write(sheet,"png",contact.toFile());
        var report=new LinkedHashMap<String,Object>();report.put("producer",GasAnalyzerProductionPreview.class.getName());
        report.put("source","GasAnalyzerPaint.paint production painter; resolved analyzer profile; immutable server-snapshot DTO fixtures");
        report.put("sourceHashSha256",sourceHash);report.put("prototypeIds",List.of(PROFILE.toString()));
        report.put("resolvedProfile",Map.of("maximumRange",profile.maximumRange(),"defaultSampleDistance",profile.defaultSampleDistance(),
                "updateIntervalTicks",profile.updateIntervalTicks(),"minimumGasMoles",profile.minimumGasMoles()));
        int expectedCaseCount=states.size()*viewports.size()*scales.size();
        if(manifest.size()!=expectedCaseCount)throw new AssertionError("preview matrix count mismatch: "+manifest.size()+" != "+expectedCaseCount);
        report.put("expectedCaseCount",expectedCaseCount);report.put("matrix",Map.of("states",states,"viewports",viewports,"guiScales",scales));
        report.put("caseIds",manifest.stream().map(c->c.get("caseId")).toList());
        report.put("nonVisibleStates",Map.of("closed","authoritative close dismisses the nonmodal workspace; no closed window is painted"));
        report.put("cases",manifest);report.put("contactSheet",Map.of("path",root.relativize(contact).toString(),"sha256",sha256(Files.readAllBytes(contact))));
        Files.writeString(output.resolve("manifest.json"),new GsonBuilder().setPrettyPrinting().create().toJson(report),StandardCharsets.UTF_8);
        return List.copyOf(manifest);
    }
    private static void assertGasTextRows(List<CpuOrderedPaint.TextRun> runs,int windowX,GasAnalyzerLayout.Layout layout,
                                          int scale,String caseId){
        var names=List.of("Oxygen","Nitrogen","Carbon dioxide","Plasma","Tritium","Water vapor","Ammonia","Nitrous oxide","Frezon");
        float expectedLeft=(windowX+layout.gasList().x())*scale;
        float expectedRight=(windowX+layout.gasList().x()+layout.gasList().width())*scale;
        for(var name:runs)if(names.contains(name.text())){
            var quantity=runs.stream().filter(r->r.y()==name.y()&&r.x()>name.x()&&r.text().contains("mol")&&r.text().contains("%"))
                    .findFirst().orElseThrow(()->new AssertionError("gas row lacks same-line moles and percent; case="+caseId+" species="+name));
            if(name.x()+name.width()>quantity.x()+.01||name.x()<expectedLeft-.01
                    ||quantity.x()+quantity.width()>expectedRight+.01)
                throw new AssertionError("gas text overlap/out-of-bounds; case="+caseId+", scale="+scale
                        +", expected physical bounds=["+expectedLeft+","+expectedRight+"]: "+name+" / "+quantity);
        }
        for(var quantity:runs)if(quantity.text().contains("mol")&&quantity.text().contains("%")
                &&runs.stream().noneMatch(r->names.contains(r.text())&&r.y()==quantity.y()&&r.x()<quantity.x()))
            throw new AssertionError("orphan quantity row; case="+caseId+", scale="+scale+", text="+quantity);
    }
    private static GasAnalyzerSnapshot snapshot(GasAnalyzerSnapshot.Status status,GasAnalyzerSample local,GasAnalyzerSample target,int distance,BlockPos targetCell){
        return new GasAnalyzerSnapshot(SESSION,1,3,0,20,1,BODY,"preview-source-token",ResourceLocation.withDefaultNamespace("overworld"),
                new BlockPos(4,65,5),targetCell,distance,status,local,target,false);
    }
    private static GasAnalyzerSnapshot named(GasAnalyzerPipeSamples.Source source,BlockPos pos,List<GasAnalyzerPipeSamples.Entry> entries){
        return new GasAnalyzerSnapshot(SESSION,1,3,0,20,1,BODY,"preview-source-token",ResourceLocation.withDefaultNamespace("overworld"),
                new BlockPos(4,65,5),pos,1,GasAnalyzerSnapshot.Status.READY,null,null,false,source,entries);
    }
    private static GasAnalyzerSnapshot unary(BlockPos host,GasMixture mixture,
            com.juicyslew.moonstation14.ms14.atmos.analyzer.GasAnalyzerProfile profile){
        double volume=Ss14VentilationDefaults.PIPE_NODE_VOLUME_M3;
        var pipe=host.west();var node=new PipeTopology.NodeKey(pipe,PipeTopology.Layer.PRIMARY);
        var identity=UUID.nameUUIDFromBytes(("gas-analyzer-preview:unary:"+node).getBytes(StandardCharsets.UTF_8));
        var entry=new GasAnalyzerPipeSamples.Entry(new GasAnalyzerPipeSamples.Stamp(node,identity,1,volume),
                GasAnalyzerPipeSamples.visiblePipeLabel(PipeTopology.Layer.PRIMARY),GasAnalyzerSample.fromMixture(mixture,volume,profile));
        return new GasAnalyzerSnapshot(SESSION,1,3,0,20,1,BODY,"preview-source-token",ResourceLocation.withDefaultNamespace("overworld"),
                new BlockPos(4,65,5),host,1,GasAnalyzerSnapshot.Status.READY,null,null,false,
                GasAnalyzerPipeSamples.Source.UNARY_DEVICE,List.of(entry));
    }
    private static GasAnalyzerPipeSamples.Entry namedEntry(PipeTopology.Layer layer,String port,GasMixture mixture,double volume,
                                                           com.juicyslew.moonstation14.ms14.atmos.analyzer.GasAnalyzerProfile profile){
        return namedEntry(layer,port,port.isEmpty()?GasAnalyzerPipeSamples.visiblePipeLabel(layer):
                GasAnalyzerPipeSamples.deviceLabel(null,port,layer,null),mixture,volume,profile);
    }
    private static GasAnalyzerPipeSamples.Entry namedEntry(PipeTopology.Layer layer,String port,String labelKey,GasMixture mixture,double volume,
                                                           com.juicyslew.moonstation14.ms14.atmos.analyzer.GasAnalyzerProfile profile){
        if(Float.compare((float)mixture.volumeCubicMeters(),(float)volume)!=0)
            throw new IllegalArgumentException("preview mixture volume mismatch");
        var key=new PipeTopology.NodeKey(new BlockPos(4,65,6),layer,port);
        var identity=UUID.nameUUIDFromBytes(("gas-analyzer-preview:"+key).getBytes(StandardCharsets.UTF_8));
        return new GasAnalyzerPipeSamples.Entry(new GasAnalyzerPipeSamples.Stamp(key,identity,1,volume),labelKey,
                GasAnalyzerSample.fromMixture(mixture,volume,profile));
    }
    private static GasAnalyzerSample empty(){return new GasAnalyzerSample(1000,0,2.7,List.of());}
    private static GasMixture mixture(double water,double carbonDioxide,double nitrousOxide,double volume,int variant){
        return new GasMixture(Map.of(GasType.WATER_VAPOR,water,GasType.CARBON_DIOXIDE,carbonDioxide,
                GasType.NITROUS_OXIDE,nitrousOxide),293.15+variant,volume);
    }
    private static GasAnalyzerSample sample(boolean longList){
        var all=List.of(GasType.NITROGEN,GasType.OXYGEN,GasType.CARBON_DIOXIDE,GasType.PLASMA,GasType.TRITIUM,
                GasType.WATER_VAPOR,GasType.AMMONIA,GasType.NITROUS_OXIDE,GasType.FREZON);
        int count=longList?9:5;List<GasAnalyzerSample.GasAmount> gases=new ArrayList<>();double total=0;
        for(int i=0;i<count;i++){double amount=count-i+0.25; if(longList)amount*=1e120;total+=amount;gases.add(new GasAnalyzerSample.GasAmount(all.get(i),amount,0));}
        double sum=total;gases=gases.stream().map(g->new GasAnalyzerSample.GasAmount(g.gas(),g.moles(),g.moles()*100/sum)).toList();
        return new GasAnalyzerSample(1000,101.325,293.15,gases);
    }
    private static String sourceHash(Path root)throws Exception{
        var md=MessageDigest.getInstance("SHA-256");
        for(String name:List.of("src/main/java/com/juicyslew/moonstation14/ms14/atmos/analyzer/client/GasAnalyzerPaint.java",
                "src/main/java/com/juicyslew/moonstation14/ms14/atmos/analyzer/client/GasAnalyzerLayout.java",
                "src/main/java/com/juicyslew/moonstation14/ms14/atmos/analyzer/client/GasAnalyzerWorkspace.java",
                "src/main/java/com/juicyslew/moonstation14/ms14/atmos/analyzer/client/GasAnalyzerRows.java",
                "src/main/java/com/juicyslew/moonstation14/ms14/atmos/analyzer/client/GasAnalyzerInputPolicy.java",
                "src/main/java/com/juicyslew/moonstation14/ms14/atmos/analyzer/GasAnalyzerSessionService.java",
                "src/main/java/com/juicyslew/moonstation14/ms14/atmos/analyzer/GasAnalyzerSessionPolicy.java",
                "src/main/java/com/juicyslew/moonstation14/ms14/atmos/analyzer/GasAnalyzerSnapshot.java",
                "src/main/java/com/juicyslew/moonstation14/ms14/atmos/analyzer/GasAnalyzerPipeSamples.java",
                "src/main/java/com/juicyslew/moonstation14/ms14/atmos/analyzer/GasAnalyzerSample.java",
                "src/main/java/com/juicyslew/moonstation14/ms14/atmos/piping/Ss14VentilationDefaults.java",
                "src/main/java/com/juicyslew/moonstation14/ms14/atmos/visual/GasVisibility.java",
                "src/main/resources/data/moonstation14/moonstation14/gas_analyzer/gas_analyzer_base.json",
                "src/main/resources/data/moonstation14/moonstation14/gas_analyzer/atmosphere_analyzer.json",
                "src/main/resources/assets/moonstation14/lang/en_us.json",
                "src/test/java/com/juicyslew/moonstation14/ms14/ui/preview/GasAnalyzerProductionPreview.java",
                "src/main/resources/data/moonstation14/moonstation14/atmos_machine/atmos_machine_base.json",
                "src/main/resources/data/moonstation14/moonstation14/atmos_machine/gas_pipe_sans_layers.json",
                "src/main/resources/data/moonstation14/moonstation14/atmos_machine/gas_pipe_base.json",
                "src/main/resources/data/moonstation14/moonstation14/atmos_machine/gas_trinary_base.json",
                "src/main/resources/data/moonstation14/moonstation14/atmos_machine/gas_mixer.json")) {
            md.update(name.getBytes(StandardCharsets.UTF_8));md.update(Files.readAllBytes(root.resolve(name)));
        }
        return HexFormat.of().formatHex(md.digest());
    }
    private static com.juicyslew.moonstation14.ms14.atmos.analyzer.GasAnalyzerProfile resolvedProfile()throws Exception{
        var manager=new PrototypeManager();manager.register(ModGasAnalyzers.TYPE);
        var raw=new LinkedHashMap<ResourceLocation,JsonObject>();
        for(String name:List.of("gas_analyzer_base","atmosphere_analyzer")){
            String path="/data/moonstation14/moonstation14/gas_analyzer/"+name+".json";
            try(var stream=GasAnalyzerProductionPreview.class.getResourceAsStream(path)){
                if(stream==null)throw new IllegalStateException("missing analyzer prototype resource "+path);
                raw.put(ModGasAnalyzers.id(name),JsonParser.parseReader(new java.io.InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonObject());
            }
        }
        manager.reload(ModGasAnalyzers.TYPE,raw);
        return ModGasAnalyzers.resolve(manager.snapshot(ModGasAnalyzers.TYPE));
    }
    private static AtmosMachineProfile resolvedMixerProfile()throws Exception{
        var manager=new PrototypeManager();manager.register(ModAtmosMachines.TYPE);
        var raw=new LinkedHashMap<ResourceLocation,JsonObject>();
        for(String name:List.of("atmos_machine_base","gas_pipe_sans_layers","gas_pipe_base","gas_trinary_base","gas_mixer")){
            String path="/data/moonstation14/moonstation14/atmos_machine/"+name+".json";
            try(var stream=GasAnalyzerProductionPreview.class.getResourceAsStream(path)){
                if(stream==null)throw new IllegalStateException("missing atmosphere-machine prototype "+path);
                raw.put(ModAtmosMachines.id(name),JsonParser.parseReader(new java.io.InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonObject());
            }
        }
        manager.reload(ModAtmosMachines.TYPE,raw);
        return manager.snapshot(ModAtmosMachines.TYPE).get(ModAtmosMachines.id("gas_mixer"));
    }
    private static String sha256(byte[] value)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));}
    private static void assertContains(List<String> values,String part,String message){
        if(values.stream().noneMatch(value->value.contains(part)))throw new AssertionError(message+"; painted="+values);
    }
}
