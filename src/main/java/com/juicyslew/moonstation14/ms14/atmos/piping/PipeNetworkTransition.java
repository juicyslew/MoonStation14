package com.juicyslew.moonstation14.ms14.atmos.piping;

import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Pure gas arithmetic for a topology change. This provides neither persistent identity nor crash atomicity. */
public final class PipeNetworkTransition {
    private PipeNetworkTransition() { }

    /** An output network identified within this result by canonical key and deterministic ordinal. */
    public record Network(BlockPos key, int ordinal, PipeTopology.Layer layer, GasMixture mixture) {
        public Network(BlockPos key, int ordinal, GasMixture mixture) {
            this(key, ordinal, PipeTopology.Layer.PRIMARY, mixture);
        }
        public Network {
            key = Objects.requireNonNull(key, "key").immutable();
            Objects.requireNonNull(layer, "layer");
            Objects.requireNonNull(mixture, "mixture");
        }
        public PipeTopology.NodeKey nodeKey() { return new PipeTopology.NodeKey(key, layer); }
    }

    /**
     * Redistributes old network stores by segment volume into the next snapshot. Position/volume sets
     * must be identical: in particular, this deliberately rejects unloads rather than losing dormant gas.
     */
    public static List<Network> transition(List<PipeTopology.Component> previous,
                                           List<GasMixture> previousStates,
                                           List<PipeTopology.Component> next) {
        Objects.requireNonNull(previous, "previous");
        Objects.requireNonNull(previousStates, "previousStates");
        Objects.requireNonNull(next, "next");
        if (previous.size() != previousStates.size()) throw new IllegalArgumentException("missing old component state");

        // Provisional arithmetic bound inherited from PipeGasLedger; not yet owner-approved.
        rejectOversized(previous);
        rejectOversized(next);

        Map<PipeTopology.NodeKey, Double> oldVolumes = validate(previous);
        Map<PipeTopology.NodeKey, Double> newVolumes = validate(next);
        if (!oldVolumes.equals(newVolumes)) throw new IllegalArgumentException("topology transition changed segment positions or volumes");

        Map<PipeTopology.NodeKey, List<GasMixture>> incoming = new HashMap<>();
        for (int c = 0; c < previous.size(); c++) {
            PipeTopology.Component component = previous.get(c);
            GasMixture state = Objects.requireNonNull(previousStates.get(c), "old component state");
            List<PipeTopology.Segment> segments = ordered(component);
            double[] volumes = new double[segments.size()];
            for (int i = 0; i < segments.size(); i++) volumes[i] = segments.get(i).volume();
            List<GasMixture> portions = PipeGasLedger.split(state, volumes);
            for (int i = 0; i < segments.size(); i++) incoming.computeIfAbsent(nodeKey(segments.get(i)), ignored -> new ArrayList<>()).add(portions.get(i));
        }

        List<PipeTopology.Component> orderedNext = new ArrayList<>(next);
        orderedNext.sort(Comparator.comparing(PipeNetworkTransition::nodeKey, PipeNetworkTransition::compareNode));
        List<Network> result = new ArrayList<>(orderedNext.size());
        for (int i = 0; i < orderedNext.size(); i++) {
            PipeTopology.Component component = orderedNext.get(i);
            List<GasMixture> parts = new ArrayList<>();
            for (PipeTopology.Segment segment : ordered(component)) {
                List<GasMixture> portion = incoming.get(nodeKey(segment));
                if (portion == null || portion.size() != 1) throw new IllegalArgumentException("invalid segment ownership");
                parts.add(portion.getFirst());
            }
            PipeTopology.NodeKey key = nodeKey(component);
            result.add(new Network(key.position(), i, key.layer(), PipeGasLedger.merge(parts)));
        }
        return List.copyOf(result);
    }

