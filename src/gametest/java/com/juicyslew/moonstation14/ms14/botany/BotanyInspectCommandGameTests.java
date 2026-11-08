package com.juicyslew.moonstation14.ms14.botany;

import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server.RegisteredPlayerReadyDriver;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import com.juicyslew.moonstation14.ms14.hands.live.BodyItemTargetProbe;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@PrefixGameTestTemplate(false)
@GameTestTags({"botany-inspector"})
public final class BotanyInspectCommandGameTests {
    private BotanyInspectCommandGameTests() { }

    @GameTest(template="empty", templateNamespace="moonstation14", timeoutTicks=3400)
    @GameTestTags({"botany-inspector"})
    public static void publicCommandCapturesAndComparesAuthorizedBodyTarget(GameTestHelper helper) {
        com.juicyslew.moonstation14.ms14.hands.network.BotanyHarvestAuthenticatedGameTests.runAuthenticatedPlanting(
                        helper,new net.minecraft.world.item.ItemStack(com.juicyslew.moonstation14.item.ModItems.WHEAT_SEEDS.get()),
                        "inspector-native",(h,harvestFixture,epoch,hand,tray,target,world,done)->{
                            var commandSource=BotanyFixtureCommandGameTests.capture(harvestFixture.player().createCommandSourceStack().withPermission(2));
                             boolean baselineOwned=false;
                            try {
                                var body=com.juicyslew.moonstation14.ms14.hands.HandActorAuthority.resolve(harvestFixture.player()).orElseThrow().body();
                                body.setPos(target.getX()+.5,target.getY()+1,target.getZ()-1.5);
                                 RegisteredPlayerReadyDriver.aimAt(body,world,target);
                                BotanyHoeGameTestSupport.setWeeds(tray,3); // trusted fixture arrangement before sampling
                                 var plant=tray.plantState(); var reference=plant.seedReference(); var genetics=plant.genetics();
                                 h.assertTrue(plant.status()==PlantInstance.Status.GROWING && reference!=null && genetics!=null,
                                         "ordinary authenticated planting provides initialized growing wheat for inspection");
                                long revision=plant.revision(); var profile=plant.effectiveProfile(); var state=plant.status(); boolean unique=reference.unique();
                                var actualDispatcher=world.getServer().getCommands().getDispatcher();
                                int captured=BotanyFixtureCommandGameTests.execute(actualDispatcher,"ms14 botany_inspect capture",commandSource.stack());
                                baselineOwned=true;
                                h.assertTrue(captured==1&&commandSource.feedback().stream().anyMatch(s->s.contains("CAPTURED BASELINE")),"public capture reports baseline");
                                h.assertTrue(plant.revision()==revision&&plant.seedReference()==reference&&plant.genetics()==genetics
                                        &&plant.effectiveProfile()==profile&&plant.status()==state&&reference.unique()==unique,"capture is read-only for identity/unique/revision/state");
                                h.assertTrue(commandSource.feedback().stream().anyMatch(s->s.contains("biology.endurance"))
                                        &&commandSource.feedback().stream().anyMatch(s->s.contains("lifecycle.lifespan")),"baseline names resolved biology/lifecycle fields");
                                var otherPos=target.east(2);
                                world.setBlock(otherPos,com.juicyslew.moonstation14.block.ModBlocks.HYDROPONICS_TRAY.get().defaultBlockState(),3);
                                  final boolean ownedBaseline = baselineOwned;
                                  h.runAfterDelay(2,()->continueWithSecondTray(h,harvestFixture,epoch,hand,tray,target,world,done,
                                          commandSource,body,plant,reference,genetics,revision,profile,state,unique,actualDispatcher,otherPos,ownedBaseline));
                                 return;
                              } catch(Throwable failure) {
                                  h.testInfo.fail(failure);
                                  if(baselineOwned) try { BotanyFixtureCommandGameTests.execute(world.getServer().getCommands().getDispatcher(),"ms14 botany_inspect clear",commandSource.stack()); }
                                  catch(Throwable ignored) { }
                                  harvestFixture.close(); done.set(true);
                              }
                        });
    }

