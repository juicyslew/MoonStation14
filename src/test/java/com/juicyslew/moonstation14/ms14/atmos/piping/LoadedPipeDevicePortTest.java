package com.juicyslew.moonstation14.ms14.atmos.piping;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Focused contract checks for the exact named-node values consumed by loaded discovery. */
final class LoadedPipeDevicePortTest {
    @Test void namedPortsAtOneHostAreDistinctNodesAndKeepTheirOwnLayerAndIncarnation() {
        BlockPos host = new BlockPos(4, 5, 6);
        UUID inletId = UUID.randomUUID();
        UUID outletId = UUID.randomUUID();
        var inlet = new PipeGasPortHost.Port("inlet", PipeTopology.Layer.PRIMARY,
                Set.of(Direction.WEST), 0.2, inletId);
        var outlet = new PipeGasPortHost.Port("outlet", PipeTopology.Layer.SECONDARY,
                Set.of(Direction.UP), 0.4, outletId);

        assertNotEquals(inlet.key(host), outlet.key(host));
        assertEquals("inlet", inlet.key(host).portId());
        assertEquals("outlet", outlet.key(host).portId());
        assertEquals(PipeTopology.Layer.PRIMARY, inlet.key(host).layer());
        assertEquals(PipeTopology.Layer.SECONDARY, outlet.key(host).layer());
        assertEquals(inletId, inlet.incarnation());
        assertEquals(outletId, outlet.incarnation());
        assertEquals(0.6, inlet.volume() + outlet.volume(), 1e-12);
    }

    @Test void internalEdgesAreExplicitAndRejectSelfJoins() {
        var join = new PipeGasPortHost.InternalEdge("inlet", "outlet");
        assertEquals("inlet", join.firstPortId());
        assertEquals("outlet", join.secondPortId());
        assertThrows(IllegalArgumentException.class,
                () -> new PipeGasPortHost.InternalEdge("inlet", "inlet"));
    }

    @Test void invalidPortDescriptorsFailClosed() {
        UUID id = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class, () -> new PipeGasPortHost.Port(" ",
                PipeTopology.Layer.PRIMARY, Set.of(Direction.NORTH), 1, id));
        assertThrows(IllegalArgumentException.class, () -> new PipeGasPortHost.Port("port",
                PipeTopology.Layer.PRIMARY, Set.of(Direction.NORTH), Double.POSITIVE_INFINITY, id));
        assertThrows(IllegalArgumentException.class, () -> new PipeGasPortHost.Port("port",
                PipeTopology.Layer.PRIMARY, Set.of(), 1, id));
    }
}
