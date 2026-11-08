package com.juicyslew.moonstation14.ms14.botany;

import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.gametest.support.RegisteredServerPlayerFixture;
import com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait;
import com.juicyslew.moonstation14.gametest.support.PowerFixtureReadiness;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server.RegisteredPlayerReadyDriver;
import com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestTags({"botany-mutation-showcase", "botany-fixture"})
@PrefixGameTestTemplate(false)
public final class BotanyMutationShowcaseGameTests {
    private BotanyMutationShowcaseGameTests() { }

    @GameTest(template="botany_fixture_space", templateNamespace="moonstation14", timeoutTicks=500)
    public static void registeredPlayerCommandArrangesMutationShowcase(GameTestHelper helper) {
        ServerLevel level=(ServerLevel)helper.getLevel();
        var fixture=RegisteredServerPlayerFixture.register(level,"botany-mutation-showcase");
        helper.runAtTickTime(2,()->{
            try {
                var active=RegisteredPlayerReadyDriver.readyAndQuery(fixture);
                BlockPos clear=helper.absolutePos(new BlockPos(2,22,2));
                BlockPos origin=clear.offset(2,0,2);
                var footprint=SmallBotanyFixtureLayout.plannedFootprint(origin);
                int minX=footprint.stream().mapToInt(BlockPos::getX).min().orElseThrow(), maxX=footprint.stream().mapToInt(BlockPos::getX).max().orElseThrow();
                int minZ=footprint.stream().mapToInt(BlockPos::getZ).min().orElseThrow(), maxZ=footprint.stream().mapToInt(BlockPos::getZ).max().orElseThrow();
                for(int x=minX>>4;x<=maxX>>4;x++) for(int z=minZ>>4;z<=maxZ>>4;z++) level.getChunk(x,z);
                var body=active.body(); body.teleportTo(clear.getX()+.5,clear.getY(),clear.getZ()+.5);
                var source=BotanyFixtureCommandGameTests.capture(fixture.player().createCommandSourceStack().withPermission(2));
                var dispatcher=level.getServer().getCommands().getDispatcher();
                helper.assertTrue(BotanyFixtureCommandGameTests.execute(dispatcher,"ms14 botany_fixture",source.stack())==1,"fixture command accepted");
                  BoundedGameTestWait.await(helper, 100,
                         () -> "botany mutation showcase setup did not report ready; feedback=" + source.feedback(),
                          () -> source.feedback().stream().anyMatch(m -> m.contains("Botany fixture ready")), () -> {
                      try {
                         helper.assertTrue(BotanyFixtureCommandGameTests.execute(dispatcher,"ms14 botany_fixture start 41 52",source.stack())==1,
                                 "explicitly start showcase stations 41 through 52 so ordinary first-cycle polling runs");
                         for(int i=0;i<12;i++) {
                            BlockPos pos=SmallBotanyFixtureLayout.showcaseTray(origin,i);
                            helper.assertTrue(level.getBlockEntity(pos) instanceof HydroponicsTrayBlockEntity t && t.isLoadedValid() && t.plantState()!=null,
                                    "showcase tray exists and is initialized: "+i);
                            BlockPos sign=SmallBotanyFixtureLayout.showcaseSign(origin,i);
                            helper.assertTrue(level.getBlockState(sign).is(net.minecraft.world.level.block.Blocks.OAK_SIGN)
                                    && level.getBlockEntity(sign) instanceof net.minecraft.world.level.block.entity.SignBlockEntity,
                                    "showcase sign exists: "+i);
                        }
                        for(int i=0;i<12;i++) {
                            var tray=(HydroponicsTrayBlockEntity)level.getBlockEntity(SmallBotanyFixtureLayout.showcaseTray(origin,i));
                            var plant=tray.plantState();
                            if(i>=6&&i<=9) helper.assertTrue(plant.ready()&&!plant.dead(),"showcase station "+i+" is harvest-ready");
                            if(i==6) helper.assertTrue(plant.genetics().mutations().stream().anyMatch(m->m.name().equals("Slippery")&&m.slippery()!=null&&!m.appliesToPlant()&&m.appliesToProduce()&&m.persists()),"station 6 has actual Slippery genetics");
                            if(i==7) helper.assertTrue(plant.genetics().profile().chemicals().keySet().stream().anyMatch(k->!plant.genetics().profile().chemicals().get(k).inherent()),"station 7 has changed chemistry");
                            if(i==8) helper.assertTrue(plant.seed().toString().equals("moonstation14:wheat"),"station 8 is wheat");
                            if(i==9) helper.assertTrue(plant.seed().toString().equals("moonstation14:meatwheat"),"station 9 is meatwheat");
                             if(i<6) helper.assertTrue(plant.seed()!=null && plant.genetics()!=null&&!plant.dead()
                                      &&plant.health()==plant.genetics().profile().biology().endurance()
                                      &&plant.water()==100&&plant.nutrition()==100&&plant.skipAging()==0,
                                      "paired showcase specimens start at full resolved endurance with full resources at station "+i);
                        }
                        var consume=(HydroponicsTrayBlockEntity)level.getBlockEntity(SmallBotanyFixtureLayout.showcaseTray(origin,10));
                        var exude=(HydroponicsTrayBlockEntity)level.getBlockEntity(SmallBotanyFixtureLayout.showcaseTray(origin,11));
                        helper.assertTrue(consume.plantState().effectiveProfile().consumeGasses().keySet().equals(java.util.Set.of(com.juicyslew.moonstation14.ms14.atmos.core.GasType.NITROGEN))
                                &&consume.plantState().effectiveProfile().exudeGasses().isEmpty(),"station 10 genetics consume nitrogen only");
                        helper.assertTrue(exude.plantState().effectiveProfile().exudeGasses().keySet().equals(java.util.Set.of(com.juicyslew.moonstation14.ms14.atmos.core.GasType.OXYGEN))
                                &&exude.plantState().effectiveProfile().consumeGasses().isEmpty(),"station 11 genetics exude oxygen only");
                         var air=AtmosphereService.INSTANCE.readAtmosphere(level,SmallBotanyFixtureLayout.showcaseTray(origin,10).above()).orElseThrow();
                         helper.assertTrue(air.status()==com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereReading.Status.FINITE,"showcase air cell is known finite");
                         BoundedGameTestWait.await(helper, 80, () -> "AT46 atmosphere was not cached as known before examination",
                                 () -> {
                                     var at=(HydroponicsTrayBlockEntity)level.getBlockEntity(SmallBotanyFixtureLayout.stationTray(origin,"AT"));
                                     return at!=null&&at.cachedAppearanceEnvironment().isPresent();
                                 }, () -> {
                             assertExamine(helper,level,body,SmallBotanyFixtureLayout.stationTray(origin,"AP"),"examine.moonstation14.botany.plant.unviable");
                              var aged=(HydroponicsTrayBlockEntity)level.getBlockEntity(SmallBotanyFixtureLayout.stationTray(origin,"AR"));
                              var agedPlant=aged.plantState();
                              var control=(HydroponicsTrayBlockEntity)level.getBlockEntity(SmallBotanyFixtureLayout.stationTray(origin,"AQ"));
                               helper.assertTrue(!agedPlant.dead()&&agedPlant.age()>agedPlant.genetics().profile().lifecycle().lifespan()
                                       &&!control.plantState().dead()
                                       &&control.plantState().age()<=control.plantState().genetics().profile().lifecycle().lifespan(),
                                       "AR is alive beyond its shortened lifespan while the AQ control remains unexpired");
                              assertExamineAbsent(helper,level,body,SmallBotanyFixtureLayout.stationTray(origin,"AR"),"examine.moonstation14.botany.plant.old");
                             assertExamine(helper,level,body,SmallBotanyFixtureLayout.stationTray(origin,"V"),"examine.moonstation14.botany.plant.screams");
                             assertExamine(helper,level,body,SmallBotanyFixtureLayout.stationTray(origin,"AT"),"examine.moonstation14.botany.warning.pressure");
                         });
                        var lamp=SmallBotanyFixtureLayout.showcaseLamp(origin);
                        helper.assertTrue(level.getBlockState(lamp).is(com.juicyslew.moonstation14.block.ModBlocks.POWER_LAMP.get())
                                &&level.getBlockState(lamp).getValue(com.juicyslew.moonstation14.block.custom.PowerDeviceBlock.FACING)==Direction.NORTH,
                                "real showcase power lamp exists in expected mounting orientation");
                          var showcaseLabels=com.juicyslew.moonstation14.ms14.botany.fixture.BotanyFixtureSigns.labels(origin);
                          for(int station=0;station<12;station++) {
                              BlockPos signPos=SmallBotanyFixtureLayout.showcaseSign(origin,station);
                              var label=showcaseLabels.stream().filter(l->l.pos().equals(signPos)).findFirst().orElseThrow();
                              var sign=(net.minecraft.world.level.block.entity.SignBlockEntity)level.getBlockEntity(label.pos());
                              for(int line=0;line<label.lines().length;line++) helper.assertTrue(sign.getFrontText().getMessage(line,false).getString().equals(label.lines()[line]),
                                      "showcase sign line matches readable label: "+label.lines()[line]);
                              for(int line=0;line<label.lines().length;line++) helper.assertTrue(sign.getBackText().getMessage(line,false).getString().equals(label.lines()[line]),
                                     "showcase sign is readable from either approach side");
                          }
                          var allLabels=new java.util.ArrayList<com.juicyslew.moonstation14.ms14.botany.fixture.BotanyFixtureSigns.Label>();
                          allLabels.addAll(showcaseLabels);
                          var careOrigin=SmallBotanyFixtureLayout.careOrigin(origin);
                          allLabels.addAll(com.juicyslew.moonstation14.ms14.botany.fixture.BotanyFixtureSigns.careLabels(careOrigin));
                          allLabels.addAll(com.juicyslew.moonstation14.ms14.botany.fixture.BotanyFixtureSigns.toleranceLabels(careOrigin));
                          for(var stationInfo:com.juicyslew.moonstation14.ms14.botany.fixture.BotanyFixtureStations.all()) {
                              String id=stationInfo.legacyId();
                              BlockPos trayPos=SmallBotanyFixtureLayout.stationTray(origin,id), signPos=trayPos.south();
                              BlockPos torchPos=SmallBotanyFixtureLayout.stationTorch(origin,id), approach=trayPos.south(2);
                              helper.assertTrue(level.getBlockEntity(trayPos) instanceof HydroponicsTrayBlockEntity t && t.isLoadedValid() && t.plantState()!=null,
                                      "all-station tray initialized: "+id);
                              var label=allLabels.stream().filter(l->l.pos().equals(signPos)).findFirst().orElseThrow();
                              var sign=(net.minecraft.world.level.block.entity.SignBlockEntity)level.getBlockEntity(signPos);
                              helper.assertTrue(label.lines()[0].startsWith(String.format(java.util.Locale.ROOT,"%02d ",stationInfo.number())),"station label has expected number: "+id);
                              for(int line=0;line<4;line++) {
                                  helper.assertTrue(sign.getFrontText().getMessage(line,false).getString().equals(label.lines()[line]),"station front sign line: "+id+"/"+line);
                                  helper.assertTrue(sign.getBackText().getMessage(line,false).getString().equals(label.lines()[line]),"station back sign line: "+id+"/"+line);
                              }
                              helper.assertTrue(level.getBlockState(torchPos).is(net.minecraft.world.level.block.Blocks.TORCH),"station torch exists: "+id);
                              helper.assertTrue(level.getBlockState(torchPos.below()).isSolid(),"station torch has solid support: "+id);
                              helper.assertTrue(level.isEmptyBlock(approach)&&level.isEmptyBlock(approach.above()),"station approach collision cells clear: "+id);
                              helper.assertTrue(!signPos.equals(approach)&&!torchPos.equals(approach),"station approach does not overlap sign or torch: "+id);
                          }
                           for(BlockPos planned:footprint) {
                               helper.assertTrue(helper.getBounds().contains(planned.getCenter()),
                                       "planned footprint within fixture bounds: worldPos="+planned+", bounds="+helper.getBounds());
                           }
                          PowerFixtureReadiness.await(helper,level,500,
                                 ()->level.getBlockState(lamp).getValue(com.juicyslew.moonstation14.block.custom.PowerDeviceBlock.LIT),
                                 ()->"showcase power lamp did not become lit: "+lamp,
                                 ()->{ fixture.close(); helper.succeed(); });
                    } catch(RuntimeException|Error failure) { fixture.close(); throw failure; }
                 });
            } catch(RuntimeException|Error failure) { fixture.close(); throw failure; }
        });
    }