    private static void continueWithSecondTray(GameTestHelper h,
            com.juicyslew.moonstation14.gametest.support.RegisteredServerPlayerFixture harvestFixture,
            long epoch,String hand,HydroponicsTrayBlockEntity tray,net.minecraft.core.BlockPos target,
            net.minecraft.server.level.ServerLevel world,java.util.concurrent.atomic.AtomicBoolean done,
            BotanyFixtureCommandGameTests.CapturedSource commandSource,net.minecraft.world.entity.LivingEntity body,
            PlantInstance plant,PlantSeedReference reference,GeneticValue genetics,long revision,SeedProfile profile,
            PlantInstance.Status state,boolean unique,com.mojang.brigadier.CommandDispatcher<net.minecraft.commands.CommandSourceStack> actualDispatcher,
            net.minecraft.core.BlockPos otherPos,boolean baselineOwned) {
                             boolean passed=false;
                             try {
                                 var otherTray=(HydroponicsTrayBlockEntity)world.getBlockEntity(otherPos);
                                  h.assertTrue(otherTray!=null,"second fixture tray initializes through registered tray block entity");
                                  h.assertTrue(otherTray.plantState()!=null,"second fixture tray plant state initializes after normal startup delay");
                                 var wheat=ModSeedProfiles.serverLookup(ModSeedProfiles.seedId("wheat"));
                                 otherTray.plantState().plant(ModSeedProfiles.seedId("wheat"),wheat,world.getGameTime());
                                 otherTray.commitMutation();
                                 h.assertTrue(otherTray.plantState().seed()!=null&&otherTray.plantState().genetics()!=null,"trusted fixture arrangement gives second tray resolved wheat state");
                                body.setPos(otherPos.getX()+.5,otherPos.getY()+1,otherPos.getZ()-1.5);
                                RegisteredPlayerReadyDriver.aimAt(body,world,otherPos);
                                var otherProbe=com.juicyslew.moonstation14.ms14.hands.network.BodyBlockUseTargetProbe.probeSelected(world,body);
                                h.assertTrue(otherProbe!=null&&otherProbe.pos().equals(otherPos),"shared inspector target probe selects the second tray before mismatch dispatch: "+otherProbe);
                                commandSource.feedback().clear();
                                h.assertTrue(BotanyFixtureCommandGameTests.execute(actualDispatcher,"ms14 botany_inspect compare",commandSource.stack())==0
                                        &&commandSource.feedback().stream().anyMatch(s->s.contains("Baseline target differs")),"different valid nonempty tray is rejected without replacing baseline");
                                body.setPos(target.getX()+.5,target.getY()+1,target.getZ()-1.5);
                                 RegisteredPlayerReadyDriver.aimAt(body,world,target);
                                var firstProbe=com.juicyslew.moonstation14.ms14.hands.network.BodyBlockUseTargetProbe.probeSelected(world,body);
                                h.assertTrue(firstProbe!=null&&firstProbe.pos().equals(target),"shared inspector target probe reacquires original tray after mismatch: "+firstProbe);
                                commandSource.feedback().clear();
                                int unchanged=BotanyFixtureCommandGameTests.execute(actualDispatcher,"ms14 botany_inspect compare",commandSource.stack());
                                h.assertTrue(unchanged==1&&commandSource.feedback().stream().anyMatch(s->s.contains("No named fields changed.")),"same-target unchanged comparison");
                                var hands=body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());
                                String handId=hands.activeHand();
                                var looseHoe=com.juicyslew.moonstation14.entities.LooseItemEntity.create(world,body.position().add(0,.1,.8),
                                        new net.minecraft.world.item.ItemStack(com.juicyslew.moonstation14.item.ModItems.BOTANY_HOE.get()));
                                looseHoe.setNoGravity(true); h.assertTrue(world.addFreshEntity(looseHoe),"ordinary-care hoe published");
                                 body.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, looseHoe.getBoundingBox().getCenter());
                                 var hoeTarget=BodyItemTargetProbe.probeSelected(world,body,looseHoe.getUUID(),BodyItemTargetProbe.MAX_REACH);
                                 h.assertTrue(hoeTarget!=null&&hoeTarget.itemId().equals(looseHoe.getUUID()),
                                         "shared inspector target probe selects the hoe before native pickup: "+hoeTarget);
                                hands=body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());
                                h.assertTrue(com.juicyslew.moonstation14.ms14.hands.live.BodyHandItemTransfer.pickupSelected(
                                        harvestFixture.player(),looseHoe.getUUID(),handId,hands.revision(),ignored->{})
                                        ==com.juicyslew.moonstation14.ms14.hands.live.BodyHandItemTransfer.Result.SUCCESS,"trusted real-tool pickup obtains hoe");
                                var handSnapshot=com.juicyslew.moonstation14.ms14.hands.network.BodyHandStateService.query(harvestFixture.player(),
                                        new com.juicyslew.moonstation14.ms14.hands.network.BodyHandStateQuery(epoch,4));
                                var held=handSnapshot.hands().stream().filter(value->value.id().equals(handId)).findFirst().orElseThrow();
                                var targetHit=RegisteredPlayerReadyDriver.aimAt(body,world,target);
                                var careRequest=new com.juicyslew.moonstation14.ms14.hands.network.BodyBlockUseRequest(epoch,3,handSnapshot.revision(),
                                        handId,held.token(),target,targetHit.getDirection(),false,
                                        com.juicyslew.moonstation14.ms14.hands.network.BodyBlockUseRequest.Intent.USE);
                                h.assertTrue(com.juicyslew.moonstation14.ms14.hands.network.AuthenticatedBlockUseDriver.use(harvestFixture.player(),careRequest)
                                        ==com.juicyslew.moonstation14.ms14.hands.network.BodyBlockUseResult.Outcome.PLACED
                                        &&plant.weeds()==0,"ordinary authenticated hoe USE clears arranged tray weeds");
                                commandSource.feedback().clear();
                                int compared=BotanyFixtureCommandGameTests.execute(actualDispatcher,"ms14 botany_inspect compare",commandSource.stack());
                                h.assertTrue(compared==1&&commandSource.feedback().stream().anyMatch(s->s.contains("weeds: 3.0 → 0.0"))
                                        &&commandSource.feedback().stream().anyMatch(s->s.contains("not mutation provenance")),"compare reports named ordinary-care change, not acquisition/provenance");
                                commandSource.feedback().clear();
                                try { actualDispatcher.execute("ms14 botany_inspect compare",harvestFixture.player().createCommandSourceStack().withPermission(0));
                                    throw new GameTestAssertException("permission-zero player dispatched inspector command");
                                } catch(com.mojang.brigadier.exceptions.CommandSyntaxException expected) { }
                                commandSource.feedback().clear();
                                h.assertTrue(BotanyFixtureCommandGameTests.execute(actualDispatcher,"ms14 botany_inspect clear",commandSource.stack())==1,"public clear succeeds");
                                commandSource.feedback().clear();
                                h.assertTrue(BotanyFixtureCommandGameTests.execute(actualDispatcher,"ms14 botany_inspect compare",commandSource.stack())==0
                                        &&commandSource.feedback().stream().anyMatch(s->s.contains("No baseline")),"compare after clear denies missing baseline");
                                passed=true;
                            } catch(Throwable failure) {
                                // GameTestHelper.fail throws; the wrapper catches callback failures and sees done=true
                                // after cleanup, so report the original assertion directly through testInfo.
                                h.testInfo.fail(failure);
                            } finally {
                                if(baselineOwned) try { BotanyFixtureCommandGameTests.execute(world.getServer().getCommands().getDispatcher(),"ms14 botany_inspect clear",commandSource.stack()); }
                                catch(Throwable ignored) { }
                                harvestFixture.close(); done.set(true);
                            }
                             if(passed) h.succeed();
    }
}
