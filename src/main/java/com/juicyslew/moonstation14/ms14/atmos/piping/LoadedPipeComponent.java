package com.juicyslew.moonstation14.ms14.atmos.piping;

import com.juicyslew.moonstation14.block.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Read-only discovery of one fully loaded live pipe component. */
public final class LoadedPipeComponent {
    // The topology kernel's 65,536 node cap is too expensive for synchronous live discovery.
    private static final int MAX_NODES = 384;

    private LoadedPipeComponent() { }

    /** Immutable batch input. An empty result means the whole component could not be validated. */
    public record Snapshot(List<Node> nodes, double totalVolume) {
        public Snapshot { nodes = List.copyOf(nodes); }
    }

    /** Immutable live-entry values; retaining Entry also preserves its version for batch validation. */
    public record Node(PipeTopology.NodeKey key, UUID incarnation, double volume,
                       PipeGasInventory.Entry entry) { }

    /** Starts from one caller-validated location; performs no seeding, mutation, or chunk loading. */
    public static Optional<Snapshot> discover(ServerLevel level, BlockPos start, int lane) {
        PipeTopology.Layer layer = layer(lane);
        if (layer == null) return Optional.empty();
        return discover(level, new PipeTopology.NodeKey(start, layer));
    }

    /** Starts from one exact live pipe lane or named device port. Never loads chunks or admits ownership. */
    public static Optional<Snapshot> discover(ServerLevel level, PipeTopology.NodeKey startKey) {
        if (level == null || startKey == null || !level.getServer().isSameThread() || !inHeight(level, startKey.position())) return Optional.empty();
        Map<PipeTopology.NodeKey, Node> nodes = new HashMap<>();
        ArrayDeque<PipeTopology.NodeKey> pending = new ArrayDeque<>();
        Set<PipeTopology.NodeKey> visited = new HashSet<>();
        pending.add(startKey);
        visited.add(startKey);
        double volume = 0;
        while (!pending.isEmpty()) {
            PipeTopology.NodeKey key = pending.removeFirst();
            if (nodes.size() >= MAX_NODES) return Optional.empty();
            BlockPos pos = key.position();
            LevelChunk chunk = fullChunk(level, pos);
            if (chunk == null) return Optional.empty();
            BlockEntity raw = chunk.getBlockEntity(pos);
            LiveNode live = resolve(level, pos, key);
            if (live == null) return Optional.empty();
            PipeGasInventory.Entry entry = live.entry;
            if (entry == null || !Double.isFinite(entry.volume()) || entry.volume() <= 0) return Optional.empty();
            Node node = new Node(key, entry.incarnation(), entry.volume(), entry);
            nodes.put(key, node);
            volume += entry.volume();
            if (!Double.isFinite(volume)) return Optional.empty();

            if (live.port != null) {
                PipeGasPortHost host = LoadedDevicePortGasOwner.host(level, pos).orElse(null);
                if (host == null) return Optional.empty();
                Set<PipeGasPortHost.InternalEdge> edges;
                try { edges = Set.copyOf(host.openInternalGasEdges()); } catch (RuntimeException badHost) { return Optional.empty(); }
                for (PipeGasPortHost.InternalEdge edge : edges) {
                    String other = edge.firstPortId().equals(live.port.id()) ? edge.secondPortId()
                            : edge.secondPortId().equals(live.port.id()) ? edge.firstPortId() : null;
                    if (other == null) continue;
                    PipeGasPortHost.Port adjacent;
                    try { adjacent = host.gasPorts().stream().filter(p -> p.id().equals(other)).findFirst().orElse(null); }
                    catch (RuntimeException malformedHost) { return Optional.empty(); }
                    if (adjacent == null) continue;
                    PipeTopology.NodeKey linked = adjacent.key(pos);
                    if (visited.add(linked)) pending.addLast(linked);
                }
            }
            for (Direction direction : Direction.values()) {
                BlockPos neighborPos = offset(pos, direction);
                if (neighborPos == null || !inHeight(level, neighborPos)) continue;
                if (!live.opens(direction)) continue;
                LevelChunk neighborChunk = fullChunk(level, neighborPos);
                if (neighborChunk == null) return Optional.empty();
                BlockEntity neighborRaw = neighborChunk.getBlockEntity(neighborPos);
                for (PipeTopology.NodeKey neighborKey : neighborKeys(level, neighborChunk, neighborRaw,
                        neighborPos, live.layer(), direction.getOpposite())) {
                    LiveNode neighbor = resolve(level, neighborPos, neighborKey);
                    if (neighbor != null && neighbor.opens(direction.getOpposite())) {
                    if (visited.add(neighborKey)) pending.addLast(neighborKey);
                    }
                }
            }
        }
        List<Node> ordered = new ArrayList<>(nodes.values());
        ordered.sort(Comparator.comparing((Node n) -> n.key().position().getX())
                .thenComparing(n -> n.key().position().getY()).thenComparing(n -> n.key().position().getZ())
                .thenComparing(n -> n.key().layer()).thenComparing(n -> n.key().portId()));
        return Optional.of(new Snapshot(ordered, volume));
    }

