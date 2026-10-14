package com.juicyslew.moonstation14.ms14.singularity.pa;

import com.juicyslew.moonstation14.block.block_entity.ParticleAcceleratorPartBlockEntity;
import com.juicyslew.moonstation14.block.custom.PowerDeviceBlock;
import com.juicyslew.moonstation14.entities.ParticleAcceleratorParticleEntity;
import com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.gametest.support.PowerFixtureReadiness;
import com.juicyslew.moonstation14.ms14.power.cable.CableStorage;
import com.juicyslew.moonstation14.ms14.power.cable.CableTier;
import com.juicyslew.moonstation14.block.block_entity.PowerDeviceBlockEntity;
import com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime;
import com.juicyslew.moonstation14.ms14.power.device.PowerDeviceFixturePlacement;
import com.juicyslew.moonstation14.ms14.power.topology.CableFaceNode;
import com.juicyslew.moonstation14.ms14.power.topology.PowerTopology;
import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.block.block_entity.SingularityGeneratorBlockEntity;
import com.juicyslew.moonstation14.entities.SingularityEntity;
import com.juicyslew.moonstation14.ms14.singularity.containment.ContainmentFixture;
import com.juicyslew.moonstation14.ms14.singularity.containment.FieldContainmentRuntime;
import com.juicyslew.moonstation14.ms14.singularity.containment.ClosedFieldVolume;
import com.juicyslew.moonstation14.ms14.singularity.ModSingularityGameplay;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.*;
import java.util.function.Consumer;

@GameTestTags({"particle-accelerator-firing", "power"})
@PrefixGameTestTemplate(false)
public final class ParticleAcceleratorFiringGameTests {
    private record StandbyProjectionEpoch(boolean enabled, ParticleAcceleratorStrength strength, double mvDemand,
                                           PowerRuntime.MvPowerObservation apcMeter,
                                           com.juicyslew.moonstation14.block.custom.ParticleAcceleratorPartBlock.Visual visual,
                                           long gameTime, UUID apcIdentity, UUID controlIdentity,
                                           ParticleAcceleratorPartBlockEntity controlEntity, UUID powerIdentity,
                                           ParticleAcceleratorPartBlockEntity powerEntity) { }

    private static final int CONTAINED_PA_STARTUP_TIMEOUT_TICKS=1000;
    private ParticleAcceleratorFiringGameTests() { }
    private static void require(boolean ok, String message) { if (!ok) throw new GameTestAssertException(message); }
    private static BlockPos at(GameTestHelper h, int x, int y, int z) { return h.absolutePos(new BlockPos(x,y,z)); }
    private static BlockPos at(GameTestHelper h, int offsetX, int x, int y, int z) { return h.absolutePos(new BlockPos(x+offsetX,y,z)); }
    private static void cable(ServerLevel level, BlockPos pos, Direction face, CableTier tier) {
        if (level.getBlockState(pos).isAir()||level.getBlockState(pos).is(Blocks.BARRIER)) level.setBlock(pos, Blocks.STONE.defaultBlockState(), 3);
        require(CableStorage.has(level,pos,face,tier)||CableStorage.place(level,pos,face,tier),
                "place " + tier + " cable at " + pos + " face=" + face+" host="+level.getBlockState(pos)+" sturdy="+level.getBlockState(pos).isFaceSturdy(level,pos,face));
    }

    @GameTest(templateNamespace="moonstation14", template="power_live_chain_clear", timeoutTicks=1000)
    public static void realPoweredControlEmitsThreeParticlesAfterResolvedDelay(GameTestHelper h) {
        ServerLevel level=(ServerLevel)h.getLevel();
        var fixture=poweredAssembly(h,level);
        ParticleAcceleratorPartBlockEntity control=fixture.control();
        ParticleAcceleratorPartBlockEntity power=fixture.power();
        var assembly=fixture.assembly();
        BlockPos apc=fixture.apcPos();
        CableFaceNode[] ports=fixture.ports();
        List<ParticleAcceleratorParticleEntity> particles=fixture.particles();
        List<Long> joinTicks=fixture.joinTicks();
        List<net.minecraft.world.phys.Vec3> joinVelocities=fixture.velocities();
        List<Long> joinEpochs=fixture.joinEpochs();
        Set<UUID> expectedSources=fixture.expectedSourceIDs();
        long[] observedEpoch={-1};
        PowerFixtureReadiness.await(h,level,500,
                 ()->PowerFixtureReadiness.topologyReadyWithKnownPorts(level,ports),
                  ()->powerDiagnostics(level, control, power, apc, ports),()->{
                      control.setEnabled(true);
                      awaitStandbyBeforeStrengthTransition(h,level,control,power,assembly,apc,ports,0,()->{
                     BoundedGameTestWait.await(h,PowerRuntime.TICKS_PER_SOLVE*4,
                             ()->"APC="+control.apcPowerReady()+" eligible="+control.firingEligible()+"; "+powerDiagnostics(level,control,power,apc,ports),
                            ()->control.apcPowerReady()&&control.firingEligible(),()->{
                        require(control.mvPowerDemandWatts()==null && power.mvPowerDemandWatts()!=null,
                                "only resolved power box requests MV allocation");
                        require(power.mvPowerDemandWatts()==(double)assembly.profile().strength(ParticleAcceleratorStrength.LEVEL_2).powerBoxMv(),
                                "selected LEVEL_2 demand comes from resolved profile");
                         final long deadline=level.getGameTime()+1600;
                        Runnable[] poll={null};
                        poll[0]=()->{
                            var schedule=control.waveScheduleSnapshot();
                            if(!particles.isEmpty() && observedEpoch[0]<0) observedEpoch[0]=schedule.eligibleSinceTick();
                            if(level.getGameTime()>=deadline) {
                                 var failure=new GameTestAssertException("timed out waiting two waves; eligible="+control.firingEligible()+"; apcReady="+control.apcPowerReady()+"; observedEpoch="+observedEpoch[0]+"; schedule="+schedule+"; particleCount="+particles.size()+"; joinTicks="+joinTicks+"; joinEpochs="+joinEpochs+"; currentMeters="+PowerRuntime.mvPowerMeter(level,power)+"/"+PowerRuntime.apcPowerMeter(level,control)+"; testTick="+h.getTick()+"; "+powerDiagnostics(level,control,power,apc,ports));
                                h.testInfo.fail(failure); return;
                            }
                            if(!control.firingEligible()) { BoundedGameTestWait.safeDelay(h,1,poll[0]); return; }
                            long interval=assembly.profile().waveSeconds()*20L;
                             int first=-1;
                             for(int i=0;i+5<joinTicks.size();i+=3) if(joinEpochs.get(i).equals(joinEpochs.get(i+3))) { first=i; break; }
                             if(first>=0) {
                                 long epoch=joinEpochs.get(first);
                                 require(joinTicks.get(first)-epoch==interval,"first wave in eligibility epoch occurs exactly one resolved interval later");
                                 require(joinTicks.get(first+3)-joinTicks.get(first)==interval,"consecutive waves in same epoch follow exact cadence");
                                 for(int wave=0;wave<2;wave++) {
                                     int base=first+wave*3;
                                     long tick=joinTicks.get(base);
                                     require(joinEpochs.get(base+1)==epoch&&joinEpochs.get(base+2)==epoch,"wave particles share eligibility epoch");
                                     require(joinTicks.get(base+1)==tick&&joinTicks.get(base+2)==tick,"each wave joins atomically on one game tick");
                                     for(int i=0;i<3;i++) {
                                         var p=particles.get(base+i);
                                        require(expectedSources.contains(p.sourceEmitterIdentity()),"only this assembly's emitter identities are captured");
                                        require(p.resolvedProfile()!=null,"spawned particle has resolved profile");
                                        var role=assembly.parts().entrySet().stream().filter(e->e.getValue().placementIdentity().equals(p.sourceEmitterIdentity())).findFirst().orElseThrow().getKey();
                                        var partProfile=assembly.profile().parts().get(role);
                                        var rotation=Rotation.NONE.getRotated(partProfile.relativeRotation());
                                        var expectedDirection=net.minecraft.world.phys.Vec3.atLowerCornerOf(rotation.rotate(Direction.SOUTH).getNormal());
                                         var observed=joinVelocities.get(base+i);
                                        require(observed.normalize().distanceTo(expectedDirection)<1e-8,"particle launch direction follows resolved emitter orientation for "+role);
                                        require(Math.abs(observed.length()-assembly.profile().particleSpeed()/20.0)<1e-8,"particle speed follows resolved profile");
                                    }
                                }
                                h.succeed(); return;
                            }
                            BoundedGameTestWait.safeDelay(h,1,poll[0]);
                        };
                         h.runAfterDelay(1,poll[0]);
                     });
                      });
                 });
    }

