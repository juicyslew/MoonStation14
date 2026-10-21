package com.juicyslew.moonstation14.ms14.power.runtime;

import com.juicyslew.moonstation14.block.block_entity.PowerDeviceBlockEntity;
import com.juicyslew.moonstation14.block.custom.PowerDeviceBlock;
import com.juicyslew.moonstation14.ms14.power.cable.CableTier;
import com.juicyslew.moonstation14.ms14.power.device.PowerDeviceKind;
import com.juicyslew.moonstation14.ms14.power.device.PowerDeviceRules;
import com.juicyslew.moonstation14.ms14.power.device.ModPowerAppearances;
import com.juicyslew.moonstation14.ms14.power.device.PowerAppearanceData;
import com.juicyslew.moonstation14.ms14.power.graph.LoadedPowerGraph;
import com.juicyslew.moonstation14.ms14.power.graph.PowerGraphService;
import com.juicyslew.moonstation14.ms14.power.topology.CableFaceNode;
import com.juicyslew.moonstation14.ms14.power.topology.DevicePort;
import com.juicyslew.moonstation14.ms14.power.topology.PowerTopology;
import com.juicyslew.moonstation14.ms14.power.ui.ApcVisualState;
import com.juicyslew.moonstation14.ms14.atmos.piping.VentilationDeviceBlockEntity;
import com.juicyslew.moonstation14.ms14.atmos.piping.VentilationDeviceBlock;
import com.juicyslew.moonstation14.ms14.atmos.piping.VentilationGasRuntime;
import com.juicyslew.moonstation14.ms14.atmos.piping.alarm.AirAlarmBlock;
import com.juicyslew.moonstation14.ms14.atmos.piping.alarm.AirAlarmBlockEntity;
import com.juicyslew.moonstation14.ms14.atmos.piping.alarm.prototype.AirAlarmData;
import com.juicyslew.moonstation14.ms14.doors.AirlockBlock;
import com.juicyslew.moonstation14.ms14.doors.AirlockBlockEntity;
import com.juicyslew.moonstation14.ms14.disposal.unit.DisposalUnitBlockEntity;
import com.juicyslew.moonstation14.ms14.disposal.prototype.DisposalEntityData;
import com.juicyslew.moonstation14.ms14.chemistry.chemmaster.ChemMasterBlockEntity;
import com.juicyslew.moonstation14.ms14.botany.ReagentGrinderBlockEntity;
import com.juicyslew.moonstation14.ms14.botany.SeedExtractorBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.*;

/** Bounded server-side device projection. Devices are indexed by lifecycle events, never world-scanned. */
public final class PowerRuntime {
    public static final int TICKS_PER_SOLVE = 20;
    public static final double SOURCE_WATTS = 25_000;
    public static final double LAMP_WATTS = 100;
    private static final Map<ServerLevel, Set<BlockPos>> DEVICES = new WeakHashMap<>();
    private static final Map<ServerLevel, Set<BlockPos>> VENTILATION_DEVICES = new WeakHashMap<>();
    private static final Map<ServerLevel, Set<BlockPos>> AIR_ALARMS = new WeakHashMap<>();
    private static final Map<ServerLevel, Set<BlockPos>> AIRLOCKS = new WeakHashMap<>();
    private static final Map<ServerLevel,Map<BlockPos,AirlockMeterSample>> AIRLOCK_METERS=new WeakHashMap<>();
    private static final Map<ServerLevel, Set<BlockPos>> DISPOSAL_UNITS = new WeakHashMap<>();
    private static final Map<ServerLevel, Map<BlockPos, DisposalMeterSample>> DISPOSAL_METERS = new WeakHashMap<>();
    private static final Map<ServerLevel, Set<BlockPos>> CHEM_MASTERS = new WeakHashMap<>();
    private static final Map<ServerLevel, Map<BlockPos, ChemMasterMeterSample>> CHEM_MASTER_METERS = new WeakHashMap<>();
    private static final Map<ServerLevel, Map<BlockPos, MvRuntimeMeterSample>> MV_RUNTIME_METERS = new WeakHashMap<>();
    private static final Map<ServerLevel, Map<BlockPos, ApcRuntimeMeterSample>> APC_RUNTIME_METERS = new WeakHashMap<>();
    private static final Map<ServerLevel, Set<BlockPos>> PROCESSING_MACHINES = new WeakHashMap<>();
    private static final Map<ServerLevel, Map<BlockPos, ProcessingMeterSample>> PROCESSING_METERS = new WeakHashMap<>();
    private static final Map<ServerLevel, Map<BlockPos, AlarmMeterSample>> ALARM_METERS = new WeakHashMap<>();
    private static final Map<ServerLevel, Map<BlockPos, VentMeterSample>> VENT_METERS = new WeakHashMap<>();
    private static final Map<ServerLevel, Map<BlockPos, VentilationPaidInterval.ReceiverSignature>> PREVIOUS_VENT_SIGNATURES = new WeakHashMap<>();
    private static final Map<ServerLevel, Map<BlockPos, CompletedVentBudget>> COMPLETED_VENT_BUDGETS = new WeakHashMap<>();
    private static final Map<ServerLevel, VentilationPaidInterval> PAID_INTERVALS = new WeakHashMap<>();
    private static final Map<ServerLevel, Long> LAST_SOLVE = new WeakHashMap<>();
    private static final Set<ServerLevel> RESOLVE_AFTER_TRIP = Collections.newSetFromMap(new WeakHashMap<>());
    private static final Set<ServerLevel> RESOLVE_AFTER_TOPOLOGY = Collections.newSetFromMap(new WeakHashMap<>());
    private static final Map<ServerLevel, String> LAST_DIAGNOSTIC = new WeakHashMap<>();
    private static final Map<ServerLevel, Map<BlockPos, ApcOutputSample>> APC_OUTPUT_SAMPLES = new WeakHashMap<>();
    private static long serverTickSequence;
    private record ApcOutputSample(long sampleTick, long topologyGeneration, boolean graphKnown,
                                   double outputWatts, PowerDeviceBlockEntity device, long breakerRevision,
                                   ApcVisualState visualState, long visualTick, long visualGeneration) { }
    private record VentMeterSample(long sampleTick, long topologyGeneration, double deliveredWatts,
                                     VentilationDeviceBlockEntity vent, PowerDeviceBlockEntity apc, long breakerRevision) { }
    private record AlarmMeterSample(long sampleTick, long topologyGeneration, double deliveredWatts,
                                    AirAlarmBlockEntity alarm, AirAlarmData prototype,
                                    PowerDeviceBlockEntity apc, long breakerRevision) { }
    private record AirlockMeterSample(long sampleTick,long topologyGeneration,double deliveredWatts,
                                       AirlockBlockEntity airlock,com.juicyslew.moonstation14.ms14.doors.prototype.AirlockData prototype,
                                       PowerDeviceBlockEntity apc,long breakerRevision) { }
    private record DisposalMeterSample(long sampleTick, long topologyGeneration, double deliveredWatts,
                                       DisposalUnitBlockEntity unit, DisposalEntityData.Unit prototype,
                                        PowerDeviceBlockEntity apc, long breakerRevision) { }
    private record ChemMasterMeterSample(long sampleTick, long topologyGeneration, double deliveredWatts,
                                         ChemMasterBlockEntity machine, java.util.UUID identity, double demandWatts,
                                          PowerDeviceBlockEntity apc, long breakerRevision) { }
    private record MvRuntimeMeterSample(long sampleTick, long topologyGeneration, double deliveredWatts,
                                          double demandWatts, PowerRuntimeNode node, BlockEntity identity,
                                          boolean graphKnown) { }
    private record ApcRuntimeMeterSample(long sampleTick, long topologyGeneration, double deliveredWatts,
                                          double demandWatts, PowerRuntimeNode node, BlockEntity identity,
                                          java.util.UUID placementIdentity, boolean graphKnown) { }
    private record RuntimePortState(PowerRuntimeNode node, Map<CableTier, LoadedPowerGraph.NodeState> states,
                                    Map<CableTier, Integer> counts) {
        private boolean hasExactlyOne(CableTier tier) { return counts.getOrDefault(tier, 0) == 1; }
    }
    private record ProcessingMeterSample(long sampleTick, long topologyGeneration, double deliveredWatts,
                                         Object machine, java.util.UUID identity, double demandWatts,
                                         PowerDeviceBlockEntity apc, long breakerRevision) { }
    private record CompletedVentBudget(long solveTick, VentilationPaidInterval.WorkBudget budget,
                                       PowerDeviceBlockEntity apc, long breakerRevision, long topologyGeneration) { }
    public record VentMeterObservation(boolean known, double deliveredWatts) {
        public static final VentMeterObservation UNKNOWN = new VentMeterObservation(false, 0);
    }
    public record MvPowerObservation(boolean known, double deliveredWatts) {
        public static final MvPowerObservation UNKNOWN = new MvPowerObservation(false, 0);
    }
    private PowerRuntime() { }

    public static void register(IEventBus bus) { bus.register(PowerRuntime.class); }

    public static void deviceChanged(ServerLevel level, BlockPos pos) {
                PowerGraphService.invalidateLampChoice(level, pos);
        Set<BlockPos> devices = DEVICES.computeIfAbsent(level, ignored -> new LinkedHashSet<>());
        devices.add(pos.immutable());
        MV_RUNTIME_METERS.computeIfAbsent(level, ignored -> new HashMap<>()).remove(pos);
        APC_RUNTIME_METERS.computeIfAbsent(level, ignored -> new HashMap<>()).remove(pos);
        invalidateRuntimeNode(level, pos);
        PowerGraphService.noteChanged(level, new ChunkPos(pos));
    }

    public static void ventilationDeviceChanged(ServerLevel level, BlockPos pos) {
                PowerGraphService.invalidateLampChoice(level, pos);
        VENTILATION_DEVICES.computeIfAbsent(level, ignored -> new LinkedHashSet<>()).add(pos.immutable());
        VENT_METERS.computeIfAbsent(level, ignored -> new HashMap<>()).remove(pos);
        COMPLETED_VENT_BUDGETS.computeIfAbsent(level, ignored -> new HashMap<>()).remove(pos);
        PowerGraphService.noteChanged(level, new ChunkPos(pos));
    }

    public static void airAlarmChanged(ServerLevel level, BlockPos pos) {
                PowerGraphService.invalidateLampChoice(level, pos);
        AIR_ALARMS.computeIfAbsent(level, ignored -> new LinkedHashSet<>()).add(pos.immutable());
        ALARM_METERS.computeIfAbsent(level, ignored -> new HashMap<>()).remove(pos);
        PowerGraphService.noteChanged(level, new ChunkPos(pos));
    }

    public static void airAlarmRemoved(ServerLevel level, BlockPos pos) {
        PowerGraphService.invalidateLampChoice(level, pos);
        Set<BlockPos> alarms = AIR_ALARMS.get(level);
        if (alarms != null) alarms.remove(pos);
        Map<BlockPos, AlarmMeterSample> meters = ALARM_METERS.get(level);
        if (meters != null) meters.remove(pos);
        PowerGraphService.noteChanged(level, new ChunkPos(pos));
    }

