package com.juicyslew.moonstation14.ms14.botany;

import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.gametest.support.RegisteredServerPlayerFixture;
import com.juicyslew.moonstation14.gametest.support.PowerFixtureReadiness;
import com.juicyslew.moonstation14.gametest.support.FixtureChunkTickets;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server.RegisteredPlayerReadyDriver;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Exercises all eight tolerance holders produced by the public fixture command through ordinary tray polling. */
@GameTestTags({"botany", "botany-tolerance", "fixture"})
@PrefixGameTestTemplate(false)
public final class BotanyToleranceCommandGameTests {
    private BotanyToleranceCommandGameTests() { }

    @GameTest(template="botany_fixture_space", templateNamespace="moonstation14", timeoutTicks=900)
    public static void commandBuiltTolerancePairsResolveOnFirstOrdinaryCycle(GameTestHelper helper) {
        ServerLevel level=(ServerLevel)helper.getLevel();
        var fixture=RegisteredServerPlayerFixture.register(level,"botany-tolerance-command");
        helper.testInfo.addListener(new net.minecraft.gametest.framework.GameTestListener() {
            private void close(net.minecraft.gametest.framework.GameTestInfo info) {
                try { fixture.close(); }
                catch(Throwable cleanup) {
                    Throwable original=info.getError();
                    if(original!=null) original.addSuppressed(cleanup); else info.fail(cleanup);
                }
            }
            @Override public void testStructureLoaded(net.minecraft.gametest.framework.GameTestInfo ignored) { }
            @Override public void testPassed(net.minecraft.gametest.framework.GameTestInfo info,net.minecraft.gametest.framework.GameTestRunner runner) { close(info); }
            @Override public void testFailed(net.minecraft.gametest.framework.GameTestInfo info,net.minecraft.gametest.framework.GameTestRunner runner) { close(info); }
            @Override public void testAddedForRerun(net.minecraft.gametest.framework.GameTestInfo oldTest,net.minecraft.gametest.framework.GameTestInfo newTest,net.minecraft.gametest.framework.GameTestRunner runner) { close(oldTest); }
        });
        helper.runAtTickTime(2,()->{
            try {
                var active=RegisteredPlayerReadyDriver.readyAndQuery(fixture);
                BlockPos clear=helper.absolutePos(new BlockPos(2,22,2));
                active.body().teleportTo(clear.getX()+.5,clear.getY(),clear.getZ()+.5);
                active.body().setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
                BlockPos main=active.body().blockPosition().offset(2,0,2);
                var footprint=SmallBotanyFixtureLayout.plannedFootprint(main);
                var bounds=new net.minecraft.world.phys.AABB(footprint.stream().mapToDouble(BlockPos::getX).min().orElseThrow(),
                        footprint.stream().mapToDouble(BlockPos::getY).min().orElseThrow(),footprint.stream().mapToDouble(BlockPos::getZ).min().orElseThrow(),
                        footprint.stream().mapToDouble(BlockPos::getX).max().orElseThrow()+1,footprint.stream().mapToDouble(BlockPos::getY).max().orElseThrow()+1,
                        footprint.stream().mapToDouble(BlockPos::getZ).max().orElseThrow()+1);
                var tickets=FixtureChunkTickets.acquire(helper,bounds);
                FixtureChunkTickets.awaitEntityTicking(helper,tickets,40,()->{},footprint.toArray(BlockPos[]::new));
                var source=fixture.player().createCommandSourceStack().withPermission(2);
                var dispatcher=level.getServer().getCommands().getDispatcher();
                helper.assertTrue(execute(dispatcher,"ms14 botany_fixture",source)==1,"public fixture construction command accepted");
                PowerFixtureReadiness.await(helper,level,40,()->{
                    for(int i=0;i<8;i++) {
                        var found=level.getBlockEntity(SmallBotanyFixtureLayout.toleranceTray(SmallBotanyFixtureLayout.careOrigin(main),i));
                        if(!(found instanceof HydroponicsTrayBlockEntity t)||t.plantState()==null
                                ||t.plantState().seed()==null||t.plantState().genetics()==null) return false;
                    }
                    return true;
                },()->"command-built AF-AN holders did not become ready",()->{
                    try {
                        helper.assertTrue(execute(dispatcher,"ms14 botany_fixture start 33 40",source)==1,
                                "explicitly activate tolerance stations 33..40 before ordinary first-cycle assertions");
                        var wing=SmallBotanyFixtureLayout.careOrigin(main);
                        HydroponicsTrayBlockEntity[] trays=new HydroponicsTrayBlockEntity[8];
                        long[] cycles=new long[8];
                        GeneticValue[] initialGenetics=new GeneticValue[8];
                        int bound=1;
                        for(int i=0;i<8;i++) {
                            trays[i]=(HydroponicsTrayBlockEntity)level.getBlockEntity(SmallBotanyFixtureLayout.toleranceTray(wing,i));
                            helper.assertTrue(trays[i]!=null,"command created tolerance tray "+i);
                            var tray=trays[i]; var plant=tray.plantState();
                            var genetics=plant.genetics();
                            helper.assertTrue(genetics!=null&&genetics.species().equals(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("moonstation14","wheat"))
                                    &&plant.age()==2&&plant.health()==100&&plant.skipAging()==0&&plant.water()==100&&plant.nutrition()==100
                                    &&plant.soilToxins()==(i<2?5:0)&&plant.pests()==(i>=2&&i<4?6:0)&&plant.weeds()==(i>=4&&i<6?6:0)
                                    &&plant.mutationLevel()==0&&plant.mutationModifier()==1f,
                                    "actual command holder has resolved wheat genetics and matched healthy exposed baseline at station "+i
                                            +": "+baselineFields(plant));
                             helper.assertTrue((tray.warningBits()&2)==0&&tray.appearanceStatus()==PlantInstance.Status.GROWING,
                                     "healthy baseline above half endurance has no health warning while alive at station "+i);
                            var bio=genetics.profile().biology();
                            float tolerance=switch(i) { case 0,1 -> bio.toxinTolerance(); case 2,3 -> bio.pestTolerance(); case 4,5 -> bio.weedTolerance(); default -> bio.heatTolerance(); };
                            helper.assertTrue(tolerance==new float[]{4,10,5,15,5,15,10,25}[i],"command-resolved tolerance gene at station "+i+" = "+tolerance);
                            if(i>=6) helper.assertTrue(bio.idealHeat()==275,"shared ideal heat at station "+i);
                            helper.assertTrue(tray.resolvedTrayProfile()!=null&&tray.resolvedTrayProfile().cycleDelayTicks()>0,"resolved tray profile exists");
                            bound=Math.max(bound,tray.resolvedTrayProfile().cycleDelayTicks()+3);
                            cycles[i]=plant.lastCycle();
                            initialGenetics[i]=genetics;
                            tray.setFixtureCycleRandom(new java.util.random.RandomGenerator(){
                                @Override public float nextFloat(){return .99f;}
                                @Override public int nextInt(int origin,int limit){return Math.min(1,limit-1);}
                                @Override public long nextLong(){return 0L;}
                            });
                            var sample=SmallBotanyFixtureLayout.toleranceTray(wing,i).above();
                            var air=com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereService.INSTANCE.sample(level,sample);
                            var diagnostic=com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereService.INSTANCE.diagnosticCell(level,sample);
                            helper.assertTrue("finite".equals(diagnostic.status())&&air.isPresent()&&air.get().moles(com.juicyslew.moonstation14.ms14.atmos.core.GasType.OXYGEN)>0
                                    &&air.get().moles(com.juicyslew.moonstation14.ms14.atmos.core.GasType.NITROGEN)>0
                                    &&Math.abs(air.get().temperatureKelvin()-293)<10,"initialized finite oxygen/nitrogen air at tolerance tray "+i);
                        }
                        for(int pair=0;pair<4;pair++) {
                            var low=initialGenetics[pair*2].profile().biology();
                            var high=initialGenetics[pair*2+1].profile().biology();
                            helper.assertTrue(sameBiologyExcept(low,high,pair),"matched tolerance pair differs only in its intended gene: pair "+pair);
                        }
                        final int timeout=bound;
                        PowerFixtureReadiness.await(helper,level,timeout,
                                ()->{for(int i=0;i<8;i++) if(trays[i].plantState().lastCycle()<=cycles[i]) return false; return true;},
                                ()->"first ordinary world-poll timeout (bound="+timeout+"): "+java.util.Arrays.toString(java.util.Arrays.stream(trays).map(t->t==null?"missing":t.plantState().toString()).toArray()),
                                ()->{
                                    try {
                                        for(int i=0;i<8;i++) {
                                            var plant=trays[i].plantState();
                                             helper.assertTrue(!plant.dead()&&plant.health()>=1&&plant.skipAging()==0,
                                                      "first ordinary cycle leaves tolerance specimen alive station "+i+": "+plant);
                             helper.assertTrue(i%2==0?plant.health()<100:plant.health()==100,
                                     "ordinary first-cycle exposure damages susceptible baseline but not matched tolerant control station "+i+": "+plant);
                            helper.assertTrue(plant.genetics().equals(initialGenetics[i])&&plant.age()==2,
                                                     "unrelated source genetics and holder state remain unchanged station "+i);
                                            helper.assertTrue(trays[i].appearanceStatus()==PlantInstance.Status.GROWING,
                                                     "normal projection remains growing at station "+i);
                                        }
                        tickets.close(); closeFixture(fixture,null); helper.succeed();
                                    } catch(RuntimeException|Error failure) { closeFixture(fixture,failure); throw failure; }
                                });
                    } catch(RuntimeException|Error failure) { closeFixture(fixture,failure); throw failure; }
                });
            } catch(RuntimeException|Error failure) { closeFixture(fixture,failure); throw failure; }
        });
    }