    private static void awaitStandbyBeforeStrengthTransition(GameTestHelper h,ServerLevel level,
            ParticleAcceleratorPartBlockEntity control,ParticleAcceleratorPartBlockEntity power,
            PAAssemblyFixture.Assembly assembly,BlockPos apc,CableFaceNode[] ports,int retry,Runnable continued) {
        double standbyDemand=assembly.profile().strength(ParticleAcceleratorStrength.STANDBY).powerBoxMv();
        BoundedGameTestWait.await(h,PowerRuntime.TICKS_PER_SOLVE*4,
                ()->"standby MV baseline retry="+retry+" expectedVisual="+visual(ParticleAcceleratorStrength.STANDBY)
                        +" actualVisual="+control.getBlockState().getValue(com.juicyslew.moonstation14.block.custom.ParticleAcceleratorPartBlock.VISUAL)
                        +" enabled="+control.enabled()+" strength="+control.strength()+" APCmeter="+PowerRuntime.apcPowerMeter(level,control)
                        +" gameTime="+level.getGameTime()+" controlBE="+(level.getBlockEntity(control.getBlockPos())==control)
                        +" APCidentity="+control.apcPowerIdentity()+" controlPlacementIdentity="+control.placementIdentity()
                        +"; "+powerDiagnostics(level,control,power,apc,ports),
                ()->power.mvPowerDemandWatts()!=null&&power.mvPowerDemandWatts()==standbyDemand
                        &&PowerRuntime.mvPowerMeter(level,power).known()
                        &&PowerRuntime.mvPowerMeter(level,power).deliveredWatts()>=standbyDemand
                        &&PowerRuntime.apcPowerMeter(level,control).known(),()->{
            var before=PowerRuntime.mvPowerMeter(level,power);
            if(power.mvPowerDemandWatts()==null||power.mvPowerDemandWatts()!=standbyDemand||!before.known()) {
                if(retry>=5) throw new GameTestAssertException("could not capture a fresh standby sample immediately before demand transition; retry="+retry
                        +" standbyDemand="+standbyDemand+" actualDemand="+power.mvPowerDemandWatts()+" meter="+before
                        +" powerIdentity="+power.placementIdentity()+" controlIdentity="+control.placementIdentity()
                        +" expectedVisual="+visual(ParticleAcceleratorStrength.STANDBY)+" actualVisual="
                        +control.getBlockState().getValue(com.juicyslew.moonstation14.block.custom.ParticleAcceleratorPartBlock.VISUAL)
                        +" enabled="+control.enabled()+" strength="+control.strength()+" APCmeter="+PowerRuntime.apcPowerMeter(level,control)
                        +" gameTime="+level.getGameTime()+" controlBE="+(level.getBlockEntity(control.getBlockPos())==control)
                        +" APCidentity="+control.apcPowerIdentity()
                        +" topology="+com.juicyslew.moonstation14.ms14.power.graph.PowerGraphService.topologyGeneration(level)
                        +"; "+powerDiagnostics(level,control,power,apc,ports));
                awaitStandbyBeforeStrengthTransition(h,level,control,power,assembly,apc,ports,retry+1,continued);
                return;
            }
            long topology=com.juicyslew.moonstation14.ms14.power.graph.PowerGraphService.topologyGeneration(level);
            UUID powerIdentity=power.placementIdentity();
            control.setStrength(ParticleAcceleratorStrength.LEVEL_2);
            var after=PowerRuntime.mvPowerMeter(level,power);
            require(power.mvPowerDemandWatts()==(double)assembly.profile().strength(ParticleAcceleratorStrength.LEVEL_2).powerBoxMv(),
                    "selected resolved strength changes physical power-box demand; before="+before+" demand="+standbyDemand
                            +" after="+after+" actualDemand="+power.mvPowerDemandWatts()+" identity="+power.placementIdentity());
            require(!after.known(),"actual demand increase retires prior MV observation; beforeDemand="+standbyDemand+" before="+before
                    +" afterDemand="+power.mvPowerDemandWatts()+" after="+after+" identityBefore="+powerIdentity
                    +" identityAfter="+power.placementIdentity()+" topologyBefore="+topology
                    +" topologyAfter="+com.juicyslew.moonstation14.ms14.power.graph.PowerGraphService.topologyGeneration(level));
            require(power.placementIdentity().equals(powerIdentity),"same registered power node identity across demand transition");
            require(com.juicyslew.moonstation14.ms14.power.graph.PowerGraphService.topologyGeneration(level)==topology,
                    "strength demand change preserves graph topology");
            continued.run();
        });
    }

