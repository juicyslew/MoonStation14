package com.juicyslew.moonstation14.ms14.atmos.piping;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ChunkPos;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/** Read-only projection of explicitly supplied detached chunk snapshots into topology components. */
public final class PipeChunkTopology {
    private PipeChunkTopology() { }

    /**
     * Projects authoritative records only. EXPOSED records must already have been checked by the caller
     * against live world ownership. Sealed-wall records are rejected entirely: persisted host metadata
     * cannot prove a live seal/incarnation. No world access, chunk lookup/loading, or source mutation occurs.
     */
    public static java.util.List<PipeTopology.Component> components(Map<ChunkPos, PipeChunkData> chunks) {
        Objects.requireNonNull(chunks, "chunks");
        long count = 0;
        for (var entry : chunks.entrySet()) {
            Objects.requireNonNull(entry.getKey(), "chunk position");
            PipeChunkData data = Objects.requireNonNull(entry.getValue(), "chunk data");
            count += data.size();
            if (count > PipeTopology.MAX_INPUT_NODES)
                throw new IllegalArgumentException("candidate node limit exceeded: " + PipeTopology.MAX_INPUT_NODES);
        }

        Map<PipeTopology.NodeKey, PipeTopology.Segment> candidates = new HashMap<>();
        for (var entry : chunks.entrySet()) {
            ChunkPos chunk = entry.getKey();
            long minX = (long) chunk.x * 16;
            long minZ = (long) chunk.z * 16;
            for (PipeChunkData.Record record : entry.getValue().snapshot()) {
                if (record.kind() == PipeTopology.Kind.STRAIGHT_SEALED_WALL)
                    throw new IllegalArgumentException("sealed-wall records require live host seal and incarnation proof");
                long x = minX + record.x();
                long z = minZ + record.z();
                if (x < Integer.MIN_VALUE || x > Integer.MAX_VALUE || z < Integer.MIN_VALUE || z > Integer.MAX_VALUE)
                    throw new IllegalArgumentException("projected pipe coordinate exceeds integer range");
                EnumSet<Direction> ports = EnumSet.noneOf(Direction.class);
                for (Direction direction : Direction.values())
                    if ((record.ports() & (1 << direction.ordinal())) != 0) ports.add(direction);
                BlockPos position = new BlockPos((int) x, record.y(), (int) z);
                PipeTopology.Segment segment = new PipeTopology.Segment(position, record.volume(), ports, record.kind(), record.layer());
                PipeTopology.NodeKey key = new PipeTopology.NodeKey(position, record.layer());
                if (candidates.put(key, segment) != null) throw new IllegalArgumentException("duplicate projected pipe position and layer");
            }
        }
        return PipeTopology.componentsLayered(candidates);
    }
}