    private static boolean sameBiologyExcept(SeedProfile.Biology a,SeedProfile.Biology b,int pair) {
        return a.endurance()==b.endurance()&&a.nutrientConsumption()==b.nutrientConsumption()
                &&a.waterConsumption()==b.waterConsumption()&&a.idealHeat()==b.idealHeat()
                // Pair 3 intentionally differs in heat tolerance (10 vs 25).
                &&(pair==3||a.heatTolerance()==b.heatTolerance())&&a.lowPressure()==b.lowPressure()
                &&a.highPressure()==b.highPressure()&&a.weedHighLevel()==b.weedHighLevel()
                &&(pair==0?a.pestTolerance()==b.pestTolerance()&&a.weedTolerance()==b.weedTolerance()&&a.idealHeat()==b.idealHeat()&&a.heatTolerance()==b.heatTolerance()
                    :pair==1?a.toxinTolerance()==b.toxinTolerance()&&a.weedTolerance()==b.weedTolerance()&&a.idealHeat()==b.idealHeat()&&a.heatTolerance()==b.heatTolerance()
                    :pair==2?a.toxinTolerance()==b.toxinTolerance()&&a.pestTolerance()==b.pestTolerance()&&a.idealHeat()==b.idealHeat()&&a.heatTolerance()==b.heatTolerance()
                    :a.toxinTolerance()==b.toxinTolerance()&&a.pestTolerance()==b.pestTolerance()&&a.weedTolerance()==b.weedTolerance()&&a.idealHeat()==b.idealHeat());
    }