    public static void disposalUnitChanged(ServerLevel level, BlockPos pos) {
        DISPOSAL_UNITS.computeIfAbsent(level, ignored -> new LinkedHashSet<>()).add(pos.immutable());
        DISPOSAL_METERS.computeIfAbsent(level, ignored -> new HashMap<>()).remove(pos);
        PowerGraphService.noteChanged(level, new ChunkPos(pos));
    }
    public static void disposalUnitRemoved(ServerLevel level, BlockPos pos) {
        Set<BlockPos> indexed = DISPOSAL_UNITS.get(level); if (indexed != null) indexed.remove(pos);
        Map<BlockPos, DisposalMeterSample> meters = DISPOSAL_METERS.get(level); if (meters != null) meters.remove(pos);
        PowerGraphService.noteChanged(level, new ChunkPos(pos));
    }
    public static void chemMasterChanged(ServerLevel level, BlockPos pos) {
        CHEM_MASTERS.computeIfAbsent(level, ignored -> new LinkedHashSet<>()).add(pos.immutable());
        CHEM_MASTER_METERS.computeIfAbsent(level, ignored -> new HashMap<>()).remove(pos);
        PowerGraphService.noteChanged(level, new ChunkPos(pos));
    }
    public static void chemMasterRemoved(ServerLevel level, BlockPos pos) {
        Set<BlockPos> index = CHEM_MASTERS.get(level); if (index != null) index.remove(pos);
        Map<BlockPos, ChemMasterMeterSample> meters = CHEM_MASTER_METERS.get(level); if (meters != null) meters.remove(pos);
        PowerGraphService.noteChanged(level, new ChunkPos(pos));
    }
    public static void processingMachineChanged(ServerLevel level, BlockPos pos) {
        PROCESSING_MACHINES.computeIfAbsent(level, ignored -> new LinkedHashSet<>()).add(pos.immutable());
        PROCESSING_METERS.computeIfAbsent(level, ignored -> new HashMap<>()).remove(pos);
        PowerGraphService.noteChanged(level, new ChunkPos(pos));
    }
    public static void processingMachineRemoved(ServerLevel level, BlockPos pos) {
        Set<BlockPos> index = PROCESSING_MACHINES.get(level); if (index != null) index.remove(pos);
        Map<BlockPos, ProcessingMeterSample> meters = PROCESSING_METERS.get(level); if (meters != null) meters.remove(pos);
        PowerGraphService.noteChanged(level, new ChunkPos(pos));
    }
    /** Fresh allocated receiver observation; zero demand is known without requiring a topology/APC. */
    public static VentMeterObservation processingMeter(ServerLevel level, BlockPos pos) {
        if (level == null || pos == null || !level.getServer().isSameThread()
                || !(level.getChunkSource().getChunk(pos.getX() >> 4, pos.getZ() >> 4, ChunkStatus.FULL, false) instanceof LevelChunk chunk)) return VentMeterObservation.UNKNOWN;
        BlockEntity be = chunk.getBlockEntity(pos);
        if (be instanceof ReagentGrinderBlockEntity grinder) return processingMeter(level, grinder);
        if (be instanceof SeedExtractorBlockEntity extractor) return processingMeter(level, extractor);
        return VentMeterObservation.UNKNOWN;
    }
    public static VentMeterObservation processingMeter(ServerLevel level, ReagentGrinderBlockEntity machine) {
        if (level == null || machine == null || machine.getLevel() != level || machine.isRemoved() || !machine.isLoadedValid() || machine.resolvedProfile() == null) return VentMeterObservation.UNKNOWN;
        return processingMeterValidated(level, machine, machine.placementIdentity(), machine.requiredPowerWatts());
    }
    public static VentMeterObservation processingMeter(ServerLevel level, SeedExtractorBlockEntity machine) {
        if (level == null || machine == null || machine.getLevel() != level || machine.isRemoved() || !machine.isLoadedValid() || !machine.isInitialized()) return VentMeterObservation.UNKNOWN;
        var profile = machine.resolvedProfile();
        if (profile == null || !profile.extractor()) return VentMeterObservation.UNKNOWN;
        return processingMeterValidated(level, machine, machine.placementIdentity(), profile.powered() ? profile.powerWatts() : 0d);
    }
    private static VentMeterObservation processingMeterValidated(ServerLevel level, Object machine, java.util.UUID identity, double demand) {
        if (level == null || machine == null || !level.getServer().isSameThread() || identity == null || !Double.isFinite(demand) || demand < 0) return VentMeterObservation.UNKNOWN;
        BlockPos pos = machine instanceof ReagentGrinderBlockEntity g ? g.getBlockPos() : ((SeedExtractorBlockEntity) machine).getBlockPos();
        if (demand == 0) return new VentMeterObservation(true, 0);
        ProcessingMeterSample sample = PROCESSING_METERS.getOrDefault(level, Map.of()).get(pos);
        if (sample == null || sample.machine() != machine || !identity.equals(sample.identity()) || demand != sample.demandWatts()
                || !freshFullDemandApcAuthorization(level, sample.sampleTick(), sample.topologyGeneration(),
                sample.deliveredWatts(), sample.demandWatts(), sample.apc(), sample.breakerRevision())) return VentMeterObservation.UNKNOWN;
        return new VentMeterObservation(true, sample.deliveredWatts());
    }
    /** Fresh full-demand observation. Unknown topology, stale identity, or partial power is not authorization. */
    public static VentMeterObservation chemMasterMeter(ServerLevel level, ChemMasterBlockEntity expected) {
        return chemMasterMeterCheck(level, expected).observation();
    }
    public static String chemMasterMeterDiagnostic(ServerLevel level, ChemMasterBlockEntity expected) {
        ChemMasterMeterCheck check = chemMasterMeterCheck(level, expected);
        ChemMasterMeterSample sample = check.sample();
        return check.reason() + (sample == null ? " sample=absent" : " sampleTick=" + sample.sampleTick()
                + " sampleGeneration=" + sample.topologyGeneration() + " delivered=" + sample.deliveredWatts()
                + " demand=" + sample.demandWatts() + " sampleMachineIdentity=" + (sample.machine() == expected)
                + " samplePlacementIdentity=" + (expected != null && sample.identity().equals(expected.placementIdentity()))
                + " sampleApc=" + (sample.apc() == null ? "none" : sample.apc().getBlockPos().toShortString())
                + " sampleBreakerRevision=" + sample.breakerRevision()) + " " + PowerGraphService.diagnosticSnapshot(level);
    }
    private record ChemMasterMeterCheck(VentMeterObservation observation, String reason, ChemMasterMeterSample sample) { }
    private static ChemMasterMeterCheck chemMasterMeterCheck(ServerLevel level, ChemMasterBlockEntity expected) {
        if (level == null || expected == null || expected.isRemoved() || !level.getServer().isSameThread()
                || !expected.isLoadedValid() || expected.powerLoadWatts() <= 0)
            return new ChemMasterMeterCheck(VentMeterObservation.UNKNOWN, "machine=invalid-or-no-demand", null);
        ChemMasterMeterSample sample = CHEM_MASTER_METERS.getOrDefault(level, Map.of()).get(expected.getBlockPos());
        if (sample == null) return new ChemMasterMeterCheck(VentMeterObservation.UNKNOWN, "sample=absent", null);
        if (sample.machine() != expected) return new ChemMasterMeterCheck(VentMeterObservation.UNKNOWN, "sample=machine-identity-mismatch", sample);
        if (!sample.identity().equals(expected.placementIdentity())) return new ChemMasterMeterCheck(VentMeterObservation.UNKNOWN, "sample=placement-identity-mismatch", sample);
        if (sample.demandWatts() != expected.powerLoadWatts()) return new ChemMasterMeterCheck(VentMeterObservation.UNKNOWN, "demand=mismatch", sample);
        String reason = fullDemandApcAuthorizationFailure(level, sample.sampleTick(), sample.topologyGeneration(),
                sample.deliveredWatts(), sample.demandWatts(), sample.apc(), sample.breakerRevision());
        if (reason != null) return new ChemMasterMeterCheck(VentMeterObservation.UNKNOWN, reason, sample);
        return new ChemMasterMeterCheck(new VentMeterObservation(true, sample.deliveredWatts()), "accepted", sample);
    }
    /** Shared freshness, topology, full-delivery, and loaded APC authorization for processing meters. */
    private static boolean freshFullDemandApcAuthorization(ServerLevel level, long sampleTick, long generation,
                                                             double delivered, double demand,
                                                             PowerDeviceBlockEntity apc, long breakerRevision) {
        return fullDemandApcAuthorizationFailure(level, sampleTick, generation, delivered, demand, apc, breakerRevision) == null;
    }
    private static String fullDemandApcAuthorizationFailure(ServerLevel level, long sampleTick, long generation,
                                                             double delivered, double demand,
                                                             PowerDeviceBlockEntity apc, long breakerRevision) {
        if (!PowerGraphService.topologyReady(level)) return "topology=not-ready";
        if (demand <= 0 || delivered != demand) return "delivery=mismatch";
        if (generation != PowerGraphService.topologyGeneration(level)) return "generation=mismatch";
        if (serverTickSequence < sampleTick) return "sample=future";
        if (serverTickSequence - sampleTick > TICKS_PER_SOLVE) return "sample=stale(age=" + (serverTickSequence - sampleTick) + ")";
        if (apc == null) return "apc=missing";
        if (apc.isRemoved()) return "apc=removed";
        if (!apc.breakerClosed()) return "apc=breaker-open";
        if (apc.breakerRevision() != breakerRevision) return "apc=breaker-revision-mismatch";
        BlockPos apcPos = apc.getBlockPos();
        if (!(level.getChunkSource().getChunk(apcPos.getX() >> 4, apcPos.getZ() >> 4,
                ChunkStatus.FULL, false) instanceof LevelChunk chunk
                && chunk.getBlockEntity(apcPos) == apc
                && apc.getBlockState().getBlock() instanceof PowerDeviceBlock block
                && block.kind() == PowerDeviceKind.APC)) return "apc=stale-or-invalid";
        return null;
    }
    /** Position lookup for backend callers; caller must separately bind placementIdentity to its session. */
    public static VentMeterObservation chemMasterMeter(ServerLevel level, BlockPos pos) {
        if (level == null || pos == null || !level.getServer().isSameThread()
                || !(level.getChunkSource().getChunk(pos.getX() >> 4, pos.getZ() >> 4, ChunkStatus.FULL, false) instanceof LevelChunk chunk)
                || !(chunk.getBlockEntity(pos) instanceof ChemMasterBlockEntity machine)) return VentMeterObservation.UNKNOWN;
        return chemMasterMeter(level, machine);
    }
    /** Identity-bound authorization predicate for session handlers; rejects partial/unknown delivery. */
    public static boolean hasFullFreshChemMasterPower(ServerLevel level, ChemMasterBlockEntity expected,
                                                       java.util.UUID placementIdentity) {
        return expected != null && placementIdentity != null && placementIdentity.equals(expected.placementIdentity())
                && chemMasterMeter(level, expected).known();
    }
    public static VentMeterObservation disposalUnitMeter(ServerLevel level, DisposalUnitBlockEntity expected) {
        return disposalUnitMeterCheck(level, expected).observation();
    }

    public static String disposalUnitMeterDiagnostic(ServerLevel level, DisposalUnitBlockEntity expected) {
        DisposalMeterCheck check = disposalUnitMeterCheck(level, expected);
        DisposalMeterSample sample = check.sample();
        return check.reason() + (sample == null ? " sample=absent" : " sampleTick=" + sample.sampleTick()
                + " sampleGeneration=" + sample.topologyGeneration() + " sampleWatts=" + sample.deliveredWatts()
                + " sampleUnitIdentity=" + (sample.unit() == expected)
                + " samplePrototypeIdentity=" + (expected != null && sample.prototype() == expected.prototype())
                + " sampleApc=" + (sample.apc() == null ? "none" : sample.apc().getBlockPos().toShortString())
                + " sampleBreakerRevision=" + sample.breakerRevision())
                + " " + PowerGraphService.diagnosticSnapshot(level);
    }

    private record DisposalMeterCheck(VentMeterObservation observation, String reason, DisposalMeterSample sample) { }

    private static DisposalMeterCheck disposalMeterCheck(VentMeterObservation observation, String reason,
                                                          DisposalMeterSample sample) {
        return new DisposalMeterCheck(observation, reason, sample);
    }

    private static DisposalMeterCheck disposalUnitMeterCheck(ServerLevel level, DisposalUnitBlockEntity expected) {
        if (expected == null) return disposalMeterCheck(VentMeterObservation.UNKNOWN, "expected=missing", null);
        if (expected.isRemoved()) return disposalMeterCheck(VentMeterObservation.UNKNOWN, "expected=removed", null);
        BlockPos pos = expected.getBlockPos();
        if (!(level.getChunkSource().getChunk(pos.getX() >> 4, pos.getZ() >> 4, ChunkStatus.FULL, false) instanceof LevelChunk chunk)
                ) return disposalMeterCheck(VentMeterObservation.UNKNOWN, "unitChunk=not-full", null);
        if (chunk.getBlockEntity(pos) != expected) return disposalMeterCheck(VentMeterObservation.UNKNOWN, "unitIdentity=stale", null);
        if (!PowerGraphService.topologyReady(level)) return disposalMeterCheck(VentMeterObservation.UNKNOWN, "topology=not-ready", null);
        DisposalMeterSample sample = DISPOSAL_METERS.getOrDefault(level, Map.of()).get(pos);
        if (!expected.participatesInPowerLoad()) return disposalMeterCheck(VentMeterObservation.UNKNOWN, "unit=not-participating", sample);
        if (sample == null) return disposalMeterCheck(VentMeterObservation.UNKNOWN, "sample=absent", null);
        if (sample.unit() != expected) return disposalMeterCheck(VentMeterObservation.UNKNOWN, "sampleUnit=identity-mismatch", sample);
        if (sample.prototype() != expected.prototype()) return disposalMeterCheck(VentMeterObservation.UNKNOWN, "samplePrototype=identity-mismatch", sample);
        long generation = PowerGraphService.topologyGeneration(level);
        if (sample.topologyGeneration() != generation) return disposalMeterCheck(VentMeterObservation.UNKNOWN,
                "generation=mismatch(sample=" + sample.topologyGeneration() + ",current=" + generation + ")", sample);
        if (serverTickSequence < sample.sampleTick()) return disposalMeterCheck(VentMeterObservation.UNKNOWN, "sample=future", sample);
        long age = serverTickSequence - sample.sampleTick();
        if (age > TICKS_PER_SOLVE) return disposalMeterCheck(VentMeterObservation.UNKNOWN, "sample=stale(age=" + age + ")", sample);
        if (sample.apc() == null) return disposalMeterCheck(VentMeterObservation.UNKNOWN, "apc=missing", sample);
        if (sample.apc().isRemoved()) return disposalMeterCheck(VentMeterObservation.UNKNOWN, "apc=removed", sample);
        if (!sample.apc().breakerClosed()) return disposalMeterCheck(VentMeterObservation.UNKNOWN, "apc=breaker-open", sample);
        if (sample.apc().breakerRevision() != sample.breakerRevision()) return disposalMeterCheck(VentMeterObservation.UNKNOWN,
                "apc=breaker-revision-mismatch(current=" + sample.apc().breakerRevision() + ")", sample);
        if (sample.deliveredWatts() != sample.prototype().powerWatts()) return disposalMeterCheck(VentMeterObservation.UNKNOWN,
                "delivery=mismatch(expected=" + sample.prototype().powerWatts() + ")", sample);
        BlockPos apcPos = sample.apc().getBlockPos();
        if (!(level.getChunkSource().getChunk(apcPos.getX() >> 4, apcPos.getZ() >> 4, ChunkStatus.FULL, false) instanceof LevelChunk apcChunk)
                ) return disposalMeterCheck(VentMeterObservation.UNKNOWN, "apcChunk=not-full", sample);
        if (apcChunk.getBlockEntity(apcPos) != sample.apc()) return disposalMeterCheck(VentMeterObservation.UNKNOWN, "apcIdentity=stale", sample);
        if (!(sample.apc().getBlockState().getBlock() instanceof PowerDeviceBlock apcBlock)
                || apcBlock.kind() != PowerDeviceKind.APC) return disposalMeterCheck(VentMeterObservation.UNKNOWN, "apcBlock=wrong", sample);
        return disposalMeterCheck(new VentMeterObservation(true, sample.deliveredWatts()), "accepted", sample);
    }
    public static void airlockChanged(ServerLevel level,BlockPos pos) {
        PowerGraphService.invalidateLampChoice(level,pos);
        AIRLOCKS.computeIfAbsent(level,ignored->new LinkedHashSet<>()).add(pos.immutable());
        AIRLOCK_METERS.computeIfAbsent(level,ignored->new HashMap<>()).remove(pos); PowerGraphService.noteChanged(level, new ChunkPos(pos));
    }
    public static void airlockRemoved(ServerLevel level,BlockPos pos) {
        PowerGraphService.invalidateLampChoice(level,pos);Set<BlockPos> index=AIRLOCKS.get(level);if(index!=null)index.remove(pos);
        Map<BlockPos,AirlockMeterSample> meters=AIRLOCK_METERS.get(level);if(meters!=null)meters.remove(pos);
        PowerGraphService.noteChanged(level, new ChunkPos(pos));
    }
    public static VentMeterObservation airlockPower(ServerLevel level,AirlockBlockEntity expected) {
        return airlockMeterCheck(level, expected).observation();
    }

