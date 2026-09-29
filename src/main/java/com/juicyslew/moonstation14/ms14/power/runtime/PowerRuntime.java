package com.juicyslew.moonstation14.ms14.power.runtime;

import com.juicyslew.moonstation14.block.block_entity.PowerDeviceBlockEntity;
import com.juicyslew.moonstation14.block.custom.PowerDeviceBlock;
import com.juicyslew.moonstation14.ms14.power.PowerSimulationGate;
import com.juicyslew.moonstation14.ms14.power.cable.CableTier;
import com.juicyslew.moonstation14.ms14.power.device.PowerDeviceKind;
import com.juicyslew.moonstation14.ms14.power.device.PowerDeviceRules;
import com.juicyslew.moonstation14.ms14.power.graph.LoadedPowerGraph;
import com.juicyslew.moonstation14.ms14.power.graph.PowerGraphService;
import com.juicyslew.moonstation14.ms14.power.topology.CableFaceNode;
import com.juicyslew.moonstation14.ms14.power.topology.DevicePort;
import com.juicyslew.moonstation14.ms14.power.topology.PowerTopology;
import com.juicyslew.moonstation14.ms14.power.ui.ApcVisualState;
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
    private static final Map<ServerLevel, Long> LAST_SOLVE = new WeakHashMap<>();
    private static final Set<ServerLevel> RESOLVE_AFTER_TRIP = Collections.newSetFromMap(new WeakHashMap<>());
    private static final Set<ServerLevel> RESOLVE_AFTER_TOPOLOGY = Collections.newSetFromMap(new WeakHashMap<>());
    private static final Map<ServerLevel, String> LAST_DIAGNOSTIC = new WeakHashMap<>();
    private static final Map<ServerLevel, Map<BlockPos, ApcOutputSample>> APC_OUTPUT_SAMPLES = new WeakHashMap<>();
    private static long serverTickSequence;
    private record ApcOutputSample(long sampleTick, long topologyGeneration, boolean graphKnown,
                                   double outputWatts, PowerDeviceBlockEntity device, long breakerRevision,
                                   ApcVisualState visualState, long visualTick, long visualGeneration) { }
    private PowerRuntime() { }

    public static void register(IEventBus bus) { bus.register(PowerRuntime.class); }

    public static void deviceChanged(ServerLevel level, BlockPos pos) {
        if (!PowerSimulationGate.isEnabled()) return;
        PowerGraphService.invalidateLampChoice(level, pos);
        Set<BlockPos> devices = DEVICES.computeIfAbsent(level, ignored -> new LinkedHashSet<>());
        devices.add(pos.immutable());
        PowerGraphService.noteChanged(level, new ChunkPos(pos));
    }

    /** Number of currently indexed loaded devices; exposed for diagnostics and scale audits. */
    public static int indexedDeviceCount(ServerLevel level) {
        Set<BlockPos> devices = DEVICES.get(level);
        return devices == null ? 0 : devices.size();
    }

    public static String lastSolveDiagnostic(ServerLevel level) {
        return LAST_DIAGNOSTIC.getOrDefault(level, "no solve yet");
    }

    /** Constant-time, read-only menu lookup. Never triggers a solve or loads a chunk. */
    public static ApcVisualState apcVisualState(ServerLevel level, PowerDeviceBlockEntity device) {
        if (!PowerSimulationGate.isEnabled() || device == null) return ApcVisualState.UNKNOWN;
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
                || serverTickSequence - sample.visualTick() > TICKS_PER_SOLVE) return ApcVisualState.UNKNOWN;
        return sample.visualState();
    }

    public static void deviceRemoved(ServerLevel level, BlockPos pos) {
        if (!PowerSimulationGate.isEnabled()) return;
        PowerGraphService.invalidateLampChoice(level, pos);
        Set<BlockPos> devices = DEVICES.get(level);
        if (devices != null) devices.remove(pos);
        Map<BlockPos, ApcOutputSample> samples = APC_OUTPUT_SAMPLES.get(level);
        if (samples != null) samples.remove(pos);
        PowerGraphService.noteChanged(level, new ChunkPos(pos));
    }

    @net.neoforged.bus.api.SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !(event.getChunk() instanceof LevelChunk chunk)) return;
        if (!PowerSimulationGate.isEnabled()) {
            // Existing saved lit states are presentation, not simulation state. Fail dark once
            // as each chunk enters the world without building the runtime device index.
            for (BlockEntity be : chunk.getBlockEntities().values()) {
                if (be instanceof PowerDeviceBlockEntity
                        && be.getBlockState().getBlock() instanceof PowerDeviceBlock block
                         && block.kind().isLamp()) {
                    setLampDark(level, be.getBlockPos());
                }
            }
            return;
        }
        for (BlockEntity be : chunk.getBlockEntities().values())
            if (be instanceof PowerDeviceBlockEntity) deviceChanged(level, be.getBlockPos());
    }

    @net.neoforged.bus.api.SubscribeEvent
    public static void onChunkUnload(ChunkEvent.Unload event) {
        if (!PowerSimulationGate.isEnabled()) return;
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        Set<BlockPos> devices = DEVICES.get(level);
        if (devices != null) devices.removeIf(pos -> new ChunkPos(pos).equals(event.getChunk().getPos()));
        Map<BlockPos, ApcOutputSample> samples = APC_OUTPUT_SAMPLES.get(level);
        if (samples != null) samples.keySet().removeIf(pos -> new ChunkPos(pos).equals(event.getChunk().getPos()));
    }

    @net.neoforged.bus.api.SubscribeEvent
    public static void onTick(ServerTickEvent.Post event) {
        if (!PowerSimulationGate.isEnabled()) return;
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
            DEVICES.remove(level);
            LAST_SOLVE.remove(level);
            RESOLVE_AFTER_TRIP.remove(level);
            RESOLVE_AFTER_TOPOLOGY.remove(level);
            LAST_DIAGNOSTIC.remove(level);
            APC_OUTPUT_SAMPLES.remove(level);
        }
    }
    @net.neoforged.bus.api.SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        DEVICES.clear(); LAST_SOLVE.clear(); RESOLVE_AFTER_TRIP.clear(); RESOLVE_AFTER_TOPOLOGY.clear();
        LAST_DIAGNOSTIC.clear(); APC_OUTPUT_SAMPLES.clear(); serverTickSequence = 0;
    }

    public static void onSimulationDisabled() {
        // Be safe if a lifecycle/load callback registered devices before the startup sample.
        // This is a one-time pass over the existing index, never a world scan.
        for (Map.Entry<ServerLevel, Set<BlockPos>> entry : DEVICES.entrySet()) {
            ServerLevel level = entry.getKey();
            for (BlockPos pos : entry.getValue()) {
                if (level.hasChunkAt(pos)) {
                    BlockEntity be = level.getBlockEntity(pos);
                    if (be instanceof PowerDeviceBlockEntity apc) apc.observeActualApcOutput(serverTickSequence,
                            serverTickSequence, false, 0);
                    setLampDark(level, pos);
                }
            }
        }
        DEVICES.clear();
        LAST_SOLVE.clear();
        RESOLVE_AFTER_TRIP.clear();
        RESOLVE_AFTER_TOPOLOGY.clear();
        LAST_DIAGNOSTIC.clear();
        APC_OUTPUT_SAMPLES.clear();
    }

    private static void solve(ServerLevel level, long elapsedTicks, long sampleTick) {
        Set<BlockPos> indexed = DEVICES.get(level);
        if (indexed == null || indexed.isEmpty()) return;
        // Snapshot is bounded by the insertion cap; stale or unloaded entries are removed without loading chunks.
        List<PowerDeviceBlockEntity> devices = new ArrayList<>();
        Iterator<BlockPos> iterator = indexed.iterator();
        while (iterator.hasNext()) {
            BlockPos pos = iterator.next();
            if (!level.hasChunkAt(pos)) { iterator.remove(); continue; }
            BlockEntity be = level.getBlockEntity(pos);
            if (!(be instanceof PowerDeviceBlockEntity device)) { iterator.remove(); continue; }
            devices.add(device);
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

        List<PowerDeviceBlockEntity> sources = byKind(devices, PowerDeviceKind.HV_SOURCE);
        List<PowerDeviceBlockEntity> substations = byKind(devices, PowerDeviceKind.HV_MV_SUBSTATION);
        List<PowerDeviceBlockEntity> apcs = byKind(devices, PowerDeviceKind.APC);
        List<PowerDeviceBlockEntity> lamps = devices.stream().filter(device ->
                device.getBlockState().getBlock() instanceof PowerDeviceBlock block && block.kind().isLamp()).toList();
        var input = new PowerLiveLoop.Input(
                sources.stream().map(source -> new PowerLiveLoop.Source(id(source), component(components, source, CableTier.HV),
                        isKnown(known, source, CableTier.HV), SOURCE_WATTS)).toList(),
                substations.stream().map(sub -> new PowerLiveLoop.Substation(id(sub),
                        component(components, sub, CableTier.HV), isKnown(known, sub, CableTier.HV),
                        component(components, sub, CableTier.MV), isKnown(known, sub, CableTier.MV))).toList(),
                apcs.stream().map(apc -> new PowerLiveLoop.Apc(id(apc), component(components, apc, CableTier.MV),
                        isKnown(known, apc, CableTier.MV), component(components, apc, CableTier.APC),
                        isKnown(known, apc, CableTier.APC), apc.breakerClosed(), apc.energyJoules())).toList(),
                lamps.stream().map(lamp -> new PowerLiveLoop.Lamp(id(lamp), component(components, lamp, CableTier.APC),
                         isKnown(known, lamp, CableTier.APC),
                         ((PowerDeviceBlock) lamp.getBlockState().getBlock()).kind())).toList(), elapsedTicks);
        PowerLiveLoop.Result result = PowerLiveLoop.solve(input);
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
        LAST_DIAGNOSTIC.put(level, "sourcePorts=" + input.sources().stream()
                .map(s -> s.component() + "/" + s.known()).toList()
                + ",subPorts=" + input.substations().stream()
                .map(s -> s.hvComponent() + "/" + s.hvKnown() + "->" + s.mvComponent() + "/" + s.mvKnown()).toList()
                + ",apcPorts=" + input.apcs().stream()
                .map(a -> a.mvComponent() + "/" + a.mvKnown() + "->" + a.apcComponent() + "/" + a.apcKnown()).toList()
                + ",lampWatts=" + result.lampWatts());
        for (PowerDeviceBlockEntity apc : apcs)
            apc.setEnergyJoules(result.batteryEnergyJoules().getOrDefault(id(apc), apc.energyJoules()));
        // Each lamp has exactly one final projection, regardless of how many APCs share its component.
        for (PowerDeviceBlockEntity lamp : lamps)
            setLit(level, lamp.getBlockPos(), result.lampWatts().getOrDefault(id(lamp), 0.0)
                    >= ((PowerDeviceBlock) lamp.getBlockState().getBlock()).kind().lampDemandWatts());
    }

    /** Consume cached solve meters only; this per-tick pass performs no topology query or solve. */
    private static void observeApcOutputs(ServerLevel level, long serverTick) {
        Map<BlockPos, ApcOutputSample> samples = APC_OUTPUT_SAMPLES.get(level);
        if (samples == null || samples.isEmpty()) return;
        Iterator<Map.Entry<BlockPos, ApcOutputSample>> iterator = samples.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<BlockPos, ApcOutputSample> entry = iterator.next();
            BlockPos pos = entry.getKey();
            if (!level.hasChunkAt(pos)) { iterator.remove(); continue; }
            BlockEntity be = level.getBlockEntity(pos);
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

    private static void setLampDark(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof PowerDeviceBlock block && block.kind().isLamp()
                && state.getValue(PowerDeviceBlock.LIT)) {
            // Update the light/client projection without notifying all neighboring blocks.
            level.setBlock(pos, state.setValue(PowerDeviceBlock.LIT, false), 2);
        }
    }
}
