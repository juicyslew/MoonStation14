package com.juicyslew.moonstation14.ms14.botany;

import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.gametest.support.RegisteredServerPlayerFixture;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server.RegisteredPlayerReadyDriver;
import com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereService;
import com.juicyslew.moonstation14.ms14.hands.HandActorAuthority;
import com.juicyslew.moonstation14.ms14.hands.ItemToken;
import com.juicyslew.moonstation14.ms14.hands.transfer.ItemLocation;
import com.juicyslew.moonstation14.ms14.hands.transfer.LevelItemLocationLedgers;
import com.juicyslew.moonstation14.component.ModDataComponents;
import com.juicyslew.moonstation14.item.ModItems;
import com.juicyslew.moonstation14.ms14.reagent.ReagentComponent;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server.LifecycleCharacterSessionControl;
import com.juicyslew.moonstation14.gametest.support.PowerFixtureReadiness;
import com.juicyslew.moonstation14.block.block_entity.PowerDeviceBlockEntity;
import com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime;
import com.juicyslew.moonstation14.ms14.power.topology.CableFaceNode;
import com.juicyslew.moonstation14.ms14.power.cable.CableTier;
import com.juicyslew.moonstation14.ms14.botany.fixture.BotanyFixtureStations;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.commands.CommandSource;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Map;
import java.util.UUID;

@GameTestTags({"botany", "botany-fixture", "botany-chemical-mutation"})
@PrefixGameTestTemplate(false)
public final class BotanyFixtureCommandGameTests {
    static boolean plainBeakerName(net.minecraft.world.item.ItemStack stack) {
        return stack.getHoverName().getString().equals(ModItems.BEAKER.get().getName(stack).getString());
    }
    static java.util.List<com.juicyslew.moonstation14.entities.LooseItemEntity> findPlannedFixtureBeaker(ServerLevel level, BlockPos origin, String canonicalLabel, String reagent, long cents) {
        var descriptors=com.juicyslew.moonstation14.ms14.botany.fixture.BotanyFixtureSupplies.descriptors();
        var planned=com.juicyslew.moonstation14.ms14.botany.fixture.BotanyFixtureSupplies.plan(origin,descriptors).stream().filter(p->p.label().equals(canonicalLabel)).findFirst()
                .orElseThrow(()->new GameTestAssertException("No planned fixture beaker for canonical label '"+canonicalLabel+"'; known canonical labels: "+descriptors.stream().map(d->d.label()).toList()));
        var pos=planned.position(); var target=new net.minecraft.world.phys.AABB(pos).inflate(.75,1.5,.75);
        var key=com.juicyslew.moonstation14.ms14.reagent.ModReagents.createKey(reagent);
        return level.getEntitiesOfClass(com.juicyslew.moonstation14.entities.LooseItemEntity.class,target,e->{
            var stack=e.getItem();
            return !stack.isEmpty()&&stack.is(ModItems.BEAKER.get())&&!stack.has(net.minecraft.core.component.DataComponents.CUSTOM_NAME)
                    &&plainBeakerName(stack)&&Math.abs(e.getX()-(pos.getX()+.5))<=.6&&Math.abs(e.getZ()-(pos.getZ()+.5))<=.6&&Math.abs(e.getY()-pos.getY())<=1.5
                    &&stack.getOrDefault(ModDataComponents.REAGENT.get(),new ReagentComponent(Map.of())).centContents().equals(Map.of(key,cents));
        });
    }
    private BotanyFixtureCommandGameTests() { }

