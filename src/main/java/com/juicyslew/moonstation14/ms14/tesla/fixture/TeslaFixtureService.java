package com.juicyslew.moonstation14.ms14.tesla.fixture;

import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.block.block_entity.PowerDeviceBlockEntity;
import com.juicyslew.moonstation14.block.custom.PowerDeviceBlock;
import com.juicyslew.moonstation14.ms14.power.device.PowerDeviceKind;
import com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime;
import com.juicyslew.moonstation14.ms14.singularity.fixture.CreateSingularityFixtureStartup;
import com.juicyslew.moonstation14.ms14.singularity.fixture.SingularityFixtureService;
import com.juicyslew.moonstation14.ms14.singularity.fixture.SingularityFixturePower;
import com.juicyslew.moonstation14.ms14.tesla.TeslaMachineBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Tesla commissioning layered on the canonical cage/PA/power fixture, without singularity startup. */
@EventBusSubscriber(modid="moonstation14")
public final class TeslaFixtureService {
    private record BeamTest(BlockPos origin, long fireAt, long expireAt, net.minecraft.world.phys.Vec3 from, net.minecraft.world.phys.Vec3 to, com.juicyslew.moonstation14.ms14.tesla.TeslaBallProfile.Contact contact) { }
    private static final Map<ServerLevel, Map<BlockPos, BeamTest>> BEAM_TESTS = new HashMap<>();
    private static final Map<ServerLevel, Map<BlockPos, Job>> JOBS = new HashMap<>();
    private static final Map<ServerLevel, Map<BlockPos, java.util.List<BlockPos>>> COILS = new HashMap<>();
    private static final Map<ServerLevel, Map<BlockPos, java.util.List<BlockPos>>> RODS = new HashMap<>();
    private record PlacementSnapshot(net.minecraft.world.level.block.state.BlockState state,net.minecraft.world.level.block.entity.BlockEntity entity) { }
    private static final class Job {
        final SingularityFixtureService.BuildRecord record;
        final CreateSingularityFixtureStartup startup;
        final com.juicyslew.moonstation14.block.block_entity.SingularityGeneratorBlockEntity generator;
        final UUID generatorIdentity;
        final java.util.List<UUID> coilIdentities;
        final java.util.List<UUID> rodIdentities;
        boolean captured;
        UUID source;
        Job(ServerLevel level, BlockPos origin, SingularityFixtureService.BuildRecord record) {
            this.record=record;
            // The resolved Tesla startup profile retains the native 16-charge threshold.
            // The compact fixture's real LEVEL_0 run produced eight valid charge units before
            // the bounded commissioning wait ended. LEVEL_1 contributes two per ordinary hit;
            // this is a normal PA setting, not a synthetic feed or energy grant.
            startup=new CreateSingularityFixtureStartup(level,record.placed(),false,
                    com.juicyslew.moonstation14.ms14.singularity.pa.ParticleAcceleratorStrength.LEVEL_1);
            generator=startup.startupGenerator();
            generatorIdentity=generator==null?null:generator.placementIdentity();
            coilIdentities=record.placed().collectors().stream().skip(1).map(level::getBlockEntity).filter(TeslaMachineBlockEntity.class::isInstance).map(TeslaMachineBlockEntity.class::cast).map(TeslaMachineBlockEntity::placementIdentity).toList();
            rodIdentities=RODS.getOrDefault(level,Map.of()).getOrDefault(origin,java.util.List.of()).stream().map(level::getBlockEntity).filter(TeslaMachineBlockEntity.class::isInstance).map(TeslaMachineBlockEntity.class::cast).map(TeslaMachineBlockEntity::placementIdentity).toList();
        }
    }
    private TeslaFixtureService() { }