    /** Read-only reciprocal external connection check; never counts device-internal edges or loads chunks. */
    public static boolean hasExternalConnection(ServerLevel level, PipeTopology.NodeKey key, Direction direction) {
        if (level == null || key == null || direction == null || !level.getServer().isSameThread()
                || !inHeight(level, key.position())) return false;
        BlockPos pos = key.position();
        LevelChunk chunk = fullChunk(level, pos);
        if (chunk == null) return false;
        LiveNode live = resolve(level, pos, key);
        if (live == null || !live.opens(direction)) return false;
        BlockPos neighborPos = offset(pos, direction);
        if (neighborPos == null || !inHeight(level, neighborPos)) return false;
        LevelChunk neighborChunk = fullChunk(level, neighborPos);
        if (neighborChunk == null) return false;
        BlockEntity raw = neighborChunk.getBlockEntity(neighborPos);
        for (PipeTopology.NodeKey neighborKey : neighborKeys(level, neighborChunk, raw, neighborPos,
                live.layer(), direction.getOpposite())) {
            LiveNode neighbor = resolve(level, neighborPos, neighborKey);
            if (neighbor != null && neighbor.opens(direction.getOpposite())) return true;
        }
        return false;
    }

    private record LiveNode(PipeTopology.Layer layer, VisualPipeBlockEntity visual,
                            PipeChunkData.Record sealed, PipeGasPortHost.Port port, PipeGasInventory.Entry entry) {
        boolean opens(Direction direction) { return port != null ? port.directions().contains(direction)
                : LoadedPipeComponent.opens(visual, sealed, lane(layer), direction); }
    }

    private static LiveNode resolve(ServerLevel level, BlockPos pos, PipeTopology.NodeKey key) {
        LevelChunk chunk = fullChunk(level, pos);
        if (chunk == null) return null;
        if (key.devicePort()) {
            PipeGasPortHost.Port port = LoadedDevicePortGasOwner.host(level, pos).flatMap(host -> {
                try {
                    Set<String> ids = new HashSet<>();
                    for (PipeGasPortHost.Port candidate : host.gasPorts())
                        if (candidate == null || !ids.add(candidate.id())) return Optional.empty();
                    return host.gasPorts().stream().filter(p -> p.id().equals(key.portId()) && p.layer() == key.layer()).findFirst();
                }
                catch (RuntimeException failure) { return Optional.empty(); }
            }).orElse(null);
            if (port == null) return null;
            PipeGasInventory.Entry entry = LoadedDevicePortGasOwner.activeSnapshot(level, pos, port.id()).orElse(null);
            return entry == null ? null : new LiveNode(key.layer(), null, null, port, entry);
        }
        int lane = lane(key.layer());
        BlockEntity raw = chunk.getBlockEntity(pos);
        VisualPipeBlockEntity visual = raw instanceof VisualPipeBlockEntity v
                && chunk.getBlockState(pos).is(ModBlocks.GAS_PIPE_VISUAL.get()) && v.hasLayer(lane) ? v : null;
        PipeChunkData.Record sealed = visual == null ? LoadedSealedWallSegment.matchingLive(level, pos, key.layer()).orElse(null) : null;
        if (visual == null && sealed == null) return null;
        PipeGasInventory.Entry entry = visual != null ? LoadedPipeGasOwner.activeSnapshot(level, pos, lane).orElse(null)
                : LoadedSealedWallGasOwner.activeSnapshot(level, pos, key.layer()).orElse(null);
        return entry == null ? null : new LiveNode(key.layer(), visual, sealed, null, entry);
    }