    @GameTest(template="botany_fixture_space", templateNamespace="moonstation14", timeoutTicks=1600)
    public static void singleCommandBuildsCompleteFixture(GameTestHelper helper) {
        String mixedPending="H CHEMISTRY — preset genes: STATIC\nJ GAS — preset gas genes: STATIC\nH CHEMISTRY — CHANGED: acquired [moonstation14:test]\nJ GAS — preset gas genes: WAITING: ordinary exchange cycle not reported";
        String mixedComplete="H CHEMISTRY — preset genes: STATIC\nJ GAS — preset gas genes: STATIC\nH CHEMISTRY — CHANGED: acquired [moonstation14:test]\nJ GAS — preset gas genes: EXCHANGED: ordinary cycle report confirms gas exchange";
        String mixedTechnical="POWER DIAGNOSTICS (technical): APC=battery103500; grinder=VentMeterObservation[known=true, deliveredWatts=750.0]; extractor=VentMeterObservation[known=true, deliveredWatts=1000.0]";
        helper.assertTrue(statusLine(mixedPending,"H CHEMISTRY").contains("CHANGED")
                        &&statusLine(mixedPending,"H CHEMISTRY").contains("moonstation14:test")
                        &&statusLine(mixedPending,"J GAS").contains("WAITING")
                        &&!statusLine(mixedPending,"J GAS").contains("STATIC")
                        &&statusLine(mixedComplete,"H CHEMISTRY").contains("CHANGED")
                        &&prefixedStatusLine(mixedTechnical,"POWER DIAGNOSTICS (technical)").contains("extractor=VentMeterObservation[known=true, deliveredWatts=1000.0]")
                        &&statusLine(mixedTechnical,"POWER DIAGNOSTICS (technical)").isEmpty()
                        &&!gasStatusCompleted("J GAS — EXCHANGED: word only")
                        &&gasStatusCompleted(mixedComplete),
                "station-linked human status independently represents chemistry and gas; EXCHANGED requires an actual report");
        ServerLevel level=(ServerLevel)helper.getLevel();
        RegisteredServerPlayerFixture fixture=RegisteredServerPlayerFixture.register(level,"botany-fixture-command");
        helper.runAtTickTime(2,()->{
          try {
            var active=RegisteredPlayerReadyDriver.readyAndQuery(fixture);
            BlockPos clear=helper.absolutePos(new BlockPos(2,22,2));
            BlockPos origin=clear.offset(2,0,2);
             var footprint=SmallBotanyFixtureLayout.plannedFootprint(origin);
             int minX=footprint.stream().mapToInt(BlockPos::getX).min().orElseThrow(), maxX=footprint.stream().mapToInt(BlockPos::getX).max().orElseThrow();
             int minZ=footprint.stream().mapToInt(BlockPos::getZ).min().orElseThrow(), maxZ=footprint.stream().mapToInt(BlockPos::getZ).max().orElseThrow();
              for(int x=minX>>4;x<=maxX>>4;x++) for(int z=minZ>>4;z<=maxZ>>4;z++) level.getChunk(x,z);
            var body=active.body();
            body.teleportTo(clear.getX()+.5,clear.getY(),clear.getZ()+.5);
            body.setDeltaMovement(Vec3.ZERO);
              var initialVestineCents=new java.util.concurrent.atomic.AtomicLong(-1);
              var initialMutagenCents=new java.util.concurrent.atomic.AtomicLong(-1);
              var initialModifier=new java.util.concurrent.atomic.AtomicReference<Float>();
              var initialGasConsume=new java.util.concurrent.atomic.AtomicReference<Map<com.juicyslew.moonstation14.ms14.atmos.core.GasType,Float>>();
              var initialGasExude=new java.util.concurrent.atomic.AtomicReference<Map<com.juicyslew.moonstation14.ms14.atmos.core.GasType,Float>>();
               var initialGasSpecies=new java.util.concurrent.atomic.AtomicReference<net.minecraft.resources.ResourceLocation>();
              var initialCare=new java.util.concurrent.atomic.AtomicReference<java.util.List<String>>();
               var sourceRef=new java.util.concurrent.atomic.AtomicReference<CapturedSource>();
               CapturedSource source=capture(fixture.player().createCommandSourceStack().withPermission(2),message->{
                   if(message.contains("Botany fixture ready")) {
                       var station3=BotanyFixtureStations.all().stream().filter(s->s.number()==3).findFirst().orElseThrow();
                       var station5=BotanyFixtureStations.all().stream().filter(s->s.number()==5).findFirst().orElseThrow();
                        helper.assertTrue(execute(level.getServer().getCommands().getDispatcher(),"ms14 botany_fixture start core",sourceRef.get().stack())==1,
                               "registered named-set command starts core");
                        helper.assertTrue(execute(level.getServer().getCommands().getDispatcher(),"ms14 botany_fixture pause",sourceRef.get().stack())==1,
                               "registered pause command succeeds and pauses all stations");
                        helper.assertTrue(execute(level.getServer().getCommands().getDispatcher(),"ms14 botany_fixture resume",sourceRef.get().stack())==1,
                               "registered resume command succeeds");
                       var coreTray=(HydroponicsTrayBlockEntity)level.getBlockEntity(SmallBotanyFixtureLayout.stationTray(origin,station3.legacyId()));
                       var otherTray=(HydroponicsTrayBlockEntity)level.getBlockEntity(SmallBotanyFixtureLayout.stationTray(origin,station5.legacyId()));
                       helper.assertTrue(coreTray!=null&&!coreTray.fixturePaused()&&otherTray!=null&&otherTray.fixturePaused(),
                               "resume reactivates selected core only; unrelated station remains paused");
                        helper.assertTrue(execute(level.getServer().getCommands().getDispatcher(),"ms14 botany_fixture start 1 2",sourceRef.get().stack())==1,
                               "numeric pacing activates only the baseline pair");
                       var baseline=(HydroponicsTrayBlockEntity)level.getBlockEntity(SmallBotanyFixtureLayout.normalTray(origin,false));
                       var unrelated=(HydroponicsTrayBlockEntity)level.getBlockEntity(SmallBotanyFixtureLayout.statsMutationTray(origin));
                       helper.assertTrue(baseline!=null&&!baseline.fixturePaused()&&unrelated!=null&&unrelated.fixturePaused(),
                               "selected pair activates while unrelated station remains paused");
                        helper.assertTrue(execute(level.getServer().getCommands().getDispatcher(),"ms14 botany_fixture pause",sourceRef.get().stack())==1
                                       &&baseline.fixturePaused(),"pause command freezes selected fixture stations");
                       var arranged=(HydroponicsTrayBlockEntity)level.getBlockEntity(SmallBotanyFixtureLayout.untouchedTray(origin));
                   var vestine=com.juicyslew.moonstation14.ms14.reagent.ModReagents.createKey("vestine");
                   initialVestineCents.set(arranged==null?-1:arranged.soilSolution().snapshotUnits().getOrDefault(vestine,0L));
                   var stats=(HydroponicsTrayBlockEntity)level.getBlockEntity(SmallBotanyFixtureLayout.statsMutationTray(origin));
                   var mutagen=com.juicyslew.moonstation14.ms14.reagent.ModReagents.createKey("unstablemutagen");
                   initialMutagenCents.set(stats==null?-1:stats.soilSolution().snapshotUnits().getOrDefault(mutagen,0L));
                    initialModifier.set(stats==null?null:stats.plantState().mutationModifier());
                    var gas=(HydroponicsTrayBlockEntity)level.getBlockEntity(SmallBotanyFixtureLayout.gasExchangeTray(origin));
                    initialGasConsume.set(gas==null?null:gas.plantState().effectiveProfile().consumeGasses());
                    initialGasExude.set(gas==null?null:gas.plantState().effectiveProfile().exudeGasses());
                   initialGasSpecies.set(gas==null?null:gas.plantState().seed());
                   var careLabels=com.juicyslew.moonstation14.ms14.botany.fixture.BotanyFixtureSigns.careLabels(SmallBotanyFixtureLayout.careOrigin(origin));
                   var careSnapshot=new java.util.ArrayList<String>();
                   for(int i=0;i<careLabels.size();i++) {
                       var care=tray(level,careLabels.get(i).trayPos());
                       helper.assertTrue(care!=null && care.plantState().seed()!=null && care.plantState().genetics()!=null
                                       && care.plantState().genetics().species().equals(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("moonstation14","wheat"))
                                       && care.soilSolution().snapshotUnits().isEmpty(),
                               "ready callback captures seeded genetics and empty soil at "+careLabels.get(i).title());
                        var p=care.plantState(); var profile=p.genetics().profile(); var life=profile.lifecycle();
                         float expectedHealth=(i==0||i==6)?profile.biology().endurance()/2f:profile.biology().endurance();
                         helper.assertTrue(p.health()==expectedHealth,"care fixture starts at expected health: "+careLabels.get(i).title());
                         float expectedNutrition=i==0?80f:100f;
                         helper.assertTrue(p.water()==100f&&p.nutrition()==expectedNutrition,"care fixture starts with expected water/nutrition: "+careLabels.get(i).title());
                        switch(i) {
                           case 1 -> helper.assertTrue(p.toxins()==39,"L starts with exactly 39 toxins");
                           case 2 -> helper.assertTrue(p.pests()==6,"M starts with exactly 6 pests");
                           case 3 -> helper.assertTrue(p.weeds()==6,"N starts with exactly 6 weeds");
                           case 4 -> helper.assertTrue(life.potency()==12,"O starts at potency 12");
                           case 5 -> helper.assertTrue(life.potency()==29 && life.yield()==2,"P starts at potency 29/yield 2");
                           case 6 -> helper.assertTrue(p.age()>profile.lifecycle().maturation(),"Q starts past mature age for Cryoxadone eligibility");
                           case 7 -> helper.assertTrue(p.age()<life.maturation(),"R starts immature");
                           case 9 -> helper.assertTrue(!life.viable(),"T starts nonviable");
                             case 10 -> helper.assertTrue(p.seed()!=null&&p.ready()&&life.harvest()==SeedProfile.Harvest.REPEAT&&life.seedless(),"U is seeded, ready, repeat-harvest, and seedless");
                        }
                        if(i==6) helper.assertTrue(p.soilToxins()==9,"Q starts with exactly 9 toxins");
                       careSnapshot.add(careLabels.get(i).title()+":"+p.seed()+":"+p.age()+":"+p.toxins()+":"+p.pests()+":"+p.weeds()+":"+life);
                   }
                   initialCare.set(java.util.List.copyOf(careSnapshot));
                  }
             });
             var dispatcher=level.getServer().getCommands().getDispatcher();
              helper.assertTrue(execute(dispatcher,"ms14 botany_fixture status",source.stack())==0
                              &&source.feedback().stream().anyMatch(message -> message.contains("No botany fixture remembered in this dimension")),
                      "status without a remembered fixture reports unavailable");
              int result=execute(dispatcher,"ms14 botany_fixture",source.stack());
              helper.assertTrue(result==1 && source.feedback().stream().anyMatch(message -> message.contains("setup accepted")),"setup command accepted: "+source.feedback());
              int pendingStart=execute(dispatcher,"ms14 botany_fixture start core",source.stack());
              helper.assertTrue(pendingStart==0&&source.feedback().stream().anyMatch(message->message.contains("setup is pending")),
                      "pacing commands are rejected while fixture setup is pending");
             helper.runAfterDelay(30,()->{
                  try {
                     helper.assertTrue(source.feedback().stream().anyMatch(message -> message.contains("Botany fixture ready")),"setup completion feedback: "+source.feedback());
                     var standby=(HydroponicsTrayBlockEntity)level.getBlockEntity(SmallBotanyFixtureLayout.toleranceTray(SmallBotanyFixtureLayout.careOrigin(origin),0));
                     helper.assertTrue(standby!=null&&standby.fixturePaused(),"fixture's tolerance reference tray remains paused immediately after setup");
                     helper.assertTrue(execute(dispatcher,"ms14 botany_fixture start 1 52",source.stack())==1,"fixture command test explicitly activates all stations before asserting ordinary cycle outcomes");
                    helper.assertTrue(initialCare.get()!=null&&initialCare.get().size()==11,"ready-feedback callback captured all eleven care baseline states before ordinary polling: "+initialCare.get());
                      helper.assertTrue(source.feedback().stream().anyMatch(message -> message.contains("Botany fixture ready in standby; supplies placed locally. All trays start paused.")),"completion confirms paused fixture and locally placed supplies: "+source.feedback());
                    var doses=com.juicyslew.moonstation14.ms14.botany.fixture.BotanyFixtureSigns.careDoses();
                       var grantArea=footprintBounds(SmallBotanyFixtureLayout.plannedFootprint(origin));
                    var inventoryStacks=new java.util.ArrayList<net.minecraft.world.item.ItemStack>();
                    var liveHands=body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());
                    if(liveHands!=null) for(var hand:liveHands.handIds()) liveHands.stackCopy(hand).ifPresent(inventoryStacks::add);
                     var grantedLoose=level.getEntitiesOfClass(com.juicyslew.moonstation14.entities.LooseItemEntity.class,grantArea,e->!e.getItem().isEmpty());
                     grantedLoose.forEach(e->inventoryStacks.add(e.getItem()));
                      var supplyPlan=com.juicyslew.moonstation14.ms14.botany.fixture.BotanyFixtureSupplies.plan(origin,
                              com.juicyslew.moonstation14.ms14.botany.fixture.BotanyFixtureSupplies.descriptors());
                      var descriptors=com.juicyslew.moonstation14.ms14.botany.fixture.BotanyFixtureSupplies.descriptors();
                       helper.assertTrue(supplyPlan.size()==grantedLoose.size(),
                               "every canonical supply descriptor has one local world drop and no duplicate drops; plan="+supplyPlan.size()+", loose="+grantedLoose.size());
                       var matchedSupplyEntities=new java.util.HashSet<java.util.UUID>();
                       var actualByDescriptor=new java.util.HashMap<String,net.minecraft.world.item.ItemStack>();
                      for(var planned:supplyPlan) {
                          var expected=descriptors.stream()
                                  .filter(d->d.label().equals(planned.label())&&d.stationId().equals(planned.stationId())).findFirst().orElseThrow();
                          java.util.function.Predicate<net.minecraft.world.item.ItemStack> identity;
                          if(expected.kind()==com.juicyslew.moonstation14.ms14.botany.fixture.BotanyFixtureSupplies.Kind.SEED) {
                              var profile=com.juicyslew.moonstation14.ms14.botany.ModSeedProfiles.serverCatalog().get(com.juicyslew.moonstation14.ms14.botany.ModSeedProfiles.seedId(expected.value()));
                              identity=s->profile!=null&&s.is(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(profile.packet()));
                          } else if(expected.kind()==com.juicyslew.moonstation14.ms14.botany.fixture.BotanyFixtureSupplies.Kind.TOOL) {
                              var item=switch(expected.value()){case "hoe"->ModItems.BOTANY_HOE.get();case "spade"->ModItems.BOTANY_SPADE.get();case "wirecutter"->ModItems.WIRECUTTER.get();default->ModItems.KITCHEN_KNIFE.get();};
                              identity=s->s.is(item);
                          } else if(expected.kind()==com.juicyslew.moonstation14.ms14.botany.fixture.BotanyFixtureSupplies.Kind.EMPTY_BEAKER) {
                              identity=s->s.is(ModItems.BEAKER.get())&&s.getOrDefault(ModDataComponents.REAGENT.get(),new ReagentComponent(Map.of())).centContents().isEmpty();
                          } else {
                              identity=s->{ if(!s.is(ModItems.BEAKER.get())) return false;
                                   if(s.has(net.minecraft.core.component.DataComponents.CUSTOM_NAME)) return false;
                                  var contents=s.getOrDefault(ModDataComponents.REAGENT.get(),new ReagentComponent(Map.of())).centContents();
                                  return contents.size()==1&&contents.getOrDefault(com.juicyslew.moonstation14.ms14.reagent.ModReagents.createKey(expected.value()),0L)==expected.cents(); };
                          }
                           var pos=planned.position();
                           var matches=grantedLoose.stream().filter(e->identity.test(e.getItem())&&!matchedSupplyEntities.contains(e.getUUID())
                                   &&Math.abs(e.getX()-(pos.getX()+.5))<=.6&&Math.abs(e.getZ()-(pos.getZ()+.5))<=.6
                                   &&Math.abs(e.getY()-pos.getY())<=1.5).toList();
                          helper.assertTrue(matches.size()==1,"exactly one drop matches descriptor "+expected.label()+"; matches="+matches.size());
                            var entity=matches.get(0);
                           if(expected.kind()==com.juicyslew.moonstation14.ms14.botany.fixture.BotanyFixtureSupplies.Kind.REAGENT
                                   ||expected.kind()==com.juicyslew.moonstation14.ms14.botany.fixture.BotanyFixtureSupplies.Kind.EMPTY_BEAKER) {
                               var beaker=entity.getItem();
                               helper.assertTrue(beaker.is(ModItems.BEAKER.get())&&!beaker.has(net.minecraft.core.component.DataComponents.CUSTOM_NAME)&&plainBeakerName(beaker),
                                       "all planned reagent/empty beakers use ordinary plain display names: "+expected.label());
                           }
                          helper.assertTrue(Math.abs(entity.getX()-(pos.getX()+.5))<=.5&&Math.abs(entity.getZ()-(pos.getZ()+.5))<=.5
                                          &&Math.abs(entity.getY()-pos.getY())<=1.5,"descriptor-matched supply is near planned floor position: "+expected.label()+" planned="+pos+" actual="+entity.position());
                           matchedSupplyEntities.add(entity.getUUID());
                           actualByDescriptor.put(expected.label(),entity.getItem());
                       }
                      helper.assertTrue(matchedSupplyEntities.size()==supplyPlan.size(),"planned supplies bijectively map to distinct local drops");
                    var matchedDoseStacks=new java.util.HashSet<net.minecraft.world.item.ItemStack>();
                     for(var dose:com.juicyslew.moonstation14.ms14.botany.fixture.BotanyFixtureSigns.allFixtureDoses()) {
                        String expectedName=dose.displayStationName()+" "+dose.cents()/100+"u "+dose.reagent();
                         var stack=actualByDescriptor.get(expectedName);
                          helper.assertTrue(stack!=null&&!stack.isEmpty()&&stack.is(ModItems.BEAKER.get())&&!stack.has(net.minecraft.core.component.DataComponents.CUSTOM_NAME)
                                          &&plainBeakerName(stack),"canonical care-dose descriptor resolves to one actual ordinary beaker: "+expectedName);
                         matchedDoseStacks.add(stack);
                        var contents=stack.get(ModDataComponents.REAGENT.get()).centContents();
                        var reagent=com.juicyslew.moonstation14.ms14.reagent.ModReagents.createKey(dose.reagent());
                        helper.assertTrue(contents.size()==1&&contents.getOrDefault(reagent,0L)==dose.cents(),
                                "labelled inventory beaker contains exactly its canonical reagent dose: "+expectedName+" contents="+contents);
                    }
                      helper.assertTrue(matchedDoseStacks.size()==12,"twelve distinct actual inventory stacks back the fixture reagent labels");
                       var reserveReagent=com.juicyslew.moonstation14.ms14.reagent.ModReagents.createKey("unstablemutagen");
                       var reserveDescriptors=descriptors.stream().filter(d->d.label().startsWith("MUTATION RESERVE ")).toList();
                       var reserves=reserveDescriptors.stream().map(d->actualByDescriptor.get(d.label())).toList();
                       helper.assertTrue(reserveDescriptors.size()==4&&reserves.size()==4&&reserves.stream().allMatch(java.util.Objects::nonNull)
                                       &&reserves.stream().map(System::identityHashCode).distinct().count()==4,
                               "four distinct canonical mutation reserve descriptors resolve to four actual beakers");
                       for(int i=0;i<reserves.size();i++) {
                           var reserve=reserves.get(i);
                           helper.assertTrue(reserve.is(ModItems.BEAKER.get())&&!reserve.has(net.minecraft.core.component.DataComponents.CUSTOM_NAME)
                                           &&plainBeakerName(reserve)&&reserve.getOrDefault(ModDataComponents.REAGENT.get(),new ReagentComponent(Map.of())).centContents().equals(Map.of(reserveReagent,500L)),
                                   "canonical mutation reserve has exact 500-cent dose: "+reserveDescriptors.get(i).label());
                       }
                     helper.assertTrue(inventoryStacks.stream().filter(stack->!stack.isEmpty()&&stack.is(ModItems.WIRECUTTER.get())).count()==1,
                             "fixture grants exactly one real wirecutter in ordinary inventory");
                      var sampling=tray(level,SmallBotanyFixtureLayout.samplingTray(origin));
                      int samplingStage=sampling==null||sampling.plantState().genetics()==null?-1:PlantMetabolismEvaluator.rawGrowthStage(sampling.plantState(),sampling.plantState().effectiveProfile());
                      helper.assertTrue(sampling!=null&&net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("moonstation14","wheat").equals(sampling.plantState().seed())
                                      &&sampling.plantState().genetics().profile().scream().enabled()
                                      &&samplingStage>1
                                      &&!sampling.plantState().ready()&&!sampling.plantState().dead(),
                              "V contains resolved screaming wheat: present="+(sampling!=null)+", seed="+(sampling==null?null:sampling.plantState().seed())+", age="+(sampling==null?-1:sampling.plantState().age())+", maturation="+(sampling==null||sampling.plantState().genetics()==null?-1:sampling.plantState().genetics().profile().lifecycle().maturation())+", rawStage="+samplingStage+", scream="+(sampling!=null&&sampling.plantState().genetics()!=null&&sampling.plantState().genetics().profile().scream().enabled())+", genetics="+(sampling==null?null:sampling.plantState().genetics())+", ready="+(sampling!=null&&sampling.plantState().ready())+", dead="+(sampling!=null&&sampling.plantState().dead()));
                     helper.assertTrue(execute(dispatcher,"ms14 botany_fixture status",source.stack())==1,"status succeeds: "+source.feedback());
                    String statusText=source.feedback().get(source.feedback().size()-1);
                    helper.assertTrue(statusText.contains("A NO-REPEAT")&&statusText.contains("B REPEAT")&&statusText.contains("C SELF-HARVEST")
                                    &&(statusLine(statusText,"H CHEMISTRY").contains("CHANGED")||statusLine(statusText,"H CHEMISTRY").contains("WAITING"))
                                    &&(statusLine(statusText,"J GAS").contains("EXCHANGED")||statusLine(statusText,"J GAS").contains("WAITING")
                                    ||statusLine(statusText,"J GAS").contains("MISSING GAS")||statusLine(statusText,"J GAS").contains("WRITE FAILED")),
                            "actual station status reports observed chemistry/gas state without claiming early success: "+statusText);
                  var tomato=tray(level,SmallBotanyFixtureLayout.normalTray(origin,false));
                  var wheat=tray(level,SmallBotanyFixtureLayout.normalTray(origin,true));
                  var weeds=tray(level,SmallBotanyFixtureLayout.dryTray(origin,false));
                  helper.assertTrue(tomato!=null && tomato.plantState().ready(),"tomato fixture tray is cached ready");
                  helper.assertTrue(wheat!=null && wheat.plantState().ready(),"wheat fixture tray is cached ready");
                    helper.assertTrue(weeds!=null && weeds.plantState().seed()!=null
                                    && weeds.plantState().water()==100f && weeds.plantState().nutrition()==100f
                                    && weeds.plantState().weeds()==6 && weeds.warningBits()==4,
                            "weedy fixture tray is a planted healthy weed specimen with weed-only warning: "+(weeds==null?"missing":weeds.warningBits()));
                   var comparisonPositions=java.util.List.of(SmallBotanyFixtureLayout.noRepeatTray(origin), SmallBotanyFixtureLayout.repeatTray(origin), SmallBotanyFixtureLayout.selfHarvestTray(origin));
                   var comparisonModes=java.util.List.of(SeedProfile.Harvest.NO_REPEAT, SeedProfile.Harvest.REPEAT, SeedProfile.Harvest.SELF_HARVEST);
                   for(int i=0;i<comparisonPositions.size();i++) {
                       var comparison=tray(level,comparisonPositions.get(i));
                       helper.assertTrue(comparison!=null && comparison.plantState().ready()
                                       && comparison.plantState().effectiveProfile().lifecycle().harvest()==comparisonModes.get(i),
                               "comparison tray has mature wheat in arranged "+comparisonModes.get(i)+" mode");
                   }
                    var labels=com.juicyslew.moonstation14.ms14.botany.fixture.BotanyFixtureSigns.labels(origin);
                     var actualTrayPositions=java.util.List.of(SmallBotanyFixtureLayout.normalTray(origin,false),
                            SmallBotanyFixtureLayout.normalTray(origin,true),SmallBotanyFixtureLayout.dryTray(origin,false),
                            SmallBotanyFixtureLayout.dryTray(origin,true),SmallBotanyFixtureLayout.untouchedTray(origin),
                            SmallBotanyFixtureLayout.statsMutationTray(origin),SmallBotanyFixtureLayout.gasExchangeTray(origin),
                             SmallBotanyFixtureLayout.noRepeatTray(origin),SmallBotanyFixtureLayout.repeatTray(origin),
                             SmallBotanyFixtureLayout.samplingTray(origin),SmallBotanyFixtureLayout.selfHarvestTray(origin));
                    var protectedUtilityPositions=java.util.List.of(SmallBotanyFixtureLayout.apc(origin),SmallBotanyFixtureLayout.grinder(origin),
                            SmallBotanyFixtureLayout.extractor(origin),SmallBotanyFixtureLayout.lamp(origin),origin.offset(7,1,-3),
                            origin.offset(7,1,-1),origin.offset(8,1,-3),origin.offset(8,1,-2),origin.offset(8,1,-1),
                            origin.offset(6,1,-1),origin.offset(6,1,0),origin.offset(6,1,1),origin.offset(8,1,1),
                            origin.offset(8,1,2),origin.offset(8,1,3),origin.offset(8,2,2),origin.offset(8,3,2));
                    var signPositions=labels.stream().map(com.juicyslew.moonstation14.ms14.botany.fixture.BotanyFixtureSigns.Label::pos).toList();
                    helper.assertTrue(signPositions.stream().distinct().count()==signPositions.size()
                                    &&signPositions.stream().noneMatch(actualTrayPositions::contains)
                                    &&signPositions.stream().noneMatch(protectedUtilityPositions::contains),
                            "all sign positions are unique and disjoint from every tray and power/machine utility");
                    for(BlockPos trayPos:actualTrayPositions) helper.assertTrue(level.getBlockEntity(trayPos) instanceof HydroponicsTrayBlockEntity,
                            "every original and comparison tray remains present after sign placement: "+trayPos);
                    for(var label:labels) {
                        helper.assertTrue(level.getBlockState(label.pos()).is(net.minecraft.world.level.block.Blocks.OAK_SIGN)
                                        &&level.getBlockEntity(label.pos()) instanceof net.minecraft.world.level.block.entity.SignBlockEntity
                                        &&level.getBlockState(label.pos()).canSurvive(level,label.pos())
                                        &&label.pos().getY()==origin.getY()+1
                                        &&label.trayPos()!=null&&!label.pos().equals(label.trayPos()),
                                "station sign exists at "+label.pos());
                        helper.assertTrue(Math.abs(label.pos().getX()-label.trayPos().getX())
                                        +Math.abs(label.pos().getZ()-label.trayPos().getZ())==1,
                                "station arrow points directly to its neighboring tray");
                        var sign=(net.minecraft.world.level.block.entity.SignBlockEntity)level.getBlockEntity(label.pos());
                        for(int i=0;i<label.lines().length;i++) helper.assertTrue(sign.getFrontText().getMessage(i,false).getString().equals(label.lines()[i]),
                                "station sign line matches readable label: "+label.lines()[i]);
                        for(int i=0;i<label.lines().length;i++) helper.assertTrue(sign.getBackText().getMessage(i,false).getString().equals(label.lines()[i]),
                                "station sign is readable from either approach side");
                        helper.assertTrue(level.getBlockEntity(label.trayPos()) instanceof HydroponicsTrayBlockEntity,
                                "sign arrow association targets an actual tray: "+label.lines()[0]);
                    }
                    helper.assertTrue(level.getBlockEntity(SmallBotanyFixtureLayout.apc(origin)) instanceof PowerDeviceBlockEntity,
                            "sign placement preserves the APC at its utility location");
                  BlockPos sample=SmallBotanyFixtureLayout.sample(origin);
                  var reading=AtmosphereService.INSTANCE.readAtmosphere(level,sample).orElseThrow();
                  double pressure=reading.mixture().pressureKpa(1.0),temperature=reading.mixture().temperatureKelvin();
                   helper.assertTrue(reading.status()==com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereReading.Status.FINITE
                          &&Double.isFinite(pressure)&&pressure>=81&&pressure<=121&&Math.abs(temperature-293)<=10,
                           "initialized command room is known finite at expected pressure/temp: "+reading);
                   Vec3 entry=SmallBotanyFixtureLayout.entry(origin);
                   helper.assertTrue(body.isAddedToLevel() && level.getEntity(body.getUUID())==body && body.position().distanceTo(entry) < 1.0,
                            "authorized existing body entered fixture: "+diagnostics(fixture.player(),body,level));
                   BlockPos lampPos=SmallBotanyFixtureLayout.lamp(origin);
                   helper.assertTrue(level.getBlockState(lampPos).is(com.juicyslew.moonstation14.block.ModBlocks.POWER_LAMP.get())
                           &&level.getBlockState(lampPos).getValue(com.juicyslew.moonstation14.block.custom.PowerDeviceBlock.FACING)==Direction.EAST,
                           "real APC-fed lamp is mounted facing its cable host");
                   for (String obsolete : java.util.List.of("build","initialize","enter","kit","scenarios")) {
                       try { dispatcher.execute("ms14 botany_fixture "+obsolete,source.stack()); throw new GameTestAssertException("obsolete command remains exposed: "+obsolete); }
                       catch(com.mojang.brigadier.exceptions.CommandSyntaxException expected) { }
                   }
                     var sourceHv=new CableFaceNode(origin.offset(8,1,-3),Direction.WEST,CableTier.HV);
                     var subHv=new CableFaceNode(origin.offset(8,1,-1),Direction.WEST,CableTier.HV);
                     var subMv=new CableFaceNode(origin.offset(6,1,-1),Direction.EAST,CableTier.MV);
                     var apcMv=new CableFaceNode(origin.offset(6,1,1),Direction.EAST,CableTier.MV);
                                      var apcOutput=new CableFaceNode(origin.offset(8,1,1),Direction.WEST,CableTier.APC);
                      var careLampPos=SmallBotanyFixtureLayout.careLamp(SmallBotanyFixtureLayout.careOrigin(origin));
                      var grinderApcHost=new CableFaceNode(origin.offset(8,1,2),Direction.WEST,CableTier.APC);
                      var grinderApcHostAlternate=new CableFaceNode(origin.offset(8,1,3),Direction.WEST,CableTier.APC);
                      var lampApcHost=new CableFaceNode(origin.offset(8,3,2),Direction.WEST,CableTier.APC);
                      var knownPorts=java.util.List.of(sourceHv,subHv,subMv,apcMv,apcOutput,grinderApcHost,grinderApcHostAlternate,lampApcHost);
                     PowerFixtureReadiness.await(helper,level,500,
                              ()->PowerFixtureReadiness.topologyReadyWithKnownPorts(level,knownPorts.toArray(CableFaceNode[]::new)),
                              ()->"botany native power topology unavailable; ports="+knownPorts
                                      +", topologyReady="+com.juicyslew.moonstation14.ms14.power.graph.PowerGraphService.topologyReady(level)
                                      +", indexedDeviceCount="+PowerRuntime.indexedDeviceCount(level),()->{
                                 try {
                                     BlockPos apcPos=SmallBotanyFixtureLayout.apc(origin);
                                      PowerDeviceBlockEntity apc=(PowerDeviceBlockEntity)level.getBlockEntity(apcPos);
                                      double seededJoules=100_000;
                                       var machine=(ReagentGrinderBlockEntity)level.getBlockEntity(SmallBotanyFixtureLayout.grinder(origin));
                                       var extractor=(SeedExtractorBlockEntity)level.getBlockEntity(SmallBotanyFixtureLayout.extractor(origin));
                                       helper.assertTrue(machine!=null && machine.isLoadedValid(),"fixture grinder owner loaded valid");
                                       helper.assertTrue(extractor!=null && extractor.isLoadedValid(),"fixture extractor owner loaded valid");
                                      var ledger=LevelItemLocationLedgers.forLevel(level);
                                      var beaker=new net.minecraft.world.item.ItemStack(ModItems.BEAKER.get());
                                      beaker.set(ModDataComponents.REAGENT.get(),new ReagentComponent(Map.of()));
                                      var wheatStack=ProduceFactory.createHarvest(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("moonstation14","wheat"),5);
                                      var beakerToken=new ItemToken("botany-fixture-beaker-"+UUID.randomUUID());
                                      var wheatToken=new ItemToken("botany-fixture-wheat-"+UUID.randomUUID());
                                      // Fixture-arranged ownership records model test setup, not player input.
                                      var beakerOrigin=new ItemLocation.BodyHand(UUID.randomUUID(),"left");
                                      var wheatOrigin=new ItemLocation.BodyHand(UUID.randomUUID(),"left");
                                      helper.assertTrue(ledger.register(beakerToken,beakerOrigin).accepted()
                                              &&ledger.register(wheatToken,wheatOrigin).accepted(),"fixture items registered with unique ownership");
                                      var beakerSlot=machine.beakerSlot();
                                      var wheatSlot=machine.chamberSlots().get(0);
                                      helper.assertTrue(beakerSlot.insert(0,ledger.snapshot().revision(),beakerToken,beakerOrigin,beaker),"fixture beaker inserted through slot owner");
                                      helper.assertTrue(wheatSlot.insert(0,ledger.snapshot().revision(),wheatToken,wheatOrigin,wheatStack),"fixture wheat inserted through slot owner");
                                      helper.assertTrue(machine.startTrusted(machine.startProof(),level.getGameTime(),true),"trusted grinder domain start accepted");
                                      PowerFixtureReadiness.await(helper,level,30,
                                                ()->PowerRuntime.processingMeter(level,machine).known()
                                                        &&PowerRuntime.processingMeter(level,machine).deliveredWatts()==750
                                                         &&PowerRuntime.processingMeter(level,extractor).known()
                                                         &&PowerRuntime.processingMeter(level,extractor).deliveredWatts()==1000
                                                         &&extractor.isPowered()
                                                         &&level.getBlockState(extractor.getBlockPos()).getValue(SeedExtractorBlock.POWERED)
                                                        &&level.getBlockState(careLampPos).is(com.juicyslew.moonstation14.block.ModBlocks.POWER_LAMP.get())
                                                        &&level.getBlockState(careLampPos).getValue(com.juicyslew.moonstation14.block.custom.PowerDeviceBlock.LIT),
                                                ()->"botany machine/care-lamp allocation mismatch: grinder="+PowerRuntime.processingMeter(level,machine)+", extractor="+PowerRuntime.processingMeter(level,extractor)
                                                        +", careLamp="+level.getBlockState(careLampPos)+", solve="+PowerRuntime.lastSolveDiagnostic(level),()->{
                                       PowerFixtureReadiness.await(helper,level,30,
                                               ()->apc.energyJoules()>seededJoules
                                                      && PowerRuntime.apcOutputObservation(level,apc).known()
                                                      && PowerRuntime.apcOutputObservation(level,apc).outputWatts()>0
                                                       &&level.getBlockState(SmallBotanyFixtureLayout.lamp(origin)).getValue(com.juicyslew.moonstation14.block.custom.PowerDeviceBlock.LIT)
                                                       &&level.getBlockState(careLampPos).getValue(com.juicyslew.moonstation14.block.custom.PowerDeviceBlock.LIT)
                                                       &&level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK,net.minecraft.core.BlockPos.containing(SmallBotanyFixtureLayout.entry(origin)))>0,
                                              ()->"botany APC did not charge from native power: energy="+apc.energyJoules()
                                                      +", output="+PowerRuntime.apcOutputObservation(level,apc)
                                                       +", lamp="+level.getBlockState(SmallBotanyFixtureLayout.lamp(origin))
                                                       +", careLamp="+level.getBlockState(careLampPos)
                                                       +", entryBlockLight="+level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK,net.minecraft.core.BlockPos.containing(SmallBotanyFixtureLayout.entry(origin)))
                                                      +", solve="+PowerRuntime.lastSolveDiagnostic(level),
                                               ()->{
                                                     int feedbackBeforeStatus=source.feedback().size();
                                                     int statusResult=execute(dispatcher,"ms14 botany_fixture status",source.stack());
                                                     helper.assertTrue(statusResult==1 && source.feedback().size()>feedbackBeforeStatus
                                                             &&source.feedback().subList(feedbackBeforeStatus,source.feedback().size()).stream()
                                                                      .anyMatch(message -> prefixedStatusLine(message,"POWER DIAGNOSTICS (technical)")
                                                                             .contains("extractor=VentMeterObservation[known=true, deliveredWatts=1000.0]")),
                                                           "fixture status reports extractor powered by its resolved 1000 W demand: "+source.feedback());
                                                    var mutationTray=(HydroponicsTrayBlockEntity)level.getBlockEntity(SmallBotanyFixtureLayout.untouchedTray(origin));
                                                    var vestine=com.juicyslew.moonstation14.ms14.reagent.ModReagents.createKey("vestine");
                                                     helper.assertTrue(initialVestineCents.get()==100L
                                                                     &&mutationTray.plantState().seed().equals(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("moonstation14","wheat")),
                                                             "setup-ready callback observed normal wheat with exactly 1 unit Vestine before ordinary polling; later soil may be consumed");
                                                     PowerFixtureReadiness.await(helper,level,300,
                                                            ()->mutationTray.plantState().effectiveProfile().chemicals().entrySet().stream().anyMatch(e->!e.getValue().inherent()),
                                                             ()->"ordinary tray metabolism did not acquire non-inherent chemistry: "+mutationTray.plantState().effectiveProfile().chemicals(),()->{
                                                                try {
                                                                    var acquiredIds=mutationTray.plantState().effectiveProfile().chemicals().entrySet().stream()
                                                                            .filter(e->!e.getValue().inherent()).map(e->e.getKey().toString()).collect(java.util.stream.Collectors.toSet());
                                                                    var tableId=com.juicyslew.moonstation14.ms14.botany.ModWeightedRandomFillSolutions.canonicalId("EvilRandomFillSolution");
                                                                    var table=com.juicyslew.moonstation14.ms14.botany.ModWeightedRandomFillSolutions.serverCatalog().get(tableId);
                                                                    var allowedIds=table.fills().stream().flatMap(group->group.reagents().stream()).map(Object::toString).collect(java.util.stream.Collectors.toSet());
                                                                    helper.assertTrue(acquiredIds.size()==1&&allowedIds.containsAll(acquiredIds),
                                                                            "exactly one acquired reagent comes from resolved EvilRandomFillSolution: acquired="+acquiredIds+", allowed="+allowedIds);
                                                                      helper.assertTrue(execute(dispatcher,"ms14 botany_fixture status",source.stack())==1
                                                                              &&source.feedback().stream().anyMatch(message->statusLine(message,"H CHEMISTRY").contains("CHANGED")
                                                                                      &&statusLine(message,"H CHEMISTRY").contains(acquiredIds.iterator().next())),
                                                                            "fixture status reports acquired non-inherent reagent IDs: "+source.feedback());
                                                                 var statsTray=(HydroponicsTrayBlockEntity)level.getBlockEntity(SmallBotanyFixtureLayout.statsMutationTray(origin));
                                                                  helper.assertTrue(initialMutagenCents.get()==0L&&Float.valueOf(3f).equals(initialModifier.get())
                                                                          &&statsTray.plantState().seed().equals(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("moonstation14","wheat")),
                                                                          "setup-ready snapshot confirms normal wheat, empty soil, and operator-preset modifier 3 before dosing");
                                                                                 var gasTray=(HydroponicsTrayBlockEntity)level.getBlockEntity(SmallBotanyFixtureLayout.gasExchangeTray(origin));
                                                                                var nitrogen=com.juicyslew.moonstation14.ms14.atmos.core.GasType.NITROGEN;
                                                                                var oxygen=com.juicyslew.moonstation14.ms14.atmos.core.GasType.OXYGEN;
                                                                                helper.assertTrue(Map.of(nitrogen,.02f).equals(initialGasConsume.get())
                                                                                        &&Map.of(oxygen,.01f).equals(initialGasExude.get())
                                                                                        &&net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("moonstation14","wheat").equals(initialGasSpecies.get()),
                                                                                        "setup-ready callback snapshots normal wheat gas genes before exchange polling");
                                                                                BlockPos exposed=SmallBotanyFixtureLayout.gasExchangeTray(origin).above();
                                                                                helper.assertTrue(!level.getBlockState(exposed).isSolid()
                                                                                        &&AtmosphereService.INSTANCE.readAtmosphere(level,exposed)
                                                                                                .map(r->r.status()==com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereReading.Status.FINITE).orElse(false),
                                                                                        "gas tray exposes an available finite-owned atmosphere cell");
                                                                                PowerFixtureReadiness.await(helper,level,320,
                                                                                        ()->gasTray.gasExchangeReport().attempted(),
                                                                                        ()->"ordinary first-cycle gas exchange report was not attempted: "+gasTray.gasExchangeReport(),()->{
                                                                                   try {
                                                                                     var report=gasTray.gasExchangeReport();
                                                                                     helper.assertTrue(Map.of(nitrogen,.02f).equals(gasTray.plantState().effectiveProfile().consumeGasses())
                                                                                             &&Map.of(oxygen,.01f).equals(gasTray.plantState().effectiveProfile().exudeGasses()),
                                                                                             "arranged maps remain configured after normal first cycle");
                                                                                     helper.assertTrue(report.unmetRequiredGases().isEmpty()&&report.failedExudationGases().isEmpty(),
                                                                                             "actual finite-cell exchange report has no missing consume or failed exudation: "+report);
                                                                                       helper.assertTrue(execute(dispatcher,"ms14 botany_fixture status",source.stack())==1
                                                                                               &&source.feedback().stream().anyMatch(BotanyFixtureCommandGameTests::gasStatusCompleted),
                                                                                             "status presents completed state from actual gas exchange report");
                                                                                      var extractorIdentity=extractor.placementIdentity();
                                                                                      // Trusted fixture topology change: remove its actual APC through the
                                                                                      // normal world removal path after positive powered assertions.
                                                                                      level.removeBlock(apcPos,false);
                                                                                      PowerFixtureReadiness.await(helper,level,60,
                                                                                              ()->extractor.isLoadedValid()
                                                                                                      &&extractor.placementIdentity().equals(extractorIdentity)
                                                                                                      &&!extractor.isPowered()
                                                                                                      &&!level.getBlockState(extractor.getBlockPos()).getValue(SeedExtractorBlock.POWERED),
                                                                                              ()->"extractor power visual did not follow real APC removal: powered="+extractor.isPowered()
                                                                                                      +", blockstate="+level.getBlockState(extractor.getBlockPos())
                                                                                                      +", meter="+PowerRuntime.processingMeter(level,extractor)
                                                                                                      +", solve="+PowerRuntime.lastSolveDiagnostic(level),()->{
                                                                                          try { helper.assertTrue(extractor.isInitialized() && extractor.placementIdentity().equals(extractorIdentity),
                                                                                                  "same initialized extractor placement survives real power loss");
                                                                                              fixture.close(); helper.succeed();
                                                                                          } catch(RuntimeException|Error failure) { fixture.close(); throw failure; }
                                                                                      });
                                                                                   } catch(RuntimeException|Error failure) { fixture.close(); throw failure; }
                                                                                  });
                                                                } catch(RuntimeException|Error failure) { fixture.close(); throw failure; }
                                                            }); });
                                      });
                                 } catch(RuntimeException | Error failure) {
                                     fixture.close();
                                     throw failure;
                                 }
                             });
                } catch(RuntimeException | Error failure) {
                  fixture.close();
                  throw failure;
                }
              });
              sourceRef.set(source);
          } catch(RuntimeException | Error failure) {
            fixture.close();
            throw failure;
          }
        });
    }

    @GameTest(template="botany_fixture_space", templateNamespace="moonstation14", timeoutTicks=700)
    @GameTestTags({"botany-care-showcase", "botany-care"})
    public static void registeredPlayerPoursPhalanximineIntoCareTray(GameTestHelper helper) {
        ServerLevel level=(ServerLevel)helper.getLevel();
        var fixture=RegisteredServerPlayerFixture.register(level,"botany-care-phalanximine");
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
                var source=capture(fixture.player().createCommandSourceStack().withPermission(2));
                var dispatcher=level.getServer().getCommands().getDispatcher();
                helper.assertTrue(execute(dispatcher,"ms14 botany_fixture",source.stack())==1,"fixture command accepted");
                helper.runAfterDelay(30,()->{
                    try {
                        helper.assertTrue(source.feedback().stream().anyMatch(m->m.contains("Botany fixture ready")),"fixture ready feedback");
                        var labels=com.juicyslew.moonstation14.ms14.botany.fixture.BotanyFixtureSigns.careLabels(SmallBotanyFixtureLayout.careOrigin(origin));
                        var label=labels.get(9); var care=tray(level,label.trayPos());
                        helper.assertTrue(care!=null&&care.plantState().seed()!=null&&care.plantState().genetics()!=null
                                &&!care.plantState().effectiveProfile().lifecycle().viable()&&care.soilSolution().snapshotUnits().isEmpty(),"T Phalanximine tray baseline from fixture");
                         var tDose=com.juicyslew.moonstation14.ms14.botany.fixture.BotanyFixtureSigns.careDoses().get(9);
                          String expected=tDose.displayStationName()+" "+tDose.cents()/100+"u "+tDose.reagent();
                          var doseArea=grantArea(origin);
                          var doseMatches=findPlannedFixtureBeaker(level,origin,expected,"phalanximine",tDose.cents());
                          helper.assertTrue(doseMatches.size()==1,"one actual labelled T beaker in own grant area; "+grantDiagnostics(level,origin,expected,doseMatches));
                         var iLoose=doseMatches.get(0);
                         var stack=iLoose.getItem();
                         var reagent=com.juicyslew.moonstation14.ms14.reagent.ModReagents.createKey("phalanximine");
                         helper.assertTrue(stack.getCount()==1&&stack.get(ModDataComponents.REAGENT.get()).centContents().getOrDefault(reagent,0L)==400,"actual labelled world beaker contains 400 cents");
                         var published=stack;
                         helper.assertTrue(!published.isEmpty()&&published.is(ModItems.BEAKER.get())&&published.getCount()==1
                                 &&!published.has(net.minecraft.core.component.DataComponents.CUSTOM_NAME)&&plainBeakerName(published)
                                &&published.get(ModDataComponents.REAGENT.get()).centContents().getOrDefault(reagent,0L)==400,
                                "published loose source is the captured actual labelled beaker and dose");
                         var hands=body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());
                         String hand=hands.activeHand();
                          var beforePickup=com.juicyslew.moonstation14.ms14.hands.network.BodyHandStateService.query(fixture.player(),new com.juicyslew.moonstation14.ms14.hands.network.BodyHandStateQuery(active.epoch(),2));
                          helper.assertTrue(beforePickup.reason()==com.juicyslew.moonstation14.ms14.hands.network.BodyHandStateSnapshot.Reason.OK,"first post-ready dose query has fresh sequence");
                          var activeBefore=beforePickup.hands().stream().filter(h->h.id().equals(hand)).findFirst().orElseThrow();
                          helper.assertTrue(activeBefore.token()==null&&hands.stackCopy(hand).isEmpty(),"all fixture supplies are world-placed and active hand starts empty");
                          var emptyHandsBeforePickup=body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());
                          helper.assertTrue(emptyHandsBeforePickup.revision()==beforePickup.revision(),"empty-hand assertion uses fresh queried hand revision");
                          helper.assertTrue(aimFromSupplyPosition(level,body,iLoose),"actual labelled T dose is visible from a collision-free nearby stance");
                           helper.assertTrue(com.juicyslew.moonstation14.ms14.hands.live.BodyHandItemTransfer.pickupSelected(fixture.player(),iLoose.getUUID(),hand,emptyHandsBeforePickup.revision(),ignored->{})==com.juicyslew.moonstation14.ms14.hands.live.BodyHandItemTransfer.Result.SUCCESS,"normal selected-hand pickup of existing labelled dose");
                         helper.assertTrue(level.getEntity(iLoose.getUUID())==null,"pickup consumes same original labelled dose entity");
                         body.setPos(label.trayPos().getX()-.5,label.trayPos().getY(),label.trayPos().getZ()+.5);
                         helper.assertTrue(level.noCollision(body,body.getBoundingBox()),"T tray interaction stance is collision-free on the floor");
                         var query=com.juicyslew.moonstation14.ms14.hands.network.BodyHandStateService.query(fixture.player(),new com.juicyslew.moonstation14.ms14.hands.network.BodyHandStateQuery(active.epoch(),3));
                        var held=query.hands().stream().filter(h->h.id().equals(hand)).findFirst().orElseThrow();
                        var hit=RegisteredPlayerReadyDriver.aimAt(body,level,label.trayPos());
                        var request=new com.juicyslew.moonstation14.ms14.hands.network.BodyBlockUseRequest(active.epoch(),1,query.revision(),hand,held.token(),label.trayPos(),hit.getDirection(),false,com.juicyslew.moonstation14.ms14.hands.network.BodyBlockUseRequest.Intent.USE);
                        var outcome=com.juicyslew.moonstation14.ms14.hands.network.AuthenticatedBlockUseDriver.use(fixture.player(),request);
                        helper.assertTrue(outcome==com.juicyslew.moonstation14.ms14.hands.network.BodyBlockUseResult.Outcome.PLACED,"ordinary body USE pours beaker through registered action handler");
                         helper.assertTrue(care.soilSolution().snapshotUnits().getOrDefault(reagent,0L)==400,"soil immediately receives 400 cents");
                         helper.assertTrue(execute(dispatcher,"ms14 botany_fixture start "+com.juicyslew.moonstation14.ms14.botany.fixture.BotanyFixtureStations.lookup("T").number(),source.stack())==1,"activate T only after Phalanximine pour");
                         var after=com.juicyslew.moonstation14.ms14.hands.network.BodyHandStateService.query(fixture.player(),new com.juicyslew.moonstation14.ms14.hands.network.BodyHandStateQuery(active.epoch(),4));
                        var afterHand=after.hands().stream().filter(h->h.id().equals(hand)).findFirst().orElseThrow();
                        var liveHands=body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());
                        var heldBeaker=liveHands.stackCopy(hand).orElseThrow();
                        helper.assertTrue(after.revision()>query.revision()&&liveHands.revision()==after.revision()&&afterHand.token().equals(held.token())
                                &&heldBeaker.is(ModItems.BEAKER.get())&&heldBeaker.getCount()==1,"emptied beaker remains in same active hand with owner token and updated revision");
                         helper.assertTrue(!heldBeaker.has(net.minecraft.core.component.DataComponents.CUSTOM_NAME)&&plainBeakerName(heldBeaker),"empty beaker has ordinary name and no station label");
                        var remaining=heldBeaker.get(ModDataComponents.REAGENT.get());
                        helper.assertTrue(remaining==null||remaining.centContents().isEmpty(),"held beaker has no remaining reagent");
                         helper.assertTrue(level.getEntity(iLoose.getUUID())==null,"picked-up original source has no duplicate loose beaker");
                        PowerFixtureReadiness.await(helper,level,60,()->care.soilSolution().snapshotUnits().getOrDefault(reagent,0L)==300&&care.plantState().effectiveProfile().lifecycle().viable(),()->"Phalanximine care response not observed: soil="+care.soilSolution().snapshotUnits()+" viable="+care.plantState().effectiveProfile().lifecycle().viable(),()->{
                            try {
                                var handsNow=body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());
                                 var dropQuery=com.juicyslew.moonstation14.ms14.hands.network.BodyHandStateService.query(fixture.player(),new com.juicyslew.moonstation14.ms14.hands.network.BodyHandStateQuery(active.epoch(),5));
                                var dropHand=dropQuery.hands().stream().filter(h->h.id().equals(hand)).findFirst().orElseThrow();
                                var dropped=com.juicyslew.moonstation14.ms14.hands.network.BodyHandRequestService.apply(fixture.player(),new com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionRequest(com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionRequest.Action.DROP,hand,active.epoch(),5,dropQuery.revision(),null,dropHand.token()));
                                 helper.assertTrue(dropped.accepted(),"normal authenticated DROP accepted for emptied Phalanximine beaker");
                                 handsNow=body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());
                                 helper.assertTrue(handsNow.stackCopy(hand).isEmpty(),"fresh live-hand attachment after accepted DROP proves active hand is empty");
                                var weedLabel=labels.get(3); var weedTray=tray(level,weedLabel.trayPos());
                                helper.assertTrue(weedTray!=null,"N weeds care tray exists");
                                float weedsBefore=weedTray.plantState().weeds(),toxinsBefore=weedTray.plantState().toxins();
                                 var nDose=com.juicyslew.moonstation14.ms14.botany.fixture.BotanyFixtureSigns.careDoses().get(3);
                                   String weedName=nDose.displayStationName()+" "+nDose.cents()/100+"u "+nDose.reagent();
                                    var weedMatches=findPlannedFixtureBeaker(level,origin,weedName,"weedkiller",nDose.cents());
                                 helper.assertTrue(weedMatches.size()==1,"one actual canonical N care-dose grant exists");
                                 var weedLoose=weedMatches.get(0);
                                 var weedStack=weedLoose.getItem();
                                var weedkiller=com.juicyslew.moonstation14.ms14.reagent.ModReagents.createKey("weedkiller");
                                  helper.assertTrue(weedStack.getOrDefault(ModDataComponents.REAGENT.get(),new ReagentComponent(Map.of())).centContents().equals(Map.of(weedkiller,nDose.cents())),"actual N world grant contains exactly configured cents weedkiller");
                                 var emptyHands=body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());
                                  helper.assertTrue(aimFromSupplyPosition(level,body,weedLoose),"actual N grant selected from nearby collision-free native pickup stance");
                                 helper.assertTrue(com.juicyslew.moonstation14.ms14.hands.live.BodyHandItemTransfer.pickupSelected(fixture.player(),weedLoose.getUUID(),hand,emptyHands.revision(),ignored->{})==com.juicyslew.moonstation14.ms14.hands.live.BodyHandItemTransfer.Result.SUCCESS,"normal selected-hand pickup of N kit beaker");
                                 body.setPos(weedLabel.trayPos().getX()-.5,weedLabel.trayPos().getY(),weedLabel.trayPos().getZ()+.5);
                                 helper.assertTrue(level.noCollision(body,body.getBoundingBox()),"N tray interaction stance is collision-free on the floor");
                                 var weedQuery=com.juicyslew.moonstation14.ms14.hands.network.BodyHandStateService.query(fixture.player(),new com.juicyslew.moonstation14.ms14.hands.network.BodyHandStateQuery(active.epoch(),7));
                                var weedHeld=weedQuery.hands().stream().filter(h->h.id().equals(hand)).findFirst().orElseThrow();
                                var weedHit=RegisteredPlayerReadyDriver.aimAt(body,level,weedLabel.trayPos());
                                var weedRequest=new com.juicyslew.moonstation14.ms14.hands.network.BodyBlockUseRequest(active.epoch(),7,weedQuery.revision(),hand,weedHeld.token(),weedLabel.trayPos(),weedHit.getDirection(),false,com.juicyslew.moonstation14.ms14.hands.network.BodyBlockUseRequest.Intent.USE);
                                helper.assertTrue(com.juicyslew.moonstation14.ms14.hands.network.AuthenticatedBlockUseDriver.use(fixture.player(),weedRequest)==com.juicyslew.moonstation14.ms14.hands.network.BodyBlockUseResult.Outcome.PLACED,"normal authenticated body USE pours N weedkiller");
                                 helper.assertTrue(weedTray.soilSolution().snapshotUnits().getOrDefault(weedkiller,0L)==100,"N soil immediately receives 100 cents weedkiller");
                                 helper.assertTrue(execute(dispatcher,"ms14 botany_fixture start "+com.juicyslew.moonstation14.ms14.botany.fixture.BotanyFixtureStations.lookup("N").number(),source.stack())==1,"activate N after ordinary weedkiller pour");
                                float weedsAtPour=weedTray.plantState().weeds(),toxinsAtPour=weedTray.plantState().toxins();
                                var heldAfter=body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get()).stackCopy(hand).orElseThrow();
                                 helper.assertTrue(!heldAfter.has(net.minecraft.core.component.DataComponents.CUSTOM_NAME)&&plainBeakerName(heldAfter)&&heldAfter.getOrDefault(ModDataComponents.REAGENT.get(),new ReagentComponent(Map.of())).centContents().isEmpty()&&level.getEntity(weedLoose.getUUID())==null,"N dose emptied in owned plain beaker; loose source consumed");
                                 PowerFixtureReadiness.await(helper,level,60,()->weedTray.soilSolution().snapshotUnits().getOrDefault(weedkiller,0L)==0&&weedTray.plantState().weeds()<=Math.max(0,weedsAtPour-6)&&weedTray.plantState().toxins()>=toxinsAtPour+4,()->"N weedkiller response not observed; weeds="+weedTray.plantState().weeds()+" tox="+weedTray.plantState().toxins()+" soil="+weedTray.soilSolution().snapshotUnits()+" baseline="+weedsBefore+"/"+toxinsBefore,()->{
                                     try {
                                         var handsForIDrop=body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());
                                          var iDropQuery=com.juicyslew.moonstation14.ms14.hands.network.BodyHandStateService.query(fixture.player(),new com.juicyslew.moonstation14.ms14.hands.network.BodyHandStateQuery(active.epoch(),9));
                                         var iDropHand=iDropQuery.hands().stream().filter(h->h.id().equals(hand)).findFirst().orElseThrow();
                                         var iDropped=com.juicyslew.moonstation14.ms14.hands.network.BodyHandRequestService.apply(fixture.player(),new com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionRequest(com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionRequest.Action.DROP,hand,active.epoch(),9,iDropQuery.revision(),null,iDropHand.token()));
                                          helper.assertTrue(iDropped.accepted(),"normal DROP accepted for prior emptied beaker before I pickup");
                                          handsForIDrop=body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());
                                          helper.assertTrue(handsForIDrop.stackCopy(hand).isEmpty(),"fresh live-hand attachment after accepted DROP proves hand is empty before I pickup");
                                         var iLabel=com.juicyslew.moonstation14.ms14.botany.fixture.BotanyFixtureSigns.labels(origin).stream().filter(l->l.title().equals("I MUTAGEN")).findFirst().orElseThrow();
                                         var iTray=tray(level,iLabel.trayPos());
                                         var mutagen=com.juicyslew.moonstation14.ms14.reagent.ModReagents.createKey("unstablemutagen");
                                         helper.assertTrue(iTray!=null&&iTray.soilSolution().snapshotUnits().isEmpty()&&Float.valueOf(3f).equals(iTray.plantState().mutationModifier()),"I starts with empty soil and explicit modifier-3 preset before player dose");
                                           String iName="MUTATION RESERVE 1 5u unstablemutagen";
                                            var iMatches=findPlannedFixtureBeaker(level,origin,iName,"unstablemutagen",500L);
                                          helper.assertTrue(iMatches.size()==1,"one actual canonical I care-dose grant exists");
                                          var iDoseLoose=iMatches.get(0);
                                          var iStack=iDoseLoose.getItem();
                                           helper.assertTrue(iStack.getOrDefault(ModDataComponents.REAGENT.get(),new ReagentComponent(Map.of())).centContents().equals(Map.of(mutagen,500L)),"actual I world grant contains exactly 500 cents mutagen");
                                          var emptyHandsI=body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());
                                           helper.assertTrue(aimFromSupplyPosition(level,body,iDoseLoose),"actual I grant selected from nearby collision-free native pickup stance");
                                           helper.assertTrue(com.juicyslew.moonstation14.ms14.hands.live.BodyHandItemTransfer.pickupSelected(fixture.player(),iDoseLoose.getUUID(),hand,emptyHandsI.revision(),ignored->{})==com.juicyslew.moonstation14.ms14.hands.live.BodyHandItemTransfer.Result.SUCCESS,"normal selected-hand pickup of I kit beaker");
                                          body.setPos(iLabel.trayPos().getX()+1.5,iLabel.trayPos().getY(),iLabel.trayPos().getZ()+.5);
                                          helper.assertTrue(level.noCollision(body,body.getBoundingBox()),"I tray interaction stance is collision-free on the floor");
                                          var iQuery=com.juicyslew.moonstation14.ms14.hands.network.BodyHandStateService.query(fixture.player(),new com.juicyslew.moonstation14.ms14.hands.network.BodyHandStateQuery(active.epoch(),11));
                                         var iHeld=iQuery.hands().stream().filter(h->h.id().equals(hand)).findFirst().orElseThrow();
                                         var iHit=RegisteredPlayerReadyDriver.aimAt(body,level,iLabel.trayPos());
                                         var iRequest=new com.juicyslew.moonstation14.ms14.hands.network.BodyBlockUseRequest(active.epoch(),11,iQuery.revision(),hand,iHeld.token(),iLabel.trayPos(),iHit.getDirection(),false,com.juicyslew.moonstation14.ms14.hands.network.BodyBlockUseRequest.Intent.USE);
                                         helper.assertTrue(com.juicyslew.moonstation14.ms14.hands.network.AuthenticatedBlockUseDriver.use(fixture.player(),iRequest)==com.juicyslew.moonstation14.ms14.hands.network.BodyBlockUseResult.Outcome.PLACED,"normal authenticated body USE pours actual I mutagen beaker");
                                          helper.assertTrue(iTray.soilSolution().snapshotUnits().getOrDefault(mutagen,0L)==500,"I soil immediately receives actual 500-cent dose");
                                          helper.assertTrue(execute(dispatcher,"ms14 botany_fixture start "+com.juicyslew.moonstation14.ms14.botany.fixture.BotanyFixtureStations.lookup("I").number(),source.stack())==1,"activate I after ordinary mutagen pour");
                                         var heldI=body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get()).stackCopy(hand).orElseThrow();
                                          helper.assertTrue(heldI.get(ModDataComponents.REAGENT.get()).centContents().isEmpty()&&level.getEntity(iDoseLoose.getUUID())==null,"I dose transfers once into soil; owned beaker is empty and source entity removed");
                                         PowerFixtureReadiness.await(helper,level,300,()->iTray.soilSolution().snapshotUnits().getOrDefault(mutagen,0L)==400,()->"ordinary I soil polling did not drain one 100-cent mutagen dose; soil="+iTray.soilSolution().snapshotUnits(),()->{ fixture.close(); helper.succeed(); });
                                     } catch(RuntimeException|Error failure) { fixture.close(); throw failure; }
                                 });
                            } catch(RuntimeException|Error failure) { fixture.close(); throw failure; }
                        });
                    } catch(RuntimeException|Error failure) { fixture.close(); throw failure; }
                });
            } catch(RuntimeException|Error failure) { fixture.close(); throw failure; }
        });
    }

    private static HydroponicsTrayBlockEntity tray(ServerLevel level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof HydroponicsTrayBlockEntity tray ? tray : null;
    }

    private static boolean aimFromSupplyPosition(ServerLevel level,net.minecraft.world.entity.LivingEntity body,
                                                  com.juicyslew.moonstation14.entities.LooseItemEntity item) {
        var center=item.getBoundingBox().getCenter();
        int baseX=net.minecraft.core.BlockPos.containing(center).getX(), baseZ=net.minecraft.core.BlockPos.containing(center).getZ();
        for(int dx=-2;dx<=2;dx++) for(int dz=-2;dz<=2;dz++) {
            body.setPos(baseX+dx+.5,item.getY(),baseZ+dz+.5);
            if(!level.noCollision(body,body.getBoundingBox())) continue;
            body.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES,center);
            if(com.juicyslew.moonstation14.ms14.hands.live.BodyItemTargetProbe.probeSelected(level,body,item.getUUID(),
                    com.juicyslew.moonstation14.ms14.hands.live.BodyItemTargetProbe.MAX_REACH)!=null) return true;
        }
        return false;
    }

    private static String statusLine(String message, String station) {
        return java.util.Arrays.stream(message.split("\\R")).filter(line -> line.startsWith(station)
                && (line.contains("CHANGED") || line.contains("WAITING") || line.contains("EXCHANGED")
                || line.contains("MISSING GAS") || line.contains("WRITE FAILED") || line.contains("PRESET")))
                .findFirst().orElse("");
    }
    private static String prefixedStatusLine(String message, String prefix) {
        return java.util.Arrays.stream(message.split("\\R")).filter(line -> line.startsWith(prefix))
                .findFirst().orElse("");
    }
    static net.minecraft.world.phys.AABB grantArea(BlockPos origin) {
        return footprintBounds(SmallBotanyFixtureLayout.plannedFootprint(origin));
    }
    private static net.minecraft.world.phys.AABB footprintBounds(java.util.List<BlockPos> footprint) {
        int minX=footprint.stream().mapToInt(BlockPos::getX).min().orElseThrow(), maxX=footprint.stream().mapToInt(BlockPos::getX).max().orElseThrow();
        int minY=footprint.stream().mapToInt(BlockPos::getY).min().orElseThrow(), maxY=footprint.stream().mapToInt(BlockPos::getY).max().orElseThrow();
        int minZ=footprint.stream().mapToInt(BlockPos::getZ).min().orElseThrow(), maxZ=footprint.stream().mapToInt(BlockPos::getZ).max().orElseThrow();
        return new net.minecraft.world.phys.AABB(minX,minY,minZ,maxX+1,maxY+1,maxZ+1);
    }
    private static String grantDiagnostics(ServerLevel level, BlockPos origin, String expected,
                                           java.util.List<com.juicyslew.moonstation14.entities.LooseItemEntity> matches) {
        var nearby=level.getEntitiesOfClass(com.juicyslew.moonstation14.entities.LooseItemEntity.class,grantArea(origin),e->!e.getItem().isEmpty());
        String details=nearby.stream().limit(6).map(e->e.getItem().getHoverName().getString()+"@"+e.position()).collect(java.util.stream.Collectors.joining(","));
        String result="expected="+expected+", count="+matches.size()+", nearby="+nearby.size()+", entities="+details;
        return result.length()>900?result.substring(0,900):result;
    }
    private static boolean gasStatusCompleted(String message) {
        return message.contains("ordinary cycle report confirms gas exchange")
                && !message.contains("MISSING GAS") && !message.contains("WRITE FAILED");
    }

    private static String diagnostics(net.minecraft.server.level.ServerPlayer player, net.minecraft.world.entity.LivingEntity body, ServerLevel level) {
        return "player[mode="+player.gameMode.getGameModeForPlayer()+",camera="+player.getCamera().getUUID()+"/body="+body.getUUID()
                +",alive="+player.isAlive()+",passenger="+player.isPassenger()+",menu="+(player.containerMenu==player.inventoryMenu)
                +"] body[alive="+body.isAlive()+",added="+body.isAddedToLevel()+",passenger="+body.isPassenger()
                +",levelEntity="+(level.getEntity(body.getUUID())==body)+"] active="+LifecycleCharacterSessionControl.activeCharacterBody(player).isPresent()
                +" authority="+HandActorAuthority.resolve(player).isPresent();
    }

    record CapturedSource(net.minecraft.commands.CommandSourceStack stack, java.util.List<String> feedback) { }
    static CapturedSource capture(net.minecraft.commands.CommandSourceStack original) { return capture(original, ignored->{ }); }
    static CapturedSource capture(net.minecraft.commands.CommandSourceStack original, java.util.function.Consumer<String> observer) {
        var messages=new java.util.ArrayList<String>();
        CommandSource wrapper=new CommandSource() {
            @Override public void sendSystemMessage(Component message) { String text=message.getString(); messages.add(text); observer.accept(text); }
            @Override public boolean acceptsSuccess() { return true; }
            @Override public boolean acceptsFailure() { return true; }
            @Override public boolean shouldInformAdmins() { return false; }
        };
        return new CapturedSource(original.withSource(wrapper),messages);
    }

    static int execute(com.mojang.brigadier.CommandDispatcher<net.minecraft.commands.CommandSourceStack> dispatcher,
                               String command,net.minecraft.commands.CommandSourceStack source) {
        try { return dispatcher.execute(command,source); }
        catch(com.mojang.brigadier.exceptions.CommandSyntaxException failure) {
            throw new GameTestAssertException("public command syntax failed: "+command+": "+failure.getMessage());
        }
    }
}