    @GameTestTags({"reactor-models", "pa-session-demand", "reactor-interaction"})
    @GameTest(templateNamespace="moonstation14", template="power_live_chain_clear", timeoutTicks=1000)
    public static void resolvedAssemblyProjectsEveryPartAcrossNaturalPowerAndStrengthTransitions(GameTestHelper h) {
        ServerLevel level=(ServerLevel)h.getLevel();
        var fixture=poweredAssembly(h,level,0);
        var control=fixture.control(); var power=fixture.power(); var assembly=fixture.assembly();
        var ids=new HashMap<String,UUID>();
        assembly.parts().forEach((role,part)->ids.put(role,part.placementIdentity()));
        PowerFixtureReadiness.await(h,level,500,
                 ()->PowerFixtureReadiness.topologyReadyWithKnownPorts(level,fixture.ports()),
                 ()->powerDiagnostics(level,control,power,fixture.apcPos(),fixture.ports()),()->{
                      long stableTopology=com.juicyslew.moonstation14.ms14.power.graph.PowerGraphService.topologyGeneration(level);
                     var visualProperty=com.juicyslew.moonstation14.block.custom.ParticleAcceleratorPartBlock.VISUAL;
                      StandbyProjectionEpoch[] baselineEpoch={null};
                     BoundedGameTestWait.await(h,PowerRuntime.TICKS_PER_SOLVE*4,
                             ()->"disabled standby APC baseline expectedVisual=UNLITP actualVisual="+control.getBlockState().getValue(visualProperty)
                                     +" enabled="+control.enabled()+" strength="+control.strength()+" meter="+PowerRuntime.apcPowerMeter(level,control)
                                     +" gameTime="+level.getGameTime()+" APCidentity="+control.apcPowerIdentity()+" currentBE="
                                     +(level.getBlockEntity(control.getBlockPos())==control),
                             ()->{
                                 var meter=PowerRuntime.apcPowerMeter(level,control);
                                 var actual=control.getBlockState().getValue(visualProperty);
                                 if(!meter.known()||meter.deliveredWatts()<250||control.enabled()
                                         ||control.strength()!=ParticleAcceleratorStrength.STANDBY
                                         ||actual!=com.juicyslew.moonstation14.block.custom.ParticleAcceleratorPartBlock.Visual.UNLITP)return false;
                                 baselineEpoch[0]=new StandbyProjectionEpoch(control.enabled(),control.strength(),
                                         power.mvPowerDemandWatts(),meter,actual,level.getGameTime(),control.apcPowerIdentity(),
                                         control.placementIdentity(),control,power.placementIdentity(),power);
                                 return true;
                             },()->{
                                 var epoch=baselineEpoch[0];
                                 require(epoch!=null&&!epoch.enabled()&&epoch.strength()==ParticleAcceleratorStrength.STANDBY
                                                 &&epoch.mvDemand()==(double)assembly.profile().strength(ParticleAcceleratorStrength.STANDBY).powerBoxMv()
                                                 &&epoch.visual()==com.juicyslew.moonstation14.block.custom.ParticleAcceleratorPartBlock.Visual.UNLITP
                                                 &&epoch.apcMeter().known()&&epoch.apcMeter().deliveredWatts()>=250
                                                 &&epoch.apcIdentity().equals(control.apcPowerIdentity())
                                                 &&epoch.controlIdentity().equals(control.placementIdentity())
                                                 &&level.getBlockEntity(control.getBlockPos())==epoch.controlEntity()
                                                 &&epoch.powerIdentity().equals(power.placementIdentity())
                                                 &&level.getBlockEntity(power.getBlockPos())==epoch.powerEntity(),
                                         "naturally projected disabled powered standby epoch="+epoch+" currentBE="
                                                 +(level.getBlockEntity(control.getBlockPos())==epoch.controlEntity())
                                                 +" currentAPCidentity="+control.apcPowerIdentity()+" currentControlIdentity="+control.placementIdentity()
                                                 +" currentPowerBE="+(level.getBlockEntity(power.getBlockPos())==epoch.powerEntity())
                                                 +" currentPowerIdentity="+power.placementIdentity());
                                 var levels=List.of(ParticleAcceleratorStrength.LEVEL_0,ParticleAcceleratorStrength.LEVEL_1,ParticleAcceleratorStrength.LEVEL_2);
                                var index=new int[]{0};
                                Runnable[] next={null};
                                next[0]=()->{
                                    if(index[0]>=levels.size()) {
                                        require(control.firingEligible(),"naturally powered APC plus resolved MV box remains firing-eligible");
                                        var lost=assembly.parts().get("fuel");
                                        level.setBlock(lost.getBlockPos(),Blocks.AIR.defaultBlockState(),3);
                                        BoundedGameTestWait.await(h,PowerRuntime.TICKS_PER_SOLVE*3,
                                                ()->"assembly="+control.assemblyValid(),()->!control.assemblyValid(),()->{
                                                    require(!control.enabled(),"assembly loss switches the controller off");
                                                    for(var part:assembly.parts().entrySet()) {
                                                        if(part.getKey().equals("fuel")) continue;
                                                        var be=(ParticleAcceleratorPartBlockEntity)level.getBlockEntity(part.getValue().getBlockPos());
                                                        require(be!=null&&!be.enabled(),"assembly loss resets controller-mirrored part "+part.getKey());
                                                        require(ids.get(part.getKey()).equals(be.placementIdentity()),"assembly projection preserves "+part.getKey()+" identity");
                                                        if(!part.getKey().equals("end")) require(be.getBlockState().getValue(com.juicyslew.moonstation14.block.custom.ParticleAcceleratorPartBlock.VISUAL)
                                                                ==com.juicyslew.moonstation14.block.custom.ParticleAcceleratorPartBlock.Visual.COMPLETED,
                                                                "assembly loss projects unpowered artwork for "+part.getKey());
                                                    }
                                                    h.succeed();
                                                });
                                        return;
                                    }
                                    var selected=levels.get(index[0]);
                                      long transitionTopology=com.juicyslew.moonstation14.ms14.power.graph.PowerGraphService.topologyGeneration(level);
                                      control.setStrength(selected); control.setEnabled(true);
                                      require(com.juicyslew.moonstation14.ms14.power.graph.PowerGraphService.topologyGeneration(level)==transitionTopology,
                                              "controller strength/enable demand transition preserves graph topology generation");
                                     double demand=assembly.profile().strength(selected).powerBoxMv();
                                     long[] apcReadyPredicateGameTime={-1};
                                     PowerRuntime.MvPowerObservation[] apcReadyPredicateMeter={null};
                                     com.juicyslew.moonstation14.block.custom.ParticleAcceleratorPartBlock.Visual[] apcReadyPredicateVisual={null};
                                     boolean[] apcReadyPredicateEnabled={false};
                                     ParticleAcceleratorStrength[] apcReadyPredicateStrength={ParticleAcceleratorStrength.STANDBY};
                                     UUID[] apcReadyPredicateIdentity={null};
                                     ParticleAcceleratorPartBlockEntity[] apcReadyPredicateBlockEntity={null};
                                     BoundedGameTestWait.await(h,PowerRuntime.TICKS_PER_SOLVE*5,
                                             ()->"selected="+selected+" power="+power.mvPowerDemandWatts()+"/"+PowerRuntime.mvPowerMeter(level,power)
                                                     +" APC="+PowerRuntime.apcPowerMeter(level,control)+" visual="+control.getBlockState().getValue(visualProperty),
                                             ()->{
                                                if(!power.enabled()||power.strength()!=selected||power.mvPowerDemandWatts()!=demand)return false;
                                                var meter=PowerRuntime.apcPowerMeter(level,control);
                                                var actualVisual=control.getBlockState().getValue(visualProperty);
                                                if(!meter.known()||meter.deliveredWatts()<250||!control.apcPowerReady()||!control.firingEligible()
                                                        ||actualVisual!=visual(selected))return false;
                                                apcReadyPredicateMeter[0]=meter;
                                                apcReadyPredicateVisual[0]=actualVisual;
                                                apcReadyPredicateEnabled[0]=control.enabled();
                                                apcReadyPredicateStrength[0]=control.strength();
                                                apcReadyPredicateIdentity[0]=control.apcPowerIdentity();
                                                apcReadyPredicateBlockEntity[0]=control;
                                                apcReadyPredicateGameTime[0]=level.getGameTime();
                                                return true;
                                              },()->{
                                                  require(apcReadyPredicateMeter[0]!=null&&apcReadyPredicateMeter[0].known()
                                                                  &&apcReadyPredicateMeter[0].deliveredWatts()>=250
                                                                  &&apcReadyPredicateVisual[0]==visual(selected)
                                                                  &&apcReadyPredicateEnabled[0]&&apcReadyPredicateStrength[0]==selected
                                                                  &&apcReadyPredicateIdentity[0].equals(control.apcPowerIdentity())
                                                                  &&level.getBlockEntity(control.getBlockPos())==apcReadyPredicateBlockEntity[0],
                                                          "coherent powered PA acceptance epoch selected="+selected+" predicateTick="
                                                                  +apcReadyPredicateGameTime[0]+" capturedMeter="+apcReadyPredicateMeter[0]
                                                                  +" capturedVisual="+apcReadyPredicateVisual[0]+" capturedEnabled="
                                                                  +apcReadyPredicateEnabled[0]+" capturedStrength="+apcReadyPredicateStrength[0]
                                                                  +" currentVisual="+control.getBlockState().getValue(visualProperty)
                                                                  +" currentEnabled="+control.enabled()+" currentStrength="+control.strength()
                                                                  +" capturedAPCidentity="+apcReadyPredicateIdentity[0]+" currentAPCidentity="
                                                                  +control.apcPowerIdentity()+" capturedBE="+apcReadyPredicateBlockEntity[0]+" currentBE="
                                                                  +(level.getBlockEntity(control.getBlockPos())==apcReadyPredicateBlockEntity[0])+" loaded="
                                                                  +level.hasChunkAt(control.getBlockPos()));
                                                for(var part:assembly.parts().entrySet()) {
                                                    var be=(ParticleAcceleratorPartBlockEntity)level.getBlockEntity(part.getValue().getBlockPos());
                                                    if(part.getKey().equals("end")) {
                                                        require(be.getBlockState().getValue(com.juicyslew.moonstation14.block.custom.ParticleAcceleratorPartBlock.VISUAL)
                                                                        ==com.juicyslew.moonstation14.block.custom.ParticleAcceleratorPartBlock.Visual.COMPLETED,
                                                                "end cap remains static");
                                                    } else {
                                                        require(be.enabled()&&be.strength()==selected,"same control selection reaches "+part.getKey());
                                                        require(be.getBlockState().getValue(com.juicyslew.moonstation14.block.custom.ParticleAcceleratorPartBlock.VISUAL)==visual(selected),
                                                                "all-role native visual projection "+part.getKey()+" at "+selected);
                                                    }
                                                    require(ids.get(part.getKey()).equals(be.placementIdentity()),"strength projection preserves "+part.getKey()+" identity");
                                                }
                                                index[0]++;
                                                if(index[0]<levels.size()) control.setEnabled(false);
                                                BoundedGameTestWait.safeDelay(h,1,next[0]);
                                            });
                                };
                                next[0].run();
                                });
                });
    }

