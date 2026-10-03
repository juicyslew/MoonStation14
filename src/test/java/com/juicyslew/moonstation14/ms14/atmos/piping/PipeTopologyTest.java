package com.juicyslew.moonstation14.ms14.atmos.piping;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

final class PipeTopologyTest {
    @Test void sixWayGraphSupportsBendsTeeVerticalRisersAndSeams() {
        Map<BlockPos, PipeTopology.Segment> map = new LinkedHashMap<>();
        add(map, -1, 15, 0, Set.of(Direction.EAST), PipeTopology.Kind.EXPOSED);
        add(map, 0, 15, 0, Set.of(Direction.WEST, Direction.EAST, Direction.UP), PipeTopology.Kind.EXPOSED);
        add(map, 1, 15, 0, Set.of(Direction.WEST), PipeTopology.Kind.EXPOSED);
        add(map, 0, 16, 0, Set.of(Direction.DOWN, Direction.UP), PipeTopology.Kind.STRAIGHT_SEALED_WALL);
        add(map, 0, 17, 0, Set.of(Direction.DOWN), PipeTopology.Kind.EXPOSED);
        var components = PipeTopology.components(map);
        assertEquals(1, components.size());
        assertEquals(5, components.getFirst().segments().size());
        assertEquals(5.0, components.getFirst().totalVolume());
    }

    @Test void unmatchedAndIncompatiblePortsAreCappedAndNeverAtmosphereEdges() {
        Map<BlockPos, PipeTopology.Segment> map = new LinkedHashMap<>();
        add(map, 0, 0, 0, Set.of(Direction.EAST), PipeTopology.Kind.EXPOSED);
        add(map, 1, 0, 0, Set.of(Direction.NORTH), PipeTopology.Kind.EXPOSED);
        add(map, 20, 0, 0, Set.of(Direction.EAST), PipeTopology.Kind.EXPOSED);
        assertEquals(3, PipeTopology.components(map).size());
        assertThrows(IllegalArgumentException.class, () -> segment(0, 0, 0, 1, Set.of(Direction.NORTH, Direction.EAST), PipeTopology.Kind.STRAIGHT_SEALED_WALL));
    }

    @Test void componentVolumeMustRemainFinite() {
        var connected = new LinkedHashMap<BlockPos, PipeTopology.Segment>();
        var first = segment(0, 0, 0, Double.MAX_VALUE, Set.of(Direction.EAST), PipeTopology.Kind.EXPOSED);
        var second = segment(1, 0, 0, Double.MAX_VALUE, Set.of(Direction.WEST), PipeTopology.Kind.EXPOSED);
        connected.put(first.position(), first);
        connected.put(second.position(), second);
        assertThrows(IllegalArgumentException.class, () -> PipeTopology.components(connected));

        var disconnected = new LinkedHashMap<BlockPos, PipeTopology.Segment>();
        var one = segment(0, 0, 0, Double.MAX_VALUE, Set.of(), PipeTopology.Kind.EXPOSED);
        var two = segment(2, 0, 0, Double.MAX_VALUE, Set.of(), PipeTopology.Kind.EXPOSED);
        disconnected.put(one.position(), one);
        disconnected.put(two.position(), two);
        var components = PipeTopology.components(disconnected);
        assertEquals(2, components.size());
        assertEquals(Double.MAX_VALUE, components.get(0).totalVolume());
        assertEquals(Double.MAX_VALUE, components.get(1).totalVolume());

        var borderline = new LinkedHashMap<BlockPos, PipeTopology.Segment>();
        var halfOne = segment(0, 0, 0, Double.MAX_VALUE / 2, Set.of(Direction.EAST), PipeTopology.Kind.EXPOSED);
        var halfTwo = segment(1, 0, 0, Double.MAX_VALUE / 2, Set.of(Direction.WEST), PipeTopology.Kind.EXPOSED);
        borderline.put(halfOne.position(), halfOne);
        borderline.put(halfTwo.position(), halfTwo);
        assertEquals(Double.MAX_VALUE, PipeTopology.components(borderline).getFirst().totalVolume());
    }

