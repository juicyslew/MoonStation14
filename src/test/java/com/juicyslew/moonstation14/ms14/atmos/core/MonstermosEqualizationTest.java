package com.juicyslew.moonstation14.ms14.atmos.core;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class MonstermosEqualizationTest {
    @Test
    void movesDonorGasThroughActualDoorwayFacesAndKeepsSpeciesAndEnergy() {
        BlockPos a = p(0), door = p(1), b = p(2);
        GasMixture hotOxygen = mix(GasType.OXYGEN, 10, 400);
        GasMixture doorGas = mix(GasType.NITROGEN, 5, 200);
        GasMixture coldNitrogen = GasMixture.vacuum();
        Map<BlockPos, GasMixture> cells = map(a, hotOxygen, door, doorGas, b, coldNitrogen);
        var result = MonstermosEqualization.equalize(cells, graph(a, door, b), 3);
        assertTrue(result.work().complete());
        assertTrue(result.edgeFlows().containsKey(new MonstermosEqualization.DirectedEdge(a, door)));
        assertTrue(result.edgeFlows().containsKey(new MonstermosEqualization.DirectedEdge(door, b)));
        assertEquals(total(cells), total(result.states()), 1e-10);
        assertEquals(energy(cells), energy(result.states()), 1e-8);
        assertEquals(gasTotal(cells, GasType.OXYGEN), gasTotal(result.states(), GasType.OXYGEN), 1e-10);
        assertEquals(gasTotal(cells, GasType.NITROGEN), gasTotal(result.states(), GasType.NITROGEN), 1e-10);
        assertTrue(result.states().get(door).totalMoles() > 0);
    }

    @Test
    void highPressureHundredsOfMolesMovePartiallyThroughOneDoorPerPass() {
        BlockPos source = p(0), doorway = p(1), receiver = p(2);
        GasMixture pressurized = mix(GasType.OXYGEN, 120, 400);
        var result = MonstermosEqualization.equalize(
                map(source, pressurized, doorway, GasMixture.vacuum(), receiver, GasMixture.vacuum()),
                graph(source, doorway, receiver), 3);

        assertTrue(result.work().complete());
        assertTrue(result.states().get(doorway).totalMoles() > 0);
        assertEquals(12, result.states().get(doorway).totalMoles(), 1e-10);
        assertEquals(0, result.states().get(receiver).totalMoles(), 1e-10);
        assertEquals(12, result.edgeFlows().get(new MonstermosEqualization.DirectedEdge(source, doorway)), 1e-10);
        assertTrue(result.work().transferOperations() <= 3);
        assertTrue(result.states().get(source).totalMoles() > 40, "one invocation must not equalize the room");
        assertEquals(120, gasTotal(result.states(), GasType.OXYGEN), 1e-10);
        assertEquals(pressurized.thermalEnergy(), energy(result.states()), 1e-8);
    }

    @Test
    void repeatedInvocationsConvergeGraduallyAndConserveSpeciesAndEnergy() {
        BlockPos source = p(0), receiver = p(1);
        Map<BlockPos, GasMixture> initial = map(source,
                new GasMixture(Map.of(GasType.OXYGEN, 80.0, GasType.NITROGEN, 20.0), 420),
                receiver, GasMixture.vacuum());
        Map<BlockPos, Set<BlockPos>> topology = graph(source, receiver);
        var first = MonstermosEqualization.equalize(initial, topology, 2);
        assertTrue(first.states().get(receiver).totalMoles() > 0);
        assertTrue(first.states().get(receiver).totalMoles() < 50, "first step is deliberately partial");
        Map<BlockPos, GasMixture> current = first.states();
        for (int i = 0; i < 9; i++) current = MonstermosEqualization.equalize(current, topology, 2).states();
        assertTrue(current.get(receiver).totalMoles() > first.states().get(receiver).totalMoles());
        assertTrue(current.get(receiver).totalMoles() > 35, "ten bounded steps should make substantial progress");
        for (GasType gas : GasType.values()) assertEquals(gasTotal(initial, gas), gasTotal(current, gas), 1e-9);
        assertEquals(energy(initial), energy(current), 1e-8);
    }

    @Test
    void longRoutesLeaveAConservativePlumeInTheNearestIntermediateCell() {
        BlockPos source = p(0), near = p(1), middle = p(2), receiver = p(3);
        Map<BlockPos, GasMixture> initial = map(source, mix(GasType.OXYGEN, 10, 400),
                near, mix(GasType.NITROGEN, 5, 300), middle, mix(GasType.NITROGEN, 5, 300),
                receiver, GasMixture.vacuum());
        var result = MonstermosEqualization.equalize(initial, graph(source, near, middle, receiver), 4);
        assertTrue(result.states().get(near).moles(GasType.OXYGEN) > 0);
        assertTrue(result.states().get(receiver).moles(GasType.OXYGEN) > 0);
        for (GasType gas : GasType.values()) assertEquals(gasTotal(initial, gas), gasTotal(result.states(), gas), 1e-10);
        assertEquals(energy(initial), energy(result.states()), 1e-8);
    }

    @Test
    void smallProducerPulseSpreadsFromNearToFarOverBoundedCycles() {
        Map<BlockPos, GasMixture> cells = new LinkedHashMap<>();
        Map<BlockPos, Set<BlockPos>> topology = new LinkedHashMap<>();
        for (int x = 0; x < 4; x++) for (int y = 0; y < 4; y++) for (int z = 0; z < 3; z++) {
            BlockPos pos = new BlockPos(x, y, z);
            cells.put(pos, pos.equals(new BlockPos(0, 0, 0)) ? mix(GasType.OXYGEN, 2.0, 293.15) : GasMixture.vacuum());
            Set<BlockPos> adjacent = new LinkedHashSet<>();
            for (int[] direction : new int[][]{{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}}) {
                BlockPos neighbor = pos.offset(direction[0], direction[1], direction[2]);
                if (neighbor.getX() >= 0 && neighbor.getX() < 4 && neighbor.getY() >= 0 && neighbor.getY() < 4
                        && neighbor.getZ() >= 0 && neighbor.getZ() < 3) adjacent.add(neighbor);
            }
            topology.put(pos, adjacent);
        }
        BlockPos near = new BlockPos(0, 0, 1), far = new BlockPos(3, 3, 2);
        var first = MonstermosEqualization.equalize(cells, topology, 48);
        assertTrue(first.states().get(near).totalMoles() > first.states().get(far).totalMoles());
        assertEquals(0, first.states().get(far).totalMoles(), 1e-12);
        Map<BlockPos, GasMixture> current = first.states();
        for (int i = 0; i < 50; i++) current = MonstermosEqualization.equalize(current, topology, 48).states();
        assertTrue(current.get(far).totalMoles() > 0, "bounded transport must eventually reach remote cells");
        assertEquals(2, gasTotal(current, GasType.OXYGEN), 1e-9);
    }

    @Test
    void equalTotalMolesWithDifferentSpeciesDoNotMix() {
        BlockPos a = p(0), b = p(1);
        GasMixture oxygen = mix(GasType.OXYGEN, 1, 300);
        GasMixture nitrogen = mix(GasType.NITROGEN, 1, 500);
        var result = MonstermosEqualization.equalize(map(a, oxygen, b, nitrogen), graph(a, b), 2);
        assertEquals(oxygen.gasMoles(), result.states().get(a).gasMoles());
        assertEquals(nitrogen.gasMoles(), result.states().get(b).gasMoles());
        assertTrue(result.edgeFlows().isEmpty());
    }

    @Test
    void permutedInputsAreDeterministicAndConserveEachGasAndHeat() {
        BlockPos a = p(0), b = p(1), c = p(2);
        var input = map(a, new GasMixture(Map.of(GasType.OXYGEN, 5.0, GasType.NITROGEN, 1.0), 500),
                b, mix(GasType.NITROGEN, 1, 250), c, mix(GasType.CARBON_DIOXIDE, 1, 350));
        var topology = graph(a, b, c);
        var first = MonstermosEqualization.equalize(input, topology, 3);
        var reversed = new LinkedHashMap<BlockPos, GasMixture>();
        input.entrySet().stream().toList().reversed().forEach(e -> reversed.put(e.getKey(), e.getValue()));
        var second = MonstermosEqualization.equalize(reversed, topology, 3);
        for (BlockPos pos : first.states().keySet()) {
            assertEquals(first.states().get(pos).gasMoles(), second.states().get(pos).gasMoles());
            assertEquals(first.states().get(pos).temperatureKelvin(), second.states().get(pos).temperatureKelvin());
        }
        assertEquals(first.edgeFlows(), second.edgeFlows());
        for (GasType gas : GasType.values()) assertEquals(gasTotal(input, gas), gasTotal(first.states(), gas), 1e-10);
        assertEquals(energy(input), energy(first.states()), 1e-8);
    }

    @Test
    void blockedAndUnknownCellsCannotBeTraversedAndInvalidOrOversizedPatchesReject() {
        BlockPos a = p(0), b = p(2);
        var cells = map(a, mix(GasType.OXYGEN, 2, 300), b, GasMixture.vacuum());
        assertThrows(IllegalArgumentException.class, () -> MonstermosEqualization.equalize(cells, graph(a, b), 2));
        assertThrows(IllegalArgumentException.class, () -> MonstermosEqualization.equalize(cells, graph(a), 2));
        Map<BlockPos, GasMixture> tooMany = new LinkedHashMap<>();
        Map<BlockPos, Set<BlockPos>> chain = new LinkedHashMap<>();
        for (int i = 0; i <= 800; i++) {
            BlockPos pos = p(i); tooMany.put(pos, GasMixture.vacuum());
            Set<BlockPos> neighbors = new LinkedHashSet<>();
            if (i > 0) neighbors.add(p(i - 1));
            if (i < 800) neighbors.add(p(i + 1));
            chain.put(pos, neighbors);
        }
        assertThrows(IllegalArgumentException.class, () -> MonstermosEqualization.equalize(tooMany, chain, 800));
    }

    @Test
    void strongPressureInMaximumSizedConnectedPatchDoesNotRequireTinySlices() {
        Map<BlockPos, GasMixture> cells = new LinkedHashMap<>();
        Map<BlockPos, Set<BlockPos>> topology = new LinkedHashMap<>();
        for (int i = 0; i < 800; i++) {
            BlockPos pos = p(i);
            double moles = i == 0 ? 10_100 : i == 1 ? 9_900 : 10_000;
            cells.put(pos, mix(GasType.OXYGEN, moles, 300));
            Set<BlockPos> adjacent = new LinkedHashSet<>();
            if (i > 0) adjacent.add(p(i - 1));
            if (i < 799) adjacent.add(p(i + 1));
            topology.put(pos, adjacent);
        }
        var result = MonstermosEqualization.equalize(cells, topology, 800);
        assertTrue(result.work().complete());
        assertEquals(1, result.work().transferOperations());
        assertEquals(10_085, result.states().get(p(0)).totalMoles(), 1e-9);
        assertEquals(9_915, result.states().get(p(1)).totalMoles(), 1e-9);
    }

    @Test
    void boundedIncompleteResultStillMakesConservativeProgressForMaximumSizedHighDifferencePatch() {
        Map<BlockPos, GasMixture> cells = new LinkedHashMap<>();
        Map<BlockPos, Set<BlockPos>> topology = new LinkedHashMap<>();
        for (int i = 0; i < 800; i++) {
            BlockPos pos = p(i);
            cells.put(pos, mix(GasType.OXYGEN, i == 0 ? 100_000 : 0, 300));
            Set<BlockPos> adjacent = new LinkedHashSet<>();
            if (i > 0) adjacent.add(p(i - 1));
            if (i < 799) adjacent.add(p(i + 1));
            topology.put(pos, adjacent);
        }

        var result = MonstermosEqualization.equalize(cells, topology, 800);

        assertFalse(result.work().complete(), "the long transfer plan should reach a bounded work ceiling");
        assertEquals(cells.keySet(), result.states().keySet(), "partial work still returns the fully validated patch");
        assertNotEquals(cells, result.states(), "bounded work must publish conservative partial progress");
        for (GasType gas : GasType.values()) assertEquals(gasTotal(cells, gas), gasTotal(result.states(), gas), 1e-8);
        assertEquals(energy(cells), energy(result.states()), 1e-6);

        var next = MonstermosEqualization.equalize(result.states(), topology, 800);
        assertNotEquals(result.states(), next.states(), "a later bounded invocation must continue from committed states");
        for (GasType gas : GasType.values()) assertEquals(gasTotal(cells, gas), gasTotal(next.states(), gas), 1e-8);
        assertEquals(energy(cells), energy(next.states()), 1e-6);
    }

    private static BlockPos p(int x) { return new BlockPos(x, 0, 0); }
    private static GasMixture mix(GasType gas, double amount, double temperature) {
        return new GasMixture(amount == 0 ? Map.of() : Map.of(gas, amount), temperature);
    }
    private static Map<BlockPos, GasMixture> map(Object... pairs) {
        Map<BlockPos, GasMixture> result = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) result.put((BlockPos) pairs[i], (GasMixture) pairs[i + 1]);
        return result;
    }
    private static Map<BlockPos, Set<BlockPos>> graph(BlockPos... positions) {
        Map<BlockPos, Set<BlockPos>> result = new LinkedHashMap<>();
        for (int i = 0; i < positions.length; i++) {
            Set<BlockPos> neighbors = new LinkedHashSet<>();
            if (i > 0) neighbors.add(positions[i - 1]);
            if (i + 1 < positions.length) neighbors.add(positions[i + 1]);
            result.put(positions[i], neighbors);
        }
        return result;
    }
    private static double total(Map<BlockPos, GasMixture> states) { return states.values().stream().mapToDouble(GasMixture::totalMoles).sum(); }
    private static double energy(Map<BlockPos, GasMixture> states) { return states.values().stream().mapToDouble(GasMixture::thermalEnergy).sum(); }
    private static double gasTotal(Map<BlockPos, GasMixture> states, GasType type) { return states.values().stream().mapToDouble(m -> m.moles(type)).sum(); }
}
