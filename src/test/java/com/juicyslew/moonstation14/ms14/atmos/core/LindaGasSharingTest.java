package com.juicyslew.moonstation14.ms14.atmos.core;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class LindaGasSharingTest {
    private static final BlockPos LEFT = new BlockPos(0, 0, 0);
    private static final BlockPos RIGHT = new BlockPos(1, 0, 0);
    private static final double MOLES_AT_ONE_ATMOSPHERE = 101325.0 / (8.31446261815324 * 293.15);

    @Test
    void pressureAwareSharingDoesNotEqualizeDensityForEqualComposition() {
        var dense = mixture(GasType.OXYGEN, 8, 300);
        var dilute = mixture(GasType.OXYGEN, 2, 300);
        var result = LindaGasSharing.sharePressureAware(pair(dense, dilute), pair(dense, dilute), adjacency(), Set.of(LEFT), Set.of(), Set.of(), 1);
        assertEquals(8, result.mixtures().get(LEFT).totalMoles(), 1e-6);
        assertEquals(2, result.mixtures().get(RIGHT).totalMoles(), 1e-6);
        assertTrue(result.sharedPairs().isEmpty());
    }

    @Test
    void pressureAwareCounterdiffusionPreservesEachCellInventory() {
        var cells = pair(mixture(GasType.OXYGEN, 2, 300), mixture(GasType.NITROGEN, 2, 300));
        var result = LindaGasSharing.sharePressureAware(cells, cells, adjacency(), Set.of(LEFT), Set.of(), Set.of(), 1);
        assertEquals(2, result.mixtures().get(LEFT).totalMoles(), 1e-6);
        assertEquals(2, result.mixtures().get(RIGHT).totalMoles(), 1e-6);
        assertTrue(result.mixtures().get(LEFT).moles(GasType.NITROGEN) > 0);
        assertTrue(result.mixtures().get(RIGHT).moles(GasType.OXYGEN) > 0);
    }

    @Test
    void pressureAwareExchangeWithImmutableEndpointChangesOnlyFiniteState() {
        GasMixture finite = new GasMixture(Map.of(GasType.OXYGEN, 1.0, GasType.NITROGEN, 1.0), 300);
        GasMixture ambient = new GasMixture(Map.of(GasType.WATER_VAPOR, 2.0), 300);
        var result = LindaGasSharing.processPressureAware(Map.of(LEFT, finite), Map.of(LEFT, finite, RIGHT, ambient),
                adjacency(), Set.of(LEFT), Set.of(), Set.of(), 1);
        GasMixture after = result.mixtures().get(LEFT);
        assertEquals(2.0, after.totalMoles(), 1e-6);
        assertTrue(after.moles(GasType.WATER_VAPOR) > 0);
        assertEquals(0.0, ambient.moles(GasType.OXYGEN), 0.0);
        assertNotEquals(finite.thermalEnergy(), after.thermalEnergy(),
                "boundary exchange carries donor enthalpy into the finite-side delta");
        assertEquals(ambient.temperatureKelvin(), result.mixtures().getOrDefault(RIGHT, ambient).temperatureKelvin(), 0.0);
    }

    @Test
    void pressureAwareZeroArchiveFallsBackToCurrentCompositionWithoutNan() {
        GasMixture current = mixture(GasType.OXYGEN, 2, 300);
        GasMixture emptyArchive = GasMixture.vacuum();
        var result = LindaGasSharing.processPressureAware(pair(current, current),
                Map.of(LEFT, emptyArchive, RIGHT, current), adjacency(), Set.of(LEFT), Set.of(), Set.of(), 1);
        assertTrue(Double.isFinite(result.mixtures().get(LEFT).temperatureKelvin()));
        assertTrue(Double.isFinite(result.mixtures().get(RIGHT).moles(GasType.OXYGEN)));
    }

    @Test
    void pressureAwareHeterogeneousCpCounterdiffusionConservesRawEnergy() {
        GasMixture left = new GasMixture(Map.of(GasType.TRITIUM, 1.0, GasType.FREZON, 1.0), 420);
        GasMixture right = new GasMixture(Map.of(GasType.WATER_VAPOR, 1.0, GasType.NITROGEN, 1.0), 280);
        Map<BlockPos, GasMixture> cells = pair(left, right);
        double energy = left.thermalEnergy() + right.thermalEnergy();
        var result = LindaGasSharing.processPressureAware(cells, cells, adjacency(), Set.of(LEFT), Set.of(), Set.of(), 1);
        GasMixture afterLeft = result.mixtures().get(LEFT), afterRight = result.mixtures().get(RIGHT);
        assertEquals(energy, afterLeft.thermalEnergy() + afterRight.thermalEnergy(), 2e-3);
        assertEquals(left.totalMoles(), afterLeft.totalMoles(), 2e-6);
        assertEquals(right.totalMoles(), afterRight.totalMoles(), 2e-6);
        assertTrue(afterLeft.temperatureKelvin() >= 280 && afterLeft.temperatureKelvin() <= 420);
        assertTrue(afterRight.temperatureKelvin() >= 280 && afterRight.temperatureKelvin() <= 420);
    }

    @Test
    void boundedBatchLooksUpOnlyItsArchiveAndProcessedMembership() {
        GasMixture left = mixture(GasType.OXYGEN, 8.0, 293.15);
        GasMixture right = mixture(GasType.OXYGEN, 1.0, 293.15);
        Map<BlockPos, GasMixture> data = pair(left, right);
        BlockPos unrelated = new BlockPos(100, 0, 0);
        Map<BlockPos, GasMixture> archive = new AbstractMap<>() {
            @Override public GasMixture get(Object key) { return data.get(key); }
            @Override public boolean containsKey(Object key) { return data.containsKey(key) || unrelated.equals(key); }
            @Override public Set<Entry<BlockPos, GasMixture>> entrySet() { throw new AssertionError("archive must not be enumerated"); }
        };
        Set<BlockPos> previous = new AbstractSet<>() {
            @Override public boolean contains(Object value) { return false; }
            @Override public Iterator<BlockPos> iterator() { throw new AssertionError("processed set must not be enumerated"); }
            @Override public int size() { throw new AssertionError("processed set must not be copied"); }
        };
        var result = LindaGasSharing.process(data, archive, adjacency(), Set.of(LEFT), Set.of(), previous, 1);
        assertEquals(4.5, result.mixtures().get(LEFT).moles(GasType.OXYGEN), 1e-6);
        assertEquals(1, result.sharedPairs().size());
    }

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
        double storedStandardMoles = (float) MOLES_AT_ONE_ATMOSPHERE;
        assertEquals(storedStandardMoles, left.totalMoles(), 1e-12);
        assertEquals(storedStandardMoles, right.totalMoles(), 1e-12);
        assertEquals(cells.get(LEFT).pressureKpa(1), left.pressureKpa(1), 1e-10);
        assertEquals(cells.get(RIGHT).pressureKpa(1), right.pressureKpa(1), 1e-10);
        assertEquals(MOLES_AT_ONE_ATMOSPHERE / 2.0, left.moles(GasType.NITROGEN),
                4 * Math.ulp((float) (MOLES_AT_ONE_ATMOSPHERE / 2.0)),
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
        assertEquals(20.0 / 3.0 + 0.06 / 3.0, result.movedMolesByPair().get(highPair), 1e-9);
        double liveOxygenAfterHighFlow = (double) 20.0f - (double) 20.0f / 3.0;
        double storedLowFlowOxygen = (float) (40.0 / 3.0);
        double lowPairOxygenFlux = (liveOxygenAfterHighFlow - storedLowFlowOxygen) / 3.0;
        double liveNitrogenAfterHighFlow = (double) 0.06f - (double) 0.06f / 3.0;
        double lowPairNitrogenFlux = liveNitrogenAfterHighFlow / 3.0;
        assertEquals(lowPairOxygenFlux + lowPairNitrogenFlux, result.movedMolesByPair().get(lowPair), 1e-12);
        assertTrue(result.movedMolesByPair().get(highPair) > 4.16);
        assertTrue(result.movedMolesByPair().get(lowPair) < 0.0416);
        assertEquals(Math.abs(result.movedMolesByPair().get(lowPair)), result.lastShare().get(LEFT), 0.0);
        assertEquals(result.movedMolesByPair(), reversed.movedMolesByPair());
        assertThrows(UnsupportedOperationException.class,
                () -> result.movedMolesByPair().put(highPair, 0.0));
    }

    @Test
    void multiNeighborSharingUsesReceiverDividerArchiveTemperaturesAndImmutableReservoir() {
        BlockPos north = new BlockPos(0, 0, -1);
        BlockPos south = new BlockPos(0, 0, 1);
        GasMixture receiver = mixture(GasType.OXYGEN, 9.0, 400.0);
        GasMixture immutable = mixture(GasType.NITROGEN, 6.0, 200.0);
        GasMixture mutable = mixture(GasType.OXYGEN, 0.0, 200.0);
        Map<BlockPos, GasMixture> live = Map.of(LEFT, receiver, south, mutable);
        Map<BlockPos, GasMixture> archives = Map.of(LEFT, receiver, north, immutable, south, mutable);
        Map<BlockPos, Set<BlockPos>> edges = Map.of(LEFT, Set.of(north, south));
        Set<LindaGasSharing.Pair> grouped = Set.of(LindaGasSharing.Pair.of(LEFT, north),
                LindaGasSharing.Pair.of(LEFT, south));

        var result = LindaGasSharing.process(live, archives, edges, Set.of(LEFT), grouped, 800);

        // Sorted active processing uses the six-face direction order; each receiver flux is /3.
        assertEquals(4.0, result.mixtures().get(LEFT).moles(GasType.OXYGEN), 1e-6);
        assertEquals(4.0 / 3.0, result.mixtures().get(LEFT).moles(GasType.NITROGEN), 1e-6);
        assertEquals(2.0, result.mixtures().get(south).moles(GasType.OXYGEN), 1e-6);
        assertEquals(2.0 / 3.0, result.mixtures().get(south).moles(GasType.NITROGEN), 1e-6);
        assertEquals(6.0, immutable.moles(GasType.NITROGEN), 0.0);
        float carriedAfterFirstPair = (22.5f * 400.0f - 7.5f * 400.0f + 7.5f * 200.0f) / 22.5f;
        float conductedAfterFirstPair = carriedAfterFirstPair
                - (0.4f * 200.0f * 22.5f * 22.5f / (22.5f + 22.5f)) / 22.5f;
        float expectedReceiverTemperature = (22.5f * conductedAfterFirstPair - 7.5f * 400.0f) / 15.0f;
        assertEquals(expectedReceiverTemperature, result.mixtures().get(LEFT).temperatureKelvin(),
                4.0 * Math.ulp(expectedReceiverTemperature),
                "gas-bearing immutable ambient uses its own normalized gas Cp, not virtual space Cp");
        float expectedSouthTemperature = (0.0003f * 200.0f + 7.5f * 400.0f) / (0.0003f + 7.5f);
        assertEquals(expectedSouthTemperature, result.mixtures().get(south).temperatureKelvin(),
                4.0 * Math.ulp(expectedSouthTemperature));
        assertEquals(2, result.sharedPairs().size());
        assertEquals(8.0 / 3.0, result.lastShare().get(LEFT), 1e-6,
                "LastShare is the current pair's absMovedMoles, not an aggregate over neighbors");
        assertEquals(5.0, result.absoluteMovedMolesByPair().get(LindaGasSharing.Pair.of(LEFT, north)), 1e-6);
        assertEquals(8.0 / 3.0, result.absoluteMovedMolesByPair().get(LindaGasSharing.Pair.of(LEFT, south)), 1e-6);
    }

    @Test
    void perPairAbsoluteMovementDoesNotCancelOpposingSpeciesFlux() {
        GasMixture first = new GasMixture(Map.of(GasType.OXYGEN, 1.0), 293.15);
        GasMixture second = new GasMixture(Map.of(GasType.NITROGEN, 1.0), 293.15);
        var result = LindaGasSharing.process(pair(first, second), adjacency(), Set.of(LEFT),
                Set.of(LindaGasSharing.Pair.of(LEFT, RIGHT)), 1);
        LindaGasSharing.Pair pair = LindaGasSharing.Pair.of(LEFT, RIGHT);
        assertEquals(0.0, result.movedMolesByPair().get(pair), 0.0);
        assertEquals(1.0, result.absoluteMovedMolesByPair().get(pair), 0.0);
        assertEquals(1.0, result.lastShare().get(LEFT), 0.0);
    }

    @Test
    void resumedBatchDoesNotReshareAnEdgeProcessedEarlierInTheSameCycle() {
        Map<BlockPos, GasMixture> archives = pair(mixture(GasType.OXYGEN, 8.0, 293.15),
                mixture(GasType.OXYGEN, 1.0, 293.15));
        Map<BlockPos, Set<BlockPos>> edges = adjacency();
        var first = LindaGasSharing.process(archives, archives, edges, Set.of(LEFT), Set.of(), Set.of(), 1);
        assertEquals(Set.of(LEFT), first.processedCells());
        assertEquals(4.5, first.mixtures().get(LEFT).moles(GasType.OXYGEN), 1e-6);

        var resumed = LindaGasSharing.process(first.mixtures(), archives, edges, Set.of(RIGHT), Set.of(),
                first.processedCells(), 1);
        assertEquals(Set.of(RIGHT), resumed.processedCells());
        assertTrue(resumed.sharedPairs().isEmpty());
        assertEquals(first.mixtures().get(RIGHT).moles(GasType.OXYGEN),
                resumed.mixtures().get(RIGHT).moles(GasType.OXYGEN), 0.0);
    }

    @Test
    void emptyFiniteMixturesUseCapacityFloorButImmutableSpaceUsesItsLargeCoolingCapacity() {
        GasMixture hotVacuum = new GasMixture(Map.of(), 400.0);
        GasMixture coldVacuum = GasMixture.vacuum();
        var finiteResult = LindaGasSharing.process(Map.of(LEFT, hotVacuum, RIGHT, coldVacuum),
                Map.of(LEFT, hotVacuum, RIGHT, coldVacuum), adjacency(), Set.of(LEFT),
                Set.of(LindaGasSharing.Pair.of(LEFT, RIGHT)), 1);
        assertEquals(400.0, finiteResult.mixtures().get(LEFT).temperatureKelvin(), 1e-6,
                "ordinary empty mixtures have only the minimum Cp and fail the strict conduction gate");

        GasMixture hotFinite = mixture(GasType.OXYGEN, 10.0, 400.0);
        var spaceCooling = LindaGasSharing.process(Map.of(LEFT, hotFinite),
                Map.of(LEFT, hotFinite, RIGHT, coldVacuum), adjacency(), Set.of(LEFT),
                Set.of(LindaGasSharing.Pair.of(LEFT, RIGHT)), 1);
        assertTrue(spaceCooling.mixtures().get(LEFT).temperatureKelvin() < 300.0,
                "nonempty finite gas cools against immutable empty-space capacity");
        assertEquals((float) GasMixture.MIN_TEMPERATURE_KELVIN, coldVacuum.temperatureKelvin(), 0.0);
    }

    @Test
    void sourceArchivedCapacityConductionCanIntentionallyChangeRawEnergy() {
        GasMixture archivedReceiver = mixture(GasType.NITROGEN, 43.0, 200_000.0);
        GasMixture archivedSharer = mixture(GasType.NITROGEN, 45.0, 349.0);
        GasMixture liveReceiver = mixture(GasType.NITROGEN, 42.9, 200_000.0);
        GasMixture liveSharer = archivedSharer;
        Map<BlockPos, GasMixture> archives = pair(archivedReceiver, archivedSharer);
        Map<BlockPos, GasMixture> live = pair(liveReceiver, liveSharer);

        var result = LindaGasSharing.process(live, archives, adjacency(), Set.of(LEFT), Set.of(), Set.of(), 1);

        // Source Share uses live moles/Cp for flux, but archived temperatures for carried heat.
        double delta = (liveReceiver.moles(GasType.NITROGEN) - liveSharer.moles(GasType.NITROGEN)) / 2.0;
        double carriedCp = -delta * GasType.NITROGEN.molarHeatCapacity()
                / AtmosphereUnits.UPSTREAM_HEAT_SCALE_DEFAULT;
        double oldReceiverCp = liveReceiver.normalizedHeatCapacity();
        double oldSharerCp = liveSharer.normalizedHeatCapacity();
        double newReceiverCp = oldReceiverCp + carriedCp;
        double newSharerCp = oldSharerCp - carriedCp;
        double receiverAfterCarriedHeat = (oldReceiverCp * liveReceiver.temperatureKelvin()
                + carriedCp * archivedSharer.temperatureKelvin()) / newReceiverCp;
        double sharerAfterCarriedHeat = (oldSharerCp * liveSharer.temperatureKelvin()
                - carriedCp * archivedSharer.temperatureKelvin()) / newSharerCp;

        double archivedReceiverCp = archivedReceiver.normalizedHeatCapacity();
        double archivedSharerCp = archivedSharer.normalizedHeatCapacity();
        double heat = 0.4 * (archivedReceiver.temperatureKelvin() - archivedSharer.temperatureKelvin())
                * archivedReceiverCp * archivedSharerCp / (archivedReceiverCp + archivedSharerCp);
        float expectedReceiverTemperature = (float) (receiverAfterCarriedHeat - heat / archivedReceiverCp);
        float expectedSharerTemperature = (float) (sharerAfterCarriedHeat + heat / archivedSharerCp);
        GasMixture expectedReceiver = mixture(GasType.NITROGEN,
                (float) (liveReceiver.moles(GasType.NITROGEN) - delta), expectedReceiverTemperature);
        GasMixture expectedSharer = mixture(GasType.NITROGEN,
                (float) (liveSharer.moles(GasType.NITROGEN) + delta), expectedSharerTemperature);

        assertEquals(expectedReceiver.moles(GasType.NITROGEN),
                result.mixtures().get(LEFT).moles(GasType.NITROGEN), 0.0);
        assertEquals(expectedSharer.moles(GasType.NITROGEN),
                result.mixtures().get(RIGHT).moles(GasType.NITROGEN), 0.0);
        assertEquals(expectedReceiver.temperatureKelvin(), result.mixtures().get(LEFT).temperatureKelvin(),
                2.0 * Math.ulp(expectedReceiver.temperatureKelvin()));
        assertEquals(expectedSharer.temperatureKelvin(), result.mixtures().get(RIGHT).temperatureKelvin(),
                2.0 * Math.ulp(expectedSharer.temperatureKelvin()));

        double energyBefore = live.values().stream().mapToDouble(GasMixture::thermalEnergy).sum();
        double energyAfter = result.mixtures().values().stream().mapToDouble(GasMixture::thermalEnergy).sum();
        double sourceExpectedEnergyAfter = expectedReceiver.thermalEnergy() + expectedSharer.thermalEnergy();
        assertEquals(sourceExpectedEnergyAfter, energyAfter,
                2.0 * Math.ulp((float) sourceExpectedEnergyAfter));
        double expectedRawEnergyDrift = sourceExpectedEnergyAfter - energyBefore;
        assertEquals(expectedRawEnergyDrift, energyAfter - energyBefore,
                2.0 * (Math.ulp((float) sourceExpectedEnergyAfter) + Math.ulp((float) energyBefore)));
        assertTrue(expectedRawEnergyDrift < -1_000_000.0,
                "upstream carried heat and archived-Cp conduction intentionally do not conserve this live-state raw energy");
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
