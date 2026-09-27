package com.juicyslew.moonstation14.ms14.atmos.solver;

import com.juicyslew.moonstation14.ms14.atmos.core.ExcitedAtmosphereGroups;
import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import com.juicyslew.moonstation14.ms14.atmos.core.LindaGasSharing;
import com.juicyslew.moonstation14.ms14.atmos.core.MonstermosEqualization;
import com.juicyslew.moonstation14.ms14.atmos.core.MonstermosSpaceFlow;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AtmosphereM11ScenarioTest {
    private static final double T = AtmosphereChamberFixture.ROOM_TEMPERATURE_KELVIN;
    private static final int CELL_BUDGET = MonstermosEqualization.MAX_CELLS;

    @Test
    void injectedMixedGasTravelsAcrossRealThreeDimensionalChamberWithoutCreatingMass() {
        AtmosphereChamberFixture chamber = AtmosphereChamberFixture.sealedRoom(4, 4, 3,
                Map.of(GasType.OXYGEN, 10.0, GasType.NITROGEN, 20.0, GasType.TRITIUM, 2.0), T);
        Map<BlockPos, Set<BlockPos>> adjacency = adjacency(chamber);
        BlockPos source = new BlockPos(0, 1, 1);
        BlockPos oppositeWall = new BlockPos(3, 1, 1);
        double originalTotal = totalMoles(chamber.finiteCells);
        double originalEnergy = totalEnergy(chamber.finiteCells);
        chamber.inject20MolPerSecond(source, GasType.OXYGEN, 1.0);
        double injectedEnergy = totalEnergy(chamber.finiteCells);
        double beforeOpposite = chamber.finiteCells.get(oppositeWall).moles(GasType.OXYGEN);

        for (int cycle = 0; cycle < 3; cycle++) {
            MonstermosEqualization.Result result = MonstermosEqualization.equalize(
                    chamber.finiteCells, adjacency, CELL_BUDGET);
            assertTrue(result.work().complete());
            chamber.finiteCells.clear();
            chamber.finiteCells.putAll(result.states());
            LindaGasSharing.Result linda = LindaGasSharing.process(chamber.finiteCells, adjacency,
                    chamber.finiteCells.keySet(), Set.of(), CELL_BUDGET);
            chamber.finiteCells.clear();
            chamber.finiteCells.putAll(linda.mixtures());
        }

        assertTrue(chamber.finiteCells.get(oppositeWall).moles(GasType.OXYGEN) > beforeOpposite,
                "injected oxygen must reach the far wall over bounded solver cycles");
        assertEquals(originalTotal + 20.0, totalMoles(chamber.finiteCells), 1e-8);
        assertEquals(injectedEnergy, totalEnergy(chamber.finiteCells), 1e-6);
        assertTrue(totalMoles(chamber.finiteCells) > originalTotal);
        assertTrue(originalEnergy < injectedEnergy);
    }

    @Test
    void equalPressureDoorDiffusesSpeciesGraduallyWithoutFifthCycleInstantAverage() {
        AtmosphereChamberFixture chamber = twoPureRooms(101.325, 101.325);
        chamber.openSingleDoor();
        Map<BlockPos, Set<BlockPos>> adjacency = adjacency(chamber);
        BlockPos leftDoor = new BlockPos(3, 2, 1);
        BlockPos rightDoor = new BlockPos(5, 2, 1);
        double total = totalMoles(chamber.finiteCells);
        double energy = totalEnergy(chamber.finiteCells);
        Map<GasType, Double> species = speciesTotals(chamber.finiteCells);
        assertEquals(chamber.finiteCells.get(leftDoor).pressureKpa(1.0),
                chamber.finiteCells.get(rightDoor).pressureKpa(1.0), 1e-10);

        MonstermosEqualization.Result equalized = MonstermosEqualization.equalize(chamber.finiteCells,
                adjacency, CELL_BUDGET);
        assertTrue(equalized.edgeFlows().isEmpty(), "normal Monstermos has no total-pressure gradient to route");

        ExcitedAtmosphereGroups groups = new ExcitedAtmosphereGroups();
        Map<BlockPos, GasMixture> states = new LinkedHashMap<>(chamber.finiteCells);
        LindaGasSharing.Result firstLinda = LindaGasSharing.process(states, adjacency, states.keySet(),
                Set.of(), CELL_BUDGET);
        Map<BlockPos, GasMixture> beforeShares = states;
        states = new LinkedHashMap<>(firstLinda.mixtures());
        firstLinda.movedMolesByPair().forEach((pair, moved) -> groups.onShare(pair, moved, beforeShares));
        assertTrue(sumRegion(states, 5, 8, 0, 4, GasType.OXYGEN) > 0.0);
        states = new LinkedHashMap<>(LindaGasSharing.process(states, adjacency, states.keySet(),
                Set.of(), CELL_BUDGET).mixtures());
        assertTrue(sumRegion(states, 0, 3, 0, 4, GasType.NITROGEN) > 0.0);
        double previousRightOxygen = sumRegion(states, 5, 8, 0, 4, GasType.OXYGEN);
        double previousLeftNitrogen = sumRegion(states, 0, 3, 0, 4, GasType.NITROGEN);
        for (int cycle = 0; cycle < 5; cycle++) {
            LindaGasSharing.Result linda = LindaGasSharing.process(states, adjacency, states.keySet(),
                    Set.of(), CELL_BUDGET);
            states = new LinkedHashMap<>(linda.mixtures());
            double rightOxygen = sumRegion(states, 5, 8, 0, 4, GasType.OXYGEN);
            double leftNitrogen = sumRegion(states, 0, 3, 0, 4, GasType.NITROGEN);
            assertTrue(rightOxygen >= previousRightOxygen);
            assertTrue(leftNitrogen >= previousLeftNitrogen);
            assertTrue(rightOxygen > 0.0 && leftNitrogen > 0.0,
                    "both species continue diffusing across the single doorway");
            previousRightOxygen = rightOxygen;
            previousLeftNitrogen = leftNitrogen;
            ExcitedAtmosphereGroups.CycleResult groupCycle = groups.advanceFullCycle(states, CELL_BUDGET);
            if (cycle == 4) {
                assertTrue(groupCycle.replacements().isEmpty(),
                        "large pure-species gradients must not be instantly averaged after five cycles");
            }
            states.putAll(groupCycle.replacements());
        }

        assertEquals(total, totalMoles(states), 1e-7);
        assertEquals(energy, totalEnergy(states), 1e-5);
        for (Map.Entry<GasType, Double> entry : species.entrySet()) {
            assertEquals(entry.getValue(), speciesTotal(states, entry.getKey()), 1e-7, entry.getKey().name());
        }
        assertTrue(states.get(new BlockPos(8, 0, 0)).moles(GasType.OXYGEN)
                < states.get(leftDoor).moles(GasType.OXYGEN), "breakdown is not instantaneous whole-room homogenization");
    }

    @Test
    void highPressureFiniteRoomRoutesToLowerPressureRoomAlongConnectedFacePath() {
        AtmosphereChamberFixture chamber = twoPureRooms(202.65, 101.325);
        chamber.openSingleDoor();
        Map<BlockPos, Set<BlockPos>> adjacency = adjacency(chamber);
        double beforeMoles = totalMoles(chamber.finiteCells);
        double beforeEnergy = totalEnergy(chamber.finiteCells);
        Map<GasType, Double> beforeSpecies = speciesTotals(chamber.finiteCells);
        Map<BlockPos, GasMixture> states = new LinkedHashMap<>(chamber.finiteCells);
        double previousHigh = regionMoles(states, 0, 3);
        double previousLow = regionMoles(states, 5, 8);
        for (int pass = 0; pass < 5; pass++) {
            MonstermosEqualization.Result result = MonstermosEqualization.equalize(states, adjacency, CELL_BUDGET);
            assertTrue(result.work().discoveredCells() <= CELL_BUDGET);
            assertFalse(result.edgeFlows().isEmpty());
            assertTrue(result.edgeFlows().keySet().stream().anyMatch(edge -> edge.from().getX() < 4 && edge.to().getX() >= 4));
            result.edgeFlows().keySet().forEach(edge -> assertTrue(adjacency.get(edge.from()).contains(edge.to())));
            assertEquals(beforeMoles, totalMoles(result.states()), 1e-7);
            assertEquals(beforeEnergy, totalEnergy(result.states()), 1e-5);
            beforeSpecies.forEach((gas, amount) -> assertEquals(amount, speciesTotal(result.states(), gas), 1e-7, gas.name()));
            states = new LinkedHashMap<>(result.states());
            double high = regionMoles(states, 0, 3);
            double low = regionMoles(states, 5, 8);
            assertTrue(high <= previousHigh + 1e-8, "donor-side finite inventory does not increase");
            assertTrue(low >= previousLow - 1e-8, "taker-side finite inventory does not decrease");
            previousHigh = high;
            previousLow = low;
        }
        assertTrue(previousHigh < regionMoles(chamber.finiteCells, 0, 3));
        assertTrue(previousLow > regionMoles(chamber.finiteCells, 5, 8));
        assertTrue(Math.abs(previousHigh - previousLow) < Math.abs(
                regionMoles(chamber.finiteCells, 0, 3) - regionMoles(chamber.finiteCells, 5, 8)),
                "repeated bounded passes progress toward equalization without an instantaneous jump");
    }

    @Test
    void spaceHoleRoutesInteriorInventoryBeforeExportAndAccountsForGasAndEnergy() {
        Map<GasType, Double> mix = Map.of(GasType.OXYGEN, 700.0, GasType.NITROGEN, 300.0);
        AtmosphereChamberFixture oneHole = AtmosphereChamberFixture.sealedRoom(4, 4, 3, mix, T);
        AtmosphereChamberFixture manyHoles = AtmosphereChamberFixture.sealedRoom(4, 4, 3, mix, T);
        BlockPos firstOpeningBoundary = new BlockPos(0, 3, 0);
        oneHole.finiteCells.put(firstOpeningBoundary, new GasMixture(Map.of(
                GasType.OXYGEN, 70.0, GasType.NITROGEN, 30.0), T));
        oneHole.openExteriorFace(1);
        manyHoles.openExteriorFace(12);
        MonstermosSpaceFlow.Result one = MonstermosSpaceFlow.run(oneHole.finiteCells,
                adjacency(oneHole), oneHole.exterior, 800);
        MonstermosSpaceFlow.Result many = MonstermosSpaceFlow.run(manyHoles.finiteCells,
                adjacency(manyHoles), manyHoles.exterior, 800);

        assertTrue(one.work().complete());
        BlockPos boundary = firstOpeningBoundary;
        double originalBoundaryMoles = oneHole.finiteCells.get(boundary).totalMoles();
        assertTrue(exportedMoles(one) > originalBoundaryMoles,
                "inward routing supplies the opening beyond its original boundary-cell inventory");
        assertTrue(one.states().get(boundary).totalMoles() > 0.0);
        assertTrue(totalMoles(one.states()) > 0.0, "finite room inventory remains after one pass");
        assertTrue(exportedMoles(many) > exportedMoles(one), "more open faces produce more total loss");
        assertConserved(oneHole.finiteCells, one);
        assertConserved(manyHoles.finiteCells, many);

        Map<BlockPos, GasMixture> current = new LinkedHashMap<>(oneHole.finiteCells);
        double starting = totalMoles(current);
        double previous = starting;
        for (int pass = 0; pass < 5; pass++) {
            current.put(new BlockPos(0, 0, 0), current.get(new BlockPos(0, 0, 0))
                    .withGasDelta(GasType.OXYGEN, 20.0));
            MonstermosSpaceFlow.Result step = MonstermosSpaceFlow.run(current,
                    adjacency(oneHole), oneHole.exterior, 800);
            assertTrue(step.work().complete());
            current = new LinkedHashMap<>(step.states());
            assertTrue(totalMoles(current) < previous, "room continues losing gas despite source input");
            previous = totalMoles(current);
        }
        assertTrue(starting - previous > 100.0);
    }

    @Test
    void unknownAndUnloadedSpaceNeverBecomeAdjacencyOrVacuum() {
        AtmosphereChamberFixture chamber = AtmosphereChamberFixture.sealedRoom(4, 4, 3,
                Map.of(GasType.OXYGEN, 100.0), T);
        BlockPos unloaded = new BlockPos(20, 20, 20);
        chamber.unloaded.add(unloaded);
        assertEquals(AtmosphereChamberFixture.NeighborKind.UNKNOWN, chamber.neighborKind(unloaded));
        assertFalse(adjacency(chamber).values().stream().anyMatch(neighbors -> neighbors.contains(unloaded)));
        MonstermosSpaceFlow.Result result = MonstermosSpaceFlow.run(chamber.finiteCells,
                adjacency(chamber), chamber.exterior, 800);
        assertTrue(result.exported().speciesMoles().isEmpty());
    }

    private static AtmosphereChamberFixture twoPureRooms(double leftKpa, double rightKpa) {
        return AtmosphereChamberFixture.twoRooms(4, 4, 3,
                AtmosphereChamberFixture.pureGasAtPressure(GasType.OXYGEN, leftKpa, T),
                AtmosphereChamberFixture.pureGasAtPressure(GasType.NITROGEN, rightKpa, T));
    }

    private static Map<BlockPos, Set<BlockPos>> adjacency(AtmosphereChamberFixture chamber) {
        Map<BlockPos, Set<BlockPos>> result = new LinkedHashMap<>();
        for (BlockPos pos : chamber.finiteCells.keySet()) {
            Set<BlockPos> neighbors = new LinkedHashSet<>();
            for (BlockPos neighbor : chamber.neighbors(pos)) {
                if (chamber.neighborKind(neighbor) == AtmosphereChamberFixture.NeighborKind.FINITE) neighbors.add(neighbor);
            }
            result.put(pos, Set.copyOf(neighbors));
        }
        return result;
    }

    private static double totalMoles(Map<BlockPos, GasMixture> cells) {
        return cells.values().stream().mapToDouble(GasMixture::totalMoles).sum();
    }

    private static double totalEnergy(Map<BlockPos, GasMixture> cells) {
        return cells.values().stream().mapToDouble(GasMixture::thermalEnergy).sum();
    }

    private static Map<GasType, Double> speciesTotals(Map<BlockPos, GasMixture> cells) {
        Map<GasType, Double> totals = new LinkedHashMap<>();
        for (GasType gas : GasType.values()) totals.put(gas, speciesTotal(cells, gas));
        return totals;
    }

    private static double speciesTotal(Map<BlockPos, GasMixture> cells, GasType gas) {
        return cells.values().stream().mapToDouble(mix -> mix.moles(gas)).sum();
    }

    private static double regionMoles(Map<BlockPos, GasMixture> cells, int minX, int maxX) {
        return cells.entrySet().stream().filter(e -> e.getKey().getX() >= minX && e.getKey().getX() <= maxX)
                .mapToDouble(e -> e.getValue().totalMoles()).sum();
    }

    private static double sumRegion(Map<BlockPos, GasMixture> cells, int minX, int maxX, int minY, int maxY, GasType gas) {
        return cells.entrySet().stream().filter(e -> e.getKey().getX() >= minX && e.getKey().getX() <= maxX
                        && e.getKey().getY() >= minY && e.getKey().getY() <= maxY)
                .mapToDouble(e -> e.getValue().moles(gas)).sum();
    }

    private static double exportedMoles(MonstermosSpaceFlow.Result result) {
        return result.exported().speciesMoles().values().stream().mapToDouble(Double::doubleValue).sum();
    }

    private static void assertConserved(Map<BlockPos, GasMixture> initial, MonstermosSpaceFlow.Result result) {
        for (GasType gas : GasType.values()) {
            double before = initial.values().stream().mapToDouble(mix -> mix.moles(gas)).sum();
            double finite = result.states().values().stream().mapToDouble(mix -> mix.moles(gas)).sum();
            assertEquals(before, finite + result.exported().speciesMoles().getOrDefault(gas, 0.0), 1e-6, gas.name());
        }
        assertEquals(totalEnergy(initial), totalEnergy(result.states()) + result.exported().thermalEnergyJoules(), 1e-5);
    }
}