    public static String beamTest(ServerLevel level, BlockPos origin) {
        if (origin == null || !level.getWorldBorder().isWithinBounds(origin) || origin.getY()<level.getMinBuildHeight() || origin.getY()>=level.getMaxBuildHeight()) return "Refused: beam test origin out of bounds.";
        BlockPos end=origin.east(3);
        if (!level.getWorldBorder().isWithinBounds(end) || level.getChunkSource().getChunkNow(origin.getX()>>4,origin.getZ()>>4)==null || level.getChunkSource().getChunkNow(end.getX()>>4,end.getZ()>>4)==null) return "Refused: beam test span is not already loaded.";
        var profile=com.juicyslew.moonstation14.ms14.tesla.ModTeslaProfiles.balls(level).get(com.juicyslew.moonstation14.ms14.tesla.ModTeslaProfiles.MINI_ID);
        if(profile==null) return "Refused: resolved MINI Tesla profile unavailable.";
        var contact=profile.lightning(); var from=new net.minecraft.world.phys.Vec3(origin.getX()+.5,origin.getY()+.5,origin.getZ()+.5); var to=from.add(3,0,0);
        long now=level.getGameTime(); BEAM_TESTS.computeIfAbsent(level,k->new HashMap<>()).put(origin.immutable(),new BeamTest(origin.immutable(),now+100,now+200,from,to,contact));
        return "Controlled targetless beam queued: segment "+from+" -> "+to+", fires in 5 seconds for resolved MINI lifetime "+contact.sourceLifetimeSeconds()+"s. Stand clear until it appears. This tests stationary beam hazard only, not natural MINI targeting/firing.";
    }

    public static String build(ServerLevel level, BlockPos origin) {
        if(JOBS.getOrDefault(level,Map.of()).containsKey(origin)) return "Refused: Tesla fixture already registered at "+origin+".";
        var built=SingularityFixtureService.build(level,origin);
        if(!built.successful()) return built.error();
        var placed=built.record().placed();
        var snapshot=captureBuildSnapshot(level,built.record(),java.util.List.of());
        var occupied=new java.util.HashSet<BlockPos>(placed.occupiedPositions());occupied.addAll(built.record().bootstrap().devices());occupied.addAll(built.record().export().devices());
        built.record().bootstrap().nodes().forEach(n->occupied.add(n.host()));built.record().export().nodes().forEach(n->occupied.add(n.host()));
        var supplementals=selectSupplementalRodPositions(level,origin,placed,occupied);
        if(supplementals.size()!=3) {
            rollbackBuiltFixture(level,origin,built.record(),snapshot);
            return "Refused: three safe supplemental rod positions could not be reserved after the shared build; its owned placements were rolled back.";
        }
        level.setBlock(placed.startupGenerator(),ModBlocks.TESLA_GENERATOR.get().defaultBlockState(),3);
        for(BlockPos pos:placed.collectors()) level.setBlock(pos,Blocks.AIR.defaultBlockState(),3);
        // The existing export has a prepared north-facing HV endpoint at the first collector lane.
        BlockPos[] positions=placed.collectors().toArray(BlockPos[]::new);
        for(int i=0;i<positions.length;i++) level.setBlock(positions[i],i==0?ModBlocks.TESLA_GROUNDING_ROD.get().defaultBlockState():ModBlocks.TESLA_COIL.get().defaultBlockState(),3);
        snapshot=captureBuildSnapshot(level,built.record(),java.util.List.of());
        var allRods=new java.util.ArrayList<BlockPos>();allRods.add(positions[0]);allRods.addAll(supplementals);
        var finalOccupied=new java.util.HashSet<BlockPos>(occupied);
        if(!selectSupplementalRodPositions(level,origin,placed,finalOccupied).equals(supplementals)){
            rollbackBuiltFixture(level,origin,built.record(),snapshot);
            return "Refused: safe supplemental rod positions were unavailable at final placement; owned shared placements were rolled back.";
        }
        for(BlockPos pos:supplementals) level.setBlock(pos,ModBlocks.TESLA_GROUNDING_ROD.get().defaultBlockState(),3);
        if(supplementals.stream().anyMatch(p->!level.getBlockState(p).is(ModBlocks.TESLA_GROUNDING_ROD.get())||!(level.getBlockEntity(p) instanceof TeslaMachineBlockEntity))){
            rollbackBuiltFixture(level,origin,built.record(),captureBuildSnapshot(level,built.record(),supplementals));
            return "Refused: supplemental rod placement did not produce all owned machines; shared fixture placements were rolled back.";
        }
        snapshot=captureBuildSnapshot(level,built.record(),supplementals);
        RODS.computeIfAbsent(level,k->new HashMap<>()).put(origin,java.util.List.copyOf(allRods));
        COILS.computeIfAbsent(level,k->new HashMap<>()).put(origin,java.util.List.of(positions[1],positions[2],positions[3]));
        JOBS.computeIfAbsent(level,k->new HashMap<>()).put(origin,new Job(level,origin,built.record()));
        return "Built Tesla fixture at "+origin+". Shared cage, emitters, PA, bootstrap and export remain real; Tesla coil is on the existing HV export. Start explicitly.";
    }

