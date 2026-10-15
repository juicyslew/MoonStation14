package com.juicyslew.moonstation14.ms14.singularity.fixture;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import com.juicyslew.moonstation14.block.block_entity.ParticleAcceleratorPartBlockEntity;
import com.juicyslew.moonstation14.block.block_entity.SingularityGeneratorBlockEntity;
import com.juicyslew.moonstation14.block.block_entity.PowerDeviceBlockEntity;
import com.juicyslew.moonstation14.block.custom.PowerDeviceBlock;
import com.juicyslew.moonstation14.ms14.power.device.PowerDeviceKind;
import com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime;
import com.juicyslew.moonstation14.ms14.singularity.containment.FieldContainmentRuntime;
import com.juicyslew.moonstation14.ms14.radiation.RadiationCollectorBlockEntity;
import com.juicyslew.moonstation14.item.custom.GasTankItem;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Creates and tracks explicitly inactive singularity demonstration fixtures per live level. */
public final class SingularityFixtureService {
    public enum JobState { BUILT_INACTIVE, STARTING, READY, FAILED }
    public record ReadinessSnapshot(JobState jobState, boolean apcKnown, double outputWatts, boolean lampLit,
                                    boolean generatorInert, UUID singularityId, double singularityEnergy,
                                    int singularityLevel, double fuelMoles, double fuelBaseline,
                                    boolean containmentClosed) { }
    public record BuildRecord(SingularityFixtureLayout.Placed placed, SingularityFixturePower.Circuit bootstrap,
                              SingularityFixturePower.Circuit export, SingularityFixtureOperatorKit.Placed kit,
                              String buildError) { }
    public record Result(BuildRecord record, String error) { public boolean successful(){return record!=null;} }
    private static final Map<ServerLevel,Map<BlockPos,BuildRecord>> FIXTURES=new HashMap<>();
    private static final Map<ServerLevel,Map<BlockPos,RunningJob>> JOBS=new HashMap<>();
    private static final long JOB_TIMEOUT_TICKS=7200;
    private static final class RunningJob {
        final CreateSingularityFixtureStartup startup; final long deadline; double initialFuel; boolean fuelBaselineKnown; JobState state=JobState.STARTING; String error;
        UUID singularityId; long lastTick=Long.MIN_VALUE; ReadinessSnapshot snapshot; double commissioningTarget; boolean commissioned;
        RunningJob(ServerLevel l,BuildRecord r) { startup=new CreateSingularityFixtureStartup(l,r.placed()); deadline=l.getGameTime()+JOB_TIMEOUT_TICKS; }
    }
    private SingularityFixtureService() { }

    public static Result build(ServerLevel level, BlockPos origin) {
        return build(level,origin,SingularityFixtureLayout.Enclosure.COMPACT);
    }
    /** Explicit wide geometry is intentionally inactive-only; running commissioning stays compact. */
    public static Result buildWideInactive(ServerLevel level, BlockPos origin) {
        return build(level,origin,SingularityFixtureLayout.Enclosure.WIDE);
    }
    private static Result build(ServerLevel level, BlockPos origin, SingularityFixtureLayout.Enclosure enclosure) {
        Map<BlockPos,BuildRecord> records=FIXTURES.computeIfAbsent(level,k->new HashMap<>());
        if(records.containsKey(origin)) return new Result(null,"Refused: a registered fixture already exists at "+origin+".");
        try {
            var layout=SingularityFixtureLayout.build(level,origin,enclosure);
            if(!layout.successful()) return new Result(null,"Refused with no changes: "+layout.refusal());
            SingularityFixturePower.Circuit bootstrap=null,export=null;
            SingularityFixtureOperatorKit.Placed kit=null;
            try {
                bootstrap=SingularityFixturePower.bootstrap(level,layout.layout());
                export=SingularityFixturePower.export(level,layout.layout());
                var kitResult=SingularityFixtureOperatorKit.install(level,layout.layout());
                if(kitResult.successful()) kit=kitResult.kit();
                else {
                    String error="operator kit refused: "+kitResult.refusal();
                    var partial=new BuildRecord(layout.layout(),bootstrap,export,null,error); records.put(origin,partial);
                    return new Result(null,"PARTIAL: layout and power circuits placed, but "+error);
                }
            } catch(RuntimeException e) {
                String error="build stopped: "+e.getMessage();
                var partial=new BuildRecord(layout.layout(),bootstrap,export,kit,error); records.put(origin,partial);
                return new Result(null,"PARTIAL/FAILED: "+error);
            }
            var record=new BuildRecord(layout.layout(),bootstrap,export,kit,null); records.put(origin,record);
            return new Result(record,"Built "+(enclosure==SingularityFixtureLayout.Enclosure.WIDE?"wide ":"")+"inactive singularity fixture at "+origin+"; emitters OFF, fields standby, PA STANDBY. Auxiliary bootstrap and collector-only export are separate. No power or charge was injected.");
        } catch(RuntimeException e) { return new Result(null,"PARTIAL/FAILED: fixture build stopped safely: "+e.getMessage()); }
    }

