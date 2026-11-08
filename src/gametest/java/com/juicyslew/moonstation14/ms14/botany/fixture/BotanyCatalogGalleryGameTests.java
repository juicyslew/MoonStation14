package com.juicyslew.moonstation14.ms14.botany.fixture;

import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.gametest.support.RegisteredServerPlayerFixture;
import com.juicyslew.moonstation14.ms14.botany.HydroponicsTrayBlockEntity;
import com.juicyslew.moonstation14.ms14.botany.PlantMetabolismEvaluator;
import com.juicyslew.moonstation14.ms14.botany.ModSeedProfiles;
import com.juicyslew.moonstation14.ms14.botany.BotanyFixtureCommandGameTests;
import com.juicyslew.moonstation14.ms14.botany.BotanySpeciesAvailability;
import com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereService;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server.RegisteredPlayerReadyDriver;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.util.concurrent.atomic.AtomicBoolean;

@GameTestTags({"botany", "botany-catalog-gallery"})
@PrefixGameTestTemplate(false)
public final class BotanyCatalogGalleryGameTests {
    private BotanyCatalogGalleryGameTests() { }

    @GameTest(template="botany_fixture_space", templateNamespace="moonstation14", timeoutTicks=900)
    public static void catalogCommandBuildsResolvedLivingGallery(GameTestHelper helper) {
        ServerLevel level=(ServerLevel)helper.getLevel();
        RegisteredServerPlayerFixture fixture=RegisteredServerPlayerFixture.register(level,"botany-catalog-gallery");
        var terminal=new AtomicBoolean();
        var ready=new AtomicBoolean();
        Runnable cleanup=()->{if(terminal.compareAndSet(false,true)) fixture.close();};
        try {
            helper.runAtTickTime(2,()->{
                try {
                    var active=RegisteredPlayerReadyDriver.readyAndQuery(fixture);
                    BlockPos clear=helper.absolutePos(new BlockPos(2,22,2));
                    var body=active.body();
                    var preliminary=BotanyCatalogGalleryPlan.create(clear.offset(2,0,2),ModSeedProfiles.serverCatalog(),com.juicyslew.moonstation14.ms14.botany.ModProduceProfiles.serverCatalog(),BotanySpeciesAvailability.live());
                    var footprint=preliminary.plannedFootprint();
                    for(int x=footprint.stream().mapToInt(BlockPos::getX).min().orElseThrow()>>4;x<=footprint.stream().mapToInt(BlockPos::getX).max().orElseThrow()>>4;x++)
                        for(int z=footprint.stream().mapToInt(BlockPos::getZ).min().orElseThrow()>>4;z<=footprint.stream().mapToInt(BlockPos::getZ).max().orElseThrow()>>4;z++) level.getChunk(x,z);
                    body.teleportTo(clear.getX()+.5,clear.getY(),clear.getZ()+.5);
                    body.setDeltaMovement(Vec3.ZERO);
                    var dispatcher=level.getServer().getCommands().getDispatcher();
                    var sourceRef=new java.util.concurrent.atomic.AtomicReference<BotanyFixtureCommandGameTests.CapturedSource>();
                    var source=BotanyFixtureCommandGameTests.capture(fixture.player().createCommandSourceStack().withPermission(2),message->{
                         if(!message.startsWith("Catalog gallery ready:")||terminal.get()||!ready.compareAndSet(false,true)) return;
                          helper.runAfterDelay(1,()->{
                         try {
                            var plan=BotanyFixtureCommands.rememberedCatalogPlan(fixture.player());
                            helper.assertTrue(plan!=null,"ready catalog command retains authorized resolved gallery plan");
                            var expected=new java.util.HashSet<>(ModSeedProfiles.serverCatalog().asMap().keySet());
                              expected.removeAll(java.util.Set.of("defaults","killer_tomato","anomaly_berry","gatfruit","fake_capfruit","real_capfruit","spacemans_trumpet").stream().map(id->net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("moonstation14",id)).collect(java.util.stream.Collectors.toSet()));
                             var actual=plan.entries().stream().map(BotanyCatalogGalleryPlan.Entry::species).collect(java.util.stream.Collectors.toSet());
                             helper.assertTrue(expected.size()==65,"independently resolved available catalog contains exactly 65 species: "+expected.size());
                            helper.assertTrue(actual.size()==65&&actual.equals(expected),"gallery species set matches independently resolved available catalog: expected="+expected.size()+", actual="+actual.size());
                            helper.assertTrue(plan.entries().stream().map(BotanyCatalogGalleryPlan.Entry::trayPosition).distinct().count()==65,"all gallery trays have unique positions");
                            for(var entry:plan.entries()) {
                                var tray=level.getBlockEntity(entry.trayPosition());
                                helper.assertTrue(tray instanceof HydroponicsTrayBlockEntity,"actual gallery tray block entity exists for "+entry.species());
                                var plant=((HydroponicsTrayBlockEntity)tray).plantState();
                                helper.assertTrue(entry.species().equals(plant.seed())&&plant.genetics()!=null&&plant.genetics().species().equals(entry.species()),"resolved tray identity matches "+entry.species());
                                int stage=PlantMetabolismEvaluator.rawGrowthStage(plant,plant.effectiveProfile());
                                helper.assertTrue(plant.age()==1&&stage==1,"gallery starts at age 1 visual stage 1 for "+entry.species()+": age="+plant.age()+", stage="+stage);
                                helper.assertTrue(plant.water()==100f&&plant.nutrition()==100f&&plant.health()==plant.genetics().profile().biology().endurance()
                                        &&plant.weeds()==0&&plant.soilToxins()==0&&!((HydroponicsTrayBlockEntity)tray).fixturePaused(),"healthy full-resource active gallery plant: "+entry.species());
                                var atmosphere=AtmosphereService.INSTANCE.sample(level,entry.trayPosition().above());
                                helper.assertTrue(atmosphere.isPresent(),"known atmosphere at gallery tray headspace for "+entry.species());
                                var gas=atmosphere.orElseThrow(); double pressure=gas.pressureKpa(1.0),temperature=gas.temperatureKelvin();
                                var biology=entry.profile().biology();
                                helper.assertTrue(Double.isFinite(pressure)&&pressure>=biology.lowPressure()&&pressure<=biology.highPressure()
                                                &&Double.isFinite(temperature)&&Math.abs(temperature-biology.idealHeat())<=biology.heatTolerance(),
                                        "tray headspace within seed biology bounds for "+entry.species()+": "+pressure+"kPa/"+temperature+"K");
                            }
                            for(var guide:BotanyFixtureSigns.catalogLabels(plan)) {
                                var sign=(net.minecraft.world.level.block.entity.SignBlockEntity)level.getBlockEntity(guide.pos());
                                helper.assertTrue(sign!=null,"catalog guide sign placed at "+guide.pos());
                                for(int i=0;i<guide.lines().size();i++) helper.assertTrue(sign.getFrontText().getMessage(i,false).getString().equals(guide.lines().get(i)),"readable catalog guide line "+guide.lines().get(i));
                            }
                            helper.assertTrue(BotanyFixtureCommandGameTests.execute(dispatcher,"ms14 botany_fixture pause",sourceRef.get().stack())==1,"pause all gallery trays");
                            for(var e:plan.entries()) helper.assertTrue(((HydroponicsTrayBlockEntity)level.getBlockEntity(e.trayPosition())).fixturePaused(),"all trays paused");
                            var paused=plan.entries().stream().map(e->{var p=((HydroponicsTrayBlockEntity)level.getBlockEntity(e.trayPosition())).plantState();return new float[]{p.age(),p.water(),p.nutrition()};}).toList();
                             helper.runAfterDelay(10,()->{
                                 try {
                                     for(var entry:plan.entries()) helper.assertTrue(level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK,entry.trayPosition().above())>=12,
                                             "native block light reaches gallery headspace for "+entry.species());
                                     for(int i=0;i<plan.entries().size();i++) {var entry=plan.entries().get(i);var p=((HydroponicsTrayBlockEntity)level.getBlockEntity(entry.trayPosition())).plantState();helper.assertTrue(p.age()==paused.get(i)[0]&&p.water()==paused.get(i)[1]&&p.nutrition()==paused.get(i)[2],"paused age and resources stable: "+entry.species());}
                                    helper.assertTrue(BotanyFixtureCommandGameTests.execute(dispatcher,"ms14 botany_fixture resume",sourceRef.get().stack())==1,"resume gallery without start command");
                                    record Baseline(float age,long lastCycle) { }
                                    var baseline=plan.entries().stream().map(e->{var t=(HydroponicsTrayBlockEntity)level.getBlockEntity(e.trayPosition());return new Baseline(t.plantState().age(),t.plantState().lastCycle());}).toList();
                                    int wait=plan.entries().stream().map(e->((HydroponicsTrayBlockEntity)level.getBlockEntity(e.trayPosition())).resolvedTrayProfile().cycleDelayTicks()+((HydroponicsTrayBlockEntity)level.getBlockEntity(e.trayPosition())).resolvedTrayProfile().pollDelayTicks()+30).max(Integer::compare).orElse(300);
                                    helper.runAfterDelay(wait,()->{try {int growth=0;for(int i=0;i<plan.entries().size();i++){var e=plan.entries().get(i);var t=(HydroponicsTrayBlockEntity)level.getBlockEntity(e.trayPosition());var p=t.plantState();helper.assertTrue(p.lastCycle()>baseline.get(i).lastCycle()&&!p.dead()&&p.age()>=baseline.get(i).age(),"natural runtime cycle completed for "+e.species());if(p.age()>baseline.get(i).age())growth++;var gas=AtmosphereService.INSTANCE.sample(level,e.trayPosition().above()).orElseThrow(()->new AssertionError("missing post-cycle headspace "+e.species()));var biology=e.profile().biology();double pressure=gas.pressureKpa(1.0),temperature=gas.temperatureKelvin();helper.assertTrue(Double.isFinite(pressure)&&pressure>=biology.lowPressure()&&pressure<=biology.highPressure()&&Double.isFinite(temperature)&&Math.abs(temperature-biology.idealHeat())<=biology.heatTolerance(),"post-cycle headspace compatible for "+e.species()+": "+pressure+"kPa/"+temperature+"K");}helper.assertTrue(growth>0,"ordinary metabolism produced observed age growth");helper.succeed();}catch(Throwable failure){helper.fail("gallery growth phase: "+failure); }finally{cleanup.run();}});
                                 } catch(Throwable failure){cleanup.run();helper.fail("gallery pause phase: "+failure);}
                            });
                         } catch(Throwable failure) { cleanup.run(); helper.fail("gallery ready phase: "+failure); }
                         });
                    });
                    sourceRef.set(source);
                    helper.assertTrue(BotanyFixtureCommandGameTests.execute(dispatcher,"ms14 botany_fixture catalog",source.stack())==1,"actual catalog command accepted: "+source.feedback());
                    helper.runAfterDelay(30,()->{
                        if(!ready.get()&&!terminal.get()){cleanup.run();helper.fail("catalog setup did not become ready: "+source.feedback());}
                    });
                } catch(RuntimeException|Error failure) { cleanup.run(); throw failure; }
            });
        } catch(RuntimeException|Error failure) { cleanup.run(); throw failure; }
    }
}
