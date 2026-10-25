package com.juicyslew.moonstation14.ms14.atmos.piping;

import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
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
                       GasMixture mixture, PipeGasInventory.Entry entry) { }

    /** Starts from one caller-validated location; performs no seeding, mutation, or chunk loading. */
    public static Optional<Snapshot> discover(ServerLevel level, BlockPos start, int lane) {
        PipeTopology.Layer layer = layer(lane);
        if (layer == null || !level.getServer().isSameThread() || !inHeight(level, start)) return Optional.empty();
        PipeTopology.NodeKey startKey = new PipeTopology.NodeKey(start, layer);
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
            VisualPipeBlockEntity visual = raw instanceof VisualPipeBlockEntity v
                    && chunk.getBlockState(pos).is(ModBlocks.GAS_PIPE_VISUAL.get()) && v.hasLayer(lane) ? v : null;
            PipeChunkData.Record sealed = visual == null ? LoadedSealedWallSegment.matchingLive(level, pos, layer).orElse(null) : null;
            if (visual == null && sealed == null) return Optional.empty();
            PipeGasInventory.Entry entry = visual != null ? LoadedPipeGasOwner.activeSnapshot(level, pos, lane).orElse(null)
                    : LoadedSealedWallGasOwner.activeSnapshot(level, pos, layer).orElse(null);
            if (entry == null || !Double.isFinite(entry.volume()) || entry.volume() <= 0) return Optional.empty();
            Node node = new Node(key, entry.incarnation(), entry.volume(), entry.mixture(), entry);
            nodes.put(key, node);
            volume += entry.volume();
            if (!Double.isFinite(volume)) return Optional.empty();

            for (Direction direction : Direction.values()) {
                BlockPos neighborPos = offset(pos, direction);
                if (neighborPos == null || !inHeight(level, neighborPos)) continue;
                if (!opens(visual, sealed, lane, direction)) continue;
                LevelChunk neighborChunk = fullChunk(level, neighborPos);
                if (neighborChunk == null) return Optional.empty();
                BlockEntity neighborRaw = neighborChunk.getBlockEntity(neighborPos);
                VisualPipeBlockEntity neighbor = neighborRaw instanceof VisualPipeBlockEntity v
                        && neighborChunk.getBlockState(neighborPos).is(ModBlocks.GAS_PIPE_VISUAL.get()) && v.hasLayer(lane) ? v : null;
                PipeChunkData.Record neighborSealed = neighbor == null
                        ? LoadedSealedWallSegment.matchingLive(level, neighborPos, layer).orElse(null) : null;
                if (neighbor == null && neighborSealed == null) {
                    // Wrong-lane visual hosts and stale sealed records are inert caps.
                    continue;
                }
                if (opens(visual, sealed, lane, direction)
                        && opens(neighbor, neighborSealed, lane, direction.getOpposite())) {
                    PipeTopology.NodeKey neighborKey = new PipeTopology.NodeKey(neighborPos, layer);
                    if (visited.add(neighborKey)) pending.addLast(neighborKey);
                }
            }
        }
        List<Node> ordered = new ArrayList<>(nodes.values());
        ordered.sort(Comparator.comparing((Node n) -> n.key().position().getX())
                .thenComparing(n -> n.key().position().getY()).thenComparing(n -> n.key().position().getZ()));
        return Optional.of(new Snapshot(ordered, volume));
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
}