    private static com.juicyslew.moonstation14.block.custom.ParticleAcceleratorPartBlock.Visual visual(ParticleAcceleratorStrength strength) {
        return switch(strength) {
            case STANDBY -> com.juicyslew.moonstation14.block.custom.ParticleAcceleratorPartBlock.Visual.UNLITP;
            case LEVEL_0 -> com.juicyslew.moonstation14.block.custom.ParticleAcceleratorPartBlock.Visual.UNLITP0;
            case LEVEL_1 -> com.juicyslew.moonstation14.block.custom.ParticleAcceleratorPartBlock.Visual.UNLITP1;
            case LEVEL_2 -> com.juicyslew.moonstation14.block.custom.ParticleAcceleratorPartBlock.Visual.UNLITP2;
            case LEVEL_3 -> com.juicyslew.moonstation14.block.custom.ParticleAcceleratorPartBlock.Visual.UNLITP3;
        };
    }

    @GameTest(templateNamespace="moonstation14", template="power_live_chain_clear", timeoutTicks=1000)
    public static void mvOutageRestartsFullResolvedChargeInterval(GameTestHelper h) {
        ServerLevel level=(ServerLevel)h.getLevel();
        var f=poweredAssembly(h,level);
        var control=f.control(); var power=f.power(); var assembly=f.assembly();
        var ports=f.ports(); var particles=f.particles();
        PowerFixtureReadiness.await(h,level,500,()->PowerFixtureReadiness.topologyReadyWithKnownPorts(level,ports),
                ()->powerDiagnostics(level,control,power,f.apcPos(),ports),()->{
            control.setStrength(ParticleAcceleratorStrength.LEVEL_2); control.setEnabled(true);
            BoundedGameTestWait.await(h,PowerRuntime.TICKS_PER_SOLVE*4,
                    ()->powerDiagnostics(level,control,power,f.apcPos(),ports),()->control.firingEligible(),()->{
                long interval=assembly.profile().waveSeconds()*20L;
                BlockPos outage=at(h,2,2,4);
                require(CableStorage.remove(level,outage,Direction.EAST,CableTier.MV)==CableTier.MV,"remove real MV cable");
                BoundedGameTestWait.await(h,PowerRuntime.TICKS_PER_SOLVE*4,
                        ()->powerDiagnostics(level,control,power,f.apcPos(),ports),
                        ()->PowerRuntime.mvPowerMeter(level,power).known()&&PowerRuntime.mvPowerMeter(level,power).deliveredWatts()==0.0&&!control.firingEligible(),()->{
                    require(control.enabled(),"physical enabled intent survives MV outage");
                    require(control.waveScheduleSnapshot().equals(ParticleAcceleratorWaveSchedule.IDLE),"wave schedule resets to IDLE after outage");
                    int before=particles.size();
                    BoundedGameTestWait.safeDelay(h,interval+2,()->{
                        require(particles.size()==before,"outage produces no additional own particles");
                        require(CableStorage.place(level,outage,Direction.EAST,CableTier.MV),"restore exact removed MV cable");
                        long restoredAt=level.getGameTime();
                        BoundedGameTestWait.await(h,PowerRuntime.TICKS_PER_SOLVE*4,
                                ()->powerDiagnostics(level,control,power,f.apcPos(),ports),
                                ()->control.firingEligible()&&PowerRuntime.mvPowerMeter(level,power).known()&&PowerRuntime.mvPowerMeter(level,power).deliveredWatts()>0,
                                 ()->awaitRestoredEpoch(h,level,f,restoredAt,particles.size(),interval,0,-1));
                    });
                });
            });
        });
    }