    public static String start(ServerLevel level,BlockPos origin) {
        Job job=JOBS.getOrDefault(level,Map.of()).get(origin);
        if(job==null)return "Refused: no registered Tesla fixture at "+origin+".";
        if(!owned(level,origin,job))return "Refused: Tesla fixture hardware or shared registration is stale/replaced; no startup changes made.";
        if(job.captured)return "Commissioned Tesla source UUID="+job.source+"; one-shot PA startup already stopped.";
        if(job.startup.phase()==CreateSingularityFixtureStartup.Phase.FAILED)return "Refused: startup failed: "+job.startup.error();
        job.startup.begin();
        return "STARTING: normal shared cage/PA startup; no collector feed or injected power.";
    }
    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        for(ServerLevel server:event.getServer().getAllLevels()) {
            var tests=BEAM_TESTS.get(server);
            if(tests!=null) { long now=server.getGameTime(); tests.values().removeIf(test->{ if(now>=test.expireAt()) return true; if(now>=test.fireAt()) { com.juicyslew.moonstation14.ms14.tesla.TeslaLightningSystem.emitLiveBeam(server,test.from(),test.to(),test.contact(),null); return true; } return false; }); if(tests.isEmpty()) BEAM_TESTS.remove(server); }
            for(var entry:JOBS.getOrDefault(server,Map.of()).entrySet()) {
                Job job=entry.getValue(); if(job.captured)continue;
                UUID generated=job.generator==null?null:job.generator.spawnedSourceIdentity();
                if(generated!=null&&server.getEntity(generated) instanceof com.juicyslew.moonstation14.entities.TeslaEnergyBallEntity)job.source=generated;
                if(!owned(server,entry.getKey(),job))continue;
                job.startup.tick();
                if(job.startup.phase()==CreateSingularityFixtureStartup.Phase.ACCELERATOR_STARTED) {
                    job.source=job.generator.spawnedSourceIdentity();
                    if(job.source!=null && server.getEntity(job.source) instanceof com.juicyslew.moonstation14.entities.TeslaEnergyBallEntity) {
                        var record=SingularityFixtureService.registered(server,entry.getKey());
                        if(record!=null&&server.getBlockEntity(record.placed().acceleratorControl()) instanceof com.juicyslew.moonstation14.block.block_entity.ParticleAcceleratorPartBlockEntity control) control.setEnabled(false);
                        job.captured=true;
                    }
                }
            }
        }
    }
    @SubscribeEvent public static void unload(LevelEvent.Unload event) {
        if(event.getLevel() instanceof ServerLevel level) { JOBS.remove(level); COILS.remove(level); RODS.remove(level); BEAM_TESTS.remove(level); }
    }
    public static BlockPos nearestOrigin(ServerLevel level,BlockPos pos) {
        return JOBS.getOrDefault(level,Map.of()).keySet().stream().min(java.util.Comparator.comparingDouble(p->p.distSqr(pos))).orElse(null);
    }
    /** Current fixture rod positions; returned positions are immutable copies. */
    public static java.util.List<BlockPos> groundingRodPositions(ServerLevel level,BlockPos origin){return java.util.List.copyOf(RODS.getOrDefault(level,Map.of()).getOrDefault(origin,java.util.List.of()));}
    /** Drops only this fixture's runtime ownership; world blocks are deliberately left untouched. */
    public static void forget(ServerLevel level,BlockPos origin) {
        var jobs=JOBS.get(level);if(jobs!=null){jobs.remove(origin);if(jobs.isEmpty())JOBS.remove(level);}
        var coils=COILS.get(level);if(coils!=null){coils.remove(origin);if(coils.isEmpty())COILS.remove(level);}
        var rods=RODS.get(level);if(rods!=null){rods.remove(origin);if(rods.isEmpty())RODS.remove(level);}
    }
    public static String status(ServerLevel level,BlockPos origin) {
        Job job=JOBS.getOrDefault(level,Map.of()).get(origin);
        if(job==null)return "NOT READY/UNKNOWN: no registered Tesla fixture at "+origin+".";
        var src=job.source==null?null:level.getEntity(job.source);
        var placed=job.record.placed();
        var coilPositions=COILS.getOrDefault(level,Map.of()).getOrDefault(origin,java.util.List.of());
        var coils=new java.util.ArrayList<TeslaMachineBlockEntity>();
        for(int i=0;i<coilPositions.size();i++) { var be=level.getBlockEntity(coilPositions.get(i)); if(be instanceof TeslaMachineBlockEntity machine && i<job.coilIdentities.size() && machine.placementIdentity().equals(job.coilIdentities.get(i)) && level.getBlockState(coilPositions.get(i)).is(ModBlocks.TESLA_COIL.get())) coils.add(machine); }
         var rodPositions=RODS.getOrDefault(level,Map.of()).getOrDefault(origin,java.util.List.of());
         var rods=new java.util.ArrayList<TeslaMachineBlockEntity>();
         for(int i=0;i<rodPositions.size();i++){var p=rodPositions.get(i);var be=level.getBlockEntity(p);if(i<job.rodIdentities.size()&&be instanceof TeslaMachineBlockEntity machine&&machine.placementIdentity().equals(job.rodIdentities.get(i))&&level.getBlockState(p).is(ModBlocks.TESLA_GROUNDING_ROD.get()))rods.add(machine);}
        var apc=job.record.export();
        boolean known=false; double watts=0;
        if(apc!=null) for(BlockPos p:apc.devices()) if(level.getBlockEntity(p) instanceof PowerDeviceBlockEntity d&&level.getBlockState(p).getBlock() instanceof PowerDeviceBlock b&&b.kind()==PowerDeviceKind.APC){var obs=PowerRuntime.apcOutputObservation(level,d);known=obs.known();watts=obs.outputWatts();}
         var control=level.getBlockEntity(placed.acceleratorControl()) instanceof com.juicyslew.moonstation14.block.block_entity.ParticleAcceleratorPartBlockEntity pa?pa:null;
         long liveEmitters=placed.emitters().stream().map(level::getBlockEntity).filter(com.juicyslew.moonstation14.block.block_entity.EmitterBlockEntity.class::isInstance).map(com.juicyslew.moonstation14.block.block_entity.EmitterBlockEntity.class::cast).filter(com.juicyslew.moonstation14.block.block_entity.EmitterBlockEntity::firingPowered).count();
         long eligibleFields=placed.fieldGenerators().stream().map(level::getBlockEntity).filter(com.juicyslew.moonstation14.block.block_entity.FieldGeneratorBlockEntity.class::isInstance).map(com.juicyslew.moonstation14.block.block_entity.FieldGeneratorBlockEntity.class::cast).filter(com.juicyslew.moonstation14.block.block_entity.FieldGeneratorBlockEntity::connectionEligible).count();
         String failure=job.startup.error();
          String energy=src instanceof com.juicyslew.moonstation14.entities.TeslaEnergyBallEntity tesla
                   ?tesla.energy()+" input="+tesla.lastEnergyInputKind()+":"+tesla.lastEnergyInputAmount()+"@"+tesla.lastEnergyInputTick()+" paHitsReceived="+tesla.paHitsReceived()+" totalPAEnergyReceived="+tesla.paEnergyReceived()+" lastPaInputTick="+tesla.lastPaInputTick():"UNKNOWN";
           String lightning=src instanceof com.juicyslew.moonstation14.entities.TeslaEnergyBallEntity tesla
                   ?"sourceLightningAttempts="+tesla.lightningShotAttempts()+" sourceLightningBolts="+tesla.lightningBoltsEmitted()+" lastSourceLightningTick="+tesla.lastLightningShotTick():"sourceLightning=UNKNOWN";
           String chase=src instanceof com.juicyslew.moonstation14.entities.TeslaEnergyBallEntity tesla
                   ?" chaseTarget="+(tesla.chaseTargetPosition()==null?"TARGETLESS":"kind="+tesla.chaseTargetKind()+",position="+tesla.chaseTargetPosition()+",distance="+tesla.position().distanceTo(tesla.chaseTargetPosition())):" chaseTarget=UNKNOWN";
          var nearbyMinis=level.getEntitiesOfClass(com.juicyslew.moonstation14.entities.TeslaEnergyBallEntity.class,
                  src instanceof com.juicyslew.moonstation14.entities.TeslaEnergyBallEntity tesla?tesla.getBoundingBox().inflate(25):new net.minecraft.world.phys.AABB(origin).inflate(25),
                  e->e.isAlive()&&e.isMini());
          long minisWithBolts=nearbyMinis.stream().filter(e->e.lightningBoltsEmitted()>0).count();
          long miniBolts=nearbyMinis.stream().mapToLong(com.juicyslew.moonstation14.entities.TeslaEnergyBallEntity::lightningBoltsEmitted).sum();
          var resolvedMain= com.juicyslew.moonstation14.ms14.tesla.ModTeslaProfiles.balls(level).get(com.juicyslew.moonstation14.ms14.tesla.ModTeslaProfiles.MAIN_ID);
          long inRange=src instanceof com.juicyslew.moonstation14.entities.TeslaEnergyBallEntity t&&resolvedMain!=null?rods.stream().filter(r->t.position().distanceTo(net.minecraft.world.phys.Vec3.atCenterOf(r.getBlockPos()))<=resolvedMain.shooter().range()).count():0;
          int minimumCoverage=resolvedMain==null?0:minimumSourceBoundsCoverage(rods,sourceBounds(origin),resolvedMain.shooter().range());
              return "Tesla "+job.startup.phase()+" at "+origin+" source="+(src==null?(job.source==null?"UNKNOWN":"GONE "+job.source):job.source)+" sourcePosition="+(src==null?"UNKNOWN":src.position())+" sourceVelocity="+(src==null?"UNKNOWN":src.getDeltaMovement())+" sourceEnergyInput="+energy+" "+lightning+chase+" nearbyMinis25="+nearbyMinis.size()+" nearbyMinisWithBolts="+minisWithBolts+" nearbyMiniBolts="+miniBolts+" hardware: generator="+(job.generator.isRemoved()?"GONE":"present")+" emitters="+liveEmitters+"/"+placed.emitters().size()+" fields="+eligibleFields+"/"+placed.fieldGenerators().size()+" pa="+(control==null?"UNKNOWN":"enabled="+control.enabled()+",assembly="+control.assemblyValid()+",firing="+control.firingEligible())+" coils="+coils.size()+"/3 chargedJ="+coils.stream().mapToDouble(TeslaMachineBlockEntity::energyJoules).sum()+" rods="+rods.size()+"/4 mainDirectBoltCoverage="+inRange+"/4 mainBoundsMinimumDirectBoltCoverage="+minimumCoverage+"/3 (direct bolts only; not terrain/contact/living-beam protection) exportGraph="+(known?"KNOWN outputW="+watts:"UNKNOWN")+"; failure="+(failure==null?"none":failure);
    }
    private static boolean owned(ServerLevel level,BlockPos origin,Job job) {
        if(SingularityFixtureService.registered(level,origin)!=job.record||job.generator==null
                ||!job.generator.placementIdentity().equals(job.generatorIdentity))return false;
        boolean liveGenerator=level.getBlockEntity(job.record.placed().startupGenerator())==job.generator
                &&level.getBlockState(job.record.placed().startupGenerator()).is(ModBlocks.TESLA_GENERATOR.get());
        UUID spawned=job.generator.spawnedSourceIdentity();
        boolean legallyConsumed=job.generator.isRemoved()&&spawned!=null&&(job.source==null||spawned.equals(job.source))
                &&level.getEntity(spawned) instanceof com.juicyslew.moonstation14.entities.TeslaEnergyBallEntity;
        if(!liveGenerator&&!legallyConsumed)return false;
        var collectorPositions=job.record.placed().collectors();
        if(collectorPositions.size()!=4||job.coilIdentities.size()!=3||job.rodIdentities.size()!=4)return false;
        for(int i=0;i<4;i++) {
            if(!(level.getBlockEntity(collectorPositions.get(i)) instanceof TeslaMachineBlockEntity be))return false;
            if(i==0) { if(!level.getBlockState(collectorPositions.get(i)).is(ModBlocks.TESLA_GROUNDING_ROD.get())||!be.placementIdentity().equals(job.rodIdentities.getFirst()))return false; }
            else if(!level.getBlockState(collectorPositions.get(i)).is(ModBlocks.TESLA_COIL.get())||!be.placementIdentity().equals(job.coilIdentities.get(i-1)))return false;
        }
        var rods=RODS.getOrDefault(level,Map.of()).getOrDefault(origin,java.util.List.of());
        if(rods.size()!=4)return false;
        for(int i=0;i<4;i++){BlockPos p=rods.get(i);if(!(level.getBlockEntity(p) instanceof TeslaMachineBlockEntity be)||!be.placementIdentity().equals(job.rodIdentities.get(i))||!level.getBlockState(p).is(ModBlocks.TESLA_GROUNDING_ROD.get()))return false;}
        return true;
    }
    /** Reuses the commissioning test's bounded, full-source-volume rod placement constraints. */
    private static java.util.List<BlockPos> selectSupplementalRodPositions(ServerLevel level,BlockPos origin,com.juicyslew.moonstation14.ms14.singularity.fixture.SingularityFixtureLayout.Placed layout,java.util.Set<BlockPos> extraOccupied){
        var occupied=new java.util.HashSet<BlockPos>(layout.occupiedPositions());occupied.addAll(extraOccupied);
        var bounds=new net.minecraft.world.phys.AABB(origin.getX()+6.25,origin.getY()+4.25,origin.getZ()+8.25,origin.getX()+8.75,origin.getY()+6.75,origin.getZ()+10.75);
        var main=com.juicyslew.moonstation14.ms14.tesla.ModTeslaProfiles.balls(level).get(com.juicyslew.moonstation14.ms14.tesla.ModTeslaProfiles.MAIN_ID);
        if(main==null)return java.util.List.of();double range=main.shooter().range();
        var candidates=new java.util.ArrayList<BlockPos>();
        for(int x:new int[]{3,11})for(int z=8;z<=11&&candidates.size()<3;z++){BlockPos p=origin.offset(x,4,z);
            boolean cables=java.util.Arrays.stream(net.minecraft.core.Direction.values()).anyMatch(d->com.juicyslew.moonstation14.ms14.power.cable.CableStorage.get(level,p,d)!=null);
            boolean covered=sourceCorners(bounds).stream().allMatch(c->c.distanceTo(net.minecraft.world.phys.Vec3.atCenterOf(p))<=range);
            if(covered&&!occupied.contains(p)&&supplementalCellFree(level,p)&&!cables)candidates.add(p);
        }return java.util.List.copyOf(candidates);
    }
    private static boolean supplementalCellFree(ServerLevel level,BlockPos p){return level.getBlockState(p).isAir()&&level.getBlockEntity(p)==null;}
    private static net.minecraft.world.phys.AABB sourceBounds(BlockPos origin){return new net.minecraft.world.phys.AABB(origin.getX()+6.25,origin.getY()+4.25,origin.getZ()+8.25,origin.getX()+8.75,origin.getY()+6.75,origin.getZ()+10.75);}
    private static java.util.List<net.minecraft.world.phys.Vec3> sourceCorners(net.minecraft.world.phys.AABB b){var out=new java.util.ArrayList<net.minecraft.world.phys.Vec3>(8);for(double x:new double[]{b.minX,b.maxX})for(double y:new double[]{b.minY,b.maxY})for(double z:new double[]{b.minZ,b.maxZ})out.add(new net.minecraft.world.phys.Vec3(x,y,z));return java.util.List.copyOf(out);}
    private static int minimumSourceBoundsCoverage(java.util.List<TeslaMachineBlockEntity> rods,net.minecraft.world.phys.AABB bounds,double range){return sourceCorners(bounds).stream().mapToInt(c->(int)rods.stream().filter(r->c.distanceTo(net.minecraft.world.phys.Vec3.atCenterOf(r.getBlockPos()))<=range).count()).min().orElse(0);}
    private static java.util.Map<BlockPos,PlacementSnapshot> captureBuildSnapshot(ServerLevel level,SingularityFixtureService.BuildRecord r,java.util.List<BlockPos> supplemental){
        var positions=new java.util.HashSet<BlockPos>(r.placed().occupiedPositions());
        for(var c:new SingularityFixturePower.Circuit[]{r.bootstrap(),r.export()})if(c!=null){positions.addAll(c.devices());c.nodes().forEach(n->positions.add(n.host()));}
        positions.addAll(supplemental);var out=new java.util.HashMap<BlockPos,PlacementSnapshot>();for(BlockPos p:positions)out.put(p.immutable(),new PlacementSnapshot(level.getBlockState(p),level.getBlockEntity(p)));return out;
    }
    /** Roll back only blocks still matching the just-built placement snapshot; never clear foreign replacements. */
    private static void rollbackBuiltFixture(ServerLevel level,BlockPos origin,SingularityFixtureService.BuildRecord r,java.util.Map<BlockPos,PlacementSnapshot> snapshot){
        for(var c:new SingularityFixturePower.Circuit[]{r.bootstrap(),r.export()})if(c!=null)for(var node:c.nodes()){
            PlacementSnapshot host=snapshot.get(node.host());if(host!=null&&matches(level,node.host(),host)&&com.juicyslew.moonstation14.ms14.power.cable.CableStorage.get(level,node.host(),node.face())==node.tier())
                com.juicyslew.moonstation14.ms14.power.cable.CableStorage.remove(level,node.host(),node.face(),node.tier());
        }
        snapshot.forEach((p,expected)->{if(matches(level,p,expected))level.setBlock(p,Blocks.AIR.defaultBlockState(),3);});
        SingularityFixtureService.forget(level,origin);
    }
    private static boolean matches(ServerLevel level,BlockPos p,PlacementSnapshot expected){return level.getBlockState(p).equals(expected.state())&&level.getBlockEntity(p)==expected.entity();}
    public static String breach(ServerLevel level,BlockPos corner) {
        for(var entry:JOBS.getOrDefault(level,Map.of()).entrySet()) {
            Job job=entry.getValue();
            if(job.record.placed().fieldGenerators().contains(corner)) {
                if(!owned(level,entry.getKey(),job))return "Refused: Tesla fixture ownership is stale/replaced; no breach made.";
                return SingularityFixtureService.breachOwnedCorner(level,corner);
            }
        }
        return "Refused: position is not a registered Tesla fixture corner.";
    }
}
