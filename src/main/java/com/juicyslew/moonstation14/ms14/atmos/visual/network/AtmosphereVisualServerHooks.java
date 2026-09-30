package com.juicyslew.moonstation14.ms14.atmos.visual.network;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereChunkData;
import com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereService;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.level.ChunkWatchEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.function.Function;
import java.util.function.ToIntFunction;

/** Bounded, server-only gas visual synchronization. */
public final class AtmosphereVisualServerHooks {
    public static final int MAX_CHANGED_POSITIONS_PER_CHUNK = 4096;
    public static final int MAX_CHUNKS_PER_TICK = 16;
    public static final int MAX_PACKETS_PER_LEVEL_PER_TICK = 64;
    public static final int MAX_OVERRIDE_PROBES_PER_TICK = 4096;
    private static final int SEND_CADENCE_TICKS = 7;
    private static final Map<ServerLevel, LevelState> STATES = new WeakHashMap<>();
    private static final Map<UUID, RequestRateLimit> REQUEST_LIMITS = new LinkedHashMap<>();

    private AtmosphereVisualServerHooks() { }
    public static void register(IEventBus bus) { bus.register(AtmosphereVisualServerHooks.class); }

    public static void noteChanged(ServerLevel level, BlockPos pos) {
        if (level == null || pos == null || !AtmosphereService.INSTANCE.isEnabled()) return;
        LevelState state = STATES.get(level);
        ChunkPos chunk = new ChunkPos(pos);
        if (state != null && state.watchers.containsKey(chunk)) state.changes.offer(chunk, pos);
    }

    /** Accepts only a bounded, rate-limited request for a chunk this exact player currently watches. */
    public static void requestResync(ServerPlayer player, AtmosphereVisualResyncRequest request) {
        if (player == null || request == null || !AtmosphereService.INSTANCE.isEnabled()) return;
        ServerLevel level = player.serverLevel();
        LevelState state = STATES.get(level);
        if (state == null) return;
        ChunkPos pos = new ChunkPos(request.chunkX(), request.chunkZ());
        Set<UUID> watchers = state.watchers.get(pos);
        if (!isAuthorizedWatcher(watchers, player.getUUID())) return;
        long now = level.getGameTime();
        if (!REQUEST_LIMITS.computeIfAbsent(player.getUUID(), ignored -> new RequestRateLimit()).allow(now)) return;
        if (loaded(level, pos) == null) return;
        long revision = state.revisions.merge(pos, 1L, Long::sum);
        queueRequesterSnapshot(state.transfers.computeIfAbsent(pos, ignored -> new LinkedHashMap<>()),
                player.getUUID(), revision);
    }

    static boolean isAuthorizedWatcher(Set<UUID> watchers, UUID requester) {
        return watchers != null && requester != null && watchers.contains(requester);
    }

    static void queueRequesterSnapshot(Map<UUID, SnapshotTransfer> transfers, UUID requester, long revision) {
        transfers.put(requester, new SnapshotTransfer(revision));
    }