    @GameTest(templateNamespace="moonstation14", template="power_live_chain_clear", timeoutTicks=1000)
    public static void missingEmitterPreventsPartialWave(GameTestHelper h) {
        ServerLevel level=(ServerLevel)h.getLevel();
        var f=poweredAssembly(h,level);
        var control=f.control(); var power=f.power(); var assembly=f.assembly(); var ports=f.ports();
        PowerFixtureReadiness.await(h,level,500,()->PowerFixtureReadiness.topologyReadyWithKnownPorts(level,ports),
                ()->powerDiagnostics(level,control,power,f.apcPos(),ports),()->{
            control.setStrength(ParticleAcceleratorStrength.LEVEL_2); control.setEnabled(true);
            BoundedGameTestWait.await(h,PowerRuntime.TICKS_PER_SOLVE*4,
                    ()->powerDiagnostics(level,control,power,f.apcPos(),ports),()->control.firingEligible(),()->{
                int baseline=f.particles().size();
                long interval=assembly.profile().waveSeconds()*20L;
                BlockPos emitter=assembly.parts().get("starboard").getBlockPos();
                var oldEmitter=level.getBlockEntity(emitter);
                require(oldEmitter instanceof ParticleAcceleratorPartBlockEntity,"starboard emitter is registered before removal");
                require(level.setBlock(emitter,Blocks.AIR.defaultBlockState(),3),"remove starboard emitter block");
                require(level.getBlockEntity(emitter)!=oldEmitter,"removed emitter block entity is no longer registered");
                BoundedGameTestWait.await(h,PowerRuntime.TICKS_PER_SOLVE*4,
                        ()->"eligible="+control.firingEligible()+" particles="+f.particles().size()+"; "+powerDiagnostics(level,control,power,f.apcPos(),ports),
                        ()->!control.firingEligible(),()->BoundedGameTestWait.safeDelay(h,(int)interval+1,()->{
                            require(f.particles().size()==baseline,"missing emitter prevents any partial wave from this assembly; sources="+f.expectedSourceIDs()+" particles="+f.particles().size()+" baseline="+baseline);
                            h.succeed();
                        }));
            });
        });
    }

