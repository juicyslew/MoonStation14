package com.juicyslew.moonstation14.ms14.power.cable;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.ms14.infrastructure.scanner.ScanGeometry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Read-only query over loaded power-cable host-face records. */
public final class CableScanCandidates {
    private CableScanCandidates() { }

    public record Candidate(BlockPos position, Direction face, CableTier tier) {
        public Candidate {
            position = Objects.requireNonNull(position, "position").immutable();
            Objects.requireNonNull(face, "face");
            Objects.requireNonNull(tier, "tier");
        }
    }

    public static List<Candidate> readLoaded(ServerLevel level, Vec3 body, double radius, int maxCandidates) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(body, "body");
        if (!level.getServer().isSameThread()) throw new IllegalStateException("cable scan must run on the server thread");
        if (maxCandidates < 0 || maxCandidates > ScanGeometry.MAX_CANDIDATES) throw new IllegalArgumentException("candidate budget exceeded");
        if (!Double.isFinite(radius) || radius <= 0 || !Double.isFinite(radius * radius)) throw new IllegalArgumentException("radius must be finite and positive");
        int minX = chunkCoordinate(body.x - radius), maxX = chunkCoordinate(body.x + radius);
        int minZ = chunkCoordinate(body.z - radius), maxZ = chunkCoordinate(body.z + radius);
        long count = ((long) maxX - minX + 1) * ((long) maxZ - minZ + 1);
        if (count > ScanGeometry.MAX_CANDIDATES) throw new IllegalArgumentException("scan chunk budget exceeded");
        List<Candidate> found = new ArrayList<>();
        List<ScanGeometry.Candidate> points = new ArrayList<>();
        for (int cx = minX; cx <= maxX; cx++) for (int cz = minZ; cz <= maxZ; cz++) {
            if (!(level.getChunkSource().getChunk(cx, cz, ChunkStatus.FULL, false) instanceof LevelChunk chunk)) return List.of();
            CableChunkData data = chunk.getExistingDataOrNull(ModDataAttachments.POWER_CABLE_CHUNK.get());
            if (data == null) continue;
            for (CableChunkData.Record record : data.snapshot()) {
                if (!CableStorage.validateReadOnlyRecord(level, chunk, record)) continue;
                if (found.size() == maxCandidates) throw new IllegalArgumentException("candidate budget exceeded");
                BlockPos pos = new BlockPos((cx << 4) + record.x(), record.y(), (cz << 4) + record.z());
                found.add(new Candidate(pos, record.face(), record.tier()));
                Vec3 center = new Vec3(pos.getX() + .5 + record.face().getStepX() * .5,
                        pos.getY() + .5 + record.face().getStepY() * .5,
                        pos.getZ() + .5 + record.face().getStepZ() * .5);
                points.add(new ScanGeometry.Candidate(ScanGeometry.Type.CABLE, center));
            }
        }
        List<ScanGeometry.Result> selected = ScanGeometry.select(body, radius, points);
        List<Candidate> result = new ArrayList<>();
        for (ScanGeometry.Result item : selected) {
            int index = points.indexOf(item.candidate());
            result.add(found.get(index));
        }
        return List.copyOf(result);
    }

    private static int chunkCoordinate(double coordinate) {
        if (!Double.isFinite(coordinate) || coordinate < Integer.MIN_VALUE || coordinate > Integer.MAX_VALUE)
            throw new IllegalArgumentException("scan bounds out of range");
        return ((int) Math.floor(coordinate)) >> 4;
    }
}

