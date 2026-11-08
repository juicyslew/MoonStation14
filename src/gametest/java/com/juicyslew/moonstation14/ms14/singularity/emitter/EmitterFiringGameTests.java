package com.juicyslew.moonstation14.ms14.singularity.emitter;

import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.block.block_entity.EmitterBlockEntity;
import com.juicyslew.moonstation14.block.block_entity.FieldGeneratorBlockEntity;
import com.juicyslew.moonstation14.block.custom.PowerDeviceBlock;
import com.juicyslew.moonstation14.entities.EmitterBoltEntity;
import com.juicyslew.moonstation14.entities.ModEntities;
import com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.gametest.support.PowerFixtureReadiness;
import com.juicyslew.moonstation14.ms14.power.cable.CableStorage;
import com.juicyslew.moonstation14.ms14.power.cable.CableTier;
import com.juicyslew.moonstation14.ms14.power.graph.PowerGraphService;
import com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime;
import com.juicyslew.moonstation14.ms14.power.topology.CableFaceNode;
import com.juicyslew.moonstation14.ms14.disposal.transport.DisposalsEjection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.common.NeoForge;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/** Real powered emitter cadence and loaded-world registered bolt collision. */
@GameTestTags({"power", "emitter-firing-adapter"})
@PrefixGameTestTemplate(false)
public final class EmitterFiringGameTests {
    private EmitterFiringGameTests() { }

    private static void check(boolean value, String message) {
        if (!value) throw new GameTestAssertException(message);
    }
    private static BlockPos absolute(GameTestHelper h, int x, int y, int z) { return h.absolutePos(new BlockPos(x,y,z)); }
    private static void solid(ServerLevel level, BlockPos pos) { level.setBlock(pos, Blocks.STONE.defaultBlockState(), 3); }
    private static void cable(ServerLevel level, BlockPos pos, Direction face, CableTier tier) {
        solid(level,pos);
        check(CableStorage.place(level,pos,face,tier),"place " + tier + " cable " + pos + " face=" + face);
    }