    /** Diagnostic for the exact airlock meter acceptance checks; read-only and non-loading. */
    public static String airlockPowerDiagnostic(ServerLevel level, AirlockBlockEntity expected) {
        AirlockMeterCheck check = airlockMeterCheck(level, expected);
        AirlockMeterSample sample = check.sample();
        return check.reason() + (sample == null ? " sample=absent" : " sampleTick=" + sample.sampleTick()
                + " sampleGeneration=" + sample.topologyGeneration() + " sampleWatts=" + sample.deliveredWatts()
                + " sampleDoorIdentity=" + (sample.airlock() == expected)
                + " samplePrototypeIdentity=" + (expected != null && sample.prototype() == expected.prototype())
                + " sampleApc=" + (sample.apc() == null ? "none" : sample.apc().getBlockPos().toShortString())
                + " sampleBreakerRevision=" + sample.breakerRevision());
    }

    private record AirlockMeterCheck(VentMeterObservation observation, String reason, AirlockMeterSample sample) { }

    private static AirlockMeterCheck airlockMeterCheck(ServerLevel level, AirlockBlockEntity expected) {
        if (expected == null) return airlockCheck(VentMeterObservation.UNKNOWN, "expected=missing", null);
        if (expected.isRemoved()) return airlockCheck(VentMeterObservation.UNKNOWN, "expected=removed", null);
        BlockPos pos = expected.getBlockPos();
        if (!(level.getChunkSource().getChunk(pos.getX() >> 4, pos.getZ() >> 4,
                ChunkStatus.FULL, false) instanceof LevelChunk chunk))
            return airlockCheck(VentMeterObservation.UNKNOWN, "doorChunk=not-full", null);
        if (chunk.getBlockEntity(pos) != expected) return airlockCheck(VentMeterObservation.UNKNOWN, "doorIdentity=stale", null);
        if (!(expected.getBlockState().getBlock() instanceof AirlockBlock))
            return airlockCheck(VentMeterObservation.UNKNOWN, "doorBlock=wrong", null);
        if (expected.state().receiverPowerDisabled())
            return airlockCheck(new VentMeterObservation(true, 0), "receiver=disabled", null);
        if (!PowerGraphService.topologyReady(level))
            return airlockCheck(VentMeterObservation.UNKNOWN, "topology=not-ready", null);
        AirlockMeterSample sample = AIRLOCK_METERS.getOrDefault(level, Map.of()).get(pos);
        if (sample == null) return airlockCheck(VentMeterObservation.UNKNOWN, "sample=absent", null);
        if (sample.airlock() != expected) return airlockCheck(VentMeterObservation.UNKNOWN, "sampleDoor=identity-mismatch", sample);
        if (sample.prototype() != expected.prototype()) return airlockCheck(VentMeterObservation.UNKNOWN, "samplePrototype=identity-mismatch", sample);
        long currentGeneration = PowerGraphService.topologyGeneration(level);
        if (sample.topologyGeneration() != currentGeneration)
            return airlockCheck(VentMeterObservation.UNKNOWN, "generation=mismatch(sample=" + sample.topologyGeneration()
                    + ",current=" + currentGeneration + ")", sample);
        if (serverTickSequence < sample.sampleTick())
            return airlockCheck(VentMeterObservation.UNKNOWN, "sample=future(tick=" + sample.sampleTick()
                    + ",now=" + serverTickSequence + ")", sample);
        long age = serverTickSequence - sample.sampleTick();
        if (age > TICKS_PER_SOLVE)
            return airlockCheck(VentMeterObservation.UNKNOWN, "sample=stale(age=" + age + ")", sample);
        if (sample.apc() != null) {
            if (sample.apc().isRemoved()) return airlockCheck(VentMeterObservation.UNKNOWN, "apc=removed", sample);
            if (!sample.apc().breakerClosed()) return airlockCheck(VentMeterObservation.UNKNOWN, "apc=breaker-open", sample);
            if (sample.apc().breakerRevision() != sample.breakerRevision())
                return airlockCheck(VentMeterObservation.UNKNOWN, "apc=breaker-revision-mismatch(current="
                        + sample.apc().breakerRevision() + ")", sample);
            BlockPos apcPos = sample.apc().getBlockPos();
            if (!(level.getChunkSource().getChunk(apcPos.getX() >> 4, apcPos.getZ() >> 4,
                    ChunkStatus.FULL, false) instanceof LevelChunk apcChunk))
                return airlockCheck(VentMeterObservation.UNKNOWN, "apcChunk=not-full", sample);
            if (apcChunk.getBlockEntity(apcPos) != sample.apc())
                return airlockCheck(VentMeterObservation.UNKNOWN, "apcIdentity=stale", sample);
            if (!(sample.apc().getBlockState().getBlock() instanceof PowerDeviceBlock block)
                    || block.kind() != PowerDeviceKind.APC)
                return airlockCheck(VentMeterObservation.UNKNOWN, "apcBlock=wrong", sample);
        }
        return airlockCheck(new VentMeterObservation(true, sample.deliveredWatts()), "accepted", sample);
    }

    private static AirlockMeterCheck airlockCheck(VentMeterObservation observation, String reason,
                                                   AirlockMeterSample sample) {
        return new AirlockMeterCheck(observation, reason, sample);
    }

    /** Observation, not power authorization: only a fresh, fully delivered 100 W solve is powered. */
    public static VentMeterObservation airAlarmMeter(ServerLevel level, AirAlarmBlockEntity expectedBE) {
        if (expectedBE == null || expectedBE.isRemoved())
            return VentMeterObservation.UNKNOWN;
        BlockPos pos = expectedBE.getBlockPos();
        if (!(level.getChunkSource().getChunk(pos.getX() >> 4, pos.getZ() >> 4, ChunkStatus.FULL, false) instanceof LevelChunk chunk)
                || chunk.getBlockEntity(pos) != expectedBE
                || !(expectedBE.getBlockState().getBlock() instanceof AirAlarmBlock)
                || !PowerGraphService.topologyReady(level)) return VentMeterObservation.UNKNOWN;
        AlarmMeterSample sample = ALARM_METERS.getOrDefault(level, Map.of()).get(pos);
        if (sample == null || sample.alarm() != expectedBE || sample.prototype() != expectedBE.prototype()
                || sample.topologyGeneration() != PowerGraphService.topologyGeneration(level)
                || serverTickSequence < sample.sampleTick() || serverTickSequence - sample.sampleTick() > TICKS_PER_SOLVE
                || sample.apc() == null || sample.apc().isRemoved() || !sample.apc().breakerClosed()
                || sample.apc().breakerRevision() != sample.breakerRevision()
                || sample.deliveredWatts() != sample.prototype().apcPowerReceiver().requestedWatts())
            return VentMeterObservation.UNKNOWN;
        BlockPos apcPos = sample.apc().getBlockPos();
        if (!(level.getChunkSource().getChunk(apcPos.getX() >> 4, apcPos.getZ() >> 4, ChunkStatus.FULL, false) instanceof LevelChunk apcChunk)
                || apcChunk.getBlockEntity(apcPos) != sample.apc()
                || !(sample.apc().getBlockState().getBlock() instanceof PowerDeviceBlock apcBlock)
                || apcBlock.kind() != PowerDeviceKind.APC) return VentMeterObservation.UNKNOWN;
        return new VentMeterObservation(true, sample.deliveredWatts());
    }

    public static void ventilationDeviceRemoved(ServerLevel level, BlockPos pos) {
        PowerGraphService.invalidateLampChoice(level, pos);
        VentilationDeviceBlockEntity oldVent = findVentIdentity(level, pos);
        VentilationGasRuntime.forgetDevice(level, oldVent);
        Set<BlockPos> indexed = VENTILATION_DEVICES.get(level);
        if (indexed != null) indexed.remove(pos);
        Map<BlockPos, VentMeterSample> meters = VENT_METERS.get(level);
        if (meters != null) meters.remove(pos);
        Map<BlockPos, VentilationPaidInterval.ReceiverSignature> previous = PREVIOUS_VENT_SIGNATURES.get(level);
        if (previous != null) previous.remove(pos);
        Map<BlockPos, CompletedVentBudget> budgets = COMPLETED_VENT_BUDGETS.get(level);
        if (budgets != null) budgets.remove(pos);
        VentilationPaidInterval intervals = PAID_INTERVALS.get(level);
        if (intervals != null) intervals.forget(oldVent);
        PowerGraphService.noteChanged(level, new ChunkPos(pos));
    }

    /** Gas work that may run synchronously after a completed electrical interval is consumed. */
    @FunctionalInterface
    public interface CompletedVentilationWork {
        /** Return true only when the callback actually transferred gas. */
        boolean perform(double paidElapsedSeconds);
    }

    /**
     * Run gas work using the entire completed interval, after validating and consuming its
     * single-use electrical authorization. The callback receives no authority it can replay.
     */
    public static boolean performCompletedVentilationWork(ServerLevel level,
            VentilationDeviceBlockEntity expectedBE, CompletedVentilationWork callback) {
        if (callback == null) return false;
        CompletedVentBudget completed = completedBudget(level, expectedBE);
        if (completed == null || completedBudget(level, expectedBE) != completed) return false;
        double elapsedSeconds = completed.budget().elapsedSeconds();
        if (completed.budget().consume(elapsedSeconds).isEmpty()) return false;
        return callback.perform(elapsedSeconds);
    }

    private static CompletedVentBudget completedBudget(ServerLevel level, VentilationDeviceBlockEntity vent) {
        if (level == null || vent == null || !level.getServer().isSameThread()
                || vent.isRemoved()) return null;
        BlockPos pos = vent.getBlockPos();
        if (!(level.getChunkSource().getChunk(pos.getX() >> 4, pos.getZ() >> 4, ChunkStatus.FULL, false) instanceof LevelChunk chunk)
                || chunk.getBlockEntity(pos) != vent || !PowerGraphService.topologyReady(level)) return null;
        CompletedVentBudget result = COMPLETED_VENT_BUDGETS.getOrDefault(level, Map.of()).get(pos);
        if (result == null || result.solveTick() != serverTickSequence) return null;
        VentMeterSample sample = VENT_METERS.getOrDefault(level, Map.of()).get(pos);
        if (!ventilationMeter(level, vent).known() || sample == null || sample.vent() != vent
                || sample.apc() != result.apc() || sample.breakerRevision() != result.breakerRevision()
                || sample.topologyGeneration() != result.topologyGeneration()
                || result.apc().breakerRevision() != result.breakerRevision()
                || !result.apc().breakerClosed()) return null;
        return result;
    }

    private static VentilationDeviceBlockEntity findVentIdentity(ServerLevel level, BlockPos pos) {
        if (!level.hasChunkAt(pos)) return null;
        BlockEntity be = level.getBlockEntity(pos);
        return be instanceof VentilationDeviceBlockEntity vent ? vent : null;
    }

    /** Observation of the most recent solve only; this is not a prepaid gas grant. */
    public static VentMeterObservation ventilationMeter(ServerLevel level, VentilationDeviceBlockEntity expectedBE) {
        return ventilationMeterCheck(level, expectedBE).observation();
    }

    public static String ventilationMeterDiagnostic(ServerLevel level, VentilationDeviceBlockEntity expectedBE) {
        VentMeterCheck check = ventilationMeterCheck(level, expectedBE);
        VentMeterSample sample = check.sample();
        return check.reason() + (sample == null ? " sample=absent" : " sampleTick=" + sample.sampleTick()
                + " sampleGeneration=" + sample.topologyGeneration() + " sampleWatts=" + sample.deliveredWatts()
                + " sampleVentIdentity=" + (sample.vent() == expectedBE)
                + " sampleApc=" + (sample.apc() == null ? "none" : sample.apc().getBlockPos().toShortString())
                + " sampleBreakerRevision=" + sample.breakerRevision());
    }

    private record VentMeterCheck(VentMeterObservation observation, String reason, VentMeterSample sample) { }

    private static VentMeterCheck ventMeterCheck(VentMeterObservation observation, String reason, VentMeterSample sample) {
        return new VentMeterCheck(observation, reason, sample);
    }