    private static List<PipeTopology.NodeKey> neighborKeys(ServerLevel level, LevelChunk chunk, BlockEntity raw,
                                                            BlockPos pos, PipeTopology.Layer layer, Direction entering) {
        if (raw instanceof VisualPipeBlockEntity visual && chunk.getBlockState(pos).is(ModBlocks.GAS_PIPE_VISUAL.get()))
            return visual.hasLayer(lane(layer)) ? List.of(new PipeTopology.NodeKey(pos, layer)) : List.of();
        if (raw instanceof PipeGasPortHost host) {
            try {
                Set<String> ids = new HashSet<>();
                List<PipeGasPortHost.Port> ports = host.gasPorts();
                for (PipeGasPortHost.Port p : ports) if (p == null || !ids.add(p.id())) return List.of();
                return ports.stream().filter(p -> p.layer() == layer && p.directions().contains(entering))
                    .map(p -> p.key(pos)).toList(); }
            catch (RuntimeException failure) { return List.of(); }
        }
        return LoadedSealedWallSegment.matchingLive(level, pos, layer).isPresent()
                ? List.of(new PipeTopology.NodeKey(pos, layer)) : List.of();
    }

    private static boolean opens(VisualPipeBlockEntity visual, PipeChunkData.Record sealed, int lane, Direction direction) {
        // Visual arm geometry is renderer-only. Every exposed lane end is a potential
        // capped port; connectivity requires a reciprocal live neighbor.
        if (visual != null) return true;
        if (sealed == null) return false;
        int expected = (1 << direction.ordinal()) | (1 << direction.getOpposite().ordinal());
        return sealed.ports() == expected;
    }

    private static boolean inHeight(ServerLevel level, BlockPos pos) {
        return pos.getY() >= level.getMinBuildHeight() && pos.getY() < level.getMaxBuildHeight();
    }

    private static LevelChunk fullChunk(ServerLevel level, BlockPos pos) {
        return level.getChunkSource().getChunk(pos.getX() >> 4, pos.getZ() >> 4, ChunkStatus.FULL, false)
                instanceof LevelChunk chunk ? chunk : null;
    }

    private static BlockPos offset(BlockPos pos, Direction direction) {
        long x = (long) pos.getX() + direction.getStepX();
        long y = (long) pos.getY() + direction.getStepY();
        long z = (long) pos.getZ() + direction.getStepZ();
        if (x < Integer.MIN_VALUE || x > Integer.MAX_VALUE || y < Integer.MIN_VALUE || y > Integer.MAX_VALUE
                || z < Integer.MIN_VALUE || z > Integer.MAX_VALUE) return null;
        return new BlockPos((int) x, (int) y, (int) z);
    }

    private static PipeTopology.Layer layer(int lane) {
        return switch (lane) {
            case VisualPipeBlockEntity.PRIMARY -> PipeTopology.Layer.PRIMARY;
            case VisualPipeBlockEntity.SECONDARY -> PipeTopology.Layer.SECONDARY;
            case VisualPipeBlockEntity.TERTIARY -> PipeTopology.Layer.TERTIARY;
            default -> null;
        };
    }

    private static int lane(PipeTopology.Layer layer) {
        return switch (layer) {
            case PRIMARY -> VisualPipeBlockEntity.PRIMARY;
            case SECONDARY -> VisualPipeBlockEntity.SECONDARY;
            case TERTIARY -> VisualPipeBlockEntity.TERTIARY;
        };
    }
}