    /** Source and substation are registered production devices; no runtime offer or emitter allocation is injected. */
    @GameTestTags("reactor-models")
    @GameTest(templateNamespace="moonstation14", template="power_live_chain_clear", timeoutTicks=1000)
     public static void realMvPowerSchedulesFourShotsThenOutageCancelsWhileIntentPersists(GameTestHelper h) {
        ServerLevel level=(ServerLevel)h.getLevel();
        BlockPos source=absolute(h,3,1,2), sub=absolute(h,3,1,4);
         BlockPos emitterPos=absolute(h,3,4,7), genPos=absolute(h,8,4,6), southGenPos=absolute(h,3,4,10);
        solid(level,absolute(h,4,1,2)); solid(level,absolute(h,4,1,3)); solid(level,absolute(h,4,1,4));
        level.setBlock(source,ModBlocks.HV_SOURCE.get().defaultBlockState().setValue(PowerDeviceBlock.FACING,Direction.EAST),3);
        level.setBlock(sub,ModBlocks.HV_MV_SUBSTATION.get().defaultBlockState().setValue(PowerDeviceBlock.FACING,Direction.EAST),3);
        // Production MV output rises on its west side, then follows an east-facing wall cable up to the emitter.
        cable(level,absolute(h,4,1,2),Direction.WEST,CableTier.HV);
        cable(level,absolute(h,4,1,3),Direction.WEST,CableTier.HV);
        cable(level,absolute(h,4,1,4),Direction.WEST,CableTier.HV);
        for(int y=1;y<=4;y++) cable(level,absolute(h,2,y,4),Direction.EAST,CableTier.MV);
        for(int z=5;z<=7;z++) cable(level,absolute(h,2,4,z),Direction.EAST,CableTier.MV);
        cable(level,absolute(h,3,4,6),Direction.SOUTH,CableTier.MV);
         level.setBlock(emitterPos,ModBlocks.EMITTER.get().defaultBlockState()
                 .setValue(com.juicyslew.moonstation14.block.custom.EmitterBlock.FACING,Direction.SOUTH),3);
         level.setBlock(genPos,ModBlocks.FIELD_GENERATOR.get().defaultBlockState(),3);
         level.setBlock(southGenPos,ModBlocks.FIELD_GENERATOR.get().defaultBlockState(),3);
         var southGenerator=(FieldGeneratorBlockEntity)level.getBlockEntity(southGenPos);
         EmitterBlockEntity emitter=(EmitterBlockEntity)level.getBlockEntity(emitterPos);
         check(emitter!=null,"registered emitter creates its live MV adapter");
          EmitterBlockEntity.serverTick(level,emitterPos,level.getBlockState(emitterPos),emitter);
          check(level.getBlockState(emitterPos).getValue(com.juicyslew.moonstation14.block.custom.EmitterBlock.VISUAL)
                          ==com.juicyslew.moonstation14.block.custom.EmitterBlock.Visual.OFF,
                  "live projection shows off before on intent");
          emitter.setRequestedOn(true);
          EmitterBlockEntity.serverTick(level,emitterPos,level.getBlockState(emitterPos),emitter);
           check(level.getBlockState(emitterPos).getValue(com.juicyslew.moonstation14.block.custom.EmitterBlock.VISUAL)
                           ==com.juicyslew.moonstation14.block.custom.EmitterBlock.Visual.UNDERPOWERED,
                   "live requested emitter without meter power projects underpowered");
           emitter.setRequestedOn(false);
           EmitterBlockEntity.serverTick(level,emitterPos,level.getBlockState(emitterPos),emitter);
           check(level.getBlockState(emitterPos).getValue(com.juicyslew.moonstation14.block.custom.EmitterBlock.VISUAL)
                           ==com.juicyslew.moonstation14.block.custom.EmitterBlock.Visual.OFF,
                   "turning intent off projects the off state before the off-demand readiness stage");
          check(emitter.powerRuntimePorts().get(0).face()==Direction.NORTH,
                  "non-default emitter facing resolves its rear MV power port");
        var sourcePort=new CableFaceNode(absolute(h,4,1,2),Direction.WEST,CableTier.HV);
        var subHvPort=new CableFaceNode(absolute(h,4,1,4),Direction.WEST,CableTier.HV);
        var subMvPort=new CableFaceNode(absolute(h,2,1,4),Direction.EAST,CableTier.MV);
        var emitterPort=new CableFaceNode(absolute(h,3,4,6),Direction.SOUTH,CableTier.MV);
        BoundedGameTestWait.await(h,400,()->PowerRuntime.lastSolveDiagnostic(level),
                ()->PowerFixtureReadiness.topologyReadyWithKnownPorts(level,sourcePort,subHvPort,subMvPort,emitterPort)
                        &&PowerRuntime.mvPowerMeter(level,emitter).known()
                         &&PowerRuntime.mvPowerMeter(level,emitter).deliveredWatts()==emitter.resolvedProfile().offWatts(),
                ()->{
                     List<Long> shots=new ArrayList<>();
                     List<EmitterBoltEntity> firedBolts=new ArrayList<>();
                    Consumer<EntityJoinLevelEvent> listener=event->{
                         if(event.getLevel()==level&&event.getEntity() instanceof EmitterBoltEntity bolt
                                 &&Objects.equals(bolt.emitterIdentity(),emitter.placementIdentity())) {
                             shots.add(level.getGameTime()); firedBolts.add(bolt);
                         }
                    };
                    NeoForge.EVENT_BUS.addListener(listener);
                    h.testInfo.addListener(new net.minecraft.gametest.framework.GameTestListener() {
                        @Override public void testStructureLoaded(net.minecraft.gametest.framework.GameTestInfo info) { }
                        @Override public void testPassed(net.minecraft.gametest.framework.GameTestInfo info, net.minecraft.gametest.framework.GameTestRunner runner) { NeoForge.EVENT_BUS.unregister(listener); }
                        @Override public void testFailed(net.minecraft.gametest.framework.GameTestInfo info, net.minecraft.gametest.framework.GameTestRunner runner) { NeoForge.EVENT_BUS.unregister(listener); }
                        @Override public void testAddedForRerun(net.minecraft.gametest.framework.GameTestInfo oldTest, net.minecraft.gametest.framework.GameTestInfo newTest, net.minecraft.gametest.framework.GameTestRunner runner) { NeoForge.EVENT_BUS.unregister(listener); }
                    });
                    emitter.setRequestedOn(true); // same authoritative state transition used by empty-hand interaction
                    int[] outageTicks={0}; int[] chargeAtOutage={-1}; int[] outageNoPowerTicks={0};
                    final Runnable[] tick={null};
                    tick[0]=()->{
                        var meter=PowerRuntime.mvPowerMeter(level,emitter);
                              if(shots.size()>=emitter.resolvedProfile().burstSize()&&outageTicks[0]==0) {
                             check(level.getBlockState(emitterPos).getValue(com.juicyslew.moonstation14.block.custom.EmitterBlock.VISUAL)
                                             ==com.juicyslew.moonstation14.block.custom.EmitterBlock.Visual.ON,
                                     "actual fresh MV meter projects emitter On");
                            long poweredAt=emitter.poweredEpochStartedAt();
                            check(poweredAt>=0,"actual continuous powered epoch starts firing schedule");
                             long first=shots.get(0);
                             check(first-poweredAt==emitter.resolvedProfile().firstDelayMaxTicks(),"first shot waits resolved maximum 10-second delay: first="+first+", poweredAt="+poweredAt+", resets="+emitter.poweredEpochResets());
                              for(int i=1;i<emitter.resolvedProfile().burstSize();i++)
                                  check(shots.get(i)-shots.get(i-1)==emitter.resolvedProfile().burstDelayTicks(),"resolved 40-tick burst cadence: "+shots);
                              check(!firedBolts.isEmpty()
                                              &&Math.abs(firedBolts.get(0).getDeltaMovement().z
                                              -DisposalsEjection.blocksPerSecondToBlocksPerTick(emitter.resolvedProfile().boltSpeed()))<0.001
                                              &&Math.abs(firedBolts.get(0).getDeltaMovement().x)<0.001
                                              &&Math.abs(firedBolts.get(0).getDeltaMovement().y)<0.001,
                                      "live SOUTH-facing emitter converts resolved blocks/second speed to blocks/tick");
                             check(southGenerator.snapshot().charge()>0,
                                     "live emitter bolt charges the actual generator south of the emitter");
                            check(((FieldGeneratorBlockEntity)level.getBlockEntity(genPos)).snapshot().charge()==0,
                                    "cadence observation lane is separate from the dedicated collision lane");
                            check(CableStorage.remove(level,absolute(h,2,4,5),Direction.EAST)==CableTier.MV,
                                    "remove the actual loaded MV path to interrupt power");
                            chargeAtOutage[0]=((FieldGeneratorBlockEntity)level.getBlockEntity(genPos)).snapshot().charge();
                            outageTicks[0]=1;
                        } else if(outageTicks[0]>0) {
                            outageTicks[0]++;
                            if(!meter.known()||meter.deliveredWatts()==0) outageNoPowerTicks[0]++;
                            if(outageTicks[0]>=220) {
                                check(emitter.requestedOn(),"loss of MV power does not clear on intent");
                                check(emitter.currentDemandWatts()==600,"requested-on emitter still requests profile demand");
                      check(shots.size()==emitter.resolvedProfile().burstSize(),"power outage cancels the post-burst wait without another free burst");
                                 check(level.getBlockState(emitterPos).getValue(com.juicyslew.moonstation14.block.custom.EmitterBlock.VISUAL)
                                                 ==com.juicyslew.moonstation14.block.custom.EmitterBlock.Visual.UNDERPOWERED,
                                         "real meter outage projects underpowered without clearing requested-on");
                                check(outageNoPowerTicks[0]>=200,"meter observes real loss of MV delivery after cable removal");
                                check(chargeAtOutage[0]==0,"test generator remains untouched by blocked emitter muzzle");
                                NeoForge.EVENT_BUS.unregister(listener);
                                h.succeed();
                            }
                        }
                    };
                    BoundedGameTestWait.safeOnEachTick(h,()->{ if(h.getTick()<950) tick[0].run(); });
                });
    }

