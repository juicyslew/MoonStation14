package com.juicyslew.moonstation14.ms14.botany;

import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.gametest.support.RegisteredServerPlayerFixture;
import com.juicyslew.moonstation14.gametest.support.FixtureChunkTickets;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server.RegisteredPlayerReadyDriver;
import com.juicyslew.moonstation14.ms14.hands.live.BodyHandItemTransfer;
import com.juicyslew.moonstation14.ms14.hands.network.*;
import com.juicyslew.moonstation14.item.ModItems;
import com.juicyslew.moonstation14.entities.LooseItemEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Exercises the public fixture's legacy weed bay and its supplied hoe using ordinary authenticated input. */
@GameTestTags({"botany", "botany-fixture"})
@PrefixGameTestTemplate(false)
public final class BotanyFixtureWeedBayGameTests {
    private BotanyFixtureWeedBayGameTests() { }

    @GameTest(template="botany_fixture_space", templateNamespace="moonstation14", timeoutTicks=1000)
    public static void commandBuiltPausedWeedBayCanBeClearedWithSuppliedHoe(GameTestHelper h) {
        ServerLevel level=(ServerLevel)h.getLevel();
        var fixture=RegisteredServerPlayerFixture.register(level,"botany-fixture-weed-bay");
        var tickets=new java.util.concurrent.atomic.AtomicReference<FixtureChunkTickets.Lease>();
        h.testInfo.addListener(new net.minecraft.gametest.framework.GameTestListener() {
            private void close(net.minecraft.gametest.framework.GameTestInfo info) {
                try { var lease=tickets.getAndSet(null); if(lease!=null) lease.close(); fixture.close(); }
                catch(Throwable t) { if(info.getError()!=null) info.getError().addSuppressed(t); else info.fail(t); }
            }
            public void testStructureLoaded(net.minecraft.gametest.framework.GameTestInfo i) { }
            public void testPassed(net.minecraft.gametest.framework.GameTestInfo i,net.minecraft.gametest.framework.GameTestRunner r) { close(i); }
            public void testFailed(net.minecraft.gametest.framework.GameTestInfo i,net.minecraft.gametest.framework.GameTestRunner r) { close(i); }
            public void testAddedForRerun(net.minecraft.gametest.framework.GameTestInfo a,net.minecraft.gametest.framework.GameTestInfo b,net.minecraft.gametest.framework.GameTestRunner r) { close(a); }
        });
        h.runAtTickTime(2,()->{
            try {
                var active=RegisteredPlayerReadyDriver.readyAndQuery(fixture);
                BlockPos clear=h.absolutePos(new BlockPos(2,22,2));
                BlockPos origin=clear.offset(2,0,2);
                active.body().teleportTo(clear.getX()+.5,clear.getY(),clear.getZ()+.5);
                active.body().setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
                var footprint=SmallBotanyFixtureLayout.plannedFootprint(origin);
                var bounds=new AABB(footprint.stream().mapToDouble(BlockPos::getX).min().orElseThrow(),footprint.stream().mapToDouble(BlockPos::getY).min().orElseThrow(),footprint.stream().mapToDouble(BlockPos::getZ).min().orElseThrow(),footprint.stream().mapToDouble(BlockPos::getX).max().orElseThrow()+1,footprint.stream().mapToDouble(BlockPos::getY).max().orElseThrow()+1,footprint.stream().mapToDouble(BlockPos::getZ).max().orElseThrow()+1);
                var lease=FixtureChunkTickets.acquire(h,bounds); tickets.set(lease);
                FixtureChunkTickets.awaitEntityTicking(h,lease,40,()->true,"planned footprint entity ticking",()->{
                  try {
                var captured=BotanyFixtureCommandGameTests.capture(fixture.player().createCommandSourceStack().withPermission(2));
                var source=captured.stack();
                var dispatcher=level.getServer().getCommands().getDispatcher();
                h.assertTrue(BotanyFixtureCommandGameTests.execute(dispatcher,"ms14 botany_fixture",source)==1,"public setup accepted");
                com.juicyslew.moonstation14.gametest.support.PowerFixtureReadiness.await(h,level,40,
                        ()->captured.feedback().stream().anyMatch(message->message.contains("Botany fixture ready")),
                        ()->"public fixture command did not report ready",()->{
                    try {
                        var weedPos=SmallBotanyFixtureLayout.stationTray(origin,"F");
                        h.assertTrue(level.getBlockEntity(weedPos) instanceof HydroponicsTrayBlockEntity,"completed public setup built legacy station F");
                        var tray=(HydroponicsTrayBlockEntity)level.getBlockEntity(weedPos);
                        var plant=tray.plantState();
                        h.assertTrue(plant!=null&&plant.genetics()!=null&&plant.seedReference()!=null&&tray.fixturePaused(),"completed public setup initialized paused legacy station F");
                        var genes=plant.genetics(); var seed=plant.seedReference();
                        float endurance=plant.effectiveProfile().biology().endurance();
                        h.assertTrue(tray.fixturePaused()&&plant.seed()!=null&&plant.seed().getPath().equals("wheat")&&!plant.dead()&&plant.health()==endurance&&plant.water()==100&&plant.nutrition()==100&&plant.weeds()==6,"actual command arranged healthy, seeded paused weed bay: "+plant);
                        float age=plant.age(),health=plant.health(),water=plant.water(),nutrition=plant.nutrition(),weeds=plant.weeds();
                        h.runAfterDelay(tray.resolvedTrayProfile().cycleDelayTicks()+10,()->{
                            try {
                                h.assertTrue(tray.fixturePaused()&&plant.seedReference()==seed&&plant.genetics().equals(genes)&&plant.age()==age&&plant.health()==health&&plant.water()==water&&plant.nutrition()==nutrition&&plant.weeds()==weeds,"paused ordinary cycle leaves bay unchanged");
                                var planned=com.juicyslew.moonstation14.ms14.botany.fixture.BotanyFixtureSupplies.plan(origin,com.juicyslew.moonstation14.ms14.botany.fixture.BotanyFixtureSupplies.descriptors()).stream().filter(p->p.label().equals("hoe")).findFirst().orElseThrow();
                                var hoe=level.getEntitiesOfClass(LooseItemEntity.class,new AABB(planned.position()).inflate(.75,1.5,.75),e->e.getItem().is(ModItems.BOTANY_HOE.get())).stream().findFirst().orElseThrow();
                                 // Approach from the west: the physical station sign occupies the south approach.
                                 var body=active.body(); body.teleportTo(weedPos.getX()-1.2,weedPos.getY(),weedPos.getZ()+.5);
                                 body.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
                                 var bodyBounds=body.getBoundingBox();
                                 for(int x=net.minecraft.util.Mth.floor(bodyBounds.minX);x<=net.minecraft.util.Mth.floor(bodyBounds.maxX);x++)
                                     for(int y=net.minecraft.util.Mth.floor(bodyBounds.minY);y<=net.minecraft.util.Mth.floor(bodyBounds.maxY);y++)
                                         for(int z=net.minecraft.util.Mth.floor(bodyBounds.minZ);z<=net.minecraft.util.Mth.floor(bodyBounds.maxZ);z++)
                                             h.assertTrue(level.hasChunkAt(new BlockPos(x,y,z)),"west approach body collision volume is loaded");
                                 h.assertTrue(level.noCollision(body),"west approach full body collision-free");
                                var hands=body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());
                                var hand=hands.activeHand();
                                aim(body,hoe);
                                h.assertTrue(BodyHandItemTransfer.pickupSelected(fixture.player(),hoe.getUUID(),hand,hands.revision(),ignored->{})==BodyHandItemTransfer.Result.SUCCESS,"pick up actual planned fixture hoe");
                                var held=BodyHandStateService.query(fixture.player(),new BodyHandStateQuery(active.epoch(),3));
                                var heldHand=held.hands().stream().filter(x->x.id().equals(hand)).findFirst().orElseThrow();
                                var hit=RegisteredPlayerReadyDriver.aimAt(body,level,weedPos);
                                 h.assertTrue(hit.getBlockPos().equals(weedPos),"west approach ordinary first-hit ray probe targets F without the south sign occluding it");
                                var result=AuthenticatedBlockUseDriver.use(fixture.player(),new BodyBlockUseRequest(active.epoch(),2,held.revision(),hand,heldHand.token(),weedPos,hit.getDirection(),false,BodyBlockUseRequest.Intent.USE));
                                h.assertTrue(result==BodyBlockUseResult.Outcome.PLACED&&plant.weeds()==0&&!plant.dead()&&plant.seedReference()==seed&&plant.genetics().equals(genes)&&tray.fixturePaused(),"authenticated hoe clears weeds without removing/deadening plant");
                                h.succeed();
                            } catch(Throwable t) { h.fail("weed bay interaction failed: "+t); }
                        });
                    } catch(Throwable t) { h.fail("weed bay assertion failed: "+t); }
                });
                  } catch(Throwable t) { h.fail("fixture command setup failed: "+t); }
                },footprint.toArray(BlockPos[]::new));
            } catch(Throwable t) { h.fail("fixture setup failed: "+t); }
        });
    }
    private static void aim(net.minecraft.world.entity.LivingEntity body,LooseItemEntity item) { var d=item.position().add(0,.2,0).subtract(body.getEyePosition()).normalize(); body.setYRot((float)Math.toDegrees(Math.atan2(-d.x,d.z))); body.setXRot((float)-Math.toDegrees(Math.asin(d.y))); }
}
