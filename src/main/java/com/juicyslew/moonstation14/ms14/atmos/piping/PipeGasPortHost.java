package com.juicyslew.moonstation14.ms14.atmos.piping;

import net.minecraft.core.Direction;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Contract for a loaded device exposing separately owned finite pipe ports. */
public interface PipeGasPortHost {
    /** Port descriptors must be persisted by the host; descriptor identity is the gas-owner incarnation. */
    List<Port> gasPorts();

    /** Explicit internal links only (for example, an open valve); ordinary devices return no links. */
    default Set<InternalEdge> openInternalGasEdges() { return Set.of(); }

    record Port(String id, PipeTopology.Layer layer, Set<Direction> directions, double volume, UUID incarnation) {
        public Port {
            if (id == null || id.isBlank()) throw new IllegalArgumentException("port id must be nonblank");
            if (layer == null || directions == null || incarnation == null) throw new NullPointerException();
            if (!Double.isFinite(volume) || volume <= 0) throw new IllegalArgumentException("port volume must be positive and finite");
            if (directions.isEmpty()) throw new IllegalArgumentException("port requires at least one direction");
            directions = Set.copyOf(directions);
        }
        public PipeTopology.NodeKey key(net.minecraft.core.BlockPos position) {
            return new PipeTopology.NodeKey(position, layer, id);
        }
    }

    record InternalEdge(String firstPortId, String secondPortId) {
        public InternalEdge {
            if (firstPortId == null || secondPortId == null || firstPortId.equals(secondPortId))
                throw new IllegalArgumentException("internal edge requires distinct port ids");
        }
    }
}
