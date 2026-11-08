package com.juicyslew.moonstation14.ms14.atmos.piping;

import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.ms14.infrastructure.scanner.ScanGeometry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Read-only discovery of currently active functional pipe lanes. */
public final class PipeScanCandidates {
    private PipeScanCandidates() { }

    /** A candidate exposes location and lane only, never the gas owner or its identity. */
    public record Candidate(BlockPos position, int lane) {
        public Candidate {
            position = Objects.requireNonNull(position, "position").immutable();
            if (lane != VisualPipeBlockEntity.PRIMARY && lane != VisualPipeBlockEntity.SECONDARY
                    && lane != VisualPipeBlockEntity.TERTIARY) throw new IllegalArgumentException("invalid pipe lane");
        }
    }

    /**
     * Enumerates only already-full chunks. Concealed records are returned only when their current host
     * incarnation and active gas ownership independently match the persisted record.
     */
    static List<Candidate> readLoaded(ServerLevel level, Vec3 body, double radius, int maxCandidates) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(body, "body");
        if (!level.getServer().isSameThread()) throw new IllegalStateException("pipe scan must run on the server thread");
        if (maxCandidates < 0 || maxCandidates > ScanGeometry.MAX_CANDIDATES)
            throw new IllegalArgumentException("candidate budget exceeded");
        if (!Double.isFinite(radius) || radius <= 0 || !Double.isFinite(radius * radius))
            throw new IllegalArgumentException("radius must be finite and positive");

        int minX = chunkCoordinate(body.x - radius), maxX = chunkCoordinate(body.x + radius);
        int minZ = chunkCoordinate(body.z - radius), maxZ = chunkCoordinate(body.z + radius);
        long chunkCount = ((long) maxX - minX + 1) * ((long) maxZ - minZ + 1);
        // Refuse pathological bounds instead of allowing an unbounded empty-world traversal.
        if (chunkCount > ScanGeometry.MAX_CANDIDATES) throw new IllegalArgumentException("scan chunk budget exceeded");

        List<Candidate> candidates = new ArrayList<>();
        for (int cx = minX; cx <= maxX; cx++) for (int cz = minZ; cz <= maxZ; cz++) {
            if (!(level.getChunkSource().getChunk(cx, cz, ChunkStatus.FULL, false) instanceof LevelChunk chunk))
                return List.of();
            for (var entry : chunk.getBlockEntities().entrySet()) {
                BlockPos pos = entry.getKey();
                if (!chunk.getBlockState(pos).is(ModBlocks.GAS_PIPE_VISUAL.get())
                        || !(entry.getValue() instanceof VisualPipeBlockEntity visual)) continue;
                for (int lane : new int[]{VisualPipeBlockEntity.PRIMARY, VisualPipeBlockEntity.SECONDARY,
                        VisualPipeBlockEntity.TERTIARY}) {
                    if (!visual.hasLayer(lane) || visual.functionalIdentity(lane).isEmpty()
                            || LoadedPipeGasOwner.activeSnapshot(level, pos, lane).isEmpty()) continue;
                    if (candidates.size() == maxCandidates) throw new IllegalArgumentException("candidate budget exceeded");
                    candidates.add(new Candidate(pos, lane));
                }
            }
            PipeChunkData data = chunk.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.PIPE_CHUNK.get());
            if (data != null && data.size() > PipeChunkData.MAX_RECORDS) return List.of();
            if (data == null) continue;
            for (PipeChunkData.Record record : data.snapshot()) {
                if (record.kind() != PipeTopology.Kind.STRAIGHT_SEALED_WALL) continue;
                BlockPos pos = new BlockPos((cx << 4) + record.x(), record.y(), (cz << 4) + record.z());
                if (pos.getY() < level.getMinBuildHeight() || pos.getY() >= level.getMaxBuildHeight()) continue;
                PipeTopology.Layer layer = record.layer();
                if (LoadedSealedWallSegment.matchingLive(level, pos, layer).filter(record::equals).isEmpty()
                        || LoadedSealedWallGasOwner.activeSnapshot(level, pos, layer).isEmpty()) continue;
                int lane = switch (layer) {
                    case PRIMARY -> VisualPipeBlockEntity.PRIMARY;
                    case SECONDARY -> VisualPipeBlockEntity.SECONDARY;
                    case TERTIARY -> VisualPipeBlockEntity.TERTIARY;
                };
                if (candidates.stream().anyMatch(candidate -> candidate.position().equals(pos) && candidate.lane() == lane)) continue;
                if (candidates.size() == maxCandidates) throw new IllegalArgumentException("candidate budget exceeded");
                candidates.add(new Candidate(pos, lane));
            }
        }
        List<Candidate> result = new ArrayList<>();
        for (Candidate candidate : candidates) {
            BlockPos pos = candidate.position();
            ScanGeometry.Candidate point = new ScanGeometry.Candidate(ScanGeometry.Type.PIPE,
                    new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5));
            if (!ScanGeometry.select(body, radius, List.of(point)).isEmpty()) {
                if (result.size() == ScanGeometry.MAX_RESULTS) throw new IllegalArgumentException("result budget exceeded");
                result.add(candidate);
            }
        }
        return List.copyOf(result);
    }

    private static int chunkCoordinate(double coordinate) {
        if (!Double.isFinite(coordinate) || coordinate < Integer.MIN_VALUE || coordinate > Integer.MAX_VALUE)
            throw new IllegalArgumentException("scan bounds out of range");
        return ((int) Math.floor(coordinate)) >> 4;
    }
}

