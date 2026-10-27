package com.juicyslew.moonstation14.ms14.botany;

import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Verifies fixture pausing against the ordinary tray block-entity ticker. */
@GameTestTags({"botany", "botany-fixture"})
@PrefixGameTestTemplate(false)
public final class BotanyFixturePauseGameTests {
    private BotanyFixturePauseGameTests() { }

    @GameTest(template="botany_fixture_space", templateNamespace="moonstation14", timeoutTicks=900)
    public static void pausedTrayDoesNotTickOrCatchUpOnResume(GameTestHelper helper) {
        ServerLevel level=(ServerLevel)helper.getLevel();
        BotanyTrayGameTestEnvironment.prepare(helper,tray->{
            try {
                var plant=tray.plantState();
                var seedId=ResourceLocation.fromNamespaceAndPath("moonstation14","wheat");
                var seed=ModSeedProfiles.serverLookup(seedId);
                helper.assertTrue(seed!=null,"resolved ordinary wheat profile exists");
                tray.commitPlant(seedId,seed,level.getGameTime());
                int cycle=tray.resolvedTrayProfile().cycleDelayTicks();
                int poll=tray.resolvedTrayProfile().pollDelayTicks();
                helper.assertTrue(cycle>0&&poll>0,"resolved ordinary tray timing is positive");
                tray.setFixturePaused(true);
                float age=plant.age();
                long last=plant.lastCycle(),next=plant.nextUpdate();
                float health=plant.health(),water=plant.water(),nutrition=plant.nutrition(),toxins=plant.soilToxins(),pests=plant.pests(),weeds=plant.weeds(),mutation=plant.mutationLevel();
                var genetics=plant.genetics();
                helper.runAfterDelay(cycle+40,()->{
                    try {
                        helper.assertTrue(tray.fixturePaused(),"tray remains paused");
                        assertState(helper,plant,age,health,water,nutrition,toxins,pests,weeds,mutation,genetics,true,"after more than one cycle paused");
                        helper.assertTrue(plant.lastCycle()==last&&plant.nextUpdate()==next,"both schedule timestamps remain unchanged while paused");
                        tray.setFixturePaused(false);
                        long resumed=level.getGameTime();
                        helper.runAfterDelay(20,()->{
                            try {
                                assertState(helper,plant,age,health,water,nutrition,toxins,pests,weeds,mutation,genetics,false,"shortly after resume");
                                helper.runAfterDelay(cycle+poll+20,()->{
                                    helper.assertTrue(plant.lastCycle()>last+(resumed-(last)),"ordinary cycle eventually advances after resume");
                                    helper.succeed();
                                });
                            } catch(Throwable failure) { helper.fail("resume timing assertion failed: "+failure); }
                        });
                    } catch(Throwable failure) { helper.fail("paused timing assertion failed: "+failure); }
                });
            } catch(Throwable failure) { helper.fail("fixture pause setup failed: "+failure); }
        });
    }

    private static void assertState(GameTestHelper helper,PlantInstance p,float age,float health,float water,float nutrition,float toxins,float pests,
                                   float weeds,float mutation,GeneticValue genetics,boolean paused,String when) {
        helper.assertTrue(p.age()==age&&p.health()==health&&p.water()==water&&p.nutrition()==nutrition&&p.soilToxins()==toxins
                        &&p.pests()==pests&&p.weeds()==weeds&&p.mutationLevel()==mutation&&p.genetics().equals(genetics),
                when+" advanced plant state unexpectedly (paused="+paused+"): "+p);
    }
}