    private static Map<PipeTopology.NodeKey, Double> validate(List<PipeTopology.Component> components) {
        Map<PipeTopology.NodeKey, Double> all = new HashMap<>();
        for (PipeTopology.Component component : components) {
            Objects.requireNonNull(component, "component");
            List<PipeTopology.Segment> segments = ordered(component);
            if (segments.isEmpty()) throw new IllegalArgumentException("empty component");
            double total = 0;
            PipeTopology.Layer layer = segments.getFirst().layer();
            Set<PipeTopology.NodeKey> members = new HashSet<>();
            for (PipeTopology.Segment segment : segments) {
                Objects.requireNonNull(segment, "segment");
                if (!Double.isFinite(segment.volume()) || segment.volume() <= 0) throw new IllegalArgumentException("invalid segment volume");
                total += segment.volume();
                if (!Double.isFinite(total)) throw new IllegalArgumentException("component volume overflow");
                if (segment.layer() != layer) throw new IllegalArgumentException("component contains multiple layers");
                PipeTopology.NodeKey node = nodeKey(segment);
                if (all.put(node, segment.volume()) != null) throw new IllegalArgumentException("overlapping or duplicate segment");
                members.add(node);
            }
            if (!connected(segments, members)) throw new IllegalArgumentException("component is not reciprocally connected");
            if (!Double.isFinite(component.totalVolume()) || !volumeTotalsAgree(total, component.totalVolume(), segments.size()))
                throw new IllegalArgumentException("component volume mismatch");
        }
        return all;
    }

    private static void rejectOversized(List<PipeTopology.Component> components) {
        for (PipeTopology.Component component : components) {
            Objects.requireNonNull(component, "component");
            if (component.segments().size() > PipeGasLedger.MAX_RECIPIENTS)
                throw new IllegalArgumentException("component exceeds provisional gas recipient limit: " + PipeGasLedger.MAX_RECIPIENTS);
        }
    }

    /** Allows the worst-case rounding difference of two positive summation orders (gamma_n bound). */
    private static boolean volumeTotalsAgree(double computed, double reported, int count) {
        if (Double.compare(computed, reported) == 0) return true;
        double unitRoundoff = Math.ulp(1.0) / 2.0;
        double product = count * unitRoundoff;
        if (product >= 1.0) return false;
        double tolerance = (product / (1.0 - product)) * Math.max(computed, reported);
        return Math.abs(computed - reported) <= tolerance;
    }

    private static List<PipeTopology.Segment> ordered(PipeTopology.Component component) {
        List<PipeTopology.Segment> segments = new ArrayList<>(component.segments());
        segments.sort(Comparator.comparing(PipeNetworkTransition::nodeKey, PipeNetworkTransition::compareNode));
        Set<PipeTopology.NodeKey> seen = new HashSet<>();
        for (PipeTopology.Segment segment : segments) {
            Objects.requireNonNull(segment, "segment");
            if (!seen.add(nodeKey(segment))) throw new IllegalArgumentException("duplicate segment");
        }
        return segments;
    }

    private static boolean connected(List<PipeTopology.Segment> segments, Set<PipeTopology.NodeKey> members) {
        Set<PipeTopology.NodeKey> reached = new HashSet<>();
        java.util.ArrayDeque<PipeTopology.NodeKey> queue = new java.util.ArrayDeque<>();
        PipeTopology.Segment first = segments.getFirst();
        PipeTopology.NodeKey start = nodeKey(first);
        reached.add(start); queue.add(start);
        Map<PipeTopology.NodeKey, PipeTopology.Segment> byKey = new HashMap<>();
        for (PipeTopology.Segment segment : segments) byKey.put(nodeKey(segment), segment);
        while (!queue.isEmpty()) {
            PipeTopology.NodeKey current = queue.removeFirst();
            PipeTopology.Segment segment = byKey.get(current);
            for (net.minecraft.core.Direction direction : segment.ports()) {
                PipeTopology.NodeKey neighborKey = PipeTopology.neighborKey(current, direction);
                if (neighborKey == null) continue;
                PipeTopology.Segment neighbor = byKey.get(neighborKey);
                if (neighbor != null && neighbor.ports().contains(direction.getOpposite()) && reached.add(neighborKey)) queue.addLast(neighborKey);
            }
        }
        return reached.equals(members);
    }

    private static PipeTopology.NodeKey nodeKey(PipeTopology.Segment segment) {
        return new PipeTopology.NodeKey(segment.position(), segment.layer());
    }

    private static PipeTopology.NodeKey nodeKey(PipeTopology.Component component) {
        PipeTopology.Segment first = ordered(component).getFirst();
        return nodeKey(first);
    }

    private static int compareNode(PipeTopology.NodeKey a, PipeTopology.NodeKey b) {
        int position = comparePos(a.position(), b.position());
        return position != 0 ? position : a.layer().compareTo(b.layer());
    }

    private static int comparePos(BlockPos a, BlockPos b) {
        int x = Integer.compare(a.getX(), b.getX());
        if (x != 0) return x;
        int y = Integer.compare(a.getY(), b.getY());
        return y != 0 ? y : Integer.compare(a.getZ(), b.getZ());
    }
}