    public static BuildRecord registered(ServerLevel level,BlockPos origin) { return FIXTURES.getOrDefault(level,Map.of()).get(origin); }
    public static String startRunning(ServerLevel level,BlockPos origin) {
        var r=registered(level,origin); if(r==null)return "Refused: no registered fixture at "+origin+".";
        if(r.buildError()!=null)return "Refused: partial fixture cannot be started.";
        if(r.placed().enclosure()==SingularityFixtureLayout.Enclosure.WIDE)
            return "Refused: wide fixtures are inactive-only; provide additional normal feeding and operate manually. Build a compact fixture for automatic running commissioning.";
        var jobs=JOBS.computeIfAbsent(level,k->new HashMap<>());
        if(jobs.containsKey(origin))return "STARTING: running startup already active at "+origin+".";
        var job=new RunningJob(level,r); job.initialFuel=fuel(level,r); job.fuelBaselineKnown=Double.isFinite(job.initialFuel)&&job.initialFuel>0;
        if(!job.fuelBaselineKnown)return "Refused: canonical collector profile/tank contents are missing or empty.";
         jobs.put(origin,job); return "STARTING: trusted running-mode coordinator enabled owned emitters/fields. Waiting for normal simulated startup (up to 7200 ticks); repeat status.";
    }
    public static void tick(ServerLevel level) {
        var jobs=JOBS.get(level); if(jobs==null)return;
        for(var entry:jobs.entrySet()) { RunningJob j=entry.getValue(); if(j.lastTick==level.getGameTime())continue; j.lastTick=level.getGameTime(); if(j.state!=JobState.STARTING)continue;
            if(!fixtureChunksLoaded(level,registered(level,entry.getKey()))) { j.state=JobState.FAILED; j.error="reserved fixture chunk unloaded"; continue; }
            j.startup.tick();
            if(j.startup.phase()==CreateSingularityFixtureStartup.Phase.FAILED) { j.state=JobState.FAILED; j.error=j.startup.error(); }
            else if(level.getGameTime()>j.deadline) { j.state=JobState.FAILED; j.error="readiness deadline expired"; }
            else { if(j.startup.phase()==CreateSingularityFixtureStartup.Phase.ACCELERATOR_STARTED) {
                    captureSingularity(level,registered(level,entry.getKey()),j);
                    if(!j.commissioned&&j.commissioningTarget==0&&j.singularityId!=null&&level.getEntity(j.singularityId) instanceof com.juicyslew.moonstation14.entities.SingularityEntity singularity
                            &&level.getBlockEntity(registered(level,entry.getKey()).placed().acceleratorControl()) instanceof ParticleAcceleratorPartBlockEntity pa) {
                        var paProfile=com.juicyslew.moonstation14.ms14.singularity.pa.ModParticleAccelerators.catalog(level).get(pa.profileId());
                        if(paProfile!=null) {
                            j.commissioningTarget=singularity.gravityProfile().levelThresholds().get(2)
                                    +2*3*paProfile.strength(com.juicyslew.moonstation14.ms14.singularity.pa.ParticleAcceleratorStrength.LEVEL_0).singularityFood();
                            pa.setStrength(com.juicyslew.moonstation14.ms14.singularity.pa.ParticleAcceleratorStrength.LEVEL_2);
                        }
                    }
                    if(!j.commissioned&&j.commissioningTarget>0&&j.singularityId!=null&&level.getEntity(j.singularityId) instanceof com.juicyslew.moonstation14.entities.SingularityEntity singularity&&singularity.energy()>=j.commissioningTarget) {
                        var r=registered(level,entry.getKey());
                        if(r!=null&&level.getBlockEntity(r.placed().acceleratorControl()) instanceof ParticleAcceleratorPartBlockEntity pa) {
                             pa.setStrength(com.juicyslew.moonstation14.ms14.singularity.pa.ParticleAcceleratorStrength.LEVEL_0);
                             j.commissioned=pa.strength()==com.juicyslew.moonstation14.ms14.singularity.pa.ParticleAcceleratorStrength.LEVEL_0;
                        }
                    }
                }
                var result=readiness(level,registered(level,entry.getKey()),j); j.snapshot=result.snapshot(); if(j.startup.phase()==CreateSingularityFixtureStartup.Phase.ACCELERATOR_STARTED&&result.ready())j.state=JobState.READY; }
        }
    }
    public static void unload(ServerLevel level) { FIXTURES.remove(level); JOBS.remove(level); }
    /** Removes only the requested fixture's bookkeeping, without unloading its level or neighboring fixtures. */
    public static void forget(ServerLevel level,BlockPos origin) {
        var fixtures=FIXTURES.get(level); if(fixtures!=null) { fixtures.remove(origin); if(fixtures.isEmpty())FIXTURES.remove(level); }
        var jobs=JOBS.get(level); if(jobs!=null) { jobs.remove(origin); if(jobs.isEmpty())JOBS.remove(level); }
    }
    private static double fuel(ServerLevel l,BuildRecord r) { double n=0; for(BlockPos p:r.placed().collectors()) if(l.getBlockEntity(p) instanceof RadiationCollectorBlockEntity c && c.controlStateReady()) { var t=c.tankSlot(); if(t!=null&&!t.snapshot().stack().isEmpty()) n+=GasTankItem.contents(t.snapshot().stack(),l).map(x->x.mixture().gasMoles().getOrDefault(com.juicyslew.moonstation14.ms14.atmos.core.GasType.PLASMA,0d)).orElse(0d); } return n; }
    private record Readiness(ReadinessSnapshot snapshot, boolean ready) { }
    private static Readiness readiness(ServerLevel l,BuildRecord r,RunningJob job) {
        if(r==null||r.buildError()!=null)return new Readiness(null,false);
        var p=r.placed();
        if(!l.hasChunkAt(p.startupGenerator())||!(l.getBlockEntity(p.startupGenerator()) instanceof SingularityGeneratorBlockEntity g))return new Readiness(null,false);
        var singularity=job.singularityId==null?null:l.getEntity(job.singularityId);
        var output=exportOutput(l,r); boolean lamp=exportLampLit(l,r);
        double current=fuel(l,r); boolean decreasing=job.fuelBaselineKnown&&Double.isFinite(current)&&current<job.initialFuel;
        boolean closed=false;
        var profile=com.juicyslew.moonstation14.ms14.singularity.ModSingularityGenerators.catalog(l).get(g.profileId());
        if(profile!=null) {
            var center=new net.minecraft.world.phys.Vec3(p.startupGenerator().getX()+.5,p.startupGenerator().getY()+.5,p.startupGenerator().getZ()+.5);
            closed=FieldContainmentRuntime.closedVolumes(l).stream().anyMatch(v->com.juicyslew.moonstation14.ms14.singularity.containment.ClosedFieldVolume.containsWithinFaceDistance(v,center,profile.failsafeDistance()));
        }
        var snap=new ReadinessSnapshot(job.state,output!=null&&output.known(),output==null?0:output.outputWatts(),lamp,g.startupInert(),job.singularityId,
                singularity instanceof com.juicyslew.moonstation14.entities.SingularityEntity s?s.energy():0,
                singularity instanceof com.juicyslew.moonstation14.entities.SingularityEntity s?s.singularityLevel():0,current,job.initialFuel,closed);
        return new Readiness(snap,job.commissioned&&job.startup.phase()==CreateSingularityFixtureStartup.Phase.ACCELERATOR_STARTED&&snap.apcKnown()&&snap.outputWatts()>=12000&&lamp&&closed&&decreasing&&snap.singularityId()!=null&&singularity instanceof com.juicyslew.moonstation14.entities.SingularityEntity s&&s.energy()>0&&g.startupInert());
    }
    private static void captureSingularity(ServerLevel level,BuildRecord record,RunningJob job) {
        if(job.singularityId!=null||record==null||record.buildError()!=null)return;
        BlockPos generator=record.placed().startupGenerator(); if(!level.hasChunkAt(generator))return;
        var own=level.getEntitiesOfClass(com.juicyslew.moonstation14.entities.SingularityEntity.class,
                new net.minecraft.world.phys.AABB(generator).inflate(2),e->!e.isRemoved()).stream().findFirst().orElse(null);
        if(own!=null)job.singularityId=own.getUUID();
    }
    /** Fresh, read-only view of the canonical fixture readiness signals; never advances startup or initializes collector state. */
    public static ReadinessSnapshot readinessSnapshot(ServerLevel level, BlockPos origin) {
        BuildRecord record=registered(level,origin);
        if(record==null||record.buildError()!=null)return null;
        if(!fixtureChunksLoaded(level,record))return null;
        RunningJob job=JOBS.getOrDefault(level,Map.of()).get(origin);
        if(job==null) {
            var p=record.placed();
            var gen=level.getBlockEntity(p.startupGenerator());
            double output=0; boolean known=false, lamp=exportLampLit(level,record);
            var observation=exportOutput(level,record); if(observation!=null) { known=observation.known(); output=observation.outputWatts(); }
            return new ReadinessSnapshot(JobState.BUILT_INACTIVE,known,output,lamp,gen instanceof SingularityGeneratorBlockEntity g&&g.startupInert(),null,0,0,fuel(level,record),0,false);
        }
        return readiness(level,record,job).snapshot();
    }
    private static boolean fixtureChunksLoaded(ServerLevel level,BuildRecord record) {
        if(record==null)return false;
        var o=record.placed().origin();
        int minX=o.getX()>>4,maxX=(o.getX()+26)>>4,minZ=o.getZ()>>4,maxZ=(o.getZ()+18)>>4;
        for(int x=minX;x<=maxX;x++)for(int z=minZ;z<=maxZ;z++)
            if(level.getChunk(x,z,ChunkStatus.FULL,false)==null)return false;
        return true;
    }
    private static PowerRuntime.ApcOutputObservation exportOutput(ServerLevel level,BuildRecord record) {
        if(record.export()!=null) for(BlockPos pos:record.export().devices()) if(level.getBlockEntity(pos) instanceof PowerDeviceBlockEntity d && level.getBlockState(pos).getBlock() instanceof PowerDeviceBlock b && b.kind()==PowerDeviceKind.APC) return PowerRuntime.apcOutputObservation(level,d);
        return null;
    }
    private static boolean exportLampLit(ServerLevel level,BuildRecord record) {
        if(record.export()!=null) for(BlockPos pos:record.export().devices()) if(level.getBlockEntity(pos) instanceof PowerDeviceBlockEntity d && level.getBlockState(pos).getBlock() instanceof PowerDeviceBlock b && b.kind().isLamp() && level.getBlockState(pos).hasProperty(PowerDeviceBlock.LIT)) return level.getBlockState(pos).getValue(PowerDeviceBlock.LIT);
        return false;
    }
    public static BlockPos nearestOrigin(ServerLevel level,BlockPos position) {
        return FIXTURES.getOrDefault(level,Map.of()).keySet().stream().min(java.util.Comparator.comparingDouble(p->p.distSqr(position))).orElse(null);
    }
    public static String status(ServerLevel level,BlockPos origin) {
        BuildRecord record=registered(level,origin);
        if(record==null)return "NOT READY/UNKNOWN: no registered fixture at "+origin+" (status is read-only).";
        if(!fixtureChunksLoaded(level,record))return "NOT READY/UNKNOWN: reserved fixture chunks are not fully loaded at "+origin+" (status is read-only; no chunks forced).";
        var p=record.placed(); RunningJob job=JOBS.getOrDefault(level,Map.of()).get(origin); StringBuilder out=new StringBuilder(record.buildError()==null?(job==null?"BUILT_INACTIVE":"JOB "+job.state+(job.error==null?"":" "+job.error))+" fixture origin=":"PARTIAL fixture origin=").append(origin)
                .append("; auxiliary bootstrap nodes=").append(record.bootstrap()==null?"UNKNOWN":record.bootstrap().nodes().size()).append("; collector-only export nodes=").append(record.export()==null?"UNKNOWN":record.export().nodes().size())
                 .append("; kit blocks=").append(record.kit()==null?"PARTIAL/UNKNOWN":record.kit().accessBlocks().size());
         if(job!=null) { var sing=job.singularityId==null?null:level.getEntity(job.singularityId);
             out.append("; commissioningEnergy=").append(sing instanceof com.juicyslew.moonstation14.entities.SingularityEntity s?s.energy():"UNKNOWN")
                       .append(" commissioningTarget=").append(job.commissioningTarget).append(" commissioningStrength=LEVEL_2 upkeepStrength=LEVEL_0 commissioningPhase=").append(job.commissioned?"READY":job.commissioningTarget>0?"WAITING_FOR_TARGET":"WAITING_FOR_SINGULARITY"); }
        if(record.buildError()!=null)out.append("; buildError=").append(record.buildError()); out.append(". ");
        int emitters=0,fields=0,collectors=0;
        var ownEmitters=new java.util.HashSet<>(p.emitters()); var ownFields=new java.util.HashSet<>(p.fieldGenerators());
        for(BlockPos pos:ownEmitters) { if(level.getBlockEntity(pos) instanceof com.juicyslew.moonstation14.block.block_entity.EmitterBlockEntity be) { emitters++;out.append("emitter@").append(pos).append(" requested=").append(be.requestedOn()).append(" powered=").append(be.firingPowered()).append("; "); } else out.append("emitter@").append(pos).append(" UNKNOWN/replaced; "); }
        for(BlockPos pos:ownFields) { if(level.getBlockEntity(pos) instanceof com.juicyslew.moonstation14.block.block_entity.FieldGeneratorBlockEntity be) { fields++;out.append("field@").append(pos).append(" ").append(be.snapshot()).append("; "); } else out.append("field@").append(pos).append(" UNKNOWN/replaced; "); }
        for(BlockPos pos:p.collectors()) { if(level.getBlockEntity(pos) instanceof com.juicyslew.moonstation14.ms14.radiation.RadiationCollectorBlockEntity) collectors++; else out.append("collector@").append(pos).append(" UNKNOWN/replaced; "); }
        BlockPos pa=p.acceleratorControl();
        if(level.getBlockEntity(pa) instanceof ParticleAcceleratorPartBlockEntity control && control.role().equals("control")) {
            var meter=PowerRuntime.apcPowerMeter(level,control);
            out.append("PA control enabled=").append(control.enabled()).append(" strength=").append(control.strength()).append(" firingEligible=").append(control.firingEligible())
                    .append(" APCmeter=").append(meter.known()?meter.deliveredWatts()+"W":"UNKNOWN");
            var power=p.acceleratorParts().stream().map(level::getBlockEntity).filter(ParticleAcceleratorPartBlockEntity.class::isInstance).map(ParticleAcceleratorPartBlockEntity.class::cast).filter(be->be.role().equals("power")).findFirst().orElse(null);
            var mv=power==null?null:PowerRuntime.mvPowerMeter(level,power);
            out.append(" powerRoleMVmeter=").append(mv==null||!mv.known()?"UNKNOWN":mv.deliveredWatts()+"W").append("; ");
        } else out.append("PA control UNKNOWN; PA meters UNKNOWN; ");
        if(level.getBlockEntity(p.startupGenerator()) instanceof SingularityGeneratorBlockEntity gen) out.append("GEN startupCharge=").append(gen.startupCharge()).append(" inert=").append(gen.startupInert()).append("; "); else out.append("GEN UNKNOWN; ");
        var edges=FieldContainmentRuntime.currentEdges(level).stream().filter(e->ownFields.contains(e.first())&&ownFields.contains(e.second())).count(); var faces=FieldContainmentRuntime.currentFaces(level).stream().filter(f->f.corners().stream().allMatch(ownFields::contains)).count();
        out.append("field currentEdges=").append(edges).append(" currentFaces=").append(faces).append("; ");
        var fresh=readinessSnapshot(level,origin); if(fresh!=null)out.append("readiness=").append(fresh).append("; ");
        for(BlockPos pos:p.collectors()) if(level.getBlockEntity(pos) instanceof RadiationCollectorBlockEntity collector) {
            var offer=collector.powerSupplierOffer(); var tank=collector.controlStateReady()?collector.tankSlot():null;
            var contents=tank==null||tank.snapshot().stack().isEmpty()?null:GasTankItem.contents(tank.snapshot().stack(),level).orElse(null);
            out.append("collector@").append(pos).append(" currentWatts=").append(offer==null?"UNKNOWN":offer.currentWatts()).append(" plasmaMoles=").append(contents==null?"UNKNOWN":contents.mixture().gasMoles().getOrDefault(com.juicyslew.moonstation14.ms14.atmos.core.GasType.PLASMA,0d)).append("; ");
        }
        PowerDeviceBlockEntity exportApc=null, lamp=null;
        if(record.export()!=null) for(BlockPos pos:record.export().devices()) if(level.getBlockEntity(pos) instanceof PowerDeviceBlockEntity device && level.getBlockState(pos).getBlock() instanceof PowerDeviceBlock block) { if(block.kind()==PowerDeviceKind.APC) exportApc=device; else if(block.kind().isLamp()) lamp=device; }
        var output=exportApc==null?null:PowerRuntime.apcOutputObservation(level,exportApc);
        out.append("exportAPC known/outputWatts=").append(output==null||!output.known()?"UNKNOWN":"true/"+output.outputWatts()).append(" lampLit=").append(lamp!=null&&level.getBlockState(lamp.getBlockPos()).hasProperty(PowerDeviceBlock.LIT)?level.getBlockState(lamp.getBlockPos()).getValue(PowerDeviceBlock.LIT):"UNKNOWN").append("; live emitter BEs ").append(emitters).append("/8; field BEs ").append(fields).append("/8; collector BEs ").append(collectors).append("/4. No chunks forced or machines changed.");
        return out.toString();
    }
}
