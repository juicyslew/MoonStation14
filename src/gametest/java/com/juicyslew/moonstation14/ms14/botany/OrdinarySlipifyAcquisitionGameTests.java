package com.juicyslew.moonstation14.ms14.botany;

import com.juicyslew.moonstation14.component.ModDataComponents;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.gametest.support.RegisteredServerPlayerFixture;
import com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait;
import com.juicyslew.moonstation14.item.ModItems;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server.RegisteredPlayerReadyDriver;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestTags({"botany-slipify-acquisition"})
@PrefixGameTestTemplate(false)
public final class OrdinarySlipifyAcquisitionGameTests {
    private OrdinarySlipifyAcquisitionGameTests() { }

    @GameTest(template="botany_fixture_space", templateNamespace="moonstation14", timeoutTicks=800)
    public static void authenticatedDoseAcquiresScheduledSlipify(GameTestHelper helper) {
        ServerLevel level=(ServerLevel)helper.getLevel();
        var fixture=RegisteredServerPlayerFixture.register(level,"botany-ordinary-slipify");
        var controlledTray=new java.util.concurrent.atomic.AtomicReference<HydroponicsTrayBlockEntity>();
        var cleaned=new java.util.concurrent.atomic.AtomicBoolean();
        var inspectorSource=new java.util.concurrent.atomic.AtomicReference<BotanyFixtureCommandGameTests.CapturedSource>();
        Runnable cleanup=()->{if(cleaned.compareAndSet(false,true)){var target=controlledTray.get();if(target!=null)target.clearFixtureCycleRandom();var captured=inspectorSource.get();if(captured!=null)try{BotanyFixtureCommandGameTests.execute(level.getServer().getCommands().getDispatcher(),"ms14 botany_inspect clear",captured.stack());}catch(Throwable ignored){}fixture.close();}};
        helper.runAtTickTime(2,()->{
            try {
                var active=RegisteredPlayerReadyDriver.readyAndQuery(fixture);
                BlockPos clear=helper.absolutePos(new BlockPos(2,22,2)), origin=clear.offset(2,0,2);
                var footprint=SmallBotanyFixtureLayout.plannedFootprint(origin);
                int minX=footprint.stream().mapToInt(BlockPos::getX).min().orElseThrow(), maxX=footprint.stream().mapToInt(BlockPos::getX).max().orElseThrow();
                int minZ=footprint.stream().mapToInt(BlockPos::getZ).min().orElseThrow(), maxZ=footprint.stream().mapToInt(BlockPos::getZ).max().orElseThrow();
                 for(int x=(minX>>4)-1;x<=(maxX>>4)+1;x++) for(int z=(minZ>>4)-1;z<=(maxZ>>4)+1;z++) level.getChunk(x,z);
                var body=active.body(); body.teleportTo(clear.getX()+.5,clear.getY(),clear.getZ()+.5);
                var source=BotanyFixtureCommandGameTests.capture(fixture.player().createCommandSourceStack().withPermission(2));
                var dispatcher=level.getServer().getCommands().getDispatcher();
                helper.assertTrue(BotanyFixtureCommandGameTests.execute(dispatcher,"ms14 botany_fixture",source.stack())==1,"fixture command accepted");
                helper.runAfterDelay(30,()->{
                    try {
                        helper.assertTrue(source.feedback().stream().anyMatch(m->m.contains("Botany fixture ready")),"bounded fixture readiness feedback");
                        var label=com.juicyslew.moonstation14.ms14.botany.fixture.BotanyFixtureSigns.labels(origin).stream().filter(l->l.title().equals("D WHEAT")).findFirst().orElseThrow();
                        var tray=(HydroponicsTrayBlockEntity)level.getBlockEntity(label.trayPos());
                        controlledTray.set(tray);
                        var wheat=net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("moonstation14","wheat");
                        var defaultProfile=ModSeedProfiles.serverCatalog().get(wheat);
                        helper.assertTrue(tray!=null&&wheat.equals(tray.plantState().seed())&&tray.plantState().genetics()!=null
                                &&wheat.equals(tray.plantState().genetics().species())&&tray.plantState().genetics().profile().equals(defaultProfile.withDefaultScreamSound())&&tray.plantState().mutationLevel()==0
                                &&tray.plantState().mutationModifier()==1f&&tray.plantState().genetics().mutations().isEmpty()
                                &&tray.soilSolution().snapshotUnits().isEmpty(),"D is default wheat with genetics, no mutation, default modifier, and empty soil");
                        var baseline=tray.plantState().genetics();
                         body.setPos(label.trayPos().getX()+.5,label.trayPos().getY(),label.trayPos().getZ()-.5);
                        RegisteredPlayerReadyDriver.aimAt(body,level,label.trayPos());
                        var commandSource=BotanyFixtureCommandGameTests.capture(fixture.player().createCommandSourceStack().withPermission(2));
                        inspectorSource.set(commandSource);
                        long baselineRevision=tray.plantState().revision(); var baselineReference=tray.plantState().seedReference(); var baselineGenetics=tray.plantState().genetics();
                        helper.assertTrue(BotanyFixtureCommandGameTests.execute(dispatcher,"ms14 botany_inspect capture",commandSource.stack())==1
                                &&commandSource.feedback().stream().anyMatch(m->m.contains("CAPTURED BASELINE")),"real inspector captures empty-record baseline");
                        helper.assertTrue(tray.plantState().revision()==baselineRevision&&tray.plantState().seedReference()==baselineReference
                                &&tray.plantState().genetics()==baselineGenetics&&baselineGenetics.mutations().isEmpty(),"inspector capture is read-only for D genetics/reference/revision");
                        var table=ModPlantMutations.serverCatalog().get(ModPlantMutations.tableId("random_plant_mutations"));
                        int slipifyIndex=-1;
                        for(int i=0;i<table.mutations().size();i++) if(table.mutations().get(i).effectKind()==PlantMutationTable.EffectKind.SLIPIFY){slipifyIndex=i;break;}
                        helper.assertTrue(slipifyIndex>=0&&table.mutations().stream().filter(e->e.effectKind()==PlantMutationTable.EffectKind.SLIPIFY).count()==1,"resolved main table has one Slipify row");
                        var slipify=table.mutations().get(slipifyIndex);
                        double maxOther=table.mutations().stream().filter(e->e!=slipify).mapToDouble(e->e.baseOdds()*5d).max().orElse(0d);
                        helper.assertTrue(maxOther<1d,"other source rows can fail under scripted near-one draws at severity five");
                        final int slipifyOrdinal=slipifyIndex;
                        var rngCalls=new java.util.concurrent.atomic.AtomicInteger();
                        tray.setFixtureCycleRandom(new java.util.random.RandomGenerator(){
                            @Override public double nextDouble(){return rngCalls.getAndIncrement()==slipifyOrdinal?0d:Math.nextDown(1d);}
                            @Override public float nextFloat(){return .5f;}
                            @Override public int nextInt(int bound){return 0;}
                            @Override public long nextLong(){return 0L;}
                        });
                         String doseId=com.juicyslew.moonstation14.ms14.botany.fixture.BotanyFixtureSupplies.descriptors().stream()
                                 .filter(d->d.stationId().equals("I")&&d.kind()==com.juicyslew.moonstation14.ms14.botany.fixture.BotanyFixtureSupplies.Kind.REAGENT
                                         &&d.value().equals("unstablemutagen")&&d.cents()==500L).findFirst().orElseThrow().label();
                          var doseMatches=BotanyFixtureCommandGameTests.findPlannedFixtureBeaker(level,origin,doseId,"unstablemutagen",500L);
                          helper.assertTrue(doseMatches.size()==1,"exactly one actual local Slipify beaker grant; "+slipifyGrantDiagnostics(origin,doseMatches));
                          var doseEntity=doseMatches.get(0);
                          var stack=doseEntity.getItem();
                        var mutagen=com.juicyslew.moonstation14.ms14.reagent.ModReagents.createKey("unstablemutagen");
                         helper.assertTrue(stack.getCount()==1&&!stack.has(net.minecraft.core.component.DataComponents.CUSTOM_NAME)
                                 &&BotanyFixtureCommandGameTests.plainBeakerName(stack)
                                 &&stack.getOrDefault(ModDataComponents.REAGENT.get(),new com.juicyslew.moonstation14.ms14.reagent.ReagentComponent(java.util.Map.of())).centContents().equals(java.util.Map.of(mutagen,500L)),"actual plain kit beaker contains exactly 500 cents unstablemutagen");
                        var loose=doseEntity;
                        String hand=body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get()).activeHand();
                         var hands=com.juicyslew.moonstation14.ms14.hands.network.BodyHandStateService.query(fixture.player(),new com.juicyslew.moonstation14.ms14.hands.network.BodyHandStateQuery(active.epoch(),2));
                         helper.assertTrue(hands.reason()==com.juicyslew.moonstation14.ms14.hands.network.BodyHandStateSnapshot.Reason.OK,"first post-ready hand query has fresh sequence");
                         var occupied=hands.hands().stream().filter(h->h.id().equals(hand)).findFirst().orElseThrow();
                          helper.assertTrue(hands.hands().stream().noneMatch(h->h.id().equals(hand)&&h.token()!=null),"active hand is empty before dose pickup");
                          body.setPos(loose.getX()+.8,loose.getY(),loose.getZ());
                           helper.assertTrue(level.noCollision(body,body.getBoundingBox()),"dose pickup stance is collision-free");
                          body.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES,loose.getBoundingBox().getCenter());
                          helper.assertTrue(com.juicyslew.moonstation14.ms14.hands.live.BodyItemTargetProbe.probeSelected(level,body,doseEntity.getUUID(),com.juicyslew.moonstation14.ms14.hands.live.BodyItemTargetProbe.MAX_REACH)!=null,
                                  "actual fixture-granted beaker is selected by native item ray before pickup");
                           var pickupCell=body.blockPosition();
                           helper.assertTrue(level.hasChunkAt(pickupCell)&&level.hasChunkAt(loose.blockPosition()),"body pickup stance and actual dose entity cells are loaded");
                           helper.assertTrue(level.noCollision(body,body.getBoundingBox()),"actual dose pickup stance remains collision-free");
                         helper.assertTrue(com.juicyslew.moonstation14.ms14.hands.live.BodyHandItemTransfer.pickupSelected(fixture.player(),loose.getUUID(),hand,hands.revision(),ignored->{})==com.juicyslew.moonstation14.ms14.hands.live.BodyHandItemTransfer.Result.SUCCESS,"normal selected-hand pickup of actual kit beaker entity");
                          body.setPos(label.trayPos().getX()+.5,label.trayPos().getY(),label.trayPos().getZ()-.5);
                         var query=com.juicyslew.moonstation14.ms14.hands.network.BodyHandStateService.query(fixture.player(),new com.juicyslew.moonstation14.ms14.hands.network.BodyHandStateQuery(active.epoch(),5));
                        var held=query.hands().stream().filter(h->h.id().equals(hand)).findFirst().orElseThrow();
                        var hit=RegisteredPlayerReadyDriver.aimAt(body,level,label.trayPos());
                        var request=new com.juicyslew.moonstation14.ms14.hands.network.BodyBlockUseRequest(active.epoch(),1,query.revision(),hand,held.token(),label.trayPos(),hit.getDirection(),false,com.juicyslew.moonstation14.ms14.hands.network.BodyBlockUseRequest.Intent.USE);
                        helper.assertTrue(com.juicyslew.moonstation14.ms14.hands.network.AuthenticatedBlockUseDriver.use(fixture.player(),request)==com.juicyslew.moonstation14.ms14.hands.network.BodyBlockUseResult.Outcome.PLACED,"authenticated ordinary body USE doses D");
                         helper.assertTrue(tray.soilSolution().snapshotUnits().getOrDefault(mutagen,0L)==500,"soil receives exactly 500 cents");
                         helper.assertTrue(BotanyFixtureCommandGameTests.execute(dispatcher,"ms14 botany_fixture start 1",source.stack())==1,"start ordinary wheat station after dosing while fixture was paused");
                         var after=com.juicyslew.moonstation14.ms14.hands.network.BodyHandStateService.query(fixture.player(),new com.juicyslew.moonstation14.ms14.hands.network.BodyHandStateQuery(active.epoch(),6));
                        var afterHand=after.hands().stream().filter(h->h.id().equals(hand)).findFirst().orElseThrow();
                        var owned=body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get()).stackCopy(hand).orElseThrow();
                        helper.assertTrue(after.revision()>query.revision()&&afterHand.token().equals(held.token())&&owned.is(ModItems.BEAKER.get())&&owned.getCount()==1
                                &&owned.get(ModDataComponents.REAGENT.get()).centContents().isEmpty()&&level.getEntity(loose.getUUID())==null,"same owned beaker is empty and source entity removed");
                        long waitStart=helper.getTick();
                        java.util.function.BooleanSupplier acquired=()->tray.soilSolution().snapshotUnits().getOrDefault(mutagen,0L)<500
                                &&tray.plantState().genetics().mutations().stream().anyMatch(m->m.name().equals(slipify.name()));
                        Runnable[] poll=new Runnable[1];
                        poll[0]=()->{try {
                            if(acquired.getAsBoolean()) {
                                var record=tray.plantState().genetics().mutations().stream().filter(m->m.name().equals(slipify.name())).findFirst().orElseThrow();
                                helper.assertTrue(rngCalls.get()==table.mutations().size(),"first cycle rolled each resolved mutation row exactly once: "+rngCalls.get());
                                long remaining=tray.soilSolution().snapshotUnits().getOrDefault(mutagen,0L);
                                helper.assertTrue(remaining>=0&&remaining%100==0&&remaining<500,"ordinary polling drained exact 100-cent units: "+remaining);
                                helper.assertTrue(record.appliesToPlant()==slipify.appliesToPlant()&&record.appliesToProduce()==slipify.appliesToProduce()
                                        &&record.persists()==slipify.persists()&&record.slippery().equals(slipify.slippery().orElseThrow()),"captured record matches resolved descriptor");
                                helper.assertTrue(baseline.mutations().isEmpty()&&tray.mutationResult().severity()>0,"immutable baseline remains empty; actual automatic cycle reports positive severity");
                                commandSource.feedback().clear();
                                 int compared=BotanyFixtureCommandGameTests.execute(dispatcher,"ms14 botany_inspect compare",commandSource.stack());
                                 var report=commandSource.feedback();
                                 String diagnostic=report.stream().limit(12).collect(java.util.stream.Collectors.joining(" | "));
                                 if(diagnostic.length()>850) diagnostic=diagnostic.substring(0,850);
                                 helper.assertTrue(compared==1
                                         &&report.stream().anyMatch(m->m.contains("CURRENT SAMPLE; CHANGES SINCE PRIOR SAMPLE"))
                                         &&report.stream().anyMatch(m->m.contains("Location:")&&m.contains(label.trayPos().getX()+", "+label.trayPos().getY()+", "+label.trayPos().getZ()))
                                         &&report.stream().anyMatch(m->m.contains("mutations.count: 1"))
                                         &&report.stream().anyMatch(m->m.contains("mutation.0.name: "+slipify.name()))
                                         &&report.stream().anyMatch(m->m.contains("mutation.0.slippery"))
                                         &&report.stream().anyMatch(m->m.contains("mutations.count: 0 → 1"))
                                         &&report.stream().anyMatch(m->m.contains("Captured mutation records describe carried descriptors, not provenance, history, or action cause.")),
                                         "real inspector reports same D target's current and changed descriptor fields without provenance claims: "+diagnostic);
                                 var handsQuery=com.juicyslew.moonstation14.ms14.hands.network.BodyHandStateService.query(fixture.player(),new com.juicyslew.moonstation14.ms14.hands.network.BodyHandStateQuery(active.epoch(),7));
                                var handState=handsQuery.hands().stream().filter(h->h.id().equals(hand)).findFirst().orElseThrow();
                                var dropped=com.juicyslew.moonstation14.ms14.hands.network.BodyHandRequestService.apply(fixture.player(),new com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionRequest(com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionRequest.Action.DROP,hand,active.epoch(),5,handsQuery.revision(),null,handState.token()));
                                helper.assertTrue(dropped.accepted(),"ordinary DROP releases emptied dose beaker");
                                var beforeIds=level.getEntitiesOfClass(com.juicyslew.moonstation14.entities.LooseItemEntity.class,new net.minecraft.world.phys.AABB(label.trayPos()).inflate(8),e->!e.getItem().isEmpty()).stream().map(net.minecraft.world.entity.Entity::getUUID).collect(java.util.stream.Collectors.toSet());
                                 var harvestQuery=com.juicyslew.moonstation14.ms14.hands.network.BodyHandStateService.query(fixture.player(),new com.juicyslew.moonstation14.ms14.hands.network.BodyHandStateQuery(active.epoch(),8));
                                helper.assertTrue(harvestQuery.hands().stream().noneMatch(h->h.id().equals(hand)&&h.token()!=null),"hand is empty before ordinary harvest");
                                int expectedYield=PlantMetabolismEvaluator.previewHarvestYield(tray.plantState(),tray.plantState().effectiveProfile(),1.0f);
                                 body.setPos(label.trayPos().getX()+.5,label.trayPos().getY(),label.trayPos().getZ()-.5);
                                var harvestHit=RegisteredPlayerReadyDriver.aimAt(body,level,label.trayPos());
                                var harvestRequest=new com.juicyslew.moonstation14.ms14.hands.network.BodyBlockUseRequest(active.epoch(),7,harvestQuery.revision(),hand,null,label.trayPos(),harvestHit.getDirection(),false,com.juicyslew.moonstation14.ms14.hands.network.BodyBlockUseRequest.Intent.USE);
                                helper.assertTrue(tray.plantState().ready()&&com.juicyslew.moonstation14.ms14.hands.network.AuthenticatedBlockUseDriver.use(fixture.player(),harvestRequest)==com.juicyslew.moonstation14.ms14.hands.network.BodyBlockUseResult.Outcome.PLACED,"ready D wheat harvested through ordinary authenticated empty-hand USE");
                                var expectedProduct=net.minecraft.core.registries.BuiltInRegistries.ITEM.get(baseline.profile().product());
                                var outputs=level.getEntitiesOfClass(com.juicyslew.moonstation14.entities.LooseItemEntity.class,new net.minecraft.world.phys.AABB(label.trayPos()).inflate(8),e->!e.getItem().isEmpty()&&!beforeIds.contains(e.getUUID())&&e.getItem().is(expectedProduct));
                                helper.assertTrue(outputs.size()==expectedYield&&tray.plantState().seed()==null,"actual wheat output entity count matches ordinary harvest preview and no-repeat D tray emptied: "+outputs.size()+"/"+expectedYield);
                                helper.assertTrue(outputs.stream().allMatch(e->e.isAddedToLevel()&&e.level()==level&&e.getItem().getCount()==1)
                                        &&outputs.stream().mapToInt(e->e.getItem().getCount()).sum()==expectedYield,"each expected output is a distinct live same-level one-count item entity");
                                for(var output:outputs){var produced=output.getItem();helper.assertTrue(produced.has(ModDataComponents.GENETIC_VALUE.get())
                                        &&produced.get(ModDataComponents.GENETIC_VALUE.get()).mutations().contains(record),"harvested output carries acquired genetic record");
                                    helper.assertTrue(record.slippery().equals(produced.get(ModDataComponents.ITEM_SLIPPERY_COMPOSITION.get()))
                                            &&record.slippery().equals(com.juicyslew.moonstation14.ms14.loose_objects.LooseObjectProfile.resolveSlipperyForStack(produced,false)),"actual output has typed Slipify payload and resolved slippery behavior");}
                                cleanup.run(); helper.succeed(); return;
                            }
                            if(helper.getTick()-waitStart>=450){cleanup.run();helper.fail("ordinary scheduled cycle timed out; soil="+tray.soilSolution().snapshotUnits()+", genetics="+tray.plantState().genetics().mutations());return;}
                            BoundedGameTestWait.safeDelay(helper,1,poll[0]);
                        } catch(RuntimeException|Error failure){cleanup.run();throw failure;}};
                        BoundedGameTestWait.safeDelay(helper,1,poll[0]);
                    } catch(RuntimeException|Error failure) { cleanup.run(); throw failure; }
                });
            } catch(RuntimeException|Error failure) { cleanup.run(); throw failure; }
        });
    }

    private static String slipifyGrantDiagnostics(BlockPos origin,
            java.util.List<com.juicyslew.moonstation14.entities.LooseItemEntity> matches) {
        var entry=SmallBotanyFixtureLayout.entry(origin);
        String entities=matches.stream().limit(6).map(e->e.getUUID()+"@"+e.position()).collect(java.util.stream.Collectors.joining(","));
        String text="expected="+com.juicyslew.moonstation14.ms14.botany.fixture.BotanyFixtureStations.displayTitle("I MUTAGEN")+" 5u unstablemutagen, count="+matches.size()+", entry="+entry+", entities="+entities;
        return text.length()>900?text.substring(0,900):text;
    }
}
