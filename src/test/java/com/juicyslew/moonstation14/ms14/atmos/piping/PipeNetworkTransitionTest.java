package com.juicyslew.moonstation14.ms14.atmos.piping;

import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class PipeNetworkTransitionTest {
    @Test void splitThenMergeConservesInventoryAndEnergyWithCanonicalNegativeKeys() {
        var old = topology(segment(-2, 0, 0, 2, Direction.EAST), segment(-1, 0, 0, 1, Direction.WEST));
        GasMixture source = new GasMixture(Map.of(GasType.OXYGEN, 9.0, GasType.PLASMA, 3.0), 420);
        var split = PipeNetworkTransition.transition(old, List.of(source),
                List.of(component(old.getFirst().segments().getFirst()), component(old.getFirst().segments().getLast())));
        assertEquals(2, split.size());
        assertEquals(new BlockPos(-2, 0, 0), split.get(0).key());
        assertEquals(0, split.get(0).ordinal());
        assertEquals(9, sum(split, GasType.OXYGEN), 1e-14);
        assertEquals(source.thermalEnergy(), split.stream().mapToDouble(n -> n.mixture().thermalEnergy()).sum(), 1e-10);

        var joined = PipeNetworkTransition.transition(List.of(
                        component(old.getFirst().segments().getFirst()), component(old.getFirst().segments().getLast())),
                split.stream().map(PipeNetworkTransition.Network::mixture).toList(), old);
        assertEquals(1, joined.size());
        assertEquals(source.moles(GasType.OXYGEN), joined.getFirst().mixture().moles(GasType.OXYGEN), 1e-14);
        assertEquals(source.thermalEnergy(), joined.getFirst().mixture().thermalEnergy(), 1e-10);
    }

    @Test void independentHotAndColdNetworksMergeAndRepeatedTransitionsDoNotDuplicate() {
        var a = segment(-3, 0, 0, 1);
        var b = segment(-2, 0, 0, 1);
        var disconnected = List.of(component(a), component(b));
        GasMixture hot = new GasMixture(Map.of(GasType.OXYGEN, 1.0), 600);
        GasMixture cold = new GasMixture(Map.of(GasType.OXYGEN, 2.0), 150);
        var mergedTopology = topology(segment(-3, 0, 0, 1, Direction.EAST), segment(-2, 0, 0, 1, Direction.WEST));
        var merged = PipeNetworkTransition.transition(disconnected, List.of(hot, cold), mergedTopology);
        assertEquals(1, merged.size());
        var once = PipeNetworkTransition.transition(mergedTopology, merged.stream().map(PipeNetworkTransition.Network::mixture).toList(), mergedTopology);
        assertEquals(merged.get(0).mixture().moles(GasType.OXYGEN), once.get(0).mixture().moles(GasType.OXYGEN), 1e-14);
        assertEquals(merged.get(0).mixture().thermalEnergy(), once.get(0).mixture().thermalEnergy(), 1e-10);
        assertEquals(3, merged.get(0).mixture().moles(GasType.OXYGEN), 1e-14);
        assertEquals((hot.thermalEnergy() + cold.thermalEnergy()) / merged.get(0).mixture().heatCapacity(), merged.get(0).mixture().temperatureKelvin(), 1e-12);
    }

    @Test void rejectsChangedNodeSetsAndDoesNotMutateSource() {
        var old = List.of(component(segment(0, 0, 0, 1)));
        GasMixture original = new GasMixture(Map.of(GasType.OXYGEN, 2.0), 300);
        assertThrows(IllegalArgumentException.class, () -> PipeNetworkTransition.transition(old, List.of(original), List.of()));
        assertThrows(IllegalArgumentException.class, () -> PipeNetworkTransition.transition(old, List.of(original), List.of(component(segment(0, 0, 0, 2)))));
        assertEquals(2, original.moles(GasType.OXYGEN));
    }

    @Test void acceptsTopologyVolumeSummedInDifferentOrderButRejectsForgedTotal() {
        var a = segment(0, 0, 0, 0.1, Direction.UP, Direction.EAST);
        var b = segment(0, 1, 0, 0.2, Direction.DOWN);
        var c = segment(1, 0, 0, 0.3, Direction.WEST);
        var bfs = topology(a, b, c).getFirst();
        assertEquals(List.of(a.position(), b.position(), c.position()), bfs.segments().stream().map(PipeTopology.Segment::position).toList());
        var state = new GasMixture(Map.of(GasType.OXYGEN, 1.0), 300);
        assertDoesNotThrow(() -> PipeNetworkTransition.transition(List.of(bfs), List.of(state), List.of(bfs)));

        var forged = new PipeTopology.Component(bfs.segments(), bfs.totalVolume() + 0.01);
        assertThrows(IllegalArgumentException.class,
                () -> PipeNetworkTransition.transition(List.of(forged), List.of(state), List.of(forged)));
    }

    @Test void rejectsComponentOverLedgerRecipientLimitBeforeRedistribution() {
        var segments = new java.util.ArrayList<PipeTopology.Segment>();
        for (int i = 0; i <= PipeGasLedger.MAX_RECIPIENTS; i++) segments.add(segment(i, 0, 0, 1));
        var oversized = new PipeTopology.Component(segments, segments.size());
        var state = new GasMixture(Map.of(GasType.OXYGEN, 1.0), 300);
        assertThrows(IllegalArgumentException.class,
                () -> PipeNetworkTransition.transition(List.of(oversized), List.of(state), List.of(oversized)));
    }

    @Test void overlappingLayersRemainIndependentAndHaveStableLayeredIdentity() {
        var a = layered(PipeTopology.Layer.PRIMARY, 1, 1, Direction.EAST);
        var b = layered(PipeTopology.Layer.SECONDARY, 1, 1, Direction.EAST);
        var c = layered(PipeTopology.Layer.PRIMARY, 2, 1, Direction.WEST);
        var d = layered(PipeTopology.Layer.SECONDARY, 2, 1, Direction.WEST);
        var old = List.of(new PipeTopology.Component(List.of(a, c), 2), new PipeTopology.Component(List.of(b, d), 2));
        var first = new GasMixture(Map.of(GasType.OXYGEN, 4.0), 600);
        var second = new GasMixture(Map.of(GasType.PLASMA, 6.0), 150);
        var result = PipeNetworkTransition.transition(old, List.of(first, second), old);
        assertEquals(PipeTopology.Layer.PRIMARY, result.get(0).layer());
        assertEquals(PipeTopology.Layer.SECONDARY, result.get(1).layer());
        assertEquals(result.get(0).key(), result.get(1).key());
        assertNotEquals(result.get(0).nodeKey(), result.get(1).nodeKey());
        assertEquals(4, result.get(0).mixture().moles(GasType.OXYGEN));
        assertEquals(0, result.get(0).mixture().moles(GasType.PLASMA));
        assertEquals(6, result.get(1).mixture().moles(GasType.PLASMA));
        assertEquals(0, result.get(1).mixture().moles(GasType.OXYGEN));
        assertEquals(first.thermalEnergy(), result.get(0).mixture().thermalEnergy(), 1e-10);
        assertEquals(second.thermalEnergy(), result.get(1).mixture().thermalEnergy(), 1e-10);
        var repeated = PipeNetworkTransition.transition(old, List.of(first, second), old);
        assertEquals(result.stream().map(PipeNetworkTransition.Network::nodeKey).toList(), repeated.stream().map(PipeNetworkTransition.Network::nodeKey).toList());
        assertEquals(result.get(0).mixture().moles(GasType.OXYGEN), repeated.get(0).mixture().moles(GasType.OXYGEN), 1e-14);
        assertEquals(result.get(1).mixture().moles(GasType.PLASMA), repeated.get(1).mixture().moles(GasType.PLASMA), 1e-14);
        assertThrows(IllegalArgumentException.class, () -> PipeNetworkTransition.transition(old, List.of(first, second), List.of(new PipeTopology.Component(List.of(a, c), 2))));
    }

    @Test void rejectsForgedMixedOrDisconnectedComponentsWithoutMutatingGas() {
        var a = layered(PipeTopology.Layer.PRIMARY, 0, 1);
        var b = layered(PipeTopology.Layer.SECONDARY, 0, 1);
        var mixed = new PipeTopology.Component(List.of(a, b), 2);
        var state = new GasMixture(Map.of(GasType.OXYGEN, 3.0), 300);
        assertThrows(IllegalArgumentException.class, () -> PipeNetworkTransition.transition(List.of(mixed), List.of(state), List.of(mixed)));
        var disconnected = new PipeTopology.Component(List.of(a, layered(PipeTopology.Layer.PRIMARY, 4, 1)), 2);
        assertThrows(IllegalArgumentException.class, () -> PipeNetworkTransition.transition(List.of(disconnected), List.of(state), List.of(disconnected)));
        assertEquals(3, state.moles(GasType.OXYGEN));
    }

    @Test void rejectsForgedComponentConnectedOnlyByCoordinateWraparound() {
        for (var pair : List.of(
                new BlockPos[]{new BlockPos(Integer.MAX_VALUE, 0, 0), new BlockPos(Integer.MIN_VALUE, 0, 0)},
                new BlockPos[]{new BlockPos(0, Integer.MAX_VALUE, 0), new BlockPos(0, Integer.MIN_VALUE, 0)})) {
            boolean xEdge = pair[0].getX() == Integer.MAX_VALUE;
            Direction forward = xEdge ? Direction.EAST : Direction.UP;
            var first = new PipeTopology.Segment(pair[0], 1, Set.of(forward), PipeTopology.Kind.EXPOSED);
            var second = new PipeTopology.Segment(pair[1], 1, Set.of(forward.getOpposite()), PipeTopology.Kind.EXPOSED);
            var forged = new PipeTopology.Component(List.of(first, second), 2);
            var state = new GasMixture(Map.of(GasType.OXYGEN, 1.0), 300);
            assertThrows(IllegalArgumentException.class,
                    () -> PipeNetworkTransition.transition(List.of(forged), List.of(state), List.of(forged)));
        }
    }

    private static double sum(List<PipeNetworkTransition.Network> networks, GasType type) {
        return networks.stream().mapToDouble(n -> n.mixture().moles(type)).sum();
    }

    private static List<PipeTopology.Component> topology(PipeTopology.Segment... segments) {
        var map = new java.util.LinkedHashMap<BlockPos, PipeTopology.Segment>();
        for (var segment : segments) map.put(segment.position(), segment);
        return PipeTopology.components(map);
    }

    private static PipeTopology.Component component(PipeTopology.Segment segment) {
        return new PipeTopology.Component(List.of(segment), segment.volume());
    }

    private static PipeTopology.Segment segment(int x, int y, int z, double volume, Direction... ports) {
        return new PipeTopology.Segment(new BlockPos(x, y, z), volume, Set.of(ports), PipeTopology.Kind.EXPOSED);
    }

    private static PipeTopology.Segment layered(PipeTopology.Layer layer, int x, double volume, Direction... ports) {
        return new PipeTopology.Segment(new BlockPos(x, 0, 0), volume, Set.of(ports), PipeTopology.Kind.EXPOSED, layer);
    }
}
