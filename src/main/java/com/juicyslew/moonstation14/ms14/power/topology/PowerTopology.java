package com.juicyslew.moonstation14.ms14.power.topology;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Deterministic, world-independent geometry for cable and device-port adjacency. */
public final class PowerTopology {
    /** Four coplanar edges and four perpendicular surface-edge turns at most. */
    public static final int MAX_CABLE_NEIGHBORS = 8;

    private PowerTopology() { }

    public enum EdgeKind { COPLANAR, EDGE_TURN }

    public record CableEdge(CableFaceNode first, CableFaceNode second, EdgeKind kind) { }

    /**
     * Classifies only local surface contacts. Same-face nodes touch across one
     * tangent host step. A turn joins perpendicular faces of edge-touching host
     * cubes: delta = first normal - second normal. Faces on one host never link.
     */
    public static Optional<CableEdge> cableEdge(CableFaceNode first, CableFaceNode second) {
        if (first == null || second == null || first.equals(second) || first.host().equals(second.host())
                || first.tier() != second.tier()) return Optional.empty();

        BlockPos delta = second.host().subtract(first.host());
        if (first.face() == second.face()) {
            Direction normal = first.face();
            if (delta.getY() == 0 &&
                    Math.abs(delta.getX()) + Math.abs(delta.getZ()) == 1 &&
                    (normal.getAxis() != Direction.Axis.X || delta.getX() == 0) &&
                    (normal.getAxis() != Direction.Axis.Z || delta.getZ() == 0)) {
                return Optional.of(new CableEdge(first, second, EdgeKind.COPLANAR));
            }
            if (normal.getAxis() != Direction.Axis.Y && Math.abs(delta.getY()) == 1
                    && delta.getX() == 0 && delta.getZ() == 0) {
                return Optional.of(new CableEdge(first, second, EdgeKind.COPLANAR));
            }
            return Optional.empty();
        }

        if (first.face().getAxis() == second.face().getAxis()) return Optional.empty();
        int dx = first.face().getStepX() - second.face().getStepX();
        int dy = first.face().getStepY() - second.face().getStepY();
        int dz = first.face().getStepZ() - second.face().getStepZ();
        if (delta.getX() == dx && delta.getY() == dy && delta.getZ() == dz) {
            return Optional.of(new CableEdge(first, second, EdgeKind.EDGE_TURN));
        }
        return Optional.empty();
    }

    /** Returns compatible cable neighbors in stable Direction/face order. */
    public static List<CableFaceNode> cableNeighbors(CableFaceNode node, Iterable<CableFaceNode> candidates) {
        if (node == null || candidates == null) throw new NullPointerException();
        List<CableFaceNode> neighbors = new ArrayList<>(MAX_CABLE_NEIGHBORS);
        for (CableFaceNode candidate : candidates) {
            if (cableEdge(node, candidate).isPresent() && !neighbors.contains(candidate)) {
                neighbors.add(candidate);
            }
        }
        neighbors.sort(PowerTopology::compareNodes);
        if (neighbors.size() > MAX_CABLE_NEIGHBORS)
            throw new IllegalArgumentException("candidate set exceeds geometric cable degree");
        return List.copyOf(neighbors);
    }

    private static int compareNodes(CableFaceNode left, CableFaceNode right) {
        int result = Integer.compare(left.host().getX(), right.host().getX());
        if (result == 0) result = Integer.compare(left.host().getY(), right.host().getY());
        if (result == 0) result = Integer.compare(left.host().getZ(), right.host().getZ());
        if (result == 0) result = Integer.compare(left.face().ordinal(), right.face().ordinal());
        if (result == 0) result = Integer.compare(left.tier().ordinal(), right.tier().ordinal());
        return result;
    }

    /** Exact opposing-face device port contact; no plug, rendering, or world lookup. */
    public static boolean deviceAdjacent(DevicePort port, CableFaceNode cable) {
        return port != null && cable != null && port.tier() == cable.tier()
                && cable.face() == port.face().getOpposite()
                && cable.host().equals(port.device().relative(port.face()));
    }
}