    private static VentMeterCheck ventilationMeterCheck(ServerLevel level, VentilationDeviceBlockEntity expectedBE) {
        if (expectedBE == null) return ventMeterCheck(VentMeterObservation.UNKNOWN, "expected=missing", null);
        if (expectedBE.isRemoved()) return ventMeterCheck(VentMeterObservation.UNKNOWN, "expected=removed", null);
        BlockPos pos = expectedBE.getBlockPos();
        if (!(level.getChunkSource().getChunk(pos.getX() >> 4, pos.getZ() >> 4, ChunkStatus.FULL, false) instanceof LevelChunk chunk)
                ) return ventMeterCheck(VentMeterObservation.UNKNOWN, "ventChunk=not-full", null);
        if (chunk.getBlockEntity(pos) != expectedBE) return ventMeterCheck(VentMeterObservation.UNKNOWN, "ventIdentity=stale", null);
        if (!(expectedBE.getBlockState().getBlock() instanceof VentilationDeviceBlock))
            return ventMeterCheck(VentMeterObservation.UNKNOWN, "ventBlock=wrong", null);
        if (!expectedBE.settingsValid()) return ventMeterCheck(VentMeterObservation.UNKNOWN, "ventSettings=invalid", null);
        if (!PowerGraphService.topologyReady(level)) return ventMeterCheck(VentMeterObservation.UNKNOWN, "topology=not-ready", null);
        VentMeterSample sample = VENT_METERS.getOrDefault(level, Map.of()).get(pos);
        if (sample == null) return ventMeterCheck(VentMeterObservation.UNKNOWN, "sample=absent", null);
        if (sample.vent() != expectedBE) return ventMeterCheck(VentMeterObservation.UNKNOWN, "sampleVent=identity-mismatch", sample);
        long generation = PowerGraphService.topologyGeneration(level);
        if (sample.topologyGeneration() != generation) return ventMeterCheck(VentMeterObservation.UNKNOWN,
                "generation=mismatch(sample=" + sample.topologyGeneration() + ",current=" + generation + ")", sample);
        if (serverTickSequence < sample.sampleTick()) return ventMeterCheck(VentMeterObservation.UNKNOWN, "sample=future", sample);
        long age = serverTickSequence - sample.sampleTick();
        if (age > TICKS_PER_SOLVE) return ventMeterCheck(VentMeterObservation.UNKNOWN, "sample=stale(age=" + age + ")", sample);
        if (sample.apc() == null) return ventMeterCheck(VentMeterObservation.UNKNOWN, "apc=missing", sample);
        if (sample.apc().isRemoved()) return ventMeterCheck(VentMeterObservation.UNKNOWN, "apc=removed", sample);
        if (sample.apc().breakerRevision() != sample.breakerRevision()) return ventMeterCheck(VentMeterObservation.UNKNOWN,
                "apc=breaker-revision-mismatch(current=" + sample.apc().breakerRevision() + ")", sample);
        if (!sample.apc().breakerClosed()) return ventMeterCheck(VentMeterObservation.UNKNOWN, "apc=breaker-open", sample);
        if (sample.deliveredWatts() != PowerLiveLoop.SS14_UNARY_ATMOS_POWER_WATTS)
            return ventMeterCheck(VentMeterObservation.UNKNOWN, "delivery=mismatch(expected="
                    + PowerLiveLoop.SS14_UNARY_ATMOS_POWER_WATTS + ")", sample);
        BlockPos apcPos = sample.apc().getBlockPos();
        if (!(level.getChunkSource().getChunk(apcPos.getX() >> 4, apcPos.getZ() >> 4, ChunkStatus.FULL, false) instanceof LevelChunk apcChunk)
                ) return ventMeterCheck(VentMeterObservation.UNKNOWN, "apcChunk=not-full", sample);
        if (apcChunk.getBlockEntity(apcPos) != sample.apc()) return ventMeterCheck(VentMeterObservation.UNKNOWN, "apcIdentity=stale", sample);
        if (!(sample.apc().getBlockState().getBlock() instanceof PowerDeviceBlock apcBlock)
                || apcBlock.kind() != PowerDeviceKind.APC) return ventMeterCheck(VentMeterObservation.UNKNOWN, "apcBlock=wrong", sample);
        return ventMeterCheck(new VentMeterObservation(true, sample.deliveredWatts()), "accepted", sample);
    }

    /** Number of currently indexed loaded devices; exposed for diagnostics and scale audits. */
    public static int indexedDeviceCount(ServerLevel level) {
        Set<BlockPos> devices = DEVICES.get(level);
        return devices == null ? 0 : devices.size();
    }

    /** Read-only index snapshot for an on-demand alarm menu query. An oversized index fails closed. */
    public static List<BlockPos> indexedVentilationCandidates(ServerLevel level, int visitLimit) {
        if (level == null || !level.getServer().isSameThread() || visitLimit < 1) return List.of();
        Set<BlockPos> indexed = VENTILATION_DEVICES.get(level);
        if (indexed == null || indexed.size() > visitLimit) return List.of();
        return List.copyOf(indexed);
    }

    public static String lastSolveDiagnostic(ServerLevel level) {
        return LAST_DIAGNOSTIC.getOrDefault(level, "no solve yet");
    }

    public static String lastSolveSummary(ServerLevel level) { return lastSolveDiagnostic(level); }

    /** Constant-time, read-only menu lookup. Never triggers a solve or loads a chunk. */
    public static ApcVisualState apcVisualState(ServerLevel level, PowerDeviceBlockEntity device) {
        ApcOutputSample sample = currentApcOutputSample(level, device);
        return sample == null ? ApcVisualState.UNKNOWN : sample.visualState();
    }

    /** Fresh solve-backed APC output observation for diagnostics/tests; never triggers a solve. */
    public record ApcOutputObservation(boolean known, double outputWatts, long sampleTick, long topologyGeneration) {
        public static final ApcOutputObservation UNKNOWN = new ApcOutputObservation(false, 0, -1, -1);
    }

    public static ApcOutputObservation apcOutputObservation(ServerLevel level, PowerDeviceBlockEntity device) {
        ApcOutputSample sample = currentApcOutputSample(level, device);
        return sample == null ? ApcOutputObservation.UNKNOWN : new ApcOutputObservation(true,
                sample.outputWatts(), sample.sampleTick(), sample.topologyGeneration());
    }

    private static ApcOutputSample currentApcOutputSample(ServerLevel level, PowerDeviceBlockEntity device) {
        if (device == null) return null;
        BlockPos pos = device.getBlockPos();
        Map<BlockPos, ApcOutputSample> samples = APC_OUTPUT_SAMPLES.get(level);
        ApcOutputSample sample = samples == null ? null : samples.get(pos);
        if (sample == null || sample.device() != device || device.isRemoved()
                || !(device.getBlockState().getBlock() instanceof PowerDeviceBlock block)
                || block.kind() != PowerDeviceKind.APC
                || !(level.getChunkSource().getChunk(pos.getX() >> 4, pos.getZ() >> 4,
                        ChunkStatus.FULL, false) instanceof LevelChunk chunk)
                || chunk.getBlockEntity(pos) != device
                || !sample.graphKnown() || sample.visualGeneration() != PowerGraphService.topologyGeneration(level)
                || sample.breakerRevision() != device.breakerRevision()
                || serverTickSequence < sample.visualTick()
                || serverTickSequence - sample.visualTick() > TICKS_PER_SOLVE) return null;
        return sample;
    }

    public static void deviceRemoved(ServerLevel level, BlockPos pos) {
                PowerGraphService.invalidateLampChoice(level, pos);
        Set<BlockPos> devices = DEVICES.get(level);
        if (devices != null) devices.remove(pos);
        Map<BlockPos, ApcOutputSample> samples = APC_OUTPUT_SAMPLES.get(level);
        if (samples != null) samples.remove(pos);
        Map<BlockPos, MvRuntimeMeterSample> mvMeters = MV_RUNTIME_METERS.get(level);
        if (mvMeters != null) {
            MvRuntimeMeterSample oldMeter = mvMeters.remove(pos);
            if (oldMeter != null) oldMeter.node().receiveMvPowerAllocation(0, false, serverTickSequence);
        }
        Map<BlockPos, ApcRuntimeMeterSample> apcMeters = APC_RUNTIME_METERS.get(level);
        if (apcMeters != null) { ApcRuntimeMeterSample old = apcMeters.remove(pos); if (old != null) old.node().receiveApcPowerAllocation(0, false, serverTickSequence); }
        invalidateRuntimeNode(level, pos);
        PowerGraphService.noteChanged(level, new ChunkPos(pos));
    }

    private static void invalidateRuntimeNode(ServerLevel level, BlockPos pos) {
        LevelChunk chunk = loadedFullChunk(level, pos);
        if (chunk != null && chunk.getBlockEntity(pos) instanceof PowerRuntimeNode node) {
            node.receiveMvPowerAllocation(0, false, serverTickSequence);
            node.receiveApcPowerAllocation(0, false, serverTickSequence);
        }
    }

    /** Identity-bound fresh meter for opt-in runtime nodes; never causes a solve or chunk load. */
    public static MvPowerObservation mvPowerMeter(ServerLevel level, BlockEntity expected) {
        if (level == null || expected == null || expected.isRemoved() || !level.getServer().isSameThread()
                || !(expected instanceof PowerRuntimeNode node) || !PowerGraphService.topologyReady(level))
            return MvPowerObservation.UNKNOWN;
        BlockPos pos = expected.getBlockPos();
        MvRuntimeMeterSample sample = MV_RUNTIME_METERS.getOrDefault(level, Map.of()).get(pos);
        if (sample == null || sample.identity() != expected || sample.node() != node
                || !(level.getChunkSource().getChunk(pos.getX() >> 4, pos.getZ() >> 4,
                        ChunkStatus.FULL, false) instanceof LevelChunk chunk)
                || chunk.getBlockEntity(pos) != expected
                || !sample.graphKnown() || sample.topologyGeneration() != PowerGraphService.topologyGeneration(level)
                || serverTickSequence < sample.sampleTick() || serverTickSequence - sample.sampleTick() > TICKS_PER_SOLVE
                || !Double.valueOf(sample.demandWatts()).equals(node.mvPowerDemandWatts()))
            return MvPowerObservation.UNKNOWN;
        return new MvPowerObservation(true, sample.deliveredWatts());
    }

    /** Fresh identity/demand-bound APC receiver meter; never solves or loads chunks. */
    public static MvPowerObservation apcPowerMeter(ServerLevel level, BlockEntity expected) {
        if (level == null || expected == null || expected.isRemoved() || !level.getServer().isSameThread()
                || !(expected instanceof PowerRuntimeNode node) || !PowerGraphService.topologyReady(level)) return MvPowerObservation.UNKNOWN;
        BlockPos pos = expected.getBlockPos();
        ApcRuntimeMeterSample sample = APC_RUNTIME_METERS.getOrDefault(level, Map.of()).get(pos);
        Double demand = node.apcPowerDemandWatts();
        if (sample == null || demand == null || node.apcPowerIdentity() == null
                || sample.identity() != expected || sample.node() != node
                || !DEVICES.getOrDefault(level, Set.of()).contains(pos)
                || !Objects.equals(sample.placementIdentity(), node.apcPowerIdentity())
                || !Double.valueOf(sample.demandWatts()).equals(demand)
                || !(level.getChunkSource().getChunk(pos.getX() >> 4, pos.getZ() >> 4, ChunkStatus.FULL, false) instanceof LevelChunk chunk)
                || chunk.getBlockEntity(pos) != expected || !sample.graphKnown()
                || sample.topologyGeneration() != PowerGraphService.topologyGeneration(level)
                || serverTickSequence < sample.sampleTick() || serverTickSequence - sample.sampleTick() > TICKS_PER_SOLVE)
            return MvPowerObservation.UNKNOWN;
        return new MvPowerObservation(true, sample.deliveredWatts());
    }

