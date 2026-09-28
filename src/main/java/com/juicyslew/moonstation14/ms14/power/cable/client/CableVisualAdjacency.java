package com.juicyslew.moonstation14.ms14.power.cable.client;

import com.juicyslew.moonstation14.ms14.power.topology.CableFaceNode;
import com.juicyslew.moonstation14.ms14.power.topology.PowerTopology;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/** Stable local adjacency masks built only from synchronized cable records. */
public final class CableVisualAdjacency {
    public static final int MASK_BITS = 16;

    private CableVisualAdjacency() { }

    /** Bit positions are the sorted geometric neighbor slots for this host face, independent of input order. */
    public static int mask(CableFaceNode node, Iterable<CableFaceNode> records) {
        List<CableFaceNode> neighbors = PowerTopology.cableNeighbors(node, records);
        int mask = 0;
        for (CableFaceNode neighbor : neighbors) {
            int slot = slot(node, neighbor);
            if (slot >= 0 && slot < MASK_BITS) mask |= 1 << slot;
        }
        return mask;
    }

    public static List<CableFaceNode> neighbors(CableFaceNode node, Iterable<CableFaceNode> records) {
        return PowerTopology.cableNeighbors(node, records);
    }

    /** Render joins only when both topology endpoints are visible; electrical topology is unchanged. */
    public static List<CableFaceNode> visibleNeighbors(CableFaceNode node, Set<CableFaceNode> visibleNodes) {
        List<CableFaceNode> neighbors = new ArrayList<>(PowerTopology.MAX_CABLE_NEIGHBORS);
        for (CableFaceNode candidate : possibleNeighbors(node)) {
            if (visibleNodes.contains(candidate) && PowerTopology.cableEdge(node, candidate).isPresent()) {
                neighbors.add(candidate);
            }
        }
        neighbors.sort(NODE_ORDER);
        return List.copyOf(neighbors);
    }

    /** At most four same-face tangent steps and four perpendicular edge turns. */
    public static List<CableFaceNode> possibleNeighbors(CableFaceNode node) {
        List<CableFaceNode> possible = new ArrayList<>(PowerTopology.MAX_CABLE_NEIGHBORS);
        BlockPos host = node.host();
        Direction face = node.face();

        // A coplanar cable can step along either of the two tangent axes.
        for (Direction direction : Direction.values()) {
            if (direction.getAxis() != face.getAxis()) {
                possible.add(new CableFaceNode(host.relative(direction), face, node.tier()));
            }
        }

        // Across a cube edge, delta is exactly first normal - second normal.
        for (Direction otherFace : Direction.values()) {
            if (otherFace.getAxis() == face.getAxis()) continue;
            int dx = face.getStepX() - otherFace.getStepX();
            int dy = face.getStepY() - otherFace.getStepY();
            int dz = face.getStepZ() - otherFace.getStepZ();
            possible.add(new CableFaceNode(host.offset(dx, dy, dz), otherFace, node.tier()));
        }
        possible.sort(NODE_ORDER);
        return List.copyOf(possible);
    }

    private static final Comparator<CableFaceNode> NODE_ORDER = Comparator
            .comparingInt((CableFaceNode n) -> n.host().getX())
            .thenComparingInt(n -> n.host().getY())
            .thenComparingInt(n -> n.host().getZ())
            .thenComparingInt(n -> n.face().ordinal())
            .thenComparingInt(n -> n.tier().ordinal());

    private static int slot(CableFaceNode node, CableFaceNode neighbor) {
        List<CableFaceNode> possible = new ArrayList<>(16);
        BlockPos host = node.host();
        for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) for (int dz = -1; dz <= 1; dz++) {
            if (dx == 0 && dy == 0 && dz == 0) continue;
            for (Direction face : Direction.values()) {
                CableFaceNode candidate = new CableFaceNode(host.offset(dx, dy, dz), face, node.tier());
                if (PowerTopology.cableEdge(node, candidate).isPresent()) possible.add(candidate);
            }
        }
        possible.sort(Comparator.comparingInt((CableFaceNode n) -> n.host().getX())
                .thenComparingInt(n -> n.host().getY()).thenComparingInt(n -> n.host().getZ())
                .thenComparingInt(n -> n.face().ordinal()));
        return possible.indexOf(neighbor);
    }
}
