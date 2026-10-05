package com.juicyslew.moonstation14.ms14.atmos.piping;

import net.minecraft.core.Direction;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

final class PipeChunkTopologyTest {
    @Test void layersAndChunkSeamsProjectWithoutMixing() {
        var one = new PipeChunkData();
        var two = new PipeChunkData();
        for (var layer : PipeTopology.Layer.values()) {
            assertTrue(one.add(record(15, 4, 0, layer, Set.of(Direction.EAST))));
            assertTrue(two.add(record(0, 4, 0, layer, Set.of(Direction.WEST))));
        }
        var result = PipeChunkTopology.components(Map.of(new ChunkPos(0, 0), one, new ChunkPos(1, 0), two));
        assertEquals(3, result.size());
        assertTrue(result.stream().allMatch(component -> component.segments().size() == 2));
        assertTrue(result.stream().allMatch(component -> component.segments().stream().map(PipeTopology.Segment::layer).distinct().count() == 1));
    }

    @Test void negativeCoordinatesVerticalPortsAndAbsentOrMismatchedNeighborsAreCapped() {
        var west = new PipeChunkData();
        var east = new PipeChunkData();
        west.add(record(15, 12, 15, PipeTopology.Layer.PRIMARY, Set.of(Direction.EAST, Direction.UP)));
        west.add(record(15, 13, 15, PipeTopology.Layer.PRIMARY, Set.of(Direction.DOWN)));
        east.add(record(0, 12, 15, PipeTopology.Layer.PRIMARY, Set.of(Direction.WEST)));
        assertEquals(1, PipeChunkTopology.components(Map.of(new ChunkPos(-2, -1), west, new ChunkPos(-1, -1), east)).size());
        east.remove(0, 13, 15);
        east.add(record(0, 12, 15, PipeTopology.Layer.SECONDARY, Set.of(Direction.WEST)));
        assertEquals(2, PipeChunkTopology.components(Map.of(new ChunkPos(-2, -1), west, new ChunkPos(-1, -1), east)).size());
        assertEquals(1, PipeChunkTopology.components(Map.of(new ChunkPos(-2, -1), west)).size());
    }

    @Test void boundedProjectionDoesNotMutateSnapshotsAndRejectsSealedWall() {
        var data = new PipeChunkData();
        data.add(record(1, 2, 3, PipeTopology.Layer.PRIMARY, Set.of(Direction.NORTH)));
        var before = data.snapshot();
        assertEquals(1, PipeChunkTopology.components(Map.of(new ChunkPos(0, 0), data)).size());
        assertEquals(before, data.snapshot());
        var full = new PipeChunkData();
        for (int i = 0; i < PipeChunkData.MAX_RECORDS; i++) {
            int x = i % 16, z = (i / 16) % 16, y = i / 256;
            full.add(record(x, y, z, PipeTopology.Layer.PRIMARY, Set.of()));
        }
        var overBudget = new java.util.LinkedHashMap<ChunkPos, PipeChunkData>();
        for (int i = 0; i < 17; i++) overBudget.put(new ChunkPos(i, 0), full);
        assertThrows(IllegalArgumentException.class, () -> PipeChunkTopology.components(overBudget));
        var sealed = new PipeChunkData();
        sealed.add(new PipeChunkData.Record(0, 0, 0, UUID.randomUUID(), PipeTopology.Kind.STRAIGHT_SEALED_WALL,
                PipeChunkData.Record.portMask(Set.of(Direction.NORTH, Direction.SOUTH)), 1, "minecraft:stone", UUID.randomUUID().toString()));
        assertThrows(IllegalArgumentException.class, () -> PipeChunkTopology.components(Map.of(new ChunkPos(0, 0), sealed)));
    }

    @Test void coordinateOverflowIsRejected() {
        var data = new PipeChunkData();
        data.add(record(0, 0, 0, PipeTopology.Layer.PRIMARY, Set.of()));
        assertThrows(IllegalArgumentException.class, () -> PipeChunkTopology.components(Map.of(new ChunkPos(Integer.MAX_VALUE, 0), data)));
    }

    private static PipeChunkData.Record record(int x, int y, int z, PipeTopology.Layer layer, Set<Direction> ports) {
        return new PipeChunkData.Record(x, y, z, UUID.randomUUID(), PipeTopology.Kind.EXPOSED,
                PipeChunkData.Record.portMask(ports), 1, "", "", layer);
    }
}