    @Test void layeredNodesAtSamePositionNeverMixAndSameLayerRequiresReciprocalPorts() {
        var nodes = new LinkedHashMap<PipeTopology.NodeKey, PipeTopology.Segment>();
        var pos = new BlockPos(0, 0, 0);
        for (var layer : PipeTopology.Layer.values()) {
            var segment = new PipeTopology.Segment(pos, 2, Set.of(Direction.EAST), PipeTopology.Kind.EXPOSED, layer);
            nodes.put(new PipeTopology.NodeKey(pos, layer), segment);
        }
        var next = new PipeTopology.Segment(new BlockPos(1, 0, 0), 3, Set.of(Direction.WEST), PipeTopology.Kind.EXPOSED, PipeTopology.Layer.SECONDARY);
        nodes.put(new PipeTopology.NodeKey(next.position(), next.layer()), next);
        var components = PipeTopology.componentsLayered(nodes);
        assertEquals(3, components.size());
        assertEquals(5, components.stream().filter(c -> c.segments().size() == 2).findFirst().orElseThrow().totalVolume());
        assertTrue(components.stream().allMatch(c -> c.segments().stream().map(PipeTopology.Segment::layer).distinct().count() == 1));

        for (var direction : Direction.values()) {
            var start = new BlockPos(10, 10, 10);
            var end = start.relative(direction);
            var pair = new LinkedHashMap<PipeTopology.NodeKey, PipeTopology.Segment>();
            var a = new PipeTopology.Segment(start, 1, Set.of(direction), PipeTopology.Kind.EXPOSED, PipeTopology.Layer.TERTIARY);
            var b = new PipeTopology.Segment(end, 1, Set.of(direction.getOpposite()), PipeTopology.Kind.EXPOSED, PipeTopology.Layer.TERTIARY);
            pair.put(new PipeTopology.NodeKey(start, a.layer()), a);
            pair.put(new PipeTopology.NodeKey(end, b.layer()), b);
            assertEquals(1, PipeTopology.componentsLayered(pair).size());
        }
    }

    @Test void worldCoordinateEdgesDoNotWrapIntoNeighbors() {
        for (var pair : List.of(
                new BlockPos[]{new BlockPos(Integer.MAX_VALUE, 0, 0), new BlockPos(Integer.MIN_VALUE, 0, 0)},
                new BlockPos[]{new BlockPos(0, Integer.MAX_VALUE, 0), new BlockPos(0, Integer.MIN_VALUE, 0)})) {
            var first = new PipeTopology.Segment(pair[0], 1, Set.of(pair[0].getX() == Integer.MAX_VALUE ? Direction.EAST : Direction.UP), PipeTopology.Kind.EXPOSED, PipeTopology.Layer.PRIMARY);
            var second = new PipeTopology.Segment(pair[1], 1, Set.of(pair[0].getX() == Integer.MAX_VALUE ? Direction.WEST : Direction.DOWN), PipeTopology.Kind.EXPOSED, PipeTopology.Layer.PRIMARY);
            var nodes = Map.of(new PipeTopology.NodeKey(pair[0], first.layer()), first,
                    new PipeTopology.NodeKey(pair[1], second.layer()), second);
            assertEquals(2, PipeTopology.componentsLayered(nodes).size());
        }
    }

    @Test void layeredKeysAndLegacyLayerAreValidatedAndBudgetCountsAllLanes() {
        var p = new BlockPos(0, 0, 0);
        var secondary = new PipeTopology.Segment(p, 1, Set.of(), PipeTopology.Kind.EXPOSED, PipeTopology.Layer.SECONDARY);
        assertThrows(IllegalArgumentException.class, () -> PipeTopology.components(Map.of(p, secondary)));
        assertThrows(IllegalArgumentException.class, () -> PipeTopology.componentsLayered(Map.of(
                new PipeTopology.NodeKey(p, PipeTopology.Layer.PRIMARY), secondary)));

        Map<PipeTopology.NodeKey, PipeTopology.Segment> tooMany = new java.util.AbstractMap<>() {
            @Override public Set<Entry<PipeTopology.NodeKey, PipeTopology.Segment>> entrySet() { return Set.of(); }
            @Override public int size() { return PipeTopology.MAX_INPUT_NODES + 1; }
        };
        assertThrows(IllegalArgumentException.class, () -> PipeTopology.componentsLayered(tooMany));
        var mutable = new net.minecraft.core.BlockPos.MutableBlockPos(1, 2, 3);
        var key = new PipeTopology.NodeKey(mutable, PipeTopology.Layer.PRIMARY);
        mutable.set(8, 8, 8);
        assertEquals(new BlockPos(1, 2, 3), key.position());
    }

