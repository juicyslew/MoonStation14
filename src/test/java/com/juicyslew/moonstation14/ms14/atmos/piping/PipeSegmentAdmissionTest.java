package com.juicyslew.moonstation14.ms14.atmos.piping;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PipeSegmentAdmissionTest {
    private static final BlockPos POS = new BlockPos(4, 12, 7);

    private static PipeChunkData.Record record(PipeTopology.Layer layer, UUID id, double volume, PipeTopology.Kind kind) {
        int ports = PipeChunkData.Record.portMask(java.util.List.of(net.minecraft.core.Direction.NORTH, net.minecraft.core.Direction.SOUTH));
        return new PipeChunkData.Record(4, 12, 7, id, kind, ports, volume,
                kind == PipeTopology.Kind.STRAIGHT_SEALED_WALL ? "minecraft:stone" : "", kind == PipeTopology.Kind.STRAIGHT_SEALED_WALL ? UUID.randomUUID().toString() : "", layer);
    }

    private static PipeGasInventory.Entry entry(PipeTopology.Layer layer, UUID id, double volume, boolean active) {
        var node = new PipeTopology.NodeKey(POS, layer);
        var inventory = new PipeGasInventory().admit(node, id, volume);
        if (!active) inventory = inventory.unload(node, id);
        return inventory.get(node).orElseThrow();
    }

    @Test void coLocatedLayersAreIndependentAndSecondaryNeedsNoPrimary() {
        UUID primary = UUID.randomUUID(), secondary = UUID.randomUUID();
        var p = PipeSegmentAdmission.classify(3, POS, record(PipeTopology.Layer.PRIMARY, primary, 1, PipeTopology.Kind.EXPOSED), entry(PipeTopology.Layer.PRIMARY, primary, 1, true), true);
        var s = PipeSegmentAdmission.classify(3, POS, record(PipeTopology.Layer.SECONDARY, secondary, 2, PipeTopology.Kind.EXPOSED), entry(PipeTopology.Layer.SECONDARY, secondary, 2, true), true);
        assertEquals(PipeSegmentAdmission.Decision.ACCEPTED, p.decision());
        assertEquals(PipeSegmentAdmission.Decision.ACCEPTED, s.decision());
        assertNotEquals(p.snapshot().orElseThrow().id(), s.snapshot().orElseThrow().id());
        assertEquals(PipeTopology.Layer.SECONDARY, s.snapshot().orElseThrow().node().layer());
        assertEquals(PipeSegmentAdmission.Decision.ACCEPTED, PipeSegmentAdmission.classify(2, POS,
                record(PipeTopology.Layer.SECONDARY, secondary, 2, PipeTopology.Kind.EXPOSED), entry(PipeTopology.Layer.SECONDARY, secondary, 2, true), true).decision());
        assertInert(PipeSegmentAdmission.classify(0, POS,
                record(PipeTopology.Layer.SECONDARY, secondary, 2, PipeTopology.Kind.EXPOSED), entry(PipeTopology.Layer.SECONDARY, secondary, 2, true), true));
        assertInert(PipeSegmentAdmission.classify(8, POS,
                record(PipeTopology.Layer.SECONDARY, secondary, 2, PipeTopology.Kind.EXPOSED), entry(PipeTopology.Layer.SECONDARY, secondary, 2, true), true));
    }

    @Test void rejectsUnverifiedOrMismatchedClaimsAsInert() {
        UUID id = UUID.randomUUID();
        var valid = record(PipeTopology.Layer.PRIMARY, id, 1, PipeTopology.Kind.EXPOSED);
        var inventory = new PipeGasInventory().admit(new PipeTopology.NodeKey(POS, PipeTopology.Layer.PRIMARY), id, 1);
        var before = inventory.snapshot();
        assertInert(PipeSegmentAdmission.classify(2, POS, valid, inventory.get(new PipeTopology.NodeKey(POS, PipeTopology.Layer.PRIMARY)).orElseThrow(), true));
        assertInert(PipeSegmentAdmission.classify(1, POS, record(PipeTopology.Layer.PRIMARY, id, 1, PipeTopology.Kind.EXPOSED), entry(PipeTopology.Layer.PRIMARY, UUID.randomUUID(), 1, true), true));
        assertInert(PipeSegmentAdmission.classify(1, POS, record(PipeTopology.Layer.PRIMARY, id, 2, PipeTopology.Kind.EXPOSED), entry(PipeTopology.Layer.PRIMARY, id, 1, true), true));
        assertInert(PipeSegmentAdmission.classify(1, POS, valid, entry(PipeTopology.Layer.PRIMARY, id, 1, false), true));
        assertInert(PipeSegmentAdmission.classify(1, POS, null, entry(PipeTopology.Layer.PRIMARY, id, 1, true), true));
        assertInert(PipeSegmentAdmission.classify(1, POS, valid, entry(PipeTopology.Layer.PRIMARY, id, 1, true), false));
        assertInert(PipeSegmentAdmission.classify(1, POS, record(PipeTopology.Layer.PRIMARY, id, 1, PipeTopology.Kind.STRAIGHT_SEALED_WALL), entry(PipeTopology.Layer.PRIMARY, id, 1, true), true));
        assertEquals(before, inventory.snapshot());
        assertEquals(0, inventory.get(new PipeTopology.NodeKey(POS, PipeTopology.Layer.PRIMARY)).orElseThrow().mixture().moles(com.juicyslew.moonstation14.ms14.atmos.core.GasType.OXYGEN));
    }

    private static void assertInert(PipeSegmentAdmission.Result result) {
        assertEquals(PipeSegmentAdmission.Decision.INERT, result.decision());
        assertTrue(result.snapshot().isEmpty());
    }
}