    @net.neoforged.bus.api.SubscribeEvent
    public static void onChunkSent(ChunkWatchEvent.Sent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !AtmosphereService.INSTANCE.isEnabled()) return;
        ServerPlayer player = event.getPlayer();
        if (player == null) return;
        LevelState state = state(level);
        ChunkPos pos = new ChunkPos(event.getChunk().getPos().x, event.getChunk().getPos().z);
        state.watchers.computeIfAbsent(pos, ignored -> new LinkedHashSet<>()).add(player.getUUID());
        long revision = state.revisions.computeIfAbsent(pos, ignored -> 1L);
        state.transfers.computeIfAbsent(pos, ignored -> new LinkedHashMap<>())
                .put(player.getUUID(), new SnapshotTransfer(revision));
    }

    @net.neoforged.bus.api.SubscribeEvent
    public static void onChunkUnwatch(ChunkWatchEvent.UnWatch event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        LevelState state = STATES.get(level);
        if (state == null) return;
        Set<UUID> watchers = state.watchers.get(event.getPos());
        if (watchers != null) {
            watchers.remove(event.getPlayer().getUUID());
            if (watchers.isEmpty()) {
                state.watchers.remove(event.getPos()); state.changes.remove(event.getPos());
                state.transfers.remove(event.getPos()); state.revisions.remove(event.getPos());
            }
        }
        Map<UUID, SnapshotTransfer> transfers = state.transfers.get(event.getPos());
        if (transfers != null) {
            transfers.remove(event.getPlayer().getUUID());
            if (transfers.isEmpty()) state.transfers.remove(event.getPos());
        }
    }

    @net.neoforged.bus.api.SubscribeEvent
    public static void onPostServerTick(ServerTickEvent.Post event) {
        if (!AtmosphereService.INSTANCE.isEnabled()) { STATES.clear(); return; }
        for (ServerLevel level : event.getServer().getAllLevels()) {
            if (level.getGameTime() % SEND_CADENCE_TICKS != 0) continue;
            LevelState state = STATES.get(level);
            if (state != null) flush(level, state);
        }
    }

    @net.neoforged.bus.api.SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) STATES.remove(level);
    }
    @net.neoforged.bus.api.SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) { STATES.clear(); REQUEST_LIMITS.clear(); }

    @net.neoforged.bus.api.SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) REQUEST_LIMITS.remove(player.getUUID());
    }

    private static LevelState state(ServerLevel level) { return STATES.computeIfAbsent(level, ignored -> new LevelState()); }

    private static void flush(ServerLevel level, LevelState state) {
        int packets = 0, chunks = 0, probes = 0;
        List<ChunkPos> watched = List.copyOf(state.watchers.keySet());
        int start = rotationStart(watched, state.chunkCursor);
        for (int offset = 0; offset < watched.size(); offset++) {
            if (chunks >= MAX_CHUNKS_PER_TICK || packets >= MAX_PACKETS_PER_LEVEL_PER_TICK || probes >= MAX_OVERRIDE_PROBES_PER_TICK) break;
            ChunkPos pos = watched.get((start + offset) % watched.size());
            state.chunkCursor = pos;
            Map<UUID, SnapshotTransfer> transfers = state.transfers.get(pos);
            if (transfers != null && !transfers.isEmpty()) {
                LevelChunk chunk = loaded(level, pos);
                if (chunk == null) continue;
                for (var iterator = transfers.entrySet().iterator(); iterator.hasNext()
                        && packets < MAX_PACKETS_PER_LEVEL_PER_TICK && probes < MAX_OVERRIDE_PROBES_PER_TICK;) {
                    var entry = iterator.next();
                    SnapshotTransfer transfer = entry.getValue();
                    BuildResult built = nextSnapshotPacket(level, chunk, pos, transfer, MAX_OVERRIDE_PROBES_PER_TICK - probes);
                    probes += built.probes;
                    ServerPlayer target = player(level, entry.getKey());
                    if (built.payload != null && target != null) { PacketDistributor.sendToPlayer(target, built.payload); packets++; }
                    if (transfer.done) iterator.remove();
                    if (built.probes == 0 && built.payload == null) break;
                }
                if (transfers.isEmpty()) state.transfers.remove(pos);
                chunks++;
                // Keep all mutations queued until every viewer has received its reset snapshot.
                if (state.transfers.containsKey(pos)) continue;
            }
            PendingChanges.ChangeSet change = state.changes.get(pos);
            if (change == null) continue;
            if (change.pendingPackets != null) continue;
            LevelChunk chunk = loaded(level, pos);
            if (chunk == null) continue;
            List<AtmosphereVisualPayload> packetsForChange;
            if (change.snapshot) {
                // A watcher reset takes care of snapshot semantics; all currently watched players get fresh snapshots.
                long revision = state.revisions.merge(pos, 1L, Long::sum);
                Map<UUID, SnapshotTransfer> replacements = new LinkedHashMap<>();
                for (UUID watcher : state.watchers.get(pos)) replacements.put(watcher, new SnapshotTransfer(revision));
                state.transfers.put(pos, replacements); state.changes.remove(pos); continue;
            }
            List<BlockPos> batch = List.copyOf(change.positions);
            change.positions.clear();
            packetsForChange = delta(level, chunk, state.revisions.merge(pos, 1L, Long::sum), batch);
            int sent = 0;
            while (sent < packetsForChange.size() && packets < MAX_PACKETS_PER_LEVEL_PER_TICK) {
                PacketDistributor.sendToPlayersTrackingChunk(level, pos, packetsForChange.get(sent++)); packets++;
            }
            if (sent == packetsForChange.size()) finishChange(state, pos, change);
            else { change.pendingPackets = packetsForChange; change.nextPacket = sent; }
            chunks++;
        }
        // Continue a packetized delta without losing later mutations; it stays ahead of subsequent changes.
        watched = List.copyOf(state.watchers.keySet());
        start = rotationStart(watched, state.chunkCursor);
        for (int offset = 0; offset < watched.size(); offset++) {
            if (packets >= MAX_PACKETS_PER_LEVEL_PER_TICK) break;
            ChunkPos pos = watched.get((start + offset) % watched.size());
            state.chunkCursor = pos;
            PendingChanges.ChangeSet change = state.changes.get(pos);
            if (change == null || change.pendingPackets == null || state.transfers.containsKey(pos)) continue;
            while (change.nextPacket < change.pendingPackets.size() && packets < MAX_PACKETS_PER_LEVEL_PER_TICK) {
                PacketDistributor.sendToPlayersTrackingChunk(level, pos, change.pendingPackets.get(change.nextPacket++)); packets++;
            }
            if (change.nextPacket == change.pendingPackets.size()) finishChange(state, pos, change);
        }
    }

    /** Index immediately after the last visited chunk, retaining insertion order and tolerating removal. */
    static int rotationStart(List<ChunkPos> watched, ChunkPos cursor) {
        if (watched.isEmpty() || cursor == null) return 0;
        int index = watched.indexOf(cursor);
        return index < 0 || index + 1 == watched.size() ? 0 : index + 1;
    }

    private static void finishChange(LevelState state, ChunkPos pos, PendingChanges.ChangeSet change) {
        change.pendingPackets = null;
        change.nextPacket = 0;
        if (change.positions.isEmpty() && !change.snapshot) state.changes.remove(pos);
    }

    private static ServerPlayer player(ServerLevel level, UUID id) { return level.getServer().getPlayerList().getPlayer(id); }
    private static LevelChunk loaded(ServerLevel level, ChunkPos pos) {
        return level.getChunkSource().getChunk(pos.x, pos.z, ChunkStatus.FULL, false) instanceof LevelChunk chunk ? chunk : null;
    }

    private static BuildResult nextSnapshotPacket(ServerLevel level, LevelChunk chunk, ChunkPos pos,
                                                   SnapshotTransfer transfer, int budget) {
        AtmosphereChunkData data = chunk.getExistingDataOrNull(ModDataAttachments.ATMOSPHERE_CHUNK.get());
        SnapshotBuild built = buildSnapshotPacket(level.dimension().location(), data, pos, transfer.cursor,
                transfer.sentAny, transfer.revision, budget,
                cursor -> AtmosphereService.INSTANCE.nextBurningAfter(level, pos, cursor),
                absolute -> AtmosphereService.INSTANCE.fireIntensity(level, absolute));
        transfer.cursor = built.cursor;
        transfer.done = built.done;
        if (built.payload != null && !built.payload.cells().isEmpty()) transfer.sentAny = true;
        return new BuildResult(built.payload, built.probes);
    }

    static SnapshotBuild buildSnapshotPacket(net.minecraft.resources.ResourceLocation dimension, AtmosphereChunkData data,
            ChunkPos pos, AtmosphereChunkData.CellPosition cursor, boolean sentAny, long revision, int budget,
            Function<AtmosphereChunkData.CellPosition, java.util.Optional<AtmosphereChunkData.CellPosition>> fireNextAfter,
            ToIntFunction<BlockPos> fireIntensity) {
        List<AtmosphereVisualPayload.VisualCell> cells = new ArrayList<>(AtmosphereVisualPayload.MAX_CELLS);
        int probes = 0;
        boolean done = false;
        while (probes < budget && cells.size() < AtmosphereVisualPayload.MAX_CELLS) {
            var gasNext = data == null ? java.util.Optional.<Map.Entry<AtmosphereChunkData.CellPosition, com.juicyslew.moonstation14.ms14.atmos.core.GasMixture>>empty() : data.nextAfter(cursor);
            var fireNext = fireNextAfter.apply(cursor);
            if (gasNext.isEmpty() && fireNext.isEmpty()) { done = true; break; }
            var gasCell = gasNext.map(Map.Entry::getKey).orElse(null);
            var fireCell = fireNext.orElse(null);
            var cellPos = gasCell == null ? fireCell : fireCell == null || compare(gasCell, fireCell) <= 0 ? gasCell : fireCell;
            cursor = cellPos; probes++;
            if (cellPos.y() < AtmosphereVisualPayload.MIN_BUILD_Y || cellPos.y() > AtmosphereVisualPayload.MAX_BUILD_Y) continue;
            var mixture = data == null ? null : data.get(cellPos.x(), cellPos.y(), cellPos.z());
            BlockPos absolute = new BlockPos(pos.getMinBlockX() + cellPos.x(), cellPos.y(), pos.getMinBlockZ() + cellPos.z());
            var gas = mixture == null ? new AtmosphereVisualPayload.VisualCell(cellPos.x(), cellPos.y(), cellPos.z(), 0, 0, 0, 0, 0, 0)
                    : AtmosphereVisualPayload.VisualCell.from(mixture, absolute);
            int fire = fireIntensity.applyAsInt(absolute);
            var visual = new AtmosphereVisualPayload.VisualCell(cellPos.x(), cellPos.y(), cellPos.z(), gas.plasmaAlpha(),
                    gas.tritiumAlpha(), gas.waterVaporAlpha(), gas.ammoniaAlpha(), gas.frezonAlpha(), fire);
            if (hasOpacity(visual)) cells.add(visual);
        }
        if (done && cells.isEmpty()) return new SnapshotBuild(new AtmosphereVisualPayload(dimension, pos.x, pos.z,
                revision, !sentAny, true, List.of()), cursor, true, probes);
        if (cells.isEmpty()) return new SnapshotBuild(null, cursor, false, probes);
        return new SnapshotBuild(new AtmosphereVisualPayload(dimension, pos.x, pos.z,
                revision, !sentAny, done, cells), cursor, done, probes);
    }

    private static int compare(AtmosphereChunkData.CellPosition a, AtmosphereChunkData.CellPosition b) {
        int c = Integer.compare(a.x(), b.x());
        if (c == 0) c = Integer.compare(a.z(), b.z());
        return c == 0 ? Integer.compare(a.y(), b.y()) : c;
    }

    static List<AtmosphereVisualPayload> packetize(net.minecraft.resources.ResourceLocation dimension, ChunkPos chunk,
            long revision, List<AtmosphereVisualPayload.VisualCell> cells) {
        List<AtmosphereVisualPayload> result = new ArrayList<>();
        if (cells.isEmpty()) return List.of(new AtmosphereVisualPayload(dimension, chunk.x, chunk.z, revision, true, true, List.of()));
        for (int start = 0; start < cells.size(); start += AtmosphereVisualPayload.MAX_CELLS) {
            int end = Math.min(start + AtmosphereVisualPayload.MAX_CELLS, cells.size());
            result.add(new AtmosphereVisualPayload(dimension, chunk.x, chunk.z, revision, start == 0,
                    end == cells.size(), cells.subList(start, end)));
        }
        return List.copyOf(result);
    }

    /** Delta packets never reset the client's chunk; each packet is independently final. */
    static List<AtmosphereVisualPayload> packetizeDelta(net.minecraft.resources.ResourceLocation dimension,
            ChunkPos chunk, long revision, List<AtmosphereVisualPayload.VisualCell> cells) {
        if (cells.isEmpty()) return List.of();
        List<AtmosphereVisualPayload> result = new ArrayList<>();
        for (int start = 0; start < cells.size(); start += AtmosphereVisualPayload.MAX_CELLS) {
            int end = Math.min(start + AtmosphereVisualPayload.MAX_CELLS, cells.size());
            result.add(new AtmosphereVisualPayload(dimension, chunk.x, chunk.z, revision,
                    false, true, cells.subList(start, end)));
        }
        return List.copyOf(result);
    }

    private static List<AtmosphereVisualPayload> delta(ServerLevel level, LevelChunk chunk, long revision, List<BlockPos> changed) {
        var data = chunk.getExistingDataOrNull(ModDataAttachments.ATMOSPHERE_CHUNK.get());
        List<AtmosphereVisualPayload.VisualCell> cells = new ArrayList<>(changed.size());
        for (BlockPos pos : changed) {
            var mixture = data == null ? null : data.get(pos.getX() & 15, pos.getY(), pos.getZ() & 15);
            var gas = mixture == null ? new AtmosphereVisualPayload.VisualCell(pos.getX() & 15, pos.getY(), pos.getZ() & 15, 0, 0, 0, 0, 0, 0)
                    : AtmosphereVisualPayload.VisualCell.from(mixture, pos);
            int fire = AtmosphereService.INSTANCE.fireIntensity(level, pos);
            cells.add(new AtmosphereVisualPayload.VisualCell(gas.localX(), gas.y(), gas.localZ(), gas.plasmaAlpha(),
                    gas.tritiumAlpha(), gas.waterVaporAlpha(), gas.ammoniaAlpha(), gas.frezonAlpha(), fire));
        }
        return packetizeDelta(level.dimension().location(), chunk.getPos(), revision, cells);
    }
    private static boolean hasOpacity(AtmosphereVisualPayload.VisualCell c) {
        return c.plasmaAlpha() != 0 || c.tritiumAlpha() != 0 || c.waterVaporAlpha() != 0 || c.ammoniaAlpha() != 0 || c.frezonAlpha() != 0 || c.fireIntensity() != 0;
    }

    private static final class LevelState {
        final PendingChanges changes = new PendingChanges();
        final Map<ChunkPos, Long> revisions = new LinkedHashMap<>();
        final Map<ChunkPos, Set<UUID>> watchers = new LinkedHashMap<>();
        final Map<ChunkPos, Map<UUID, SnapshotTransfer>> transfers = new LinkedHashMap<>();
        ChunkPos chunkCursor;
    }
    static final class RequestRateLimit {
        private long windowStart = Long.MIN_VALUE;
        private int count;
        boolean allow(long now) {
            if (windowStart == Long.MIN_VALUE || now < windowStart || now - windowStart >= 100) {
                windowStart = now;
                count = 0;
            }
            if (count >= 2) return false;
            count++;
            return true;
        }
    }
    static final class SnapshotTransfer {
        final long revision; AtmosphereChunkData.CellPosition cursor; boolean sentAny, done;
        SnapshotTransfer(long revision) { this.revision = revision; }
    }
    private record BuildResult(AtmosphereVisualPayload payload, int probes) { }
    record SnapshotBuild(AtmosphereVisualPayload payload, AtmosphereChunkData.CellPosition cursor, boolean done, int probes) { }

    static final class PendingChanges {
        private final LinkedHashMap<ChunkPos, ChangeSet> chunks = new LinkedHashMap<>();
        boolean offer(ChunkPos chunk, BlockPos pos) {
            ChangeSet set = chunks.computeIfAbsent(new ChunkPos(chunk.x, chunk.z), ignored -> new ChangeSet());
            if (!set.snapshot && set.positions.size() >= MAX_CHANGED_POSITIONS_PER_CHUNK) { set.positions.clear(); set.snapshot = true; }
            if (!set.snapshot) set.positions.add(pos.immutable());
            return true;
        }
        void snapshot(ChunkPos chunk) { ChangeSet set = chunks.computeIfAbsent(new ChunkPos(chunk.x, chunk.z), ignored -> new ChangeSet()); set.positions.clear(); set.snapshot = true; }
        boolean isSnapshot(ChunkPos pos) { return chunks.get(pos).snapshot; }
        List<BlockPos> take(ChunkPos pos) { return List.copyOf(chunks.get(pos).positions); }
        ChunkPos next() { return chunks.isEmpty() ? null : chunks.keySet().iterator().next(); }
        ChangeSet get(ChunkPos pos) { return chunks.get(pos); }
        void remove(ChunkPos pos) { chunks.remove(pos); }
        static final class ChangeSet {
            final LinkedHashSet<BlockPos> positions = new LinkedHashSet<>(); boolean snapshot;
            List<AtmosphereVisualPayload> pendingPackets; int nextPacket;
        }
    }
}