    /** The registered projectile's full 40-block/s sweep uses a loaded actual generator, not a fake hit callback. */
    @GameTest(templateNamespace="moonstation14", template="power_live_chain_clear", timeoutTicks=180)
    public static void highSpeedLoadedBoltSweepChargesRegisteredGenerator(GameTestHelper h) {
        ServerLevel level=(ServerLevel)h.getLevel();
        BlockPos emitterPos=absolute(h,3,1,3), genPos=absolute(h,8,1,6);
        level.setBlock(emitterPos,ModBlocks.EMITTER.get().defaultBlockState(),3);
        level.setBlock(genPos,ModBlocks.FIELD_GENERATOR.get().defaultBlockState(),3);
        var emitter=(EmitterBlockEntity)level.getBlockEntity(emitterPos);
        var generator=(FieldGeneratorBlockEntity)level.getBlockEntity(genPos);
        check(emitter!=null&&generator!=null,"registered machine adapters exist");
        EmitterBoltEntity bolt=new EmitterBoltEntity(level,emitter,emitter.resolvedProfile());
        bolt.setPos(Vec3.atLowerCornerOf(absolute(h,8,1,9)).add(0.5,0.75,0.5));
        bolt.setDeltaMovement(0,0,-2);
        check(level.addFreshEntity(bolt),"registered emitter bolt enters actual loaded world");
        BoundedGameTestWait.await(h,5,()->"generator="+generator.snapshot(),
                ()->generator.snapshot().charge()>0,
                ()->{
                    check(generator.snapshot().charge()==3,"one swept generator hit applies exactly one profile charge");
                    check(bolt.isRemoved(),"bolt is removed after real generator collision");
                    h.succeed();
                 });
    }