    @net.neoforged.bus.api.SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !(event.getChunk() instanceof LevelChunk chunk)) return;
        for (BlockEntity be : chunk.getBlockEntities().values()) {
            if (be instanceof PowerDeviceBlockEntity || be instanceof PowerRuntimeNode)
                deviceChanged(level, be.getBlockPos());
            if (be instanceof VentilationDeviceBlockEntity) ventilationDeviceChanged(level, be.getBlockPos());
            if (be instanceof AirAlarmBlockEntity) airAlarmChanged(level, be.getBlockPos());

            if (be instanceof AirlockBlockEntity) airlockChanged(level,be.getBlockPos());

            if (be instanceof DisposalUnitBlockEntity) disposalUnitChanged(level, be.getBlockPos());
            if (be instanceof ChemMasterBlockEntity) chemMasterChanged(level, be.getBlockPos());
            if (be instanceof ReagentGrinderBlockEntity || be instanceof SeedExtractorBlockEntity) processingMachineChanged(level, be.getBlockPos());

        }
    }

    @net.neoforged.bus.api.SubscribeEvent
    public static void onChunkUnload(ChunkEvent.Unload event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (event.getChunk() instanceof LevelChunk chunk) {
            for (BlockEntity be : chunk.getBlockEntities().values()) {
                if (be instanceof VentilationDeviceBlockEntity vent) VentilationGasRuntime.forgetDevice(level, vent);
                if (be instanceof PowerRuntimeNode node) {
                    node.receiveMvPowerAllocation(0, false, serverTickSequence);
                    node.receiveApcPowerAllocation(0, false, serverTickSequence);
                }
            }
        }
                Set<BlockPos> devices = DEVICES.get(level);
        if (devices != null) devices.removeIf(pos -> new ChunkPos(pos).equals(event.getChunk().getPos()));
        Set<BlockPos> vents = VENTILATION_DEVICES.get(level);
        if (vents != null) vents.removeIf(pos -> new ChunkPos(pos).equals(event.getChunk().getPos()));
        Set<BlockPos> alarms = AIR_ALARMS.get(level);
        if (alarms != null) alarms.removeIf(pos -> new ChunkPos(pos).equals(event.getChunk().getPos()));

        Set<BlockPos> doors=AIRLOCKS.get(level);if(doors!=null)doors.removeIf(pos->new ChunkPos(pos).equals(event.getChunk().getPos()));
        Map<BlockPos,AirlockMeterSample> doorMeters=AIRLOCK_METERS.get(level);if(doorMeters!=null)doorMeters.keySet().removeIf(pos->new ChunkPos(pos).equals(event.getChunk().getPos()));

        Set<BlockPos> disposal = DISPOSAL_UNITS.get(level);
        if (disposal != null) disposal.removeIf(pos -> new ChunkPos(pos).equals(event.getChunk().getPos()));

        Map<BlockPos, AlarmMeterSample> alarmSamples = ALARM_METERS.get(level);
        if (alarmSamples != null) alarmSamples.keySet().removeIf(pos -> new ChunkPos(pos).equals(event.getChunk().getPos()));
        Map<BlockPos, DisposalMeterSample> disposalSamples = DISPOSAL_METERS.get(level);
        if (disposalSamples != null) disposalSamples.keySet().removeIf(pos -> new ChunkPos(pos).equals(event.getChunk().getPos()));
        Set<BlockPos> chemMasters = CHEM_MASTERS.get(level);
        if (chemMasters != null) chemMasters.removeIf(pos -> new ChunkPos(pos).equals(event.getChunk().getPos()));
        Set<BlockPos> processing = PROCESSING_MACHINES.get(level);
        if (processing != null) processing.removeIf(pos -> new ChunkPos(pos).equals(event.getChunk().getPos()));
        Map<BlockPos, ProcessingMeterSample> processingSamples = PROCESSING_METERS.get(level);
        if (processingSamples != null) processingSamples.keySet().removeIf(pos -> new ChunkPos(pos).equals(event.getChunk().getPos()));
        Map<BlockPos, ChemMasterMeterSample> chemSamples = CHEM_MASTER_METERS.get(level);
        if (chemSamples != null) chemSamples.keySet().removeIf(pos -> new ChunkPos(pos).equals(event.getChunk().getPos()));
        Map<BlockPos, VentMeterSample> ventSamples = VENT_METERS.get(level);
        if (ventSamples != null) ventSamples.keySet().removeIf(pos -> new ChunkPos(pos).equals(event.getChunk().getPos()));
        Map<BlockPos, VentilationPaidInterval.ReceiverSignature> previousVentSignatures = PREVIOUS_VENT_SIGNATURES.get(level);
        if (previousVentSignatures != null) previousVentSignatures.entrySet().removeIf(entry -> {
            if (!new ChunkPos(entry.getKey()).equals(event.getChunk().getPos())) return false;
            VentilationPaidInterval paid = PAID_INTERVALS.get(level);
            if (paid != null) paid.forget(entry.getValue().ventilationBE());
            return true;
        });
        Map<BlockPos, CompletedVentBudget> completed = COMPLETED_VENT_BUDGETS.get(level);
        if (completed != null) completed.keySet().removeIf(pos -> new ChunkPos(pos).equals(event.getChunk().getPos()));
        Map<BlockPos, ApcOutputSample> samples = APC_OUTPUT_SAMPLES.get(level);
        if (samples != null) samples.keySet().removeIf(pos -> new ChunkPos(pos).equals(event.getChunk().getPos()));
        Map<BlockPos, MvRuntimeMeterSample> mvSamples = MV_RUNTIME_METERS.get(level);
        if (mvSamples != null) mvSamples.keySet().removeIf(pos -> new ChunkPos(pos).equals(event.getChunk().getPos()));
        Map<BlockPos, ApcRuntimeMeterSample> apcSamples = APC_RUNTIME_METERS.get(level);
        if (apcSamples != null) apcSamples.keySet().removeIf(pos -> new ChunkPos(pos).equals(event.getChunk().getPos()));
    }

    @net.neoforged.bus.api.SubscribeEvent
    public static void onTick(ServerTickEvent.Post event) {
                long now = ++serverTickSequence;
        for (ServerLevel level : event.getServer().getAllLevels()) {
            // Use this service's event sequence: GameTestServer/world time and the
            // server tick counter can be frozen while the event-driven server loop runs.
            long before = LAST_SOLVE.getOrDefault(level, now - TICKS_PER_SOLVE);
            boolean scheduledAfterTrip = RESOLVE_AFTER_TRIP.remove(level);
            boolean scheduledAfterTopology = RESOLVE_AFTER_TOPOLOGY.contains(level)
                    && PowerGraphService.topologyReady(level);
            if (scheduledAfterTopology) RESOLVE_AFTER_TOPOLOGY.remove(level);
            if (scheduledAfterTrip || scheduledAfterTopology || now - before >= TICKS_PER_SOLVE) {
                LAST_SOLVE.put(level, now);
                // Early re-solves use the entire elapsed interval to avoid double-billing storage.
                solve(level, Math.min(TICKS_PER_SOLVE, Math.max(1, now - before)), now);
            }
            observeApcOutputs(level, now);
        }
    }

    @net.neoforged.bus.api.SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            VentilationGasRuntime.forgetLevel(level);
            DEVICES.remove(level);
            VENTILATION_DEVICES.remove(level); VENT_METERS.remove(level);
            AIR_ALARMS.remove(level); ALARM_METERS.remove(level);

            AIRLOCKS.remove(level);AIRLOCK_METERS.remove(level);

            DISPOSAL_UNITS.remove(level); DISPOSAL_METERS.remove(level);
            CHEM_MASTERS.remove(level); CHEM_MASTER_METERS.remove(level);
            PROCESSING_MACHINES.remove(level); PROCESSING_METERS.remove(level);

            PREVIOUS_VENT_SIGNATURES.remove(level); COMPLETED_VENT_BUDGETS.remove(level);
            VentilationPaidInterval paid = PAID_INTERVALS.remove(level); if (paid != null) paid.clear();
            LAST_SOLVE.remove(level);
            RESOLVE_AFTER_TRIP.remove(level);
            RESOLVE_AFTER_TOPOLOGY.remove(level);
            LAST_DIAGNOSTIC.remove(level);
            APC_OUTPUT_SAMPLES.remove(level);
            MV_RUNTIME_METERS.remove(level);
            APC_RUNTIME_METERS.remove(level);
        }
    }
    @net.neoforged.bus.api.SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        VentilationGasRuntime.clear();
        DEVICES.clear(); LAST_SOLVE.clear(); RESOLVE_AFTER_TRIP.clear(); RESOLVE_AFTER_TOPOLOGY.clear();
        VENTILATION_DEVICES.clear(); VENT_METERS.clear();
        AIR_ALARMS.clear(); ALARM_METERS.clear();

        AIRLOCKS.clear();AIRLOCK_METERS.clear();

        DISPOSAL_UNITS.clear(); DISPOSAL_METERS.clear();
        CHEM_MASTERS.clear(); CHEM_MASTER_METERS.clear();
        PROCESSING_MACHINES.clear(); PROCESSING_METERS.clear();

        PREVIOUS_VENT_SIGNATURES.clear(); COMPLETED_VENT_BUDGETS.clear();
        PAID_INTERVALS.values().forEach(VentilationPaidInterval::clear); PAID_INTERVALS.clear();
        LAST_DIAGNOSTIC.clear(); APC_OUTPUT_SAMPLES.clear(); serverTickSequence = 0;
        MV_RUNTIME_METERS.clear();
        APC_RUNTIME_METERS.clear();
    }


    private static void solve(ServerLevel level, long elapsedTicks, long sampleTick) {
        Set<BlockPos> indexed = DEVICES.get(level);
        Set<BlockPos> indexedAirlockPositions=AIRLOCKS.get(level);
        if ((indexed == null || indexed.isEmpty()) && (indexedAirlockPositions == null || indexedAirlockPositions.isEmpty())
                && (DISPOSAL_UNITS.get(level) == null || DISPOSAL_UNITS.get(level).isEmpty())
                && (CHEM_MASTERS.get(level) == null || CHEM_MASTERS.get(level).isEmpty())
                && (PROCESSING_MACHINES.get(level) == null || PROCESSING_MACHINES.get(level).isEmpty())) return;
        if(indexed==null)indexed=Collections.emptySet();
        // Snapshot is bounded by the insertion cap; stale or unloaded entries are removed without loading chunks.
        List<PowerDeviceBlockEntity> devices = new ArrayList<>();
        List<Map.Entry<BlockEntity, PowerRuntimeNode>> runtimeNodes = new ArrayList<>();
        Iterator<BlockPos> iterator = indexed.iterator();
        while (iterator.hasNext()) {
            BlockPos pos = iterator.next();
            LevelChunk chunk = loadedFullChunk(level, pos);
            if (chunk == null) { iterator.remove(); continue; }
            BlockEntity be = chunk.getBlockEntity(pos);
            if (be instanceof PowerRuntimeNode node) runtimeNodes.add(Map.entry(be, node));
            else if (be instanceof PowerDeviceBlockEntity device) devices.add(device);
            else { iterator.remove(); continue; }
        }
        List<VentilationDeviceBlockEntity> vents = new ArrayList<>();
        Set<BlockPos> indexedVents = VENTILATION_DEVICES.get(level);
        if (indexedVents != null) {
            Iterator<BlockPos> ventIterator = indexedVents.iterator();
            while (ventIterator.hasNext()) {
                BlockPos pos = ventIterator.next();
                if (!(level.getChunkSource().getChunk(pos.getX() >> 4, pos.getZ() >> 4, ChunkStatus.FULL, false) instanceof LevelChunk chunk)) { ventIterator.remove(); continue; }
                BlockEntity be = chunk.getBlockEntity(pos);
                if (!(be instanceof VentilationDeviceBlockEntity vent)
                        || !(vent.getBlockState().getBlock() instanceof VentilationDeviceBlock)) { ventIterator.remove(); continue; }
                if (vent.settingsValid()) vents.add(vent);
            }
        }
        List<AirAlarmBlockEntity> alarms = new ArrayList<>();
        Set<BlockPos> indexedAlarms = AIR_ALARMS.get(level);
        if (indexedAlarms != null) {
            Iterator<BlockPos> alarmIterator = indexedAlarms.iterator();
            while (alarmIterator.hasNext()) {
                BlockPos pos = alarmIterator.next();
                if (!(level.getChunkSource().getChunk(pos.getX() >> 4, pos.getZ() >> 4, ChunkStatus.FULL, false) instanceof LevelChunk chunk)) {
                    alarmIterator.remove(); continue;
                }
                BlockEntity be = chunk.getBlockEntity(pos);
                if (!(be instanceof AirAlarmBlockEntity alarm)
                        || !(alarm.getBlockState().getBlock() instanceof AirAlarmBlock)) {
                    alarmIterator.remove(); continue;
                }
                if (alarm.prototype() != null) alarms.add(alarm);
            }
        }

        List<AirlockBlockEntity> airlocks=new ArrayList<>(); Set<BlockPos> indexedAirlocks=AIRLOCKS.get(level);
        if(indexedAirlocks!=null){Iterator<BlockPos> it=indexedAirlocks.iterator();while(it.hasNext()){
            BlockPos pos=it.next();if(!(level.getChunkSource().getChunk(pos.getX()>>4,pos.getZ()>>4,ChunkStatus.FULL,false) instanceof LevelChunk chunk)){it.remove();continue;}
            BlockEntity be=chunk.getBlockEntity(pos);if(!(be instanceof AirlockBlockEntity door)||!(door.getBlockState().getBlock() instanceof AirlockBlock)){it.remove();continue;}
            if(door.prototype()!=null)airlocks.add(door);
        }}

        List<DisposalUnitBlockEntity> disposalUnits = new ArrayList<>();
        Set<BlockPos> indexedDisposal = DISPOSAL_UNITS.get(level);
        if (indexedDisposal != null) {
            Iterator<BlockPos> disposalIterator = indexedDisposal.iterator();
            while (disposalIterator.hasNext()) {
                BlockPos pos = disposalIterator.next();
                if (!(level.getChunkSource().getChunk(pos.getX() >> 4, pos.getZ() >> 4, ChunkStatus.FULL, false) instanceof LevelChunk chunk)) { disposalIterator.remove(); continue; }
                BlockEntity be = chunk.getBlockEntity(pos);
                if (!(be instanceof DisposalUnitBlockEntity unit)) { disposalIterator.remove(); continue; }
                if (unit.prototype() != null && unit.participatesInPowerLoad()) disposalUnits.add(unit);
            }
        }
        List<ChemMasterBlockEntity> chemMasters = new ArrayList<>();
        Set<BlockPos> indexedChemMasters = CHEM_MASTERS.get(level);
        if (indexedChemMasters != null) {
            Iterator<BlockPos> it = indexedChemMasters.iterator();
            while (it.hasNext()) {
                BlockPos pos = it.next();
                if (!(level.getChunkSource().getChunk(pos.getX() >> 4, pos.getZ() >> 4, ChunkStatus.FULL, false) instanceof LevelChunk chunk)) { it.remove(); continue; }
                BlockEntity be = chunk.getBlockEntity(pos);
                if (!(be instanceof ChemMasterBlockEntity machine) || !machine.isLoadedValid() || machine.prototype() == null) { it.remove(); continue; }
                chemMasters.add(machine);
            }
        }
        List<Object> processingMachines = new ArrayList<>();
        Set<BlockPos> processingIndex = PROCESSING_MACHINES.get(level);
        if (processingIndex != null) {
            Iterator<BlockPos> it = processingIndex.iterator();
            while (it.hasNext()) {
                BlockPos pos = it.next(); LevelChunk chunk = loadedFullChunk(level, pos);
                if (chunk == null) { it.remove(); continue; }
                BlockEntity be = chunk.getBlockEntity(pos);
                if (be instanceof ReagentGrinderBlockEntity g && g.isLoadedValid() && g.resolvedProfile() != null) processingMachines.add(g);
                else if (be instanceof SeedExtractorBlockEntity e && e.isLoadedValid() && e.resolvedProfile() != null) processingMachines.add(e);
                else if (!(be instanceof ReagentGrinderBlockEntity) && !(be instanceof SeedExtractorBlockEntity)) it.remove();
            }
        }

        Map<PowerDeviceBlockEntity, Map<CableTier, Integer>> components = new IdentityHashMap<>();
        Map<PowerDeviceBlockEntity, Map<CableTier, Boolean>> known = new IdentityHashMap<>();
        for (PowerDeviceBlockEntity device : devices) {
            Map<CableTier, Integer> deviceComponents = new EnumMap<>(CableTier.class);
            Map<CableTier, Boolean> deviceKnown = new EnumMap<>(CableTier.class);
            if (device.getBlockState().getBlock() instanceof PowerDeviceBlock block
                     && block.kind().isLamp()) {
                CableFaceNode receiver = PowerGraphService.nearestLampNode(level, device.getBlockPos());
                LoadedPowerGraph.NodeState receiverState = receiver == null
                        ? new LoadedPowerGraph.NodeState(LoadedPowerGraph.Knowledge.UNKNOWN, -1)
                        : PowerGraphService.state(level, receiver);
                boolean isKnown = receiverState.knowledge() == LoadedPowerGraph.Knowledge.KNOWN;
                deviceKnown.put(CableTier.APC, isKnown);
                deviceComponents.put(CableTier.APC, isKnown ? receiverState.componentId() : -1);
                known.put(device, deviceKnown);
                components.put(device, deviceComponents);
                continue;
            }
            for (DevicePort port : device.ports()) {
                CableFaceNode node = new CableFaceNode(port.device().relative(port.face()), port.face().getOpposite(), port.tier());
                LoadedPowerGraph.NodeState state = PowerTopology.deviceAdjacent(port, node)
                        ? PowerGraphService.state(level, node)
                        : new LoadedPowerGraph.NodeState(LoadedPowerGraph.Knowledge.UNKNOWN, -1);
                boolean isKnown = state.knowledge() == LoadedPowerGraph.Knowledge.KNOWN;
                deviceKnown.put(port.tier(), isKnown);
                deviceComponents.put(port.tier(), isKnown ? state.componentId() : -1);
            }
            known.put(device, deviceKnown);
            components.put(device, deviceComponents);
        }

        Map<BlockEntity, RuntimePortState> runtimePortStates = new IdentityHashMap<>();
        for (var entry : runtimeNodes) {
            BlockEntity entity = entry.getKey();
            PowerRuntimeNode node = entry.getValue();
            Map<CableTier, LoadedPowerGraph.NodeState> portStates = new EnumMap<>(CableTier.class);
            Map<CableTier, Integer> portCounts = new EnumMap<>(CableTier.class);
            List<DevicePort> ports = node.powerRuntimePorts();
            if (ports != null) for (DevicePort port : ports) {
                if (port == null || !port.device().equals(entity.getBlockPos())) continue;
                portCounts.merge(port.tier(), 1, Integer::sum);
                CableFaceNode faceNode = new CableFaceNode(port.device().relative(port.face()),
                        port.face().getOpposite(), port.tier());
                LoadedPowerGraph.NodeState state = PowerTopology.deviceAdjacent(port, faceNode)
                        ? PowerGraphService.state(level, faceNode)
                        : new LoadedPowerGraph.NodeState(LoadedPowerGraph.Knowledge.UNKNOWN, -1);
                portStates.put(port.tier(), state);
            }
            runtimePortStates.put(entity, new RuntimePortState(node, portStates, portCounts));
        }

        List<PowerDeviceBlockEntity> sources = byKind(devices, PowerDeviceKind.HV_SOURCE);
        List<PowerDeviceBlockEntity> substations = byKind(devices, PowerDeviceKind.HV_MV_SUBSTATION);
        List<PowerDeviceBlockEntity> apcs = byKind(devices, PowerDeviceKind.APC);
        List<PowerDeviceBlockEntity> lamps = devices.stream().filter(device ->
                device.getBlockState().getBlock() instanceof PowerDeviceBlock block && block.kind().isLamp()).toList();
        Map<AirAlarmBlockEntity, PowerDeviceBlockEntity> alarmProviders = new IdentityHashMap<>();
        Map<AirAlarmBlockEntity, LoadedPowerGraph.NodeState> alarmStates = new IdentityHashMap<>();
        for (AirAlarmBlockEntity alarm : alarms) {
            LoadedPowerGraph.NodeState state = alarmReceiverState(level, alarm);
            alarmStates.put(alarm, state);
            if (PowerGraphService.topologyReady(level) && state.knowledge() == LoadedPowerGraph.Knowledge.KNOWN) {
                List<PowerDeviceBlockEntity> eligible = apcs.stream().filter(candidate ->
                        isKnown(known, candidate, CableTier.APC)
                                && component(components, candidate, CableTier.APC) == state.componentId()).toList();
                if (eligible.size() == 1 && eligible.get(0).breakerClosed())
                    alarmProviders.put(alarm, eligible.get(0));
            }
        }

        Map<AirlockBlockEntity,PowerDeviceBlockEntity> airlockProviders=new IdentityHashMap<>();
        Map<AirlockBlockEntity,LoadedPowerGraph.NodeState> airlockStates=new IdentityHashMap<>();
        for(AirlockBlockEntity door:airlocks){LoadedPowerGraph.NodeState state=airlockReceiverState(level,door);airlockStates.put(door,state);
            if(PowerGraphService.topologyReady(level)&&state.knowledge()==LoadedPowerGraph.Knowledge.KNOWN){
                List<PowerDeviceBlockEntity> eligible=apcs.stream().filter(candidate->isKnown(known,candidate,CableTier.APC)&&component(components,candidate,CableTier.APC)==state.componentId()).toList();
                if(eligible.size()==1&&eligible.get(0).breakerClosed())airlockProviders.put(door,eligible.get(0));
            }
        }

        Map<DisposalUnitBlockEntity, LoadedPowerGraph.NodeState> disposalStates = new IdentityHashMap<>();
        Map<DisposalUnitBlockEntity, PowerDeviceBlockEntity> disposalProviders = new IdentityHashMap<>();
        for (DisposalUnitBlockEntity unit : disposalUnits) {
            var state = disposalReceiverState(level, unit);
            disposalStates.put(unit, state);
            if (PowerGraphService.topologyReady(level) && state.knowledge() == LoadedPowerGraph.Knowledge.KNOWN) {
                List<PowerDeviceBlockEntity> eligible = apcs.stream().filter(candidate -> isKnown(known, candidate, CableTier.APC)
                        && component(components, candidate, CableTier.APC) == state.componentId()).toList();
                if (eligible.size() == 1 && eligible.get(0).breakerClosed()) disposalProviders.put(unit, eligible.get(0));

            }
        }
        Map<Object, LoadedPowerGraph.NodeState> processingStates = new IdentityHashMap<>();
        Map<Object, PowerDeviceBlockEntity> processingProviders = new IdentityHashMap<>();
        for (Object machine : processingMachines) {
            if (processingDemand(machine) <= 0) continue;
            CableFaceNode receiver = PowerGraphService.nearestApcExtensionNode(level, processingPos(machine));
            LoadedPowerGraph.NodeState state = receiver == null ? new LoadedPowerGraph.NodeState(LoadedPowerGraph.Knowledge.UNKNOWN, -1) : PowerGraphService.state(level, receiver);
            processingStates.put(machine, state);
            if (PowerGraphService.topologyReady(level) && state.knowledge() == LoadedPowerGraph.Knowledge.KNOWN) {
                List<PowerDeviceBlockEntity> eligible = apcs.stream().filter(candidate -> isKnown(known, candidate, CableTier.APC)
                        && component(components, candidate, CableTier.APC) == state.componentId()).toList();
                if (eligible.size() == 1 && eligible.get(0).breakerClosed()) processingProviders.put(machine, eligible.get(0));
            }
        }
        Map<ChemMasterBlockEntity, LoadedPowerGraph.NodeState> chemMasterStates = new IdentityHashMap<>();
        Map<ChemMasterBlockEntity, PowerDeviceBlockEntity> chemMasterProviders = new IdentityHashMap<>();
        for (ChemMasterBlockEntity machine : chemMasters) {
            CableFaceNode receiver = PowerGraphService.nearestApcExtensionNode(level, machine.getBlockPos(), machine.cableReach());
            LoadedPowerGraph.NodeState state = receiver == null ? new LoadedPowerGraph.NodeState(LoadedPowerGraph.Knowledge.UNKNOWN, -1) : PowerGraphService.state(level, receiver);
            chemMasterStates.put(machine, state);
            if (PowerGraphService.topologyReady(level) && state.knowledge() == LoadedPowerGraph.Knowledge.KNOWN) {
                List<PowerDeviceBlockEntity> eligible = apcs.stream().filter(candidate -> isKnown(known, candidate, CableTier.APC)
                        && component(components, candidate, CableTier.APC) == state.componentId()).toList();
                if (eligible.size() == 1 && eligible.get(0).breakerClosed()) chemMasterProviders.put(machine, eligible.get(0));
            }
        }
        List<PowerLiveLoop.Source> sourceOffers = new ArrayList<>(sources.stream().map(source -> new PowerLiveLoop.Source(id(source), component(components, source, CableTier.HV),
                        isKnown(known, source, CableTier.HV), SOURCE_WATTS)).toList());
        List<PowerLiveLoop.MvConsumer> mvConsumers = new ArrayList<>();
        List<PowerLiveLoop.ApcConsumer> apcConsumers = new ArrayList<>();
        Map<BlockEntity, Double> mvDemands = new IdentityHashMap<>();
        Map<BlockEntity, Double> apcDemands = new IdentityHashMap<>();
        Set<BlockEntity> conflictingNodes = Collections.newSetFromMap(new IdentityHashMap<>());
        for (var entry : runtimeNodes) {
            BlockEntity entity = entry.getKey();
            RuntimePortState portState = runtimePortStates.get(entity);
            PowerRuntimeNode node = entry.getValue();
            PowerRuntimeNode.SupplierOffer offer = node.powerSupplierOffer();
            Double demand = node.mvPowerDemandWatts();
            Double apcDemand = node.apcPowerDemandWatts();
            boolean conflictingRoles = (offer != null ? 1 : 0) + (demand != null ? 1 : 0) + (apcDemand != null ? 1 : 0) > 1;
            if (conflictingRoles) conflictingNodes.add(entity);
            if (offer != null && !conflictingRoles && portState.hasExactlyOne(CableTier.HV)) {
                LoadedPowerGraph.NodeState state = portState.states().get(CableTier.HV);
                if (Double.isFinite(offer.currentWatts()) && offer.currentWatts() >= 0
                        && Double.isFinite(offer.ratedMaximumWatts()) && offer.ratedMaximumWatts() >= 0)
                    sourceOffers.add(new PowerLiveLoop.Source(id(entity), state.componentId(),
                            state.knowledge() == LoadedPowerGraph.Knowledge.KNOWN,
                            offer.currentWatts(), offer.ratedMaximumWatts()));
            }
            if (demand != null) {
                mvDemands.put(entity, demand);
                boolean valid = !conflictingRoles && Double.isFinite(demand) && demand >= 0
                        && portState.hasExactlyOne(CableTier.MV);
                LoadedPowerGraph.NodeState state = portState.states().get(CableTier.MV);
                boolean isKnown = valid && state.knowledge() == LoadedPowerGraph.Knowledge.KNOWN;
                mvConsumers.add(new PowerLiveLoop.MvConsumer(id(entity), isKnown ? state.componentId() : -1,
                        isKnown, valid ? demand : 0));
            }
            if (apcDemand != null) {
                apcDemands.put(entity, apcDemand);
                boolean valid = !conflictingRoles && Double.isFinite(apcDemand) && apcDemand >= 0 && portState.hasExactlyOne(CableTier.APC);
                LoadedPowerGraph.NodeState state = portState.states().get(CableTier.APC);
                boolean hasProvider = valid && state.knowledge() == LoadedPowerGraph.Knowledge.KNOWN
                        && apcs.stream().anyMatch(apc -> isKnown(known, apc, CableTier.APC)
                        && component(components, apc, CableTier.APC) == state.componentId() && apc.breakerClosed());
                boolean isKnown = valid && state.knowledge() == LoadedPowerGraph.Knowledge.KNOWN && hasProvider;
                apcConsumers.add(new PowerLiveLoop.ApcConsumer(id(entity), isKnown ? state.componentId() : -1, isKnown, valid ? apcDemand : 0));
            }
        }
        var input = new PowerLiveLoop.Input(sourceOffers,
                substations.stream().map(sub -> new PowerLiveLoop.Substation(id(sub),
                        component(components, sub, CableTier.HV), isKnown(known, sub, CableTier.HV),
                        component(components, sub, CableTier.MV), isKnown(known, sub, CableTier.MV))).toList(),
                apcs.stream().map(apc -> new PowerLiveLoop.Apc(id(apc), component(components, apc, CableTier.MV),
                        isKnown(known, apc, CableTier.MV), component(components, apc, CableTier.APC),
                        isKnown(known, apc, CableTier.APC), apc.breakerClosed(), apc.energyJoules())).toList(),
                lamps.stream().map(lamp -> new PowerLiveLoop.Lamp(id(lamp), component(components, lamp, CableTier.APC),
                        isKnown(known, lamp, CableTier.APC), ((PowerDeviceBlock) lamp.getBlockState().getBlock()).kind())).toList(),
                vents.stream().map(vent -> {
                    LoadedPowerGraph.NodeState state = ventilationReceiverState(level, vent);
                    return new PowerLiveLoop.VentLoad(id(vent), state.componentId(),
                            state.knowledge() == LoadedPowerGraph.Knowledge.KNOWN,
                            vent.prototype().apcPowerReceiver().requestedWatts());
                }).toList(),
                alarms.stream().map(alarm -> {
                    LoadedPowerGraph.NodeState state = alarmStates.get(alarm);
                    return new PowerLiveLoop.AlarmLoad(id(alarm), state.componentId(),
                            alarmProviders.containsKey(alarm), alarm.prototype().apcPowerReceiver().requestedWatts());
                }).toList(),
                airlocks.stream().filter(door -> !door.state().receiverPowerDisabled()).map(door -> {
                    LoadedPowerGraph.NodeState state = airlockStates.get(door);
                    return new PowerLiveLoop.AirlockLoad(id(door), state.componentId(), airlockProviders.containsKey(door),
                            door.prototype().apcPowerReceiver().requestedWatts());
                }).toList(),
                disposalUnits.stream().map(unit -> {
                    var state = disposalStates.get(unit);
                    return new PowerLiveLoop.DisposalLoad(id(unit), state.componentId(), disposalProviders.containsKey(unit), unit.prototype().powerWatts());
                }).toList(),
                chemMasters.stream().map(machine -> {
                    var state = chemMasterStates.get(machine);
                    return new PowerLiveLoop.ChemMasterLoad(machine.placementIdentity().toString(), state.componentId(),
                            chemMasterProviders.containsKey(machine), machine.powerLoadWatts());
                }).toList(), mvConsumers, apcConsumers,
                processingMachines.stream().filter(machine -> processingDemand(machine) > 0).map(machine -> {
                    var state = processingStates.get(machine);
                    return new PowerLiveLoop.ProcessingLoad(processingIdentity(machine).toString(),
                            state == null ? -1 : state.componentId(), processingProviders.containsKey(machine), processingDemand(machine));
                }).toList(), elapsedTicks);
        PowerLiveLoop.Result result = PowerLiveLoop.solve(input);
        Map<BlockPos, MvRuntimeMeterSample> runtimeMeters = MV_RUNTIME_METERS.computeIfAbsent(level, ignored -> new HashMap<>());
        runtimeMeters.clear();
        long runtimeTopologyGeneration = PowerGraphService.topologyGeneration(level);
        for (var entry : runtimeNodes) {
            BlockEntity entity = entry.getKey();
            PowerRuntimeNode node = entry.getValue();
            Double demand = mvDemands.get(entity);
            if (demand == null) {
                node.receiveMvPowerAllocation(0, false, sampleTick);
                continue;
            }
            RuntimePortState portState = runtimePortStates.get(entity);
            LoadedPowerGraph.NodeState state = portState.states().get(CableTier.MV);
            boolean graphKnown = !conflictingNodes.contains(entity) && Double.isFinite(demand) && demand >= 0
                    && portState.hasExactlyOne(CableTier.MV)
                    && PowerGraphService.topologyReady(level) && state.knowledge() == LoadedPowerGraph.Knowledge.KNOWN;
            double actual = graphKnown ? result.mvConsumerWatts().getOrDefault(id(entity), 0.0) : 0;
            runtimeMeters.put(entity.getBlockPos().immutable(), new MvRuntimeMeterSample(sampleTick, runtimeTopologyGeneration,
                    actual, demand, node, entity, graphKnown));
            node.receiveMvPowerAllocation(actual, graphKnown, sampleTick);
        }
        Map<BlockPos, ApcRuntimeMeterSample> apcRuntimeMeters = APC_RUNTIME_METERS.computeIfAbsent(level, ignored -> new HashMap<>());
        apcRuntimeMeters.clear();
        for (var entry : runtimeNodes) {
            BlockEntity entity = entry.getKey(); PowerRuntimeNode node = entry.getValue();
            Double demand = apcDemands.get(entity);
            if (demand == null) { node.receiveApcPowerAllocation(0, false, sampleTick); continue; }
            RuntimePortState portState = runtimePortStates.get(entity);
            LoadedPowerGraph.NodeState state = portState.states().get(CableTier.APC);
            boolean hasProvider = apcs.stream().anyMatch(apc -> isKnown(known, apc, CableTier.APC)
                    && component(components, apc, CableTier.APC) == state.componentId() && apc.breakerClosed());
            boolean graphKnown = !conflictingNodes.contains(entity) && Double.isFinite(demand) && demand >= 0
                    && portState.hasExactlyOne(CableTier.APC) && PowerGraphService.topologyReady(level)
                    && state.knowledge() == LoadedPowerGraph.Knowledge.KNOWN && hasProvider;
            double actual = graphKnown ? result.apcConsumerWatts().getOrDefault(id(entity), 0.0) : 0;
            java.util.UUID identity = node.apcPowerIdentity();
            if (identity != null) apcRuntimeMeters.put(entity.getBlockPos().immutable(), new ApcRuntimeMeterSample(
                    sampleTick, runtimeTopologyGeneration, actual, demand, node, entity, identity, graphKnown));
            node.receiveApcPowerAllocation(actual, graphKnown, sampleTick);
        }
        Map<BlockPos, AlarmMeterSample> alarmMeters = ALARM_METERS.computeIfAbsent(level, ignored -> new HashMap<>());
        alarmMeters.clear();
        for (AirAlarmBlockEntity alarm : alarms) {
            PowerDeviceBlockEntity provider = alarmProviders.get(alarm);
            if (provider != null) alarmMeters.put(alarm.getBlockPos().immutable(),
                    new AlarmMeterSample(sampleTick, PowerGraphService.topologyGeneration(level),
                            result.alarmWatts().getOrDefault(id(alarm), 0.0), alarm,
                            alarm.prototype(), provider, provider.breakerRevision()));
        }

        Map<BlockPos,AirlockMeterSample> airlockMeters=AIRLOCK_METERS.computeIfAbsent(level,ignored->new HashMap<>());airlockMeters.clear();
        for(AirlockBlockEntity door:airlocks){PowerDeviceBlockEntity provider=airlockProviders.get(door);double delivery=result.airlockWatts().getOrDefault(id(door),0.0);
             if(provider!=null||PowerGraphService.topologyReady(level)&&airlockStates.get(door).knowledge()==LoadedPowerGraph.Knowledge.KNOWN)airlockMeters.put(door.getBlockPos().immutable(),new AirlockMeterSample(sampleTick,PowerGraphService.topologyGeneration(level),delivery,door,door.prototype(),provider,provider==null?-1:provider.breakerRevision()));
            if(!door.state().receiverPowerDisabled())door.setPowered(provider!=null&&delivery==door.prototype().apcPowerReceiver().requestedWatts());
        }

        Map<BlockPos, DisposalMeterSample> disposalMeters = DISPOSAL_METERS.computeIfAbsent(level, ignored -> new HashMap<>());
        disposalMeters.clear();
        for (DisposalUnitBlockEntity unit : disposalUnits) {
            PowerDeviceBlockEntity provider = disposalProviders.get(unit);
            if (provider != null) disposalMeters.put(unit.getBlockPos().immutable(), new DisposalMeterSample(sampleTick,
                    PowerGraphService.topologyGeneration(level), result.disposalWatts().getOrDefault(id(unit), 0.0),
                    unit, unit.prototype(), provider, provider.breakerRevision()));
        }
        Map<BlockPos, ChemMasterMeterSample> chemMeters = CHEM_MASTER_METERS.computeIfAbsent(level, ignored -> new HashMap<>());
        chemMeters.clear();
        for (ChemMasterBlockEntity machine : chemMasters) {
            PowerDeviceBlockEntity provider = chemMasterProviders.get(machine);
            LoadedPowerGraph.NodeState state = chemMasterStates.get(machine);
            if (provider != null || PowerGraphService.topologyReady(level) && state.knowledge() == LoadedPowerGraph.Knowledge.KNOWN)
                chemMeters.put(machine.getBlockPos().immutable(), new ChemMasterMeterSample(sampleTick,
                        PowerGraphService.topologyGeneration(level), result.chemMasterWatts().getOrDefault(machine.placementIdentity().toString(), 0.0),
                        machine, machine.placementIdentity(), machine.powerLoadWatts(), provider,
                        provider == null ? -1 : provider.breakerRevision()));
        }
        Map<BlockPos, ProcessingMeterSample> processingMeters = PROCESSING_METERS.computeIfAbsent(level, ignored -> new HashMap<>());
        processingMeters.clear();
        for (Object machine : processingMachines) {
            double demand = processingDemand(machine);
            if (demand <= 0) continue;
            PowerDeviceBlockEntity provider = processingProviders.get(machine);
            LoadedPowerGraph.NodeState state = processingStates.get(machine);
            if (provider != null || PowerGraphService.topologyReady(level) && state != null && state.knowledge() == LoadedPowerGraph.Knowledge.KNOWN)
                processingMeters.put(processingPos(machine).immutable(), new ProcessingMeterSample(sampleTick, PowerGraphService.topologyGeneration(level),
                        result.processingWatts().getOrDefault(processingIdentity(machine).toString(), 0.0), machine, processingIdentity(machine), demand,
                        provider, provider == null ? -1 : provider.breakerRevision()));
        }
        for (DisposalUnitBlockEntity unit : disposalUnits) {
            var meter = disposalUnitMeter(level, unit);
            unit.updatePower(meter.known() && meter.deliveredWatts() == unit.prototype().powerWatts());
        }
        Map<BlockPos, VentMeterSample> ventMeters = VENT_METERS.computeIfAbsent(level, ignored -> new HashMap<>());
        ventMeters.clear();
        Map<BlockPos, VentilationPaidInterval.ReceiverSignature> previousSignatures = PREVIOUS_VENT_SIGNATURES.computeIfAbsent(level, ignored -> new HashMap<>());
        Map<BlockPos, VentilationPaidInterval.ReceiverSignature> currentSignatures = new HashMap<>();
        Map<BlockPos, VentilationPaidInterval.WorkBudget> newBudgets = new HashMap<>();
        Map<BlockPos, Double> ventDelivery = new HashMap<>();
        for (VentilationDeviceBlockEntity vent : vents) {
            LoadedPowerGraph.NodeState state = ventilationReceiverState(level, vent);
            PowerDeviceBlockEntity apc = null;
            if (state.knowledge() == LoadedPowerGraph.Knowledge.KNOWN) {
                // Provider allocation is not attributable per load yet; fail closed on shared APC components.
                List<PowerDeviceBlockEntity> eligible = apcs.stream().filter(candidate -> isKnown(known, candidate, CableTier.APC)
                        && component(components, candidate, CableTier.APC) == state.componentId()).toList();
                if (eligible.size() == 1) apc = eligible.get(0);
            }
            BlockPos ventPos = vent.getBlockPos().immutable();
            long generation = PowerGraphService.topologyGeneration(level);
            boolean ready = PowerGraphService.topologyReady(level) && state.knowledge() == LoadedPowerGraph.Knowledge.KNOWN;
            if (apc != null && apc.breakerClosed()) {
                double delivered = result.ventWatts().getOrDefault(id(vent), 0.0);
                ventMeters.put(ventPos, new VentMeterSample(sampleTick, generation, delivered, vent, apc, apc.breakerRevision()));
                ventDelivery.put(ventPos, delivered);
                currentSignatures.put(ventPos, new VentilationPaidInterval.ReceiverSignature(vent, apc,
                        apc.breakerRevision(), generation, ready, ready, apc.breakerClosed(), sampleTick));
            }
        }
        Map<BlockPos, CompletedVentBudget> budgets = COMPLETED_VENT_BUDGETS.computeIfAbsent(level, ignored -> new HashMap<>());
        budgets.clear();
        Map<BlockPos, ApcOutputSample> samples = APC_OUTPUT_SAMPLES.computeIfAbsent(level,
                ignored -> new HashMap<>());
        for (PowerDeviceBlockEntity apc : apcs) {
            boolean graphKnown = isKnown(known, apc, CableTier.MV) && isKnown(known, apc, CableTier.APC);
            String apcId = id(apc);
            double output = result.apcOutputWatts().getOrDefault(apcId, 0.0);
            ApcVisualState visual = ApcVisualState.fromSolve(isKnown(known, apc, CableTier.MV),
                    isKnown(known, apc, CableTier.APC), result.apcInputWatts().getOrDefault(apcId, 0.0),
                    output, apc.energyJoules(), result.batteryEnergyJoules().getOrDefault(apcId, apc.energyJoules()));
            samples.put(apc.getBlockPos().immutable(), new ApcOutputSample(sampleTick,
                    PowerGraphService.topologyGeneration(level), graphKnown, output,
                    apc, apc.breakerRevision(), visual, sampleTick, PowerGraphService.topologyGeneration(level)));
        }
        projectPowerDeviceAppearances(level, devices, sources, substations, components, known, result);
        LAST_DIAGNOSTIC.put(level, "sources=" + input.sources().size() + ",substations=" + input.substations().size()
                + ",apcs=" + input.apcs().size() + ",disposal=" + disposalUnits.stream().map(unit -> {
                    var state = disposalStates.get(unit);
                    var receiver = PowerGraphService.nearestApcExtensionNode(level, unit.getBlockPos());
                    List<PowerDeviceBlockEntity> matches = apcs.stream().filter(candidate ->
                            isKnown(known, candidate, CableTier.APC)
                                    && component(components, candidate, CableTier.APC) == state.componentId()).toList();
                    var sample = disposalMeters.get(unit.getBlockPos());
                    return unit.getBlockPos().toShortString() + ":" + (indexedDisposal != null && indexedDisposal.contains(unit.getBlockPos()))
                            + "/" + unit.participatesInPowerLoad() + "/t" + PowerGraphService.topologyReady(level)
                            + "/rx" + (receiver == null ? "-" : receiver.host().toShortString() + "/" + receiver.face())
                            + "/" + state.knowledge() + "/c" + state.componentId()
                             + "/a" + matches.size() + "/s" + (sample == null ? "-" : sample.sampleTick() + "g"
                                     + sample.topologyGeneration() + ":" + sample.deliveredWatts())
                             + "/gate" + disposalUnitMeterDiagnostic(level, unit)
                             + "/r" + unit.prototype().powerWatts();
                }).limit(2).toList() + "," + PowerGraphService.diagnosticSnapshot(level));
        for (PowerDeviceBlockEntity apc : apcs)
            apc.setEnergyJoules(result.batteryEnergyJoules().getOrDefault(id(apc), apc.energyJoules()));
        VentilationPaidInterval paid = PAID_INTERVALS.computeIfAbsent(level, ignored -> new VentilationPaidInterval());
        for (var entry : currentSignatures.entrySet()) {
            var current = entry.getValue();
            var previous = previousSignatures.get(entry.getKey());
            Optional<VentilationPaidInterval.WorkBudget> authorized = paid.authorizeCompletedInterval(previous, current,
                    ventDelivery.getOrDefault(entry.getKey(), 0.0), elapsedTicks);
            authorized.ifPresent(budget -> budgets.put(entry.getKey(), new CompletedVentBudget(sampleTick, budget,
                    (PowerDeviceBlockEntity) current.apcBE(), current.breakerRevision(), current.topologyGeneration())));
        }
        previousSignatures.clear(); previousSignatures.putAll(currentSignatures);
        // Each lamp has exactly one final projection, regardless of how many APCs share its component.
        for (PowerDeviceBlockEntity lamp : lamps)
            setLit(level, lamp.getBlockPos(), result.lampWatts().getOrDefault(id(lamp), 0.0)
                    >= ((PowerDeviceBlock) lamp.getBlockState().getBlock()).kind().lampDemandWatts());
        // Gas work is authorized only after APC battery billing and single-use budgets exist.
        for (VentilationDeviceBlockEntity vent : vents)
            VentilationGasRuntime.onCompletedPaidSolve(level, vent);
    }

    /** Project native display states from the authoritative solve; DISPLAY never changes topology. */
    private static void projectPowerDeviceAppearances(ServerLevel level, List<PowerDeviceBlockEntity> devices,
            List<PowerDeviceBlockEntity> sources, List<PowerDeviceBlockEntity> substations,
            Map<PowerDeviceBlockEntity, Map<CableTier, Integer>> components,
            Map<PowerDeviceBlockEntity, Map<CableTier, Boolean>> known, PowerLiveLoop.Result result) {
        var catalog = ModPowerAppearances.catalog(level);
        PowerAppearanceData sourceProfile = catalog.get(ModPowerAppearances.SOURCE);
        for (PowerDeviceBlockEntity source : sources) {
            if (sourceProfile == null) continue;
            boolean supplying = isKnown(known, source, CableTier.HV) && substations.stream().anyMatch(sub ->
                    isKnown(known, sub, CableTier.HV)
                            && component(components, source, CableTier.HV) == component(components, sub, CableTier.HV)
                            && result.substationInputWatts().getOrDefault(id(sub), 0.0) > 0);
            updateDisplay(level, source, sourceProfile, supplying ? sourceProfile.active() : sourceProfile.inactive());
        }
        PowerAppearanceData substationProfile = catalog.get(ModPowerAppearances.SUBSTATION);
        for (PowerDeviceBlockEntity sub : substations) {
            if (substationProfile == null) continue;
            boolean converting = isKnown(known, sub, CableTier.HV) && isKnown(known, sub, CableTier.MV)
                    && result.substationInputWatts().getOrDefault(id(sub), 0.0) > 0;
            updateDisplay(level, sub, substationProfile, converting ? substationProfile.active() : substationProfile.inactive());
        }
        PowerAppearanceData apcProfile = catalog.get(ModPowerAppearances.APC);
        for (PowerDeviceBlockEntity apc : devices) {
            if (apcProfile == null) continue;
            if (!(apc.getBlockState().getBlock() instanceof PowerDeviceBlock block) || block.kind() != PowerDeviceKind.APC) continue;
            ApcOutputSample sample = APC_OUTPUT_SAMPLES.getOrDefault(level, Map.of()).get(apc.getBlockPos());
            ApcVisualState visual = sample == null || sample.device() != apc ? ApcVisualState.UNKNOWN : sample.visualState();
            String state = switch (visual) {
                case FULL -> apcProfile.full(); case CHARGING -> apcProfile.charging();
                case LACK, UNKNOWN -> apcProfile.lack();
            };
            updateDisplay(level, apc, apcProfile, state);
            // Battery energy remains available with the breaker open. Live input also counts as
            // powered for a frame where incoming supply is consumed without net charge gain.
            boolean powered = result.batteryEnergyJoules().getOrDefault(id(apc), apc.energyJoules()) > 0
                    || result.apcInputWatts().getOrDefault(id(apc), 0.0) > 0;
            updateApcGlow(level, apc, powered, powered ? Math.max(0, Math.min(15, apcProfile.lightLevel())) : 0);
        }
    }

    private static void updateApcGlow(ServerLevel level, PowerDeviceBlockEntity device, boolean powered, int emission) {
        BlockState current = device.getBlockState();
        if (current.getBlock() instanceof PowerDeviceBlock block && block.kind() == PowerDeviceKind.APC
                && (current.getValue(PowerDeviceBlock.GLOW) != powered || current.getValue(PowerDeviceBlock.EMISSION) != emission))
            level.setBlock(device.getBlockPos(), current.setValue(PowerDeviceBlock.GLOW, powered)
                    .setValue(PowerDeviceBlock.EMISSION, emission), 3);
    }

    private static void updateDisplay(ServerLevel level, PowerDeviceBlockEntity device,
                                      PowerAppearanceData profile, String value) {
        if (profile == null || value == null || !level.hasChunkAt(device.getBlockPos())) return;
        try {
            PowerDeviceBlock.Display display = PowerDeviceBlock.Display.valueOf(value.toUpperCase(Locale.ROOT));
            BlockState current = device.getBlockState();
            if (current.getBlock() instanceof PowerDeviceBlock && current.getValue(PowerDeviceBlock.DISPLAY) != display)
                level.setBlock(device.getBlockPos(), current.setValue(PowerDeviceBlock.DISPLAY, display), 3);
        } catch (IllegalArgumentException ignored) { /* invalid profile mappings fail closed without changing the machine */ }
    }

    /** Consume cached solve meters only; this per-tick pass performs no topology query or solve. */
    private static void observeApcOutputs(ServerLevel level, long serverTick) {
        Map<BlockPos, ApcOutputSample> samples = APC_OUTPUT_SAMPLES.get(level);
        if (samples == null || samples.isEmpty()) return;
        Iterator<Map.Entry<BlockPos, ApcOutputSample>> iterator = samples.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<BlockPos, ApcOutputSample> entry = iterator.next();
            BlockPos pos = entry.getKey();
            LevelChunk chunk = loadedFullChunk(level, pos);
            if (chunk == null) { iterator.remove(); continue; }
            BlockEntity be = chunk.getBlockEntity(pos);
            if (!(be instanceof PowerDeviceBlockEntity apc)
                    || !(be.getBlockState().getBlock() instanceof PowerDeviceBlock block)
                    || block.kind() != PowerDeviceKind.APC) { iterator.remove(); continue; }
            ApcOutputSample sample = entry.getValue();
            boolean knownAndFresh = sample != null && sample.graphKnown()
                    && sample.topologyGeneration() == PowerGraphService.topologyGeneration(level)
                    && serverTick >= sample.sampleTick() && serverTick - sample.sampleTick() <= TICKS_PER_SOLVE;
            if (!knownAndFresh && sample != null
                    && sample.topologyGeneration() != PowerGraphService.topologyGeneration(level))
                RESOLVE_AFTER_TOPOLOGY.add(level);
            boolean wasClosed = apc.breakerClosed();
            apc.observeActualApcOutput(serverTick, sample == null ? Long.MIN_VALUE : sample.sampleTick(),
                    knownAndFresh, sample == null ? 0 : sample.outputWatts());
            if (!apc.breakerClosed()) entry.setValue(new ApcOutputSample(serverTick,
                    PowerGraphService.topologyGeneration(level), sample != null && sample.graphKnown(), 0,
                    apc, sample == null ? apc.breakerRevision() : sample.breakerRevision(),
                    sample == null ? ApcVisualState.UNKNOWN : sample.visualState(),
                    sample == null ? serverTick : sample.visualTick(),
                    sample == null ? PowerGraphService.topologyGeneration(level) : sample.visualGeneration()));
            if (wasClosed && !apc.breakerClosed()) RESOLVE_AFTER_TRIP.add(level);
        }
    }

    private static List<PowerDeviceBlockEntity> byKind(List<PowerDeviceBlockEntity> devices, PowerDeviceKind kind) {
        return devices.stream().filter(device -> device.getBlockState().getBlock() instanceof PowerDeviceBlock block && block.kind() == kind).toList();
    }
    private static String id(PowerDeviceBlockEntity device) { return device.getBlockPos().toShortString(); }
    private static String id(BlockEntity device) { return device.getBlockPos().toShortString(); }
    private static String id(VentilationDeviceBlockEntity device) { return device.getBlockPos().toShortString(); }
    private static String id(AirAlarmBlockEntity device) { return device.getBlockPos().toShortString(); }

    private static String id(AirlockBlockEntity device) { return "airlock-"+device.getBlockPos().toShortString(); }

    private static String id(DisposalUnitBlockEntity device) { return device.getBlockPos().toShortString(); }
    private static BlockPos processingPos(Object machine) { return machine instanceof ReagentGrinderBlockEntity g ? g.getBlockPos() : ((SeedExtractorBlockEntity) machine).getBlockPos(); }
    private static java.util.UUID processingIdentity(Object machine) { return machine instanceof ReagentGrinderBlockEntity g ? g.placementIdentity() : ((SeedExtractorBlockEntity) machine).placementIdentity(); }
    private static double processingDemand(Object machine) { return machine instanceof ReagentGrinderBlockEntity g ? g.requiredPowerWatts() : (((SeedExtractorBlockEntity) machine).resolvedProfile().powered() ? ((SeedExtractorBlockEntity) machine).resolvedProfile().powerWatts() : 0d); }

    private static LoadedPowerGraph.NodeState alarmReceiverState(ServerLevel level, AirAlarmBlockEntity alarm) {
        AirAlarmData prototype = alarm.prototype();
        CableFaceNode node = prototype == null ? null : PowerGraphService.nearestApcExtensionNode(level,
                alarm.getBlockPos(), prototype.extensionCableReceiver().localReachBlocks());
        return node == null ? new LoadedPowerGraph.NodeState(LoadedPowerGraph.Knowledge.UNKNOWN, -1)
                : PowerGraphService.state(level, node);
    }
    private static LoadedPowerGraph.NodeState ventilationReceiverState(ServerLevel level, VentilationDeviceBlockEntity vent) {
        var prototype = vent.prototype();
        CableFaceNode receiver = prototype == null ? null : PowerGraphService.nearestApcExtensionNode(level,
                vent.getBlockPos(), prototype.extensionCableReceiver().localReachBlocks());
        return receiver == null
                ? new LoadedPowerGraph.NodeState(LoadedPowerGraph.Knowledge.UNKNOWN, -1)
                : PowerGraphService.state(level, receiver);
    }

    private static LoadedPowerGraph.NodeState airlockReceiverState(ServerLevel level,AirlockBlockEntity door) {
        var prototype=door.prototype(); if(prototype==null)return new LoadedPowerGraph.NodeState(LoadedPowerGraph.Knowledge.UNKNOWN,-1);
         var nearby=PowerGraphService.nodesNear(level,door.getBlockPos(),prototype.extensionCableReceiver().localReachBlocks(),CableTier.APC);
        if(nearby==null)return new LoadedPowerGraph.NodeState(LoadedPowerGraph.Knowledge.UNKNOWN,-1);
        CableFaceNode node=nearby.stream().filter(candidate->PowerGraphService.state(level,candidate).knowledge()==LoadedPowerGraph.Knowledge.KNOWN)
                .min(Comparator.comparingDouble(candidate->candidate.host().distSqr(door.getBlockPos()))).orElse(null);
        return node==null?new LoadedPowerGraph.NodeState(LoadedPowerGraph.Knowledge.KNOWN,-1):PowerGraphService.state(level,node);
    }
    /** Return an already-loaded full chunk only; periodic power work must never request a chunk ticket. */
    private static LevelChunk loadedFullChunk(ServerLevel level, BlockPos pos) {
        return level.getChunkSource().getChunk(pos.getX() >> 4, pos.getZ() >> 4,
                ChunkStatus.FULL, false) instanceof LevelChunk chunk ? chunk : null;
    }

    private static LoadedPowerGraph.NodeState disposalReceiverState(ServerLevel level, DisposalUnitBlockEntity unit) {
        var receiver = PowerGraphService.nearestApcExtensionNode(level, unit.getBlockPos());
        return receiver == null ? new LoadedPowerGraph.NodeState(LoadedPowerGraph.Knowledge.UNKNOWN, -1)
                : PowerGraphService.state(level, receiver);
    }
    private static boolean isKnown(Map<PowerDeviceBlockEntity, Map<CableTier, Boolean>> known,
                                   PowerDeviceBlockEntity device, CableTier tier) {
        return known.getOrDefault(device, Map.of()).getOrDefault(tier, false);
    }
    private static int component(Map<PowerDeviceBlockEntity, Map<CableTier, Integer>> components,
                                 PowerDeviceBlockEntity device, CableTier tier) {
        return components.getOrDefault(device, Map.of()).getOrDefault(tier, -1);
    }
    private static void setLit(ServerLevel level, BlockPos pos, boolean lit) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof PowerDeviceBlock && state.getValue(PowerDeviceBlock.LIT) != lit)
            level.setBlock(pos, state.setValue(PowerDeviceBlock.LIT, lit), 3);
    }

}

