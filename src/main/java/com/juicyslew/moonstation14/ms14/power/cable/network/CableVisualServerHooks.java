package com.juicyslew.moonstation14.ms14.power.cable.network;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.level.ChunkWatchEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.HashMap;

/** Bounded server-only chunk-watch synchronization for cable presentation. */
public final class CableVisualServerHooks {
    public static final int MAX_PACKETS_PER_TICK = 64;
    public static final int MAX_RECORD_WORK_PER_TICK = 8192;
    public static final int MAX_BYTES_PER_TICK = 256 * 1024;
    public static final int MAX_QUEUED_SENDS = 16_384;
    private static final Map<ServerLevel, LevelState> STATES = new WeakHashMap<>();
    private static final ArrayDeque<SendJob> SENDS = new ArrayDeque<>();
    private static final Map<UUID, RequestBudget> REQUESTS = new HashMap<>();
    public static final int MAX_RESYNC_REQUESTS_PER_TICK = 4;
    private CableVisualServerHooks() { }
    public static void register(IEventBus bus) { bus.register(CableVisualServerHooks.class); }

    /** Narrow mutation observation hook; storage remains the sole owner of cable records. */
    public static void noteChanged(ServerLevel level, ChunkPos chunk) {
        LevelState state = STATES.get(level);
        if (state != null && state.watchers.containsKey(chunk)) state.pending.add(chunk);
    }

    /** Accept only bounded requests for a loaded FULL chunk the player is currently watching. */
    public static boolean requestResync(ServerPlayer player, CableVisualResyncRequest request) {
        if (player == null || request == null || player.isRemoved() || player.serverLevel() == null) return false;
        ServerLevel level = player.serverLevel();
        ChunkPos pos = new ChunkPos(request.chunkX(), request.chunkZ());
        LevelState state = STATES.get(level);
        Set<UUID> watchers = state == null ? null : state.watchers.get(pos);
        if (watchers == null || !watchers.contains(player.getUUID())
                || !(level.getChunkSource().getChunk(pos.x, pos.z, ChunkStatus.FULL, false) instanceof LevelChunk))
            return false;
        long tick = level.getServer().getTickCount();
        RequestBudget budget = REQUESTS.compute(player.getUUID(), (id, old) ->
                old == null || old.tick != tick ? new RequestBudget(tick, 0) : old);
        if (budget.count >= MAX_RESYNC_REQUESTS_PER_TICK) return false;
        // Charge every validated attempt, including a queue-full rejection, so repeated
        // requests cannot use queue pressure to bypass the per-player work bound.
        REQUESTS.put(player.getUUID(), new RequestBudget(tick, budget.count + 1));
        // Do this before removeIf: when full, even a replacement request is rejected
        // rather than scanning the entire queue. The client will retry after cooldown.
        if (SENDS.size() >= MAX_QUEUED_SENDS) return false;
        // A repair is private to its requester; putting it in pending would rebroadcast
        // the full snapshot to every watcher of this chunk.
        SENDS.removeIf(job -> job.requesterOnly && job.level == level
                && job.player.equals(player.getUUID())
                && job.payload.chunkX() == pos.x && job.payload.chunkZ() == pos.z);
        LevelChunk chunk = (LevelChunk) level.getChunkSource().getChunk(pos.x, pos.z, ChunkStatus.FULL, false);
        var data = chunk.getExistingDataOrNull(ModDataAttachments.POWER_CABLE_CHUNK.get());
        var records = data == null ? List.<com.juicyslew.moonstation14.ms14.power.cable.CableChunkData.Record>of() : data.snapshot();
        long revision = ++state.revisionSequence;
        CableVisualPayload payload = new CableVisualPayload(level.dimension().location(), pos.x, pos.z,
                revision, true, records);
        SENDS.addLast(new SendJob(level, player.getUUID(), payload, true));
        return true;
    }

