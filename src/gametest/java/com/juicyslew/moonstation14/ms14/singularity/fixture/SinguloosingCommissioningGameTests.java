package com.juicyslew.moonstation14.ms14.singularity.fixture;

import com.juicyslew.moonstation14.block.block_entity.ParticleAcceleratorPartBlockEntity;
import com.juicyslew.moonstation14.block.block_entity.SingularityBeaconBlockEntity;
import com.juicyslew.moonstation14.entities.SingularityEntity;
import com.juicyslew.moonstation14.entities.ParticleAcceleratorParticleEntity;
import com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait;
import com.juicyslew.moonstation14.gametest.support.FixtureChunkTickets;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.ms14.power.cable.CableStorage;
import com.juicyslew.moonstation14.ms14.singularity.containment.FieldContainmentRuntime;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.common.NeoForge;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.ArrayDeque;

/** End-to-end commissioning with natural startup and ordinary PA particle feeding only. */
@GameTestTags({"singuloosing", "singularity-fixture", "singularity-prototype"})
@PrefixGameTestTemplate(false)
public final class SinguloosingCommissioningGameTests {
    private SinguloosingCommissioningGameTests() { }
    private static void check(boolean ok,String why) { if(!ok)throw new GameTestAssertException(why); }

    @GameTest(templateNamespace="moonstation14",template="singularity_fixture_clear",batch="singuloosing_commissioning",timeoutTicks=12000)
    public static void realStartupFeedsStageThreeThenKeepsFeedingUntilCoordinateFreeBreach(GameTestHelper h) {
        ServerLevel level=(ServerLevel)h.getLevel(); BlockPos origin=h.absolutePos(new BlockPos(3,120,3));
        var lease=FixtureChunkTickets.acquire(h,new net.minecraft.world.phys.AABB(origin.getX()-1,origin.getY(),origin.getZ()-1,
                origin.getX()+40,origin.getY()+12,origin.getZ()+20));
        var ownedEntities=new ArrayList<java.util.UUID>();
        var ownedSource=new SingularityEntity[1];
        var expectedPaEmitterIds=new HashSet<java.util.UUID>();
        var waveEvents=new ArrayList<WaveEvent>();
        var recentWaves=new ArrayDeque<WaveEvent>();
        var journey=new Journey();
        var observedParticles=new ArrayList<String>();
        var retainedParticles=new ArrayDeque<ParticleAcceleratorParticleEntity>();
        var launchTicks=new java.util.HashMap<java.util.UUID,Long>();
        var sourceLifecycle=new SourceLifecycle();
        var particleCount=new int[]{0};
        var lastWaveTick=new long[]{Long.MIN_VALUE};
        var sourceAtJoin=new SingularityEntity[1];
        var waveGapTicks=new long[]{120};
        var foodJumpTicks=new ArrayList<Long>();
        var previousEnergy=new double[]{Double.NaN};
        var previousEnergyTick=new long[]{Long.MIN_VALUE};
        var readyAtTick=new long[]{Long.MAX_VALUE};
        java.util.function.Consumer<EntityJoinLevelEvent> observer=event->{
            if(event.getLevel()!=level||!(event.getEntity() instanceof ParticleAcceleratorParticleEntity particle)
                    ||!expectedPaEmitterIds.contains(particle.sourceEmitterIdentity()))return;
            particleCount[0]++;
            particle.enableCommissioningDiagnostics();
            if(retainedParticles.size()==36)launchTicks.remove(retainedParticles.removeFirst().getUUID());
            retainedParticles.addLast(particle);
            launchTicks.put(particle.getUUID(),level.getGameTime());
            if(observedParticles.size()<8)observedParticles.add("t"+level.getGameTime()+"/"+particle.getUUID()+" emitter="+particle.sourceEmitterIdentity()+" origin="+particle.position()+" direction="+particle.getDeltaMovement());
            long now=level.getGameTime();
            if(lastWaveTick[0]==Long.MIN_VALUE||now-lastWaveTick[0]>=waveGapTicks[0]-2) {
                lastWaveTick[0]=now;
                var source=sourceAtJoin[0];
                var eventRecord=new WaveEvent(now,source==null?"unknown-before-observed-source":String.format(java.util.Locale.ROOT,"%.2f",source.energy()));
                waveEvents.add(eventRecord);
                if(recentWaves.size()==12)recentWaves.removeFirst();
                recentWaves.addLast(eventRecord);
            }
        };
        NeoForge.EVENT_BUS.addListener(observer);
        var builtRecord=new SingularityFixtureService.BuildRecord[1];
        h.testInfo.addListener(new GameTestListener(){
            private void clean(){var r=builtRecord[0];var wing=SingularityFixtureService.terrainWing(level,origin);
                NeoForge.EVENT_BUS.unregister(observer);
                var fresh=SingularityFixtureService.readinessSnapshot(level,origin);
                if(fresh!=null&&fresh.singularityId()!=null&&!ownedEntities.contains(fresh.singularityId()))ownedEntities.add(fresh.singularityId());
                if(ownedSource[0]!=null&&!ownedEntities.contains(ownedSource[0].getUUID()))ownedEntities.add(ownedSource[0].getUUID());
                if(r!=null){for(var c:r.bootstrap().nodes())CableStorage.remove(level,c.host(),c.face(),c.tier());for(var c:r.export().nodes())CableStorage.remove(level,c.host(),c.face(),c.tier());
                    var positions=new java.util.HashSet<BlockPos>(r.placed().occupiedPositions());positions.addAll(r.bootstrap().devices());positions.addAll(r.export().devices());positions.addAll(r.kit().accessBlocks());
                    r.bootstrap().nodes().forEach(c->positions.add(c.host()));r.export().nodes().forEach(c->positions.add(c.host()));
                    if(wing!=null){positions.addAll(wing.owned());for(var c:wing.beaconNodes())CableStorage.remove(level,c.host(),c.face(),c.tier());}
                    SingularityFixtureService.forget(level,origin);positions.forEach(p->level.setBlock(p,Blocks.AIR.defaultBlockState(),3));
                } else SingularityFixtureService.forget(level,origin);
                ownedEntities.forEach(id->{var e=level.getEntity(id);if(e!=null)e.discard();});}
            public void testStructureLoaded(GameTestInfo i){} public void testPassed(GameTestInfo i,GameTestRunner r){clean();}
            public void testFailed(GameTestInfo i,GameTestRunner r){clean();} public void testAddedForRerun(GameTestInfo a,GameTestInfo b,GameTestRunner r){clean();}
        });
        FixtureChunkTickets.awaitEntityTicking(h,lease,80,()->{
             var built=SingularityFixtureService.buildSinguloosing(level,origin);
             if(built.record()!=null) builtRecord[0]=built.record();
             check(built.successful(),"singuloosing fixture built and actual coordinator starts: "+built.error());
              var record=built.record(); var layout=record.placed(); var wing=SingularityFixtureService.terrainWing(level,origin);
             var missingPowerRecords=java.util.stream.Stream.of(record.bootstrap().nodes(),record.export().nodes(),
                             wing==null?java.util.List.<com.juicyslew.moonstation14.ms14.power.topology.CableFaceNode>of():wing.beaconNodes())
                     .flatMap(java.util.Collection::stream)
                     .filter(node->!CableStorage.has(level,node.host(),node.face(),node.tier()))
                     .map(node->node+" hostBlock="+net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(level.getBlockState(node.host()).getBlock()))
                     .toList();
             check(missingPowerRecords.isEmpty(),"all planned bootstrap/export/beacon cable records survive the complete fixture+kit+wing build; missing="
                     +missingPowerRecords+"; graph="+com.juicyslew.moonstation14.ms14.power.graph.PowerGraphService.diagnosticSnapshot(level));
              journey.center=fieldCenter(layout.fieldGenerators());
             check(wing!=null&&layout.enclosure()==SingularityFixtureLayout.Enclosure.SINGULOOSING,"registered running preset has owned breach wing and low-height enclosure");
              check(layout.fieldGenerators().stream().mapToInt(BlockPos::getY).min().orElseThrow()==origin.getY()+4
                              &&layout.fieldGenerators().stream().mapToInt(BlockPos::getY).max().orElseThrow()==origin.getY()+6
                             &&layout.fieldGenerators().stream().mapToInt(BlockPos::getZ).min().orElseThrow()==origin.getZ()+8
                             &&layout.fieldGenerators().stream().mapToInt(BlockPos::getZ).max().orElseThrow()==origin.getZ()+11,
                     "singuloosing field topology encloses the authored 2-high by 3-deep beam cross-section");
              check(wing!=null&&wing.breachCorner().equals(origin.offset(9,4,8))
                             &&layout.fieldGenerators().contains(wing.breachCorner()),
                     "owned breach corner follows the narrowed lower field face");
             ownedEntities.addAll(record.kit().provisionedEntities());
             var paControl=(com.juicyslew.moonstation14.block.block_entity.ParticleAcceleratorPartBlockEntity)level.getBlockEntity(layout.acceleratorControl());
             var clearanceProfile=com.juicyslew.moonstation14.ms14.singularity.pa.ModParticleAccelerators.catalog(level).get(paControl.profileId());
             var projectile=com.juicyslew.moonstation14.ms14.singularity.pa.ModParticleAcceleratorParticles.catalog(level).get(clearanceProfile.particleProfile());
             int beamCount=0;
             for(BlockPos sourcePos:layout.acceleratorParts()) if(level.getBlockEntity(sourcePos) instanceof com.juicyslew.moonstation14.block.block_entity.ParticleAcceleratorPartBlockEntity source
                     &&java.util.List.of("port","fore","starboard").contains(source.role())) {
                 beamCount++;
                 var direction=source.orientation().rotate(net.minecraft.core.Direction.SOUTH);
                 var from=com.juicyslew.moonstation14.block.block_entity.ParticleAcceleratorPartBlockEntity.emissionOrigin(sourcePos,direction);
                 var swept=new net.minecraft.world.phys.AABB(from,net.minecraft.world.phys.Vec3.atCenterOf(layout.startupGenerator())).inflate(projectile.halfExtent());
                 check(record.kit().accessBlocks().stream().noneMatch(p->swept.intersects(new net.minecraft.world.phys.AABB(p))),
                         "operator-kit blocks clear resolved PA beam from "+source.role()+" swept="+swept);
             }
             check(beamCount==3,"clearance is verified for all three resolved PA emitters");
             for(BlockPos p:record.kit().accessBlocks()) if(level.getBlockState(p).is(Blocks.STONE_STAIRS)) {
                var standingBody=new net.minecraft.world.phys.AABB(p.getX()+.18,p.getY()+1.001,p.getZ()+.18,p.getX()+.82,p.getY()+2.801,p.getZ()+.82);
                check(level.noCollision(null,standingBody),"standing above authored stair's actual top has two-block headroom at "+p
                        +" body="+standingBody+" blocks="+blockingBlocks(level,standingBody));
            }
             for(BlockPos p:layout.acceleratorParts()) if(level.getBlockEntity(p) instanceof com.juicyslew.moonstation14.block.block_entity.ParticleAcceleratorPartBlockEntity source
                     &&java.util.List.of("port","fore","starboard").contains(source.role())) {
                 check(source.completed()&&source.anchored(),"expected PA emitter is completed and anchored: "+source.role());
                 check(expectedPaEmitterIds.add(source.placementIdentity()),"PA emitter placement identities are distinct");
             }
             check(expectedPaEmitterIds.size()==3,"observer is keyed to the three real PA source parts");
            var jobStart=level.getGameTime();
              BoundedGameTestWait.await(h,9000,()->failureReport(level,origin,journey,particleCount[0],observedParticles,recentWaves,retainedParticles,launchTicks,builtRecord[0],sourceLifecycle,lease),
                     ()->{var state=SingularityFixtureService.readinessSnapshot(level,origin);
                          if(state!=null&&state.singularityId()!=null){ownedEntities.add(state.singularityId());
                              if(level.getEntity(state.singularityId()) instanceof SingularityEntity s){ownedSource[0]=s;sourceAtJoin[0]=s;sourceLifecycle.observe(level.getGameTime(),state.singularityId(),s);journey.observe(level.getGameTime(),s);}}
                         if(ownedSource[0]!=null&&ownedSource[0].isRemoved())sourceLifecycle.observe(level.getGameTime(),sourceLifecycle.identity,ownedSource[0]);
                         return state!=null&&state.jobState()==SingularityFixtureService.JobState.READY;},
                    ()->{
                        var snap=SingularityFixtureService.readinessSnapshot(level,origin);
                        check(snap.singularityLevel()>=3,"ready source resolved at stage three or higher");
                        check(level.getBlockState(layout.startupGenerator()).isAir(),"real owned startup generator was consumed, not mocked");
                        check(snap.apcKnown()&&snap.outputWatts()>=5100&&snap.lampLit(),"ordinary finite collector power serves resolved 100W lamp and 5kW beacon demand");
                        check(snap.fuelMoles()<snap.fuelBaseline(),"canonical finite collector plasma has actually declined");
                         var ownFields=new HashSet<>(layout.fieldGenerators());
                         var ownVolume=FieldContainmentRuntime.closedVolumes(level).stream().filter(v->
                                 v.min().equals(new BlockPos(layout.fieldGenerators().stream().mapToInt(BlockPos::getX).min().orElseThrow(),layout.fieldGenerators().stream().mapToInt(BlockPos::getY).min().orElseThrow(),layout.fieldGenerators().stream().mapToInt(BlockPos::getZ).min().orElseThrow()))
                                         &&v.max().equals(new BlockPos(layout.fieldGenerators().stream().mapToInt(BlockPos::getX).max().orElseThrow(),layout.fieldGenerators().stream().mapToInt(BlockPos::getY).max().orElseThrow(),layout.fieldGenerators().stream().mapToInt(BlockPos::getZ).max().orElseThrow()))).findFirst();
                         check(ownVolume.isPresent(),"the fixture's own six real field faces remain closed at READY");
                        check(level.getBlockEntity(wing.beacon()) instanceof SingularityBeaconBlockEntity b
                                &&b.placementIdentity().equals(wing.beaconIdentity())&&b.fullyPowered(),"registered owned beacon is fully powered");
                        check(level.getBlockEntity(layout.acceleratorControl()) instanceof ParticleAcceleratorPartBlockEntity pa
                                &&pa.strength()==com.juicyslew.moonstation14.ms14.singularity.pa.ParticleAcceleratorStrength.LEVEL_2,
                                "commissioning leaves the normal stage-two accelerator running after READY");
                        check(level.getEntity(snap.singularityId()) instanceof SingularityEntity,"exact accepted startup source remains alive");
                         double readyEnergy=snap.singularityEnergy();
                         readyAtTick[0]=level.getGameTime();
                         var paProfile=com.juicyslew.moonstation14.ms14.singularity.pa.ModParticleAccelerators.catalog(level).get(((ParticleAcceleratorPartBlockEntity)level.getBlockEntity(layout.acceleratorControl())).profileId());
                         long requiredWaveGap=(long)(paProfile.waveSeconds()*20);
                         waveGapTicks[0]=requiredWaveGap;
                         previousEnergy[0]=readyEnergy; previousEnergyTick[0]=level.getGameTime();
                          BoundedGameTestWait.await(h,500,()->"two independent normal PA waves with credited post-READY food were not observed; events="+waveEvents+" readyEnergy="+readyEnergy
                                          +" currentEnergy="+(level.getEntity(snap.singularityId()) instanceof SingularityEntity live?live.energy():"source-missing")
                                          +" foodJumpTicks="+foodJumpTicks+" waveCount="+waveEvents.stream().filter(w->w.tick()>readyAtTick[0]).count()
                                          +" controller="+level.getBlockEntity(layout.acceleratorControl()),
                                 ()->{
                              var live=level.getEntity(snap.singularityId()); if(!(live instanceof SingularityEntity)){sourceLifecycle.observe(level.getGameTime(),snap.singularityId(),ownedSource[0]);throw new GameTestAssertException("startup source died before two post-READY PA waves");}
                             var fresh=SingularityFixtureService.readinessSnapshot(level,origin);
                             if(fresh==null||fresh.jobState()!=SingularityFixtureService.JobState.READY)return false;
                              long now=level.getGameTime(); double energy=((SingularityEntity)live).energy();
                              sourceLifecycle.observe(now,snap.singularityId(),(SingularityEntity)live);
                             journey.observe(now,(SingularityEntity)live);
                            if(energy-previousEnergy[0]>15&&now>previousEnergyTick[0]) {
                                if(foodJumpTicks.isEmpty()||now-foodJumpTicks.get(foodJumpTicks.size()-1)>=requiredWaveGap-30)foodJumpTicks.add(now);
                            }
                            previousEnergy[0]=energy; previousEnergyTick[0]=now;
                            long postReadyWaves=waveEvents.stream().filter(w->w.tick()>readyAtTick[0]).count();
                            return postReadyWaves>=2&&foodJumpTicks.size()>=2&&((SingularityEntity)live).energy()>readyEnergy+20;
                         },()->{
                            var source=(SingularityEntity)level.getEntity(snap.singularityId());
                            var console=level.getServer().createCommandSourceStack().withLevel(level).withPermission(2)
                                    .withPosition(Vec3.atCenterOf(origin.offset(20,1,10)));
                            try { check(level.getServer().getCommands().getDispatcher().execute("ms14 singularity_fixture breach",console)==1,
                                    "coordinate-free breach command selects registered fixture corner"); }
                            catch(com.mojang.brigadier.exceptions.CommandSyntaxException e){throw new GameTestAssertException("coordinate-free breach syntax: "+e.getMessage());}
                            check(level.getBlockState(wing.breachCorner()).isAir(),"recommended registered low east corner was removed");
                            check(layout.fieldGenerators().stream().filter(p->!p.equals(wing.breachCorner())).allMatch(p->level.getBlockEntity(p)!=null),"breach preserves the other owned field corners");
                            check(level.getGameTime()>jobStart,"startup and feeding advanced through native server ticks");
                            BoundedGameTestWait.await(h,2200,()->"breached singularity did not naturally wander beyond the authored enclosure alive",
                                    ()->{if(source.isRemoved()||!source.isAlive())throw new GameTestAssertException("source died/was removed; removal is not escape");
                                         int minX=layout.fieldGenerators().stream().mapToInt(BlockPos::getX).min().orElseThrow();
                                         int maxX=layout.fieldGenerators().stream().mapToInt(BlockPos::getX).max().orElseThrow();
                                         int minY=layout.fieldGenerators().stream().mapToInt(BlockPos::getY).min().orElseThrow();
                                         int maxY=layout.fieldGenerators().stream().mapToInt(BlockPos::getY).max().orElseThrow();
                                         int minZ=layout.fieldGenerators().stream().mapToInt(BlockPos::getZ).min().orElseThrow();
                                         int maxZ=layout.fieldGenerators().stream().mapToInt(BlockPos::getZ).max().orElseThrow();
                                         return source.position().x<minX+.5||source.position().x>maxX+.5
                                             ||source.position().y<minY+.5||source.position().y>maxY+.5
                                             ||source.position().z<minZ+.5||source.position().z>maxZ+.5;},
                                    ()->{check(!source.isRemoved()&&source.isAlive(),"breach escape remains a living source");h.succeed();});
                        });
                    });
        },origin.offset(-1,0,-1),origin.offset(40,12,20));
    }

