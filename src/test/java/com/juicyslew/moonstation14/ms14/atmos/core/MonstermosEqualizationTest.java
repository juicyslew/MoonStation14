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
    void highPressureHundredsOfMolesRouteThroughOneDoorTowardPatchAverage() {
        BlockPos source = p(0), doorway = p(1), receiver = p(2);
        GasMixture pressurized = mix(GasType.OXYGEN, 120, 400);
        var result = MonstermosEqualization.equalize(
                map(source, pressurized, doorway, GasMixture.vacuum(), receiver, GasMixture.vacuum()),
                graph(source, doorway, receiver), 3);

        assertTrue(result.work().complete());
        assertTrue(result.states().get(doorway).totalMoles() > 0);
        assertEquals(40, result.states().get(doorway).totalMoles(), 1e-10);
        assertEquals(40, result.states().get(receiver).totalMoles(), 1e-10);
        assertEquals(80, result.edgeFlows().get(new MonstermosEqualization.DirectedEdge(source, doorway)), 1e-10);
        assertEquals(40, result.edgeFlows().get(new MonstermosEqualization.DirectedEdge(doorway, receiver)), 1e-10);
        assertTrue(result.work().transferOperations() <= 3);
        assertEquals(40, result.states().get(source).totalMoles(), 1e-10);
        assertEquals(120, gasTotal(result.states(), GasType.OXYGEN), 1e-10);
        assertEquals(pressurized.thermalEnergy(), energy(result.states()), 1e-8);
    }

    @Test
    void repeatedInvocationsRemainConservativeAfterFullSinglePassEqualization() {
        BlockPos source = p(0), receiver = p(1);
        Map<BlockPos, GasMixture> initial = map(source,
                new GasMixture(Map.of(GasType.OXYGEN, 80.0, GasType.NITROGEN, 20.0), 420),
                receiver, GasMixture.vacuum());
        Map<BlockPos, Set<BlockPos>> topology = graph(source, receiver);
        var first = MonstermosEqualization.equalize(initial, topology, 2);
        assertTrue(first.states().get(receiver).totalMoles() > 0);
        assertEquals(50, first.states().get(receiver).totalMoles(), 1e-10);
        Map<BlockPos, GasMixture> current = first.states();
        for (int i = 0; i < 9; i++) current = MonstermosEqualization.equalize(current, topology, 2).states();
        assertEquals(first.states().get(receiver).totalMoles(), current.get(receiver).totalMoles(), 1e-10);
        for (GasType gas : GasType.values()) assertEquals(gasTotal(initial, gas), gasTotal(current, gas), 1e-9);
        assertEquals(energy(initial), energy(current), 1e-8);
    }

    @Test
    void longRoutesCarryFullConservativeSurplusWithoutArtificialRetention() {
        BlockPos source = p(0), near = p(1), middle = p(2), receiver = p(3);
        Map<BlockPos, GasMixture> initial = map(source, mix(GasType.OXYGEN, 10, 400),
                near, mix(GasType.NITROGEN, 5, 300), middle, mix(GasType.NITROGEN, 5, 300),
                receiver, GasMixture.vacuum());
        var result = MonstermosEqualization.equalize(initial, graph(source, near, middle, receiver), 4);
        assertTrue(result.states().get(near).moles(GasType.OXYGEN) > 0);
        assertTrue(result.states().get(receiver).moles(GasType.OXYGEN) > 0);
        assertEquals(5, result.states().get(receiver).totalMoles(), 1e-10);
        for (GasType gas : GasType.values()) assertEquals(gasTotal(initial, gas), gasTotal(result.states(), gas), 1e-10);
        assertEquals(energy(initial), energy(result.states()), 1e-8);
    }

    @Test
    void producerPulseReachesPatchAverageInOnePassAndEachTenHzPulseAddsTwoMoles() {
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
        assertTrue(first.states().get(near).totalMoles() > 0);
        assertTrue(first.states().get(far).totalMoles() > 0, "normal Monstermos has no long-route holding plume");
        for (GasMixture mixture : first.states().values()) assertEquals(2.0 / 48, mixture.totalMoles(), 1e-10);
        Map<BlockPos, GasMixture> current = first.states();
        BlockPos source = new BlockPos(0, 0, 0);
        for (int step = 1; step <= 5; step++) {
            Map<BlockPos, GasMixture> injected = new LinkedHashMap<>(current);
            injected.put(source, injected.get(source).withGasDelta(GasType.OXYGEN, 2.0));
            current = MonstermosEqualization.equalize(injected, topology, 48).states();
            assertEquals((step + 1) * 2.0, gasTotal(current, GasType.OXYGEN), 1e-9,
                    "each 0.1-second source pulse contributes exactly two moles");
        }
    }

    @Test
    void twoConnectedRoomsRemainConservativeUnderBoundedRepeatedProducerPulses() {
        assertRoomPulseProgression(new BlockPos(0, 2, 1), new BlockPos(7, 2, 1), "source near outer end");
        assertRoomPulseProgression(new BlockPos(7, 2, 1), new BlockPos(0, 2, 1), "source in far room");
    }

    @Test
    void longHallwayOf124CellsCarriesTritiumToFarEndOnFirstPass() {
        Map<BlockPos, GasMixture> cells = new LinkedHashMap<>();
        Map<BlockPos, Set<BlockPos>> topology = new LinkedHashMap<>();
        for (int x = 0; x < 31; x++) {
            for (int y = 0; y < 2; y++) {
                for (int z = 0; z < 2; z++) {
                    addRoomCell(cells, topology, new BlockPos(x, y, z));
                }
            }
        }
        assertEquals(124, cells.size(), "hallway must contain exactly 124 finite cells");
        BlockPos source = new BlockPos(0, 0, 0);
        cells.put(source, mix(GasType.TRITIUM, 2.0, 293.15));

        var result = MonstermosEqualization.equalize(cells, topology, 124);
        String work = "complete=" + result.work().complete() + ", status=" + result.work().status()
                + ", discoveredCells=" + result.work().discoveredCells()
                + ", transferOperations=" + result.work().transferOperations()
                + ", edgeSearchWork=" + result.work().edgeSearchWork();
        assertTrue(result.work().complete(), "124-cell hallway did not finish its first invocation: " + work);
        assertTrue(result.work().transferOperations() <= 8000, "transfer operation ceiling exceeded: " + work);
        assertTrue(result.work().edgeSearchWork() <= 8000, "edge-search ceiling exceeded: " + work);
        for (int x = 26; x < 31; x++) {
            for (int y = 0; y < 2; y++) {
                for (int z = 0; z < 2; z++) {
                    BlockPos farEnd = new BlockPos(x, y, z);
                    assertTrue(result.states().get(farEnd).moles(GasType.TRITIUM) > 0,
                            "TRITIUM did not reach far-end cell " + farEnd + " on first pass; " + work);
                }
            }
        }
        assertConserved(cells, result.states(), "124-cell tritium hallway");
    }

    private static void assertRoomPulseProgression(BlockPos source, BlockPos farRoomPosition, String setup) {
        Map<BlockPos, GasMixture> current = new LinkedHashMap<>();
        Map<BlockPos, Set<BlockPos>> topology = new LinkedHashMap<>();
        // Chamber one is 4x5x3 (60 cells); chamber two is 4x4x4 (64 cells).
        for (int x = 0; x < 4; x++) for (int y = 0; y < 5; y++) for (int z = 0; z < 3; z++) {
            addRoomCell(current, topology, new BlockPos(x, y, z));
        }
        for (int x = 4; x < 8; x++) for (int y = 0; y < 4; y++) for (int z = 0; z < 4; z++) {
            addRoomCell(current, topology, new BlockPos(x, y, z));
        }
        connect(topology, new BlockPos(3, 0, 0), new BlockPos(4, 0, 0));
        assertEquals(124, current.size(), "fixture must contain exactly 124 finite cells");
        assertEquals(1, topology.get(new BlockPos(3, 0, 0)).stream()
                .filter(pos -> pos.getX() == 4).count(), "rooms must meet through one face-open doorway");

        current.put(source, current.get(source).withGasDelta(GasType.TRITIUM, 2.0));
        Map<BlockPos, GasMixture> firstInput = new LinkedHashMap<>(current);
        var first = MonstermosEqualization.equalize(firstInput, topology, 124);
        assertWorkBounded(first.work(), setup + " first invocation");
        assertTrue(first.work().complete(),
                setup + " first invocation complete=" + first.work().complete() + ", status=" + first.work().status());
        assertEquals(124, first.work().discoveredCells(), setup + " first invocation positions visited");
        assertTrue(first.work().edgeSearchWork() <= MonstermosEqualization.MAX_EDGE_SEARCH_WORK,
                setup + " first invocation edgeSearchWork=" + first.work().edgeSearchWork());
        assertConserved(firstInput, first.states(), setup + " first invocation");
        Map<BlockPos, GasMixture> firstFarTwenty = roomInventory(first.states(), farRoomPosition);
        assertEquals(20, firstFarTwenty.size(), setup + " first invocation far20 cell count");
        assertFarTwentyContainsTritium(firstFarTwenty, setup + " first invocation");

        current = first.states();
        for (int step = 2; step <= 30; step++) {
            Map<BlockPos, GasMixture> injected = new LinkedHashMap<>(current);
            injected.put(source, injected.get(source).withGasDelta(GasType.TRITIUM, 2.0));
            var result = MonstermosEqualization.equalize(injected, topology, 124);
            assertWorkBounded(result.work(), setup + " invocation " + step);
            assertConserved(injected, result.states(), setup + " invocation " + step);
            if (step == 2) {
                assertEquals(124, result.work().discoveredCells(), setup + " second invocation positions visited");
                assertTrue(result.work().complete(),
                        setup + " second invocation complete=" + result.work().complete() + ", status="
                                + result.work().status());
                Map<BlockPos, GasMixture> secondFarTwenty = roomInventory(result.states(), farRoomPosition);
                assertEquals(20, secondFarTwenty.size(), setup + " second invocation far20 cell count");
                assertFarTwentyContainsTritium(secondFarTwenty, setup + " second invocation");
            }
            current = result.states();
        }
        assertEquals(60.0, gasTotal(current, GasType.TRITIUM), 1e-9,
                setup + " all thirty producer pulses must remain in the patch");
    }

    private static void assertFarTwentyContainsTritium(Map<BlockPos, GasMixture> states, String label) {
        for (Map.Entry<BlockPos, GasMixture> entry : states.entrySet()) {
            assertTrue(entry.getValue().moles(GasType.TRITIUM) > 0.0,
                    label + " TRITIUM did not reach far-end cell " + entry.getKey());
        }
    }

    private static void addRoomCell(Map<BlockPos, GasMixture> cells, Map<BlockPos, Set<BlockPos>> topology,
                                    BlockPos pos) {
        cells.put(pos, GasMixture.vacuum());
        Set<BlockPos> neighbors = new LinkedHashSet<>();
        for (int[] direction : new int[][]{{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}}) {
            BlockPos neighbor = pos.offset(direction[0], direction[1], direction[2]);
            if (cells.containsKey(neighbor)) {
                neighbors.add(neighbor);
                topology.get(neighbor).add(pos);
            }
        }
        topology.put(pos, neighbors);
    }

    private static void connect(Map<BlockPos, Set<BlockPos>> topology, BlockPos a, BlockPos b) {
        topology.get(a).add(b);
        topology.get(b).add(a);
    }

    private static Map<BlockPos, GasMixture> roomInventory(Map<BlockPos, GasMixture> states, BlockPos roomPosition) {
        Map<BlockPos, GasMixture> result = new LinkedHashMap<>();
        int minX = roomPosition.getX() >= 4 ? 4 : 0;
        int maxX = roomPosition.getX() >= 4 ? 8 : 4;
        int maxY = roomPosition.getX() >= 4 ? 4 : 5;
        int maxZ = roomPosition.getX() >= 4 ? 4 : 3;
        for (int x = minX; x < maxX; x++) {
            for (int y = 0; y < maxY; y++) {
                for (int z = 0; z < maxZ; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (states.containsKey(pos)) result.put(pos, states.get(pos));
                }
            }
        }
        return result.entrySet().stream().sorted(Map.Entry.comparingByKey(
                java.util.Comparator.<BlockPos>comparingLong(BlockPos::getX).thenComparingLong(BlockPos::getY)
                        .thenComparingLong(BlockPos::getZ))).limit(20)
                .collect(LinkedHashMap::new, (map, entry) -> map.put(entry.getKey(), entry.getValue()), LinkedHashMap::putAll);
    }

    private static void assertWorkBounded(MonstermosEqualization.Work work, String label) {
        assertTrue(work.edgeSearchWork() <= MonstermosEqualization.MAX_EDGE_SEARCH_WORK,
                label + " edgeSearchWork=" + work.edgeSearchWork());
        assertTrue(work.transferOperations() <= MonstermosEqualization.MAX_TRANSFER_OPERATIONS,
                label + " transferOperations=" + work.transferOperations());
    }

    private static void assertConserved(Map<BlockPos, GasMixture> before, Map<BlockPos, GasMixture> after,
                                        String label) {
        for (GasType gas : GasType.values()) {
            assertEquals(gasTotal(before, gas), gasTotal(after, gas), 1e-9,
                    label + " conservation of " + gas);
        }
        assertEquals(energy(before), energy(after), 1e-7, label + " enthalpy conservation");
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
        assertEquals(10_000, result.states().get(p(0)).totalMoles(), 1e-9);
        assertEquals(10_000, result.states().get(p(1)).totalMoles(), 1e-9);
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
