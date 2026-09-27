package com.juicyslew.moonstation14.ms14.atmos.core;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class LindaGasSharingTest {
    private static final BlockPos LEFT = new BlockPos(0, 0, 0);
    private static final BlockPos RIGHT = new BlockPos(1, 0, 0);
    private static final double MOLES_AT_ONE_ATMOSPHERE = 101325.0 / (8.31446261815324 * 293.15);

    @Test
    void sharesOpposingPureGasesThroughOpeningWithoutBulkPressureGradient() {
        Map<BlockPos, GasMixture> cells = pair(
                mixture(GasType.OXYGEN, MOLES_AT_ONE_ATMOSPHERE, 293.15),
                mixture(GasType.NITROGEN, MOLES_AT_ONE_ATMOSPHERE, 293.15));
        var result = LindaGasSharing.process(cells, adjacency(), Set.of(LEFT), Set.of(), 800);

        GasMixture left = result.mixtures().get(LEFT);
        GasMixture right = result.mixtures().get(RIGHT);
        assertTrue(left.moles(GasType.NITROGEN) > 0.0);
        assertTrue(right.moles(GasType.OXYGEN) > 0.0);
        assertEquals(MOLES_AT_ONE_ATMOSPHERE, left.totalMoles(), 1e-12);
        assertEquals(MOLES_AT_ONE_ATMOSPHERE, right.totalMoles(), 1e-12);
        assertEquals(cells.get(LEFT).pressureKpa(1), left.pressureKpa(1), 1e-10);
        assertEquals(cells.get(RIGHT).pressureKpa(1), right.pressureKpa(1), 1e-10);
        assertEquals(MOLES_AT_ONE_ATMOSPHERE / 2.0, left.moles(GasType.NITROGEN), 1e-10,
                "one opening has divider 2, unlike the old 0.125 pair fraction");
        assertEquals(1, result.sharedPairs().size());
    }

    @Test
    void carriesSpeciesEnthalpyAndExchangesHeatConservatively() {
        Map<BlockPos, GasMixture> cells = pair(
                mixture(GasType.OXYGEN, 1.0, 400.0), mixture(GasType.OXYGEN, 1.0, 200.0));
        double energy = cells.values().stream().mapToDouble(GasMixture::thermalEnergy).sum();
        var result = LindaGasSharing.process(cells, adjacency(), Set.of(LEFT), Set.of(), 800);
        assertTrue(result.mixtures().get(LEFT).temperatureKelvin() < 400.0);
        assertTrue(result.mixtures().get(RIGHT).temperatureKelvin() > 200.0);
        assertEquals(energy, result.mixtures().values().stream().mapToDouble(GasMixture::thermalEnergy).sum(), 1e-10);
        assertEquals(2.0, result.mixtures().values().stream().mapToDouble(GasMixture::totalMoles).sum(), 1e-12);
    }

    @Test
    void activatesOnlyQualifyingLoadedOpenNeighborsAndGroupedPairsBypassThreshold() {
        BlockPos far = new BlockPos(2, 0, 0);
        Map<BlockPos, GasMixture> cells = new HashMap<>();
        cells.put(LEFT, mixture(GasType.OXYGEN, 1, 293.15));
        cells.put(RIGHT, mixture(GasType.OXYGEN, 1, 293.15));
        cells.put(far, mixture(GasType.OXYGEN, 1, 293.15));
        Map<BlockPos, Set<BlockPos>> edges = Map.of(LEFT, Set.of(RIGHT, far), RIGHT, Set.of(LEFT));
        var unchanged = LindaGasSharing.process(cells, edges, Set.of(LEFT), Set.of(), 800);
        assertTrue(unchanged.sharedPairs().isEmpty());
        assertTrue(unchanged.activatedNeighbors().isEmpty());

        var grouped = LindaGasSharing.process(cells, edges, Set.of(LEFT), Set.of(LindaGasSharing.Pair.of(LEFT, RIGHT)), 800);
        assertEquals(Set.of(RIGHT), grouped.activatedNeighbors());
        assertEquals(Set.of(LindaGasSharing.Pair.of(LEFT, RIGHT)), grouped.sharedPairs());
        assertFalse(grouped.activatedNeighbors().contains(far), "non-face adjacency must not create membership");
    }

    @Test
    void usesDeterministicSortedActiveOrderAndLiveSequentialUpdates() {
        BlockPos middle = new BlockPos(1, 0, 0);
        BlockPos end = new BlockPos(2, 0, 0);
        Map<BlockPos, GasMixture> forward = new HashMap<>();
        forward.put(LEFT, mixture(GasType.OXYGEN, 8, 293.15));
        forward.put(middle, mixture(GasType.OXYGEN, 4, 293.15));
        forward.put(end, mixture(GasType.OXYGEN, 1, 293.15));
        Map<BlockPos, GasMixture> reverse = new HashMap<>();
        reverse.put(end, forward.get(end)); reverse.put(middle, forward.get(middle)); reverse.put(LEFT, forward.get(LEFT));
        Map<BlockPos, Set<BlockPos>> edges = Map.of(LEFT, Set.of(middle), middle, Set.of(LEFT, end), end, Set.of(middle));
        Set<LindaGasSharing.Pair> grouped = Set.of(LindaGasSharing.Pair.of(LEFT, middle), LindaGasSharing.Pair.of(middle, end));
        var a = LindaGasSharing.process(forward, edges, Set.of(end, middle, LEFT), grouped, 800);
        var b = LindaGasSharing.process(reverse, edges, Set.of(LEFT, middle, end), grouped, 800);
        assertEquals(a.mixtures().get(end).moles(GasType.OXYGEN), b.mixtures().get(end).moles(GasType.OXYGEN), 0.0);
        assertEquals(a.mixtures().get(LEFT).moles(GasType.OXYGEN), b.mixtures().get(LEFT).moles(GasType.OXYGEN), 0.0);
        assertTrue(a.mixtures().get(middle).moles(GasType.OXYGEN) > 4.0,
                "the first pair's live result participates in the following pair");
    }

    @Test
    void enforcesProcessingCapAndDoesNotMutateInputs() {
        Map<BlockPos, GasMixture> cells = new HashMap<>();
        for (int x = 0; x < 4; x++) cells.put(new BlockPos(x, 0, 0), mixture(GasType.OXYGEN, x + 1, 293.15));
        var result = LindaGasSharing.process(cells, Map.of(), Set.of(new BlockPos(0, 0, 0), new BlockPos(1, 0, 0),
                new BlockPos(2, 0, 0), new BlockPos(3, 0, 0)), Set.of(), 2);
        assertEquals(2, result.workCount());
        assertEquals(1.0, cells.get(LEFT).moles(GasType.OXYGEN));
        assertThrows(IllegalArgumentException.class,
                () -> LindaGasSharing.process(cells, Map.of(), Set.of(), Set.of(), 801));
    }

    @Test
    void reportsPerPairMovedMolesInFixedOrderSeparatelyFromLastShare() {
        BlockPos highFlow = new BlockPos(0, 0, -1);
        BlockPos lowFlow = new BlockPos(0, 0, 1);
        Map<BlockPos, GasMixture> cells = new HashMap<>();
        cells.put(LEFT, new GasMixture(Map.of(GasType.OXYGEN, 20.0, GasType.NITROGEN, 0.06), 293.15));
        cells.put(highFlow, new GasMixture(Map.of(), 293.15));
        cells.put(lowFlow, mixture(GasType.OXYGEN, 40.0 / 3.0, 293.15));
        Map<BlockPos, Set<BlockPos>> edges = Map.of(LEFT, Set.of(lowFlow, highFlow),
                highFlow, Set.of(LEFT), lowFlow, Set.of(LEFT));
        Set<LindaGasSharing.Pair> grouped = Set.of(LindaGasSharing.Pair.of(LEFT, highFlow),
                LindaGasSharing.Pair.of(LEFT, lowFlow));

        var result = LindaGasSharing.process(cells, edges, Set.of(LEFT), grouped, 800);
        Map<BlockPos, GasMixture> reversedCells = new HashMap<>();
        reversedCells.put(lowFlow, cells.get(lowFlow));
        reversedCells.put(highFlow, cells.get(highFlow));
        reversedCells.put(LEFT, cells.get(LEFT));
        var reversed = LindaGasSharing.process(reversedCells, edges, Set.of(LEFT), grouped, 800);

        LindaGasSharing.Pair highPair = LindaGasSharing.Pair.of(LEFT, highFlow);
        LindaGasSharing.Pair lowPair = LindaGasSharing.Pair.of(LEFT, lowFlow);
        assertEquals(20.0 / 3.0 + 0.06 / 3.0, result.movedMolesByPair().get(highPair), 1e-12);
        assertEquals(0.04 / 3.0, result.movedMolesByPair().get(lowPair), 1e-12);
        assertTrue(result.movedMolesByPair().get(highPair) > 4.16);
        assertTrue(result.movedMolesByPair().get(lowPair) < 0.0416);
        assertEquals(result.movedMolesByPair().get(lowPair), result.lastShare().get(LEFT), 0.0,
                "lastShare remains the final neighbor's moved amount, not a per-cell sum");
        assertEquals(result.movedMolesByPair(), reversed.movedMolesByPair());
        assertThrows(UnsupportedOperationException.class,
                () -> result.movedMolesByPair().put(highPair, 0.0));
    }

    private static Map<BlockPos, GasMixture> pair(GasMixture first, GasMixture second) {
        return Map.of(LEFT, first, RIGHT, second);
    }

    private static Map<BlockPos, Set<BlockPos>> adjacency() {
        return Map.of(LEFT, Set.of(RIGHT), RIGHT, Set.of(LEFT));
    }

    private static GasMixture mixture(GasType gas, double moles, double temperature) {
        return new GasMixture(Map.of(gas, moles), temperature);
    }
}
