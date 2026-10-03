package com.juicyslew.moonstation14.ms14.atmos.piping;

import com.google.gson.JsonObject;
import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PipeLayerRoundTripTest {
    @Test
    void schemaV2PreservesCoLocatedLayersAndTransitionDoesNotCrossLanes() {
        PipeChunkData chunk = new PipeChunkData();
        add(chunk, 5, 40, 4, PipeTopology.Layer.PRIMARY, Direction.EAST);
        add(chunk, 6, 40, 4, PipeTopology.Layer.PRIMARY, Direction.WEST, Direction.UP);
        add(chunk, 6, 41, 4, PipeTopology.Layer.PRIMARY, Direction.DOWN);
        add(chunk, 5, 40, 4, PipeTopology.Layer.SECONDARY, Direction.EAST);
        add(chunk, 6, 40, 4, PipeTopology.Layer.SECONDARY, Direction.WEST);
        add(chunk, 5, 40, 4, PipeTopology.Layer.TERTIARY, Direction.EAST);
        add(chunk, 6, 40, 4, PipeTopology.Layer.TERTIARY, Direction.WEST);

        PipeChunkData decoded = PipeChunkData.CODEC.parse(JsonOps.INSTANCE,
                PipeChunkData.CODEC.encodeStart(JsonOps.INSTANCE, chunk).getOrThrow()).getOrThrow();
        Map<PipeTopology.NodeKey, PipeTopology.Segment> topology = adapt(decoded, -16, 0);
        List<PipeTopology.Component> before = PipeTopology.componentsLayered(topology);
        assertEquals(3, before.size());
        assertEquals(7, before.stream().mapToInt(component -> component.segments().size()).sum());
        assertTrue(before.stream().allMatch(component -> Double.isFinite(component.totalVolume())));
        assertEquals(3, before.stream().filter(component -> component.segments().stream()
                .anyMatch(segment -> segment.layer() == PipeTopology.Layer.PRIMARY)).findFirst().orElseThrow().segments().size());

        List<GasMixture> states = before.stream().map(component -> switch (component.segments().getFirst().layer()) {
            case PRIMARY -> mixture(2, 1, 280);
            case SECONDARY -> mixture(7, 3, 390);
            case TERTIARY -> mixture(11, 5, 510);
        }).toList();

        Map<PipeTopology.NodeKey, PipeTopology.Segment> disconnected = new HashMap<>(topology);
        BlockPos cut = new BlockPos(-10, 40, 4);
        PipeTopology.NodeKey cutKey = new PipeTopology.NodeKey(cut, PipeTopology.Layer.PRIMARY);
        PipeTopology.Segment original = disconnected.get(cutKey);
        disconnected.put(cutKey, new PipeTopology.Segment(cut, original.volume(), Set.of(Direction.WEST), original.kind(), original.layer()));
        List<PipeTopology.Component> after = PipeTopology.componentsLayered(disconnected);
        List<PipeNetworkTransition.Network> result = PipeNetworkTransition.transition(before, states, after);

        assertEquals(4, result.size());
        assertEquals(2, result.stream().filter(network -> network.layer() == PipeTopology.Layer.PRIMARY).count());
        assertEquals(1, result.stream().filter(network -> network.layer() == PipeTopology.Layer.SECONDARY).count());
        assertEquals(1, result.stream().filter(network -> network.layer() == PipeTopology.Layer.TERTIARY).count());
        for (PipeNetworkTransition.Network network : result) {
            if (network.layer() == PipeTopology.Layer.PRIMARY) continue;
            GasMixture initial = states.get(before.indexOf(before.stream().filter(component -> component.segments().getFirst().layer() == network.layer()).findFirst().orElseThrow()));
            assertEquals(initial.moles(GasType.OXYGEN), network.mixture().moles(GasType.OXYGEN), 1e-10);
            assertEquals(initial.moles(GasType.PLASMA), network.mixture().moles(GasType.PLASMA), 1e-10);
            assertEquals(initial.temperatureKelvin(), network.mixture().temperatureKelvin(), 1e-9);
            assertTrue(Double.isFinite(network.mixture().thermalEnergy()));
        }
        List<GasMixture> primaryAfter = result.stream().filter(network -> network.layer() == PipeTopology.Layer.PRIMARY)
                .map(PipeNetworkTransition.Network::mixture).toList();
        GasMixture primaryBefore = states.get(before.indexOf(before.stream().filter(component -> component.segments().getFirst().layer() == PipeTopology.Layer.PRIMARY).findFirst().orElseThrow()));
        assertEquals(primaryBefore.moles(GasType.OXYGEN), primaryAfter.stream().mapToDouble(gas -> gas.moles(GasType.OXYGEN)).sum(), 1e-10);
        assertEquals(primaryBefore.moles(GasType.PLASMA), primaryAfter.stream().mapToDouble(gas -> gas.moles(GasType.PLASMA)).sum(), 1e-10);
        assertNotEquals(result.stream().filter(network -> network.layer() == PipeTopology.Layer.SECONDARY).findFirst().orElseThrow().mixture().temperatureKelvin(),
                result.stream().filter(network -> network.layer() == PipeTopology.Layer.TERTIARY).findFirst().orElseThrow().mixture().temperatureKelvin());
    }

    @Test
    void versionOneWithoutLayerDefaultsToPrimaryAndNegativeChunkOriginsRoundTrip() {
        JsonObject legacy = new JsonObject();
        legacy.addProperty("version", 1);
        com.google.gson.JsonArray records = new com.google.gson.JsonArray();
        JsonObject record = new JsonObject();
        record.addProperty("x", 2); record.addProperty("y", 70); record.addProperty("z", 3);
        record.addProperty("id", UUID.randomUUID().toString()); record.addProperty("kind", "EXPOSED");
        record.addProperty("ports", 0); record.addProperty("volume", 1.25);
        records.add(record); legacy.add("records", records);
        PipeChunkData old = PipeChunkData.CODEC.parse(JsonOps.INSTANCE, legacy).getOrThrow();
        assertEquals(PipeTopology.Layer.PRIMARY, old.snapshot().getFirst().layer());

        PipeChunkData west = new PipeChunkData();
        PipeChunkData east = new PipeChunkData();
        add(west, 15, 12, 15, PipeTopology.Layer.SECONDARY, Direction.EAST);
        add(east, 0, 12, 15, PipeTopology.Layer.SECONDARY, Direction.WEST);
        PipeChunkData westCopy = roundTrip(west);
        PipeChunkData eastCopy = roundTrip(east);
        Map<PipeTopology.NodeKey, PipeTopology.Segment> adapted = adapt(westCopy, -32, -16);
        adapted.putAll(adapt(eastCopy, -16, -16));
        assertTrue(adapted.containsKey(new PipeTopology.NodeKey(new BlockPos(-17, 12, -1), PipeTopology.Layer.SECONDARY)));
        assertEquals(1, PipeTopology.componentsLayered(adapted).size());
    }

    private static void add(PipeChunkData chunk, int x, int y, int z, PipeTopology.Layer layer, Direction... ports) {
        assertTrue(chunk.add(new PipeChunkData.Record(x, y, z, UUID.randomUUID(), PipeTopology.Kind.EXPOSED,
                PipeChunkData.Record.portMask(List.of(ports)), 1.0, "", "", layer)));
    }

    /** Test-only persistence-to-topology adapter; this is intentionally not production recovery logic. */
    private static Map<PipeTopology.NodeKey, PipeTopology.Segment> adapt(PipeChunkData data, int chunkOriginX, int chunkOriginZ) {
        Map<PipeTopology.NodeKey, PipeTopology.Segment> result = new HashMap<>();
        for (PipeChunkData.Record record : data.snapshot()) {
            EnumSet<Direction> ports = EnumSet.noneOf(Direction.class);
            for (Direction direction : Direction.values()) if ((record.ports() & (1 << direction.ordinal())) != 0) ports.add(direction);
            BlockPos position = new BlockPos(chunkOriginX + record.x(), record.y(), chunkOriginZ + record.z());
            PipeTopology.NodeKey key = new PipeTopology.NodeKey(position, record.layer());
            result.put(key, new PipeTopology.Segment(position, record.volume(), ports, record.kind(), record.layer()));
        }
        return result;
    }

    private static GasMixture mixture(double oxygen, double plasma, double temperature) {
        return new GasMixture(Map.of(GasType.OXYGEN, oxygen, GasType.PLASMA, plasma), temperature);
    }

    private static PipeChunkData roundTrip(PipeChunkData source) {
        return PipeChunkData.CODEC.parse(JsonOps.INSTANCE,
                PipeChunkData.CODEC.encodeStart(JsonOps.INSTANCE, source).getOrThrow()).getOrThrow();
    }
}
