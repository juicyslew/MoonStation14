package com.juicyslew.moonstation14.ms14.atmos.piping;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Pure topology kernel over an explicitly supplied, finite snapshot of candidate pipe positions. */
public final class PipeTopology {
    /** Provisional bound: caps synchronous graph work at 65,536 nodes, pending profiling/approval. */
    public static final int MAX_INPUT_NODES = 65_536;

    private PipeTopology() { }

    public enum Kind { EXPOSED, STRAIGHT_SEALED_WALL }

    public enum Layer { PRIMARY, SECONDARY, TERTIARY }

    public record NodeKey(BlockPos position, Layer layer) {
        public NodeKey {
            Objects.requireNonNull(position, "position");
            Objects.requireNonNull(layer, "layer");
            position = position.immutable();
        }
    }

    /** Returns the adjacent node, or {@code null} when stepping would overflow BlockPos coordinates. */
    static NodeKey neighborKey(NodeKey key, Direction direction) {
        long x = (long) key.position().getX() + direction.getStepX();
        long y = (long) key.position().getY() + direction.getStepY();
        long z = (long) key.position().getZ() + direction.getStepZ();
        if (x < Integer.MIN_VALUE || x > Integer.MAX_VALUE
                || y < Integer.MIN_VALUE || y > Integer.MAX_VALUE
                || z < Integer.MIN_VALUE || z > Integer.MAX_VALUE) return null;
        return new NodeKey(new BlockPos((int) x, (int) y, (int) z), key.layer());
    }

    public record Segment(BlockPos position, double volume, Set<Direction> ports, Kind kind, Layer layer) {
        public Segment(BlockPos position, double volume, Set<Direction> ports, Kind kind) {
            this(position, volume, ports, kind, Layer.PRIMARY);
        }
        public Segment {
            Objects.requireNonNull(position, "position");
            position = position.immutable();
            if (!Double.isFinite(volume) || volume <= 0.0) throw new IllegalArgumentException("volume must be positive and finite");
            Objects.requireNonNull(ports, "ports");
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(layer, "layer");
            EnumSet<Direction> copy = ports.isEmpty() ? EnumSet.noneOf(Direction.class) : EnumSet.copyOf(ports);
            if (kind == Kind.STRAIGHT_SEALED_WALL) {
                boolean opposite = false;
                for (Direction direction : copy) opposite |= copy.contains(direction.getOpposite());
                if (!opposite || copy.size() != 2) throw new IllegalArgumentException("sealed-wall segment requires exactly two opposite ports");
            }
            ports = Collections.unmodifiableSet(copy);
        }
    }

    public record Component(List<Segment> segments, double totalVolume) {
        public Component { segments = List.copyOf(segments); }
    }

    /** Rejects duplicate positions and over-budget inputs before graph traversal. */
    public static List<Component> components(Map<BlockPos, Segment> candidates) {
        Objects.requireNonNull(candidates, "candidates");
        if (candidates.size() > MAX_INPUT_NODES) throw new IllegalArgumentException("candidate node limit exceeded: " + MAX_INPUT_NODES);
        Map<NodeKey, Segment> layered = new HashMap<>();
        for (var entry : candidates.entrySet()) {
            BlockPos key = Objects.requireNonNull(entry.getKey(), "candidate position").immutable();
            Segment segment = Objects.requireNonNull(entry.getValue(), "candidate segment");
            if (!key.equals(segment.position())) throw new IllegalArgumentException("map key must equal segment position");
            if (segment.layer() != Layer.PRIMARY) throw new IllegalArgumentException("legacy candidates must use PRIMARY layer");
            if (layered.put(new NodeKey(key, Layer.PRIMARY), segment) != null) throw new IllegalArgumentException("duplicate candidate position");
        }
        return componentsLayered(layered);
    }

    public static List<Component> componentsLayered(Map<NodeKey, Segment> candidates) {
        Objects.requireNonNull(candidates, "candidates");
        if (candidates.size() > MAX_INPUT_NODES) throw new IllegalArgumentException("candidate node limit exceeded: " + MAX_INPUT_NODES);
        Map<NodeKey, Segment> snapshot = new HashMap<>();
        for (var entry : candidates.entrySet()) {
            NodeKey key = Objects.requireNonNull(entry.getKey(), "candidate key");
            Segment segment = Objects.requireNonNull(entry.getValue(), "candidate segment");
            if (!key.position().equals(segment.position()) || key.layer() != segment.layer()) throw new IllegalArgumentException("map key must equal segment position and layer");
            if (snapshot.put(key, segment) != null) throw new IllegalArgumentException("duplicate candidate position and layer");
        }
        Comparator<BlockPos> positionOrder = Comparator.comparingLong((BlockPos p) -> p.getX()).thenComparingLong(p -> p.getY()).thenComparingLong(p -> p.getZ());
        Comparator<NodeKey> order = Comparator.comparing(NodeKey::position, positionOrder).thenComparing(NodeKey::layer);
        List<NodeKey> ordered = new ArrayList<>(snapshot.keySet());
        ordered.sort(order);
        Set<NodeKey> visited = new java.util.HashSet<>();
        List<Component> result = new ArrayList<>();
        for (NodeKey start : ordered) {
            if (!visited.add(start)) continue;
            ArrayDeque<NodeKey> queue = new ArrayDeque<>();
            queue.add(start);
            List<Segment> members = new ArrayList<>();
            double volume = 0;
            while (!queue.isEmpty()) {
                NodeKey key = queue.removeFirst();
                BlockPos position = key.position();
                Segment segment = snapshot.get(key);
                members.add(segment);
                volume += segment.volume();
                if (!Double.isFinite(volume)) throw new IllegalArgumentException("component volume must be finite");
                for (Direction direction : Direction.values()) {
                    if (!segment.ports().contains(direction)) continue;
                    NodeKey neighborKey = neighborKey(key, direction);
                    if (neighborKey == null) continue;
                    Segment neighbor = snapshot.get(neighborKey);
                    if (neighbor != null && neighbor.ports().contains(direction.getOpposite()) && visited.add(neighborKey)) queue.addLast(neighborKey);
                }
            }
            members.sort(Comparator.comparing(Segment::position, positionOrder).thenComparing(Segment::layer));
            result.add(new Component(members, volume));
        }
        return List.copyOf(result);
    }
}