     @GameTest(templateNamespace="moonstation14", template="pa_contained_clear", timeoutTicks=CONTAINED_PA_STARTUP_TIMEOUT_TICKS)
    public static void realPoweredWavesStartExactlyOneContainedSingularity(GameTestHelper h) {
        ServerLevel level=(ServerLevel)h.getLevel();
        var f=poweredAssembly(h,level,10);
        // Keep the entire containment structure inside this fixture's own footprint. The old
        // negative-X placement could overlap structures concurrently loaded by the GameTest runner.
        BlockPos boxOrigin=new BlockPos(4,0,6), genLocal=new BlockPos(7,3,9);
        // Refuse to place anything across an unloaded boundary: these coordinates are deliberately
        // within the already-loaded large power fixture's neighboring chunks.
        for(int[] c:new int[][]{{1,1,1},{5,1,1},{1,5,1},{5,5,1},{1,1,5},{5,1,5},{1,5,5},{5,5,5}})
            require(level.hasChunkAt(at(h,boxOrigin.getX()+c[0],boxOrigin.getY()+c[1],boxOrigin.getZ()+c[2])),"containment fixture chunk loaded");
        var corners=ContainmentFixture.placeBox(h,boxOrigin);
        FieldContainmentRuntime.refresh(level);
        var initialVolumes=FieldContainmentRuntime.closedVolumes(level);
        var preflightProfile=com.juicyslew.moonstation14.ms14.singularity.ModSingularityGenerators.catalog(level)
                .get(com.juicyslew.moonstation14.ms14.singularity.ModSingularityGenerators.DEFAULT_ID);
        require(preflightProfile!=null && preflightProfile.failsafeEnabled(),"default generator profile resolves with failsafe enabled");
        var generatorCenter=net.minecraft.world.phys.Vec3.atCenterOf(at(h,genLocal.getX(),genLocal.getY(),genLocal.getZ()));
        long edgeCount=FieldContainmentRuntime.currentEdges(level).size();
        long faceCount=corners.stream().mapToLong(be->be.connectionEligible()?1:0).sum();
        require(initialVolumes.stream().anyMatch(v->ClosedFieldVolume.containsWithinFaceDistance(v,generatorCenter,preflightProfile.failsafeDistance())),
                "PA containment preflight edges="+edgeCount+" faces="+faceCount+" volumes="+initialVolumes.size()
                        +" target="+generatorCenter+" maxDistance="+preflightProfile.failsafeDistance());
        h.setBlock(genLocal,ModBlocks.SINGULARITY_GENERATOR.get());
        BlockPos genPos=at(h,genLocal.getX(),genLocal.getY(),genLocal.getZ());
        require(level.getBlockEntity(genPos) instanceof SingularityGeneratorBlockEntity,"registered startup generator placed");
        var gen=(SingularityGeneratorBlockEntity)level.getBlockEntity(genPos);
        var profile=f.assembly().profile().strength(ParticleAcceleratorStrength.LEVEL_2);
        List<SingularityEntity> spawned=new ArrayList<>();
        List<String> spawnProfiles=new ArrayList<>();
        List<Double> spawnEnergies=new ArrayList<>();
        List<Integer> spawnLevels=new ArrayList<>();
        ArrayDeque<String> foreStates=new ArrayDeque<>();
        Consumer<EntityJoinLevelEvent> observer=e->{
            if(e.getLevel()==level && e.getEntity() instanceof SingularityEntity s &&
                    s.blockPosition().closerThan(genPos,3)) {
                spawned.add(s); spawnProfiles.add(s.profileId().toString());
                spawnEnergies.add(s.energy()); spawnLevels.add(s.singularityLevel());
            }
        };
        NeoForge.EVENT_BUS.addListener(observer);
        h.testInfo.addListener(new GameTestListener() {
            private void clean(){ NeoForge.EVENT_BUS.unregister(observer); spawned.forEach(SingularityEntity::discard);
                level.setBlock(genPos,Blocks.AIR.defaultBlockState(),3);
                corners.forEach(be->level.setBlock(be.getBlockPos(),Blocks.AIR.defaultBlockState(),3));
                FieldContainmentRuntime.refresh(level); }
            public void testStructureLoaded(GameTestInfo i){} public void testPassed(GameTestInfo i,GameTestRunner r){clean();}
            public void testFailed(GameTestInfo i,GameTestRunner r){clean();}
            public void testAddedForRerun(GameTestInfo a,GameTestInfo b,GameTestRunner r){clean();}
        });
        PowerFixtureReadiness.await(h,level,500,()->PowerFixtureReadiness.topologyReadyWithKnownPorts(level,f.ports()),
                ()->powerDiagnostics(level,f.control(),f.power(),f.apcPos(),f.ports()),()->{
                f.control().setStrength(ParticleAcceleratorStrength.LEVEL_2); f.control().setEnabled(true);
                BoundedGameTestWait.await(h,PowerRuntime.TICKS_PER_SOLVE*4,()->powerDiagnostics(level,f.control(),f.power(),f.apcPos(),f.ports()),
                        ()->f.control().firingEligible(),()->{
                     long deadline=level.getGameTime()+800;
                     long[] finalWaveDeadline={deadline}; boolean[] finalWaveAllowanceChecked={false};
                     Runnable[] poll={null}; poll[0]=()->{
                         var fore=f.assembly().parts().get("fore");
                         for(var particle:f.particles()) if(particle.sourceEmitterIdentity().equals(fore.placementIdentity())) {
                             String state="t="+level.getGameTime()+" age="+particle.tickCount+" pos="+particle.position()+" vel="+particle.getDeltaMovement()+" d2="+particle.position().distanceToSqr(genPos.getX()+.5,genPos.getY()+.5,genPos.getZ()+.5)+" removed="+particle.isRemoved();
                             if(foreStates.isEmpty()||!foreStates.getLast().equals(state)){foreStates.addLast(state);while(foreStates.size()>3)foreStates.removeFirst();}
                         }
                        // Maintain this integration fixture's powered containment independently of the PA under test.
                        corners.forEach(be->{ be.receiveEmitterBolt(); be.receiveEmitterBolt(); });
                        FieldContainmentRuntime.refresh(level);
                        if(spawned.size()>=1 && f.particles().size()>=15) {
                            require(spawned.size()==1,"five native PA waves create one singularity");
                            require(gen.startupInert()&&gen.startupCharge()==0,"generator is inert and empty after four-charge threshold");
                             var generatorProfile=com.juicyslew.moonstation14.ms14.singularity.ModSingularityGenerators.catalog(level).get(gen.profileId());
                             var expectedProfile=generatorProfile==null?null:ModSingularityGameplay.catalog(level).get(generatorProfile.spawnProfile());
                             require(generatorProfile!=null && expectedProfile!=null,"generator spawn profile resolves from gameplay catalog");
                             require(spawnProfiles.getFirst().equals(generatorProfile.spawnProfile().toString()),"spawned singularity uses configured generator spawn profile");
                            require(Math.abs(spawnEnergies.getFirst()-expectedProfile.initialEnergy())<Math.max(1e-4,expectedProfile.initialEnergy()*1e-6),"spawn initializes configured profile energy");
                            require(spawnLevels.getFirst()==expectedProfile.initialLevel(),"spawn initializes configured profile level");
                            h.succeed(); return;
                        }
                         if(level.getGameTime()>=deadline&&!finalWaveAllowanceChecked[0]) {
                             finalWaveAllowanceChecked[0]=true;
                             var control=f.control(); var power=f.power(); var schedule=control.waveScheduleSnapshot();
                             var mv=PowerRuntime.mvPowerMeter(level,power); var apcMeter=PowerRuntime.apcPowerMeter(level,control);
                             boolean startupComplete=spawned.size()==1&&gen.startupInert()&&gen.startupCharge()==0;
                             boolean knownRealPower=control.firingEligible()&&control.apcPowerReady()
                                     &&mv.known()&&mv.deliveredWatts()>=power.mvPowerDemandWatts()
                                     &&apcMeter.known()&&apcMeter.deliveredWatts()>=control.apcPowerDemandWatts();
                             if(f.particles().size()>=12&&startupComplete&&knownRealPower
                                     &&schedule.nextWaveTick()>=level.getGameTime()) {
                                 var forePart=f.assembly().parts().get("fore");
                                 Direction direction=control.orientation().getRotated(f.assembly().profile().parts().get("fore").relativeRotation()).rotate(Direction.SOUTH);
                                 var origin=net.minecraft.world.phys.Vec3.atCenterOf(forePart.getBlockPos())
                                         .add(net.minecraft.world.phys.Vec3.atLowerCornerOf(direction.getNormal()).scale(.8)).add(0,.25,0);
                                 double speed=f.assembly().profile().particleSpeed()/20.0;
                                 long flightTicks=speed>0?(long)Math.ceil(origin.distanceTo(net.minecraft.world.phys.Vec3.atCenterOf(genPos))/speed):Long.MAX_VALUE;
                                 long remaining=CONTAINED_PA_STARTUP_TIMEOUT_TICKS-h.getTick();
                                 long hardCap=level.getGameTime()+Math.max(0,remaining);
                                 long oneFinalWaveDeadline=schedule.nextWaveTick()+flightTicks+2;
                                 if(flightTicks<Long.MAX_VALUE&&oneFinalWaveDeadline<=hardCap) {
                                     finalWaveDeadline[0]=oneFinalWaveDeadline;
                                     System.out.println("PA_STARTUP_ONE_FINAL_WAVE_ALLOWANCE t="+level.getGameTime()+" deadline="+deadline
                                             +" nextWave="+schedule.nextWaveTick()+" flightTicks="+flightTicks+" cap="+hardCap
                                             +" particles="+f.particles().size()+" startup="+startupComplete+" power="+mv+"/"+apcMeter);
                                 }
                             }
                         }
                         if(level.getGameTime()>=finalWaveDeadline[0]) {
                             var gp=com.juicyslew.moonstation14.ms14.singularity.ModSingularityGenerators.catalog(level).get(gen.profileId());
                             var volumes=FieldContainmentRuntime.closedVolumes(level).stream().filter(v->ClosedFieldVolume.containsWithinFaceDistance(v,new net.minecraft.world.phys.Vec3(genPos.getX()+.5,genPos.getY()+.5,genPos.getZ()+.5),0)).toList();
                              var control=f.control(); var power=f.power();
                              throw new GameTestAssertException("PA startup timeout gen="+genPos+" spawn="+(gp==null?"missing":gp.spawnProfile())+" valid="+(gp!=null&&ModSingularityGameplay.catalog(level).get(gp.spawnProfile())!=null)+" charge="+gen.startupCharge()+" inert="+gen.startupInert()+" particles="+f.particles().size()+" singularities="+spawned.size()+" loaded="+level.hasChunkAt(genPos)+" corners="+corners.stream().map(be->be.getBlockPos()+":"+be.connectionEligible()).toList()+" volumes="+volumes+" fore="+foreStates
                                      +" controlEnabled="+control.enabled()+" firingEligible="+control.firingEligible()+" apcReady="+control.apcPowerReady()+" schedule="+control.waveScheduleSnapshot()
                                      +" mvDemand="+power.mvPowerDemandWatts()+" mvMeter="+PowerRuntime.mvPowerMeter(level,power)+" apcMeter="+PowerRuntime.apcPowerMeter(level,control)+" solve="+PowerRuntime.lastSolveSummary(level));
                          }
                        BoundedGameTestWait.safeDelay(h,1,poll[0]);
                    }; h.runAfterDelay(1,poll[0]);
                });
                });
    }