    private static String blockingBlocks(ServerLevel level,net.minecraft.world.phys.AABB body) {
        var hits=new ArrayList<String>();
        for(int x=net.minecraft.core.BlockPos.containing(body.minX,body.minY,body.minZ).getX();x<=net.minecraft.core.BlockPos.containing(body.maxX-1e-6,body.maxY-1e-6,body.maxZ-1e-6).getX();x++)
            for(int y=net.minecraft.core.BlockPos.containing(body.minX,body.minY,body.minZ).getY();y<=net.minecraft.core.BlockPos.containing(body.maxX-1e-6,body.maxY-1e-6,body.maxZ-1e-6).getY();y++)
                for(int z=net.minecraft.core.BlockPos.containing(body.minX,body.minY,body.minZ).getZ();z<=net.minecraft.core.BlockPos.containing(body.maxX-1e-6,body.maxY-1e-6,body.maxZ-1e-6).getZ();z++) {
                    var pos=new BlockPos(x,y,z); var state=level.getBlockState(pos);
                    if(!state.getCollisionShape(level,pos).isEmpty()&&net.minecraft.world.phys.shapes.Shapes.joinIsNotEmpty(
                            state.getCollisionShape(level,pos),net.minecraft.world.phys.shapes.Shapes.create(body.move(-x,-y,-z)),net.minecraft.world.phys.shapes.BooleanOp.AND))
                        hits.add(pos+"="+state);
                }
        return hits.toString();
    }
    private static String failureReport(ServerLevel level,BlockPos origin,Journey journey,int particles,
                                         java.util.List<String> particleSamples,java.util.Collection<WaveEvent> waves,
                                         java.util.Collection<ParticleAcceleratorParticleEntity> projectiles,java.util.Map<java.util.UUID,Long> launchTicks,SingularityFixtureService.BuildRecord built,SourceLifecycle lifecycle,com.juicyslew.moonstation14.gametest.support.FixtureChunkTickets.Lease lease) {
        String full=SingularityFixtureService.status(level,origin);
        System.out.println("SINGULOOSING FAILURE FULL STATUS (bounded 12000 chars): "+bounded(full,12000));
        var record=SingularityFixtureService.registered(level,origin);
        var wing=SingularityFixtureService.terrainWing(level,origin);
        String cableRecords=record==null?"fixture record unavailable":java.util.stream.Stream.of(record.bootstrap().nodes(),record.export().nodes(),
                        wing==null?java.util.List.<com.juicyslew.moonstation14.ms14.power.topology.CableFaceNode>of():wing.beaconNodes())
                .flatMap(java.util.Collection::stream)
                .filter(node->!CableStorage.has(level,node.host(),node.face(),node.tier()))
                .map(node->node+" hostBlock="+net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(level.getBlockState(node.host()).getBlock()))
                .toList().toString();
        System.out.println("SINGULOOSING FAILURE CABLE RECORDS missing="+bounded(cableRecords,4000)+" graph="
                +com.juicyslew.moonstation14.ms14.power.graph.PowerGraphService.diagnosticSnapshot(level));
        var snap=SingularityFixtureService.readinessSnapshot(level,origin);
        var exportApcPorts=built==null||built.export()==null?java.util.List.<com.juicyslew.moonstation14.ms14.power.topology.DevicePort>of():built.export().ports().stream().filter(p->p.tier()==com.juicyslew.moonstation14.ms14.power.cable.CableTier.APC&&built.export().devices().contains(p.device())).toList();
        String apcGraph=exportApcPorts.stream().map(p->{var node=new com.juicyslew.moonstation14.ms14.power.topology.CableFaceNode(p.device(),p.face(),p.tier());
            var d=com.juicyslew.moonstation14.ms14.power.graph.PowerGraphService.diagnostics(level,node);
            return node+"="+d+" outsideFixtureLease="+d.unloadedPotentialNeighborChunks().stream().filter(c->!lease.covers(c)).toList();}).toList().toString();
        String powerDiagnostics="exportAPCGraph="+apcGraph+" leaseChunks="+lease.chunkPositions();
        System.out.println("SINGULOOSING FAILURE POWER GRAPH DIAGNOSTICS (bounded 12000 chars): "+bounded(powerDiagnostics,12000));
        String diagnostics="waves="+waves+" sourceLifecycle="+lifecycle.report()+" latestJourney="+(journey.samples.peekLast())
                +" projectileReceipts="+projectiles.stream().map(p->p.getUUID()+"/launch=t"+launchTicks.get(p.getUUID())+" emitter="+p.sourceEmitterIdentity()+"/"+p.commissioningContactReceipt()+"/removed="+p.isRemoved()+" reason="+p.getRemovalReason()+" ticks="+p.tickCount+" final="+p.position()).toList()
                +" actualCurrentEmitterAllocations="+(built==null?"unknown":built.placed().acceleratorParts().stream().map(pos->level.getBlockEntity(pos) instanceof ParticleAcceleratorPartBlockEntity pa?pa.role()+"@"+pos+"="+pa.receivedWatts():"missing@"+pos).toList())
                +" particleCount="+particles+" firstParticleSamples="+particleSamples+" readiness="+(snap==null?"null":snap.jobState()+"/"+snap.singularityId()+"/"+snap.singularityEnergy()+"/L"+snap.singularityLevel());
        System.out.println("SINGULOOSING FAILURE BOUNDED COMMISSIONING DIAGNOSTICS: "+diagnostics);
        String compact="natural startup failed; journeySamples="+journey.samples.size()+" maxD="+journey.maxDistance+" maxE="+journey.maxEnergy
                +" waveCount="+waves.size()+" projectileCount="+particles+" retained="+projectiles.size()+" source="+lifecycle.compact()
                +" readiness="+(snap==null?"null":snap.jobState()+"/"+snap.singularityId()+"/"+snap.singularityEnergy()+"/L"+snap.singularityLevel());
        return compact.length()>820?compact.substring(0,820):compact;
    }
    private static String bounded(String text,int maxChars) {
        if(text.length()<=maxChars)return text;
        return text.substring(0,maxChars)+" ...[TRUNCATED; originalChars="+text.length()+"]";
    }
    private static Vec3 fieldCenter(java.util.List<BlockPos> fields) {
        return new Vec3((fields.stream().mapToInt(BlockPos::getX).min().orElseThrow()+fields.stream().mapToInt(BlockPos::getX).max().orElseThrow())*.5+.5,
                (fields.stream().mapToInt(BlockPos::getY).min().orElseThrow()+fields.stream().mapToInt(BlockPos::getY).max().orElseThrow())*.5+.5,
                (fields.stream().mapToInt(BlockPos::getZ).min().orElseThrow()+fields.stream().mapToInt(BlockPos::getZ).max().orElseThrow())*.5+.5);
    }
    private static final class Journey {
        private final java.util.ArrayDeque<String> samples=new java.util.ArrayDeque<>();
        private net.minecraft.world.phys.Vec3 lastPos;
        private double lastEnergy=Double.NaN;
        private int lastLevel=-1;
        private double maxEnergy=0,maxDistance=0;
        private Vec3 center=Vec3.ZERO;
        void observe(long tick,SingularityEntity source) {
            var pos=source.position(); double energy=source.energy(); int stage=source.singularityLevel();
            double distance=pos.distanceTo(center);
            maxEnergy=Math.max(maxEnergy,energy); maxDistance=Math.max(maxDistance,distance);
            if(stage!=lastLevel||Math.abs(energy-lastEnergy)>15||(lastPos!=null&&pos.distanceTo(lastPos)>.5)) {
                if(samples.size()==16)samples.removeFirst();
                samples.addLast("t"+tick+" L"+stage+" E"+String.format(java.util.Locale.ROOT,"%.1f",energy)+" @"+pos+" d="+String.format(java.util.Locale.ROOT,"%.2f",distance));
            }
            lastLevel=stage;lastEnergy=energy;lastPos=pos;
        }
    }
    private record WaveEvent(long tick,String energyAtLaunch) { }
    private static final class SourceLifecycle {
        private java.util.UUID identity; private String first,last,observedRemoval;
        void observe(long tick,java.util.UUID id,SingularityEntity source) {
            if(identity==null)identity=id;
            if(!identity.equals(id))return;
            String sample="t"+tick+" uuid="+id+" pos="+source.position()+" energy="+source.energy()+" removed="+source.isRemoved()+" reason="+source.getRemovalReason();
            if(first==null)first=sample;
            last=sample;
            if(source.isRemoved()&&observedRemoval==null)observedRemoval="observed-at-poll="+tick+" "+sample;
        }
        String report(){return "first="+first+" last="+last+" firstObservedRemovalAtPoll="+observedRemoval;}
        String compact(){return identity+"/first="+(first!=null)+"/removed="+(observedRemoval!=null)+"/last="+last;}
    }
}