    private static String baselineFields(PlantInstance plant) {
        return "species="+(plant.genetics()==null?"missing":plant.genetics().species())
                +",age="+plant.age()+",health="+plant.health()+",skipAging="+plant.skipAging()
                +",water="+plant.water()+",nutrition="+plant.nutrition()+",soilToxins="+plant.soilToxins()
                +",pests="+plant.pests()+",weeds="+plant.weeds()+",mutationLevel="+plant.mutationLevel()
                +",mutationModifier="+plant.mutationModifier();
    }

    private static void closeFixture(RegisteredServerPlayerFixture fixture,Throwable original) {
        try { fixture.close(); }
        catch(Throwable cleanup) {
            if(original!=null) original.addSuppressed(cleanup);
            else if(cleanup instanceof RuntimeException runtime) throw runtime;
            else if(cleanup instanceof Error error) throw error;
            else throw new RuntimeException(cleanup);
        }
    }

    private static int execute(com.mojang.brigadier.CommandDispatcher<net.minecraft.commands.CommandSourceStack> dispatcher,String command,net.minecraft.commands.CommandSourceStack source) {
        try { return dispatcher.execute(command,source); }
        catch(com.mojang.brigadier.exceptions.CommandSyntaxException e) { throw new net.minecraft.gametest.framework.GameTestAssertException("public command syntax failed: "+e.getMessage()); }
    }
}