    private static void awaitRestoredEpoch(GameTestHelper h,ServerLevel level,PoweredAssembly f,long restoredAt,
                                           int baseline,long interval,int waited,long firstObservedEpoch) {
        var schedule=f.control().waveScheduleSnapshot();
        if(schedule.eligibleSinceTick()>=restoredAt) {
            long firstEpoch=firstObservedEpoch<0?schedule.eligibleSinceTick():firstObservedEpoch;
            long observedEpoch=schedule.eligibleSinceTick();
            BoundedGameTestWait.await(h,(int)interval+PowerRuntime.TICKS_PER_SOLVE*4,
                    ()->restoredEpochDiagnostics(level,f,restoredAt,firstEpoch)+" particles="+f.particles().size(),
                    ()->f.particles().size()>baseline,()->{
                 long tick=f.joinTicks().get(baseline);
                 long actualJoinEpoch=f.joinEpochs().get(baseline);
                 require(actualJoinEpoch>=restoredAt&&actualJoinEpoch>=firstEpoch,
                         "restored wave epoch must follow restored MV power and first observed eligible epoch; restoredAt="+restoredAt
                                 +" firstObservedEpoch="+firstEpoch+" actualJoinEpoch="+actualJoinEpoch+" joinTick="+tick
                                 +" currentEpoch="+f.control().waveScheduleSnapshot().eligibleSinceTick()+" schedule="+f.control().waveScheduleSnapshot()
                                 +" meters="+PowerRuntime.mvPowerMeter(level,f.power())+"/"+PowerRuntime.apcPowerMeter(level,f.control()));
                 require(tick-actualJoinEpoch==interval,"first restored wave exactly one resolved interval after actual authoritative epoch; restoredAt="+restoredAt
                         +" firstObservedEpoch="+firstEpoch+" observedEpoch="+observedEpoch+" actualJoinEpoch="+actualJoinEpoch+" joinTick="+tick
                         +" currentSchedule="+f.control().waveScheduleSnapshot()+" meters="+PowerRuntime.mvPowerMeter(level,f.power())
                         +"/"+PowerRuntime.apcPowerMeter(level,f.control()));
                 require(f.particles().size()>=baseline+3,"restored wave contains three particles; "+restoredEpochDiagnostics(level,f,restoredAt,firstEpoch));
                  for(int i=0;i<3;i++) { require(f.joinTicks().get(baseline+i)==tick,"restored wave joins on one tick; actualJoinEpoch="+actualJoinEpoch+" joinTick="+tick);
                      require(f.joinEpochs().get(baseline+i)==actualJoinEpoch,"restored wave particles share actual phase; firstObservedEpoch="+firstEpoch
                              +" actualJoinEpoch="+actualJoinEpoch+" particleEpoch="+f.joinEpochs().get(baseline+i)+" joinTick="+tick); }
                 h.succeed();
             });
            return;
        }
        if(waited>=PowerRuntime.TICKS_PER_SOLVE*4) throw new GameTestAssertException("restored scheduler epoch not ready: "+schedule);
        long first=firstObservedEpoch<0&&schedule.eligibleSinceTick()>=restoredAt?schedule.eligibleSinceTick():firstObservedEpoch;
        BoundedGameTestWait.safeDelay(h,1,()->awaitRestoredEpoch(h,level,f,restoredAt,baseline,interval,waited+1,first));
    }

    private static String restoredEpochDiagnostics(ServerLevel level,PoweredAssembly f,long restoredAt,long firstObservedEpoch) {
        return "restoredAt="+restoredAt+" firstObservedEpoch="+firstObservedEpoch+" schedule="+f.control().waveScheduleSnapshot()
                +" mv="+PowerRuntime.mvPowerMeter(level,f.power())+" apc="+PowerRuntime.apcPowerMeter(level,f.control())
                +" mvDemand="+f.power().mvPowerDemandWatts()+" eligible="+f.control().firingEligible();
    }

    private static CableFaceNode node(BlockPos pos, Direction face, CableTier tier) {
        return new CableFaceNode(pos, face, tier);
    }

    private record PoweredAssembly(ParticleAcceleratorPartBlockEntity control, ParticleAcceleratorPartBlockEntity power,
                                   PAAssemblyFixture.Assembly assembly, BlockPos apcPos, CableFaceNode[] ports,
                                   List<ParticleAcceleratorParticleEntity> particles, List<Long> joinTicks,
                                    List<net.minecraft.world.phys.Vec3> velocities, List<Long> joinEpochs, Set<UUID> expectedSourceIDs,
                                   java.util.function.Supplier<String> readinessDiagnostics) { }

    private static PoweredAssembly poweredAssembly(GameTestHelper h, ServerLevel level) {
        return poweredAssembly(h,level,0);
    }