    private static void assertExamine(GameTestHelper helper,ServerLevel level,net.minecraft.world.entity.Entity body,BlockPos pos,String key) {
        var tray=(HydroponicsTrayBlockEntity)level.getBlockEntity(pos);
        var state=level.getBlockState(pos); var plant=tray.plantState();
        var before=plant.revision(); var resources=java.util.List.of(plant.water(),plant.nutrition(),plant.health(),plant.weeds()); var genetics=plant.genetics();
        var target=com.juicyslew.moonstation14.ms14.examine.ExamineTarget.block(pos);
        var context=new com.juicyslew.moonstation14.ms14.examine.ExamineContext(body,level,target,null,pos,state,tray,true);
        var collector=new com.juicyslew.moonstation14.ms14.examine.ExamineCollector(true);
        com.juicyslew.moonstation14.ms14.examine.BotanyExamineContributor.contribute(context,collector);
        helper.assertTrue(collector.sortedEntries().stream().anyMatch(e->e.text().getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t&&t.getKey().equals(key)),"botany examine key present: "+key);
        var hidden=new com.juicyslew.moonstation14.ms14.examine.ExamineCollector(false);
        com.juicyslew.moonstation14.ms14.examine.BotanyExamineContributor.contribute(new com.juicyslew.moonstation14.ms14.examine.ExamineContext(body,level,target,null,pos,state,tray,false),hidden);
        helper.assertTrue(hidden.sortedEntries().isEmpty(),"details disallowed emits no entries");
        helper.assertTrue(plant.revision()==before&&resources.equals(java.util.List.of(plant.water(),plant.nutrition(),plant.health(),plant.weeds()))&&java.util.Objects.equals(genetics,plant.genetics()),"examination is read-only");
    }

    private static void assertExamineAbsent(GameTestHelper helper,ServerLevel level,net.minecraft.world.entity.Entity body,BlockPos pos,String key) {
        var tray=(HydroponicsTrayBlockEntity)level.getBlockEntity(pos);
        var context=new com.juicyslew.moonstation14.ms14.examine.ExamineContext(body,level,
                com.juicyslew.moonstation14.ms14.examine.ExamineTarget.block(pos),null,pos,level.getBlockState(pos),tray,true);
        var collector=new com.juicyslew.moonstation14.ms14.examine.ExamineCollector(true);
        com.juicyslew.moonstation14.ms14.examine.BotanyExamineContributor.contribute(context,collector);
        helper.assertTrue(collector.sortedEntries().stream().noneMatch(e->e.text().getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t&&t.getKey().equals(key)),
                "healthy aged plant has no ordinary old-plant concern: "+key);
    }
}