    @net.neoforged.bus.api.SubscribeEvent
    public static void onChunkSent(ChunkWatchEvent.Sent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || event.getPlayer() == null) return;
        ChunkPos pos = event.getChunk().getPos();
        LevelState state = STATES.computeIfAbsent(level, ignored -> new LevelState());
        state.watchers.computeIfAbsent(pos, ignored -> new LinkedHashSet<>()).add(event.getPlayer().getUUID());
        state.pending.add(pos);
    }

    @net.neoforged.bus.api.SubscribeEvent
    public static void onChunkUnwatch(ChunkWatchEvent.UnWatch event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        LevelState state = STATES.get(level);
        if (state == null) return;
        Set<UUID> watchers = state.watchers.get(event.getPos());
        if (watchers != null) {
            UUID player = event.getPlayer().getUUID();
            watchers.remove(player);
            SENDS.removeIf(job -> job.level == level && job.player.equals(player)
                    && job.payload.chunkX() == event.getPos().x && job.payload.chunkZ() == event.getPos().z);
            if (watchers.isEmpty()) { state.watchers.remove(event.getPos()); state.pending.remove(event.getPos()); }
        }
    }

    @net.neoforged.bus.api.SubscribeEvent
    public static void onPostServerTick(ServerTickEvent.Post event) {
        long currentTick = event.getServer().getTickCount();
        REQUESTS.entrySet().removeIf(entry -> entry.getValue().tick != currentTick);
        int packets = 0, recordWork = 0, bytes = 0;
        for (ServerLevel level : event.getServer().getAllLevels()) {
            LevelState state = STATES.get(level);
            if (state == null) continue;
            for (ChunkPos pos : List.copyOf(state.pending)) {
                Set<UUID> watchers = state.watchers.get(pos);
                if (watchers == null || watchers.isEmpty()) { state.pending.remove(pos); continue; }
                if (SENDS.size() + watchers.size() > MAX_QUEUED_SENDS) continue;
                if (!(level.getChunkSource().getChunk(pos.x, pos.z, ChunkStatus.FULL, false) instanceof LevelChunk chunk)) continue;
                var data = chunk.getExistingDataOrNull(ModDataAttachments.POWER_CABLE_CHUNK.get());
                var records = data == null ? List.<com.juicyslew.moonstation14.ms14.power.cable.CableChunkData.Record>of() : data.snapshot();
                long revision = ++state.revisionSequence;
                CableVisualPayload payload = new CableVisualPayload(level.dimension().location(), pos.x, pos.z,
                        revision, true, records);
                for (UUID id : watchers)
                    SENDS.addLast(new SendJob(level, id, payload, false));
                state.pending.remove(pos);
            }
        }
        // A rotating queue gives each recipient/chunk a turn; both work and estimated encoded bytes
        // are globally bounded, rather than multiplying a chunk budget by the watcher count.
        int turns = SENDS.size();
        while (turns-- > 0 && packets < MAX_PACKETS_PER_TICK) {
            SendJob job = SENDS.pollFirst();
            if (job == null) break;
            int cost = job.payload.records().size();
            int byteCost = job.payload.estimatedEncodedBytes();
            if (packets > 0 && (recordWork + cost > MAX_RECORD_WORK_PER_TICK || bytes + byteCost > MAX_BYTES_PER_TICK)) {
                SENDS.addLast(job);
                continue;
            }
            ServerPlayer player = event.getServer().getPlayerList().getPlayer(job.player);
            LevelState state = STATES.get(job.level);
            Set<UUID> watchers = state == null ? null : state.watchers.get(new ChunkPos(job.payload.chunkX(), job.payload.chunkZ()));
            if (player != null && watchers != null && watchers.contains(job.player) && player.serverLevel() == job.level
                    && job.level.getChunkSource().getChunk(job.payload.chunkX(), job.payload.chunkZ(), ChunkStatus.FULL, false) instanceof LevelChunk) {
                PacketDistributor.sendToPlayer(player, job.payload);
                packets++; recordWork += cost; bytes += byteCost;
            }
        }
    }

    @net.neoforged.bus.api.SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            STATES.remove(level);
            SENDS.removeIf(job -> job.level == level);
        }
    }
    @net.neoforged.bus.api.SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) { STATES.clear(); SENDS.clear(); REQUESTS.clear(); }

    private static final class LevelState {
        final Map<ChunkPos, Set<UUID>> watchers = new LinkedHashMap<>();
        final Set<ChunkPos> pending = new LinkedHashSet<>();
        long revisionSequence;
    }

    private record SendJob(ServerLevel level, UUID player, CableVisualPayload payload, boolean requesterOnly) { }
    private record RequestBudget(long tick, int count) { }
}