    private static PoweredAssembly poweredAssembly(GameTestHelper h, ServerLevel level, int offsetX) {
        List<Entity> owned=new ArrayList<>();
        var particles=new ArrayList<ParticleAcceleratorParticleEntity>();
        var joinTicks=new ArrayList<Long>();
        var velocities=new ArrayList<net.minecraft.world.phys.Vec3>();
        var joinEpochs=new ArrayList<Long>();
        Set<UUID> expected=new HashSet<>();
        ParticleAcceleratorPartBlockEntity[] observedControl={null};
        Consumer<EntityJoinLevelEvent> observer=event->{
            if(event.getLevel()!=level || !(event.getEntity() instanceof ParticleAcceleratorParticleEntity particle)) return;
            if(particles.stream().anyMatch(p->p.getUUID().equals(particle.getUUID()))) return;
            if(expected.contains(particle.sourceEmitterIdentity())) { particles.add(particle); owned.add(particle); joinTicks.add(level.getGameTime()); velocities.add(particle.getDeltaMovement()); joinEpochs.add(observedControl[0]==null?-1L:observedControl[0].waveScheduleSnapshot().eligibleSinceTick()); }
        };
        NeoForge.EVENT_BUS.addListener(observer);
        h.testInfo.addListener(new GameTestListener() {
            private void cleanup(){ NeoForge.EVENT_BUS.unregister(observer); for(Entity entity:owned) entity.discard(); }
            @Override public void testStructureLoaded(GameTestInfo info) { }
            @Override public void testPassed(GameTestInfo info, GameTestRunner runner) { cleanup(); }
            @Override public void testFailed(GameTestInfo info, GameTestRunner runner) { cleanup(); }
            @Override public void testAddedForRerun(GameTestInfo oldTest, GameTestInfo newTest, GameTestRunner runner) { cleanup(); }
        });
        BlockPos controlPos=at(h,offsetX,9,3,8), apc=at(h,offsetX,3,1,6);
        var assembly=PAAssemblyFixture.place(level,controlPos,Rotation.NONE);
        var control=assembly.control();
        observedControl[0]=control;
        var power=assembly.parts().get("power");
        require(power!=null,"resolved assembly includes registered MV power box");
        BlockPos source=at(h,offsetX,3,1,2), sub=at(h,offsetX,3,1,4);
        for(int z=2;z<=6;z++) if(level.getBlockState(at(h,offsetX,4,1,z)).isAir()) level.setBlock(at(h,offsetX,4,1,z),Blocks.STONE.defaultBlockState(),3);
        for(int z=4;z<=6;z++) if(level.getBlockState(at(h,offsetX,2,1,z)).isAir()) level.setBlock(at(h,offsetX,2,1,z),Blocks.STONE.defaultBlockState(),3);
        level.setBlock(source,com.juicyslew.moonstation14.block.ModBlocks.HV_SOURCE.get().defaultBlockState().setValue(PowerDeviceBlock.FACING,Direction.EAST),3);
        level.setBlock(sub,com.juicyslew.moonstation14.block.ModBlocks.HV_MV_SUBSTATION.get().defaultBlockState().setValue(PowerDeviceBlock.FACING,Direction.EAST),3);
        require(PowerDeviceFixturePlacement.placeApc(level,apc,Direction.WEST),"place supported registered APC");
        for(int z=2;z<=4;z++) cable(level,at(h,offsetX,4,1,z),Direction.WEST,CableTier.HV);
        for(int z=4;z<=6;z++) cable(level,at(h,offsetX,2,1,z),Direction.EAST,CableTier.MV);
        cable(level,at(h,offsetX,4,1,6),Direction.WEST,CableTier.APC);
        for(int y=2;y<=3;y++) cable(level,at(h,offsetX,2,y,4),Direction.EAST,CableTier.MV);
        for(int z=5;z<=9;z++) cable(level,at(h,offsetX,2,3,z),Direction.EAST,CableTier.MV);
        cable(level,at(h,offsetX,3,3,8),Direction.SOUTH,CableTier.MV);
        for(int x=4;x<=8;x++) cable(level,at(h,offsetX,x,3,8),Direction.SOUTH,CableTier.MV);
        cable(level,power.getBlockPos().north(),Direction.SOUTH,CableTier.MV);
        for(int y=2;y<=3;y++) cable(level,at(h,offsetX,4,y,6),Direction.WEST,CableTier.APC);
        cable(level,at(h,offsetX,4,3,7),Direction.WEST,CableTier.APC); cable(level,at(h,offsetX,4,3,8),Direction.WEST,CableTier.APC);
        cable(level,at(h,offsetX,3,3,7),Direction.SOUTH,CableTier.APC);
        for(int x=4;x<=9;x++) cable(level,at(h,offsetX,x,3,7),Direction.SOUTH,CableTier.APC);
        cable(level,controlPos.north(),Direction.SOUTH,CableTier.APC);
        var ports=new CableFaceNode[]{node(at(h,offsetX,4,1,2),Direction.WEST,CableTier.HV),node(at(h,offsetX,4,1,4),Direction.WEST,CableTier.HV),node(at(h,offsetX,2,1,4),Direction.EAST,CableTier.MV),node(power.getBlockPos().north(),Direction.SOUTH,CableTier.MV),node(at(h,offsetX,4,1,6),Direction.WEST,CableTier.APC),node(controlPos.north(),Direction.SOUTH,CableTier.APC)};
        assertCablePath(level,List.of(node(at(h,offsetX,4,1,6),Direction.WEST,CableTier.APC),node(at(h,offsetX,4,2,6),Direction.WEST,CableTier.APC),node(at(h,offsetX,4,3,6),Direction.WEST,CableTier.APC),node(at(h,offsetX,4,3,7),Direction.WEST,CableTier.APC),node(at(h,offsetX,4,3,8),Direction.WEST,CableTier.APC),node(at(h,offsetX,3,3,7),Direction.SOUTH,CableTier.APC),node(at(h,offsetX,4,3,7),Direction.SOUTH,CableTier.APC),node(at(h,offsetX,5,3,7),Direction.SOUTH,CableTier.APC),node(at(h,offsetX,6,3,7),Direction.SOUTH,CableTier.APC),node(at(h,offsetX,7,3,7),Direction.SOUTH,CableTier.APC),node(at(h,offsetX,8,3,7),Direction.SOUTH,CableTier.APC),node(at(h,offsetX,9,3,7),Direction.SOUTH,CableTier.APC)),"APC");
        assertCablePath(level,List.of(node(at(h,offsetX,2,1,4),Direction.EAST,CableTier.MV),node(at(h,offsetX,2,2,4),Direction.EAST,CableTier.MV),node(at(h,offsetX,2,3,4),Direction.EAST,CableTier.MV),node(at(h,offsetX,2,3,5),Direction.EAST,CableTier.MV),node(at(h,offsetX,2,3,6),Direction.EAST,CableTier.MV),node(at(h,offsetX,2,3,7),Direction.EAST,CableTier.MV),node(at(h,offsetX,2,3,8),Direction.EAST,CableTier.MV),node(at(h,offsetX,2,3,9),Direction.EAST,CableTier.MV),node(at(h,offsetX,3,3,8),Direction.SOUTH,CableTier.MV),node(at(h,offsetX,4,3,8),Direction.SOUTH,CableTier.MV),node(at(h,offsetX,5,3,8),Direction.SOUTH,CableTier.MV),node(at(h,offsetX,6,3,8),Direction.SOUTH,CableTier.MV),node(at(h,offsetX,7,3,8),Direction.SOUTH,CableTier.MV),node(at(h,offsetX,8,3,8),Direction.SOUTH,CableTier.MV)),"MV");
        require(PowerTopology.deviceAdjacent(control.powerRuntimePorts().stream().filter(port->port.tier()==CableTier.APC).findFirst().orElse(null),node(controlPos.north(),Direction.SOUTH,CableTier.APC)),"APC terminal contacts controller port");
        require(PowerTopology.deviceAdjacent(power.powerRuntimePorts().stream().filter(port->port.tier()==CableTier.MV).findFirst().orElse(null),node(power.getBlockPos().north(),Direction.SOUTH,CableTier.MV)),"MV terminal contacts power box port");
        expected.addAll(Set.of(assembly.parts().get("port").placementIdentity(),assembly.parts().get("fore").placementIdentity(),assembly.parts().get("starboard").placementIdentity()));
        return new PoweredAssembly(control,power,assembly,apc,ports,particles,joinTicks,velocities,joinEpochs,expected,()->powerDiagnostics(level,control,power,apc,ports));
    }

    private static void assertCablePath(ServerLevel level, List<CableFaceNode> path, String name) {
        for(int i=0;i<path.size();i++) {
            CableFaceNode current=path.get(i);
            require(CableStorage.has(level,current.host(),current.face(),current.tier()),name+" cable missing at "+current);
            if(i>0) require(PowerTopology.cableEdge(path.get(i-1),current).isPresent(),
                    name+" route edge missing: "+path.get(i-1)+" -> "+current);
        }
    }

    private static String powerDiagnostics(ServerLevel level, ParticleAcceleratorPartBlockEntity control,
                                           ParticleAcceleratorPartBlockEntity power, BlockPos apcPos,
                                           CableFaceNode[] ports) {
        var apc = level.getBlockEntity(apcPos) instanceof PowerDeviceBlockEntity device ? device : null;
        return "indexedDeviceCount="+PowerRuntime.indexedDeviceCount(level)
                +", topologyReady="+com.juicyslew.moonstation14.ms14.power.graph.PowerGraphService.topologyReady(level)
                +", ports="+Arrays.toString(ports)
                +", controlIdentity="+control.apcPowerIdentity()+", controlDemand="+control.apcPowerDemandWatts()
                +", controlApcMeter="+PowerRuntime.apcPowerMeter(level,control)
                +", powerPlacement="+power.placementIdentity()+", mvDemand="+power.mvPowerDemandWatts()
                +", mvMeter="+PowerRuntime.mvPowerMeter(level,power)
                +", apc="+(apc==null?"missing":("breakerClosed="+apc.breakerClosed()
                +", chargeJ="+apc.energyJoules()+", output="+PowerRuntime.apcOutputObservation(level,apc)))
                +", solve="+PowerRuntime.lastSolveSummary(level);
    }
}