    /** The real projectile uses the generator's metal collision column, not its taller selection outline. */
    @GameTestTags("reactor-contact")
    @GameTest(templateNamespace="moonstation14", template="power_live_chain_clear", timeoutTicks=80)
    public static void nativeBoltContactChargesMetalColumnOnceAndDocumentsUpperCellMiss(GameTestHelper h) {
        ServerLevel level=(ServerLevel)h.getLevel();
        BlockPos emitterPos=absolute(h,2,4,2), xPos=absolute(h,8,4,5), zPos=absolute(h,5,4,9), upperPos=absolute(h,10,4,9);
        level.setBlock(emitterPos,ModBlocks.EMITTER.get().defaultBlockState(),3);
        level.setBlock(xPos,ModBlocks.FIELD_GENERATOR.get().defaultBlockState(),3);
        level.setBlock(zPos,ModBlocks.FIELD_GENERATOR.get().defaultBlockState(),3);
        level.setBlock(upperPos,ModBlocks.FIELD_GENERATOR.get().defaultBlockState(),3);
        var emitter=(EmitterBlockEntity)level.getBlockEntity(emitterPos);
        var xGenerator=(FieldGeneratorBlockEntity)level.getBlockEntity(xPos);
        var zGenerator=(FieldGeneratorBlockEntity)level.getBlockEntity(zPos);
        var upperGenerator=(FieldGeneratorBlockEntity)level.getBlockEntity(upperPos);
        check(emitter!=null&&xGenerator!=null&&zGenerator!=null&&upperGenerator!=null,"registered emitter and field generator entities are present");
        var profile=emitter.resolvedProfile();
        var generatorProfile=com.juicyslew.moonstation14.ms14.singularity.containment.ModFieldGenerators.catalog(level).get(xGenerator.profileId());
        check(profile!=null&&generatorProfile!=null&&generatorProfile.boltCharge()==3,"registered emitter shot and field-generator bolt charge profiles resolve");
        var outline=level.getBlockState(upperPos).getShape(level,upperPos,net.minecraft.world.phys.shapes.CollisionContext.empty()).bounds();
        var collision=level.getBlockState(upperPos).getCollisionShape(level,upperPos,net.minecraft.world.phys.shapes.CollisionContext.empty());
        check(outline.maxY>=1.25&&collision.toAabbs().stream().anyMatch(box->box.minX>=.375-1e-6&&box.maxX<=.625+1e-6&&box.maxY>=1.25-1e-6),
                "visible tall outline and narrow native metal column are distinct");

        EmitterBoltEntity xBolt=bolt(level,emitter,profile,new Vec3(xPos.getX()-1,xPos.getY()+.5,xPos.getZ()+.5),new Vec3(2,0,0));
        check(level.addFreshEntity(xBolt),"X-axis live bolt is registered");
        xBolt.tick();
        check(xGenerator.snapshot().charge()==generatorProfile.boltCharge()&&xBolt.isRemoved(),"X-axis beam at block-center Y hits metal column, charges once and is consumed");

        EmitterBoltEntity zBolt=bolt(level,emitter,profile,new Vec3(zPos.getX()+.5,zPos.getY()+.5,zPos.getZ()-1),new Vec3(0,0,2));
        check(level.addFreshEntity(zBolt),"Z-axis live bolt is registered");
        zBolt.tick();
        check(zGenerator.snapshot().charge()==generatorProfile.boltCharge()&&zBolt.isRemoved(),"Z-axis beam at block-center Y hits metal column, charges once and is consumed");

        for(int x=upperPos.getX()-1;x<=upperPos.getX()+1;x++) {
            BlockPos above=new BlockPos(x,upperPos.getY()+1,upperPos.getZ());
            if(!above.equals(upperPos.above())) level.setBlock(above,Blocks.AIR.defaultBlockState(),3);
        }
        Vec3 upperFrom=new Vec3(upperPos.getX()-1,upperPos.getY()+1.1,upperPos.getZ()+.5);
        Vec3 upperTo=upperFrom.add(2,0,0);
        var nativeUpperHit=level.clip(new net.minecraft.world.level.ClipContext(upperFrom,upperTo,
                net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,
                net.minecraft.world.phys.shapes.CollisionContext.empty()));
        check(nativeUpperHit.getType()==net.minecraft.world.phys.HitResult.Type.MISS,
                "vanilla native clip traverses the cell above owner and does not query the lower generator's tall column: "+nativeUpperHit);
        EmitterBoltEntity upperBolt=bolt(level,emitter,profile,upperFrom,new Vec3(2,0,0));
        check(level.addFreshEntity(upperBolt),"upper-visible-column diagnostic bolt is registered");
        upperBolt.tick();
        check(upperGenerator.snapshot().charge()==0&&!upperBolt.isRemoved(),
                "live bolt follows native clip miss above owning cell; generator remains uncharged and bolt continues");
        h.succeed();
    }

    private static EmitterBoltEntity bolt(ServerLevel level, EmitterBlockEntity emitter,
                                           com.juicyslew.moonstation14.ms14.singularity.emitter.EmitterProfile profile,
                                           Vec3 origin, Vec3 velocity) {
        EmitterBoltEntity bolt=new EmitterBoltEntity(level,emitter,profile);
        bolt.setPos(origin); bolt.setDeltaMovement(velocity);
        return bolt;
    }
}