    @Test void canonicalDuplicatesFromAdversarialEntrySetsAreRejectedWithoutMutatingKeys() {
        var firstPosition = new BlockPos.MutableBlockPos(4, 5, 6);
        var secondPosition = new BlockPos.MutableBlockPos(4, 5, 6);
        var firstSegment = new PipeTopology.Segment(new BlockPos(4, 5, 6), 1, Set.of(), PipeTopology.Kind.EXPOSED);
        var secondSegment = new PipeTopology.Segment(new BlockPos(4, 5, 6), 2, Set.of(), PipeTopology.Kind.EXPOSED);
        Map<BlockPos, PipeTopology.Segment> duplicateLegacy = new java.util.AbstractMap<>() {
            @Override public Set<Entry<BlockPos, PipeTopology.Segment>> entrySet() {
                return Set.of(Map.entry(firstPosition, firstSegment), Map.entry(secondPosition, secondSegment));
            }
            @Override public int size() { return 2; }
        };
        assertThrows(IllegalArgumentException.class, () -> PipeTopology.components(duplicateLegacy));
        assertEquals(new BlockPos(4, 5, 6), firstPosition.immutable());
        assertEquals(new BlockPos(4, 5, 6), secondPosition.immutable());

        var firstKey = new PipeTopology.NodeKey(firstPosition, PipeTopology.Layer.PRIMARY);
        var secondKey = new PipeTopology.NodeKey(secondPosition, PipeTopology.Layer.PRIMARY);
        Map<PipeTopology.NodeKey, PipeTopology.Segment> duplicateLayered = new java.util.AbstractMap<>() {
            @Override public Set<Entry<PipeTopology.NodeKey, PipeTopology.Segment>> entrySet() {
                return Set.of(Map.entry(firstKey, firstSegment), Map.entry(secondKey, secondSegment));
            }
            @Override public int size() { return 2; }
        };
        assertThrows(IllegalArgumentException.class, () -> PipeTopology.componentsLayered(duplicateLayered));
        assertEquals(new BlockPos(4, 5, 6), firstKey.position());
        assertEquals(new BlockPos(4, 5, 6), secondKey.position());
    }

    @Test void orderDoesNotMatterInputIsNotMutatedAndOversizeIsRejected() {
        var first = new LinkedHashMap<BlockPos, PipeTopology.Segment>();
        add(first, 0, 0, 0, Set.of(Direction.EAST), PipeTopology.Kind.EXPOSED);
        add(first, 1, 0, 0, Set.of(Direction.WEST), PipeTopology.Kind.EXPOSED);
        var second = new LinkedHashMap<BlockPos, PipeTopology.Segment>();
        second.putAll(Map.of(new BlockPos(1, 0, 0), first.get(new BlockPos(1, 0, 0)), new BlockPos(0, 0, 0), first.get(new BlockPos(0, 0, 0))));
        assertEquals(PipeTopology.components(first), PipeTopology.components(second));
        assertEquals(2, first.size());
        Map<BlockPos, PipeTopology.Segment> tooMany = new java.util.AbstractMap<>() {
            @Override public Set<Entry<BlockPos, PipeTopology.Segment>> entrySet() { return Set.of(); }
            @Override public int size() { return PipeTopology.MAX_INPUT_NODES + 1; }
        };
        assertThrows(IllegalArgumentException.class, () -> PipeTopology.components(tooMany));
    }

    private static void add(Map<BlockPos, PipeTopology.Segment> map, int x, int y, int z, Set<Direction> ports, PipeTopology.Kind kind) {
        var segment = segment(x, y, z, 1, ports, kind);
        map.put(segment.position(), segment);
    }

    private static PipeTopology.Segment segment(int x, int y, int z, double volume, Set<Direction> ports, PipeTopology.Kind kind) {
        return new PipeTopology.Segment(new BlockPos(x, y, z), volume, ports, kind);
    }
}
