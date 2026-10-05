package com.juicyslew.moonstation14.ms14.atmos.world;

import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import com.juicyslew.moonstation14.ms14.atmos.reaction.GasReactionStep;
import com.juicyslew.moonstation14.ms14.atmos.reaction.HotspotState;
import com.juicyslew.moonstation14.ms14.atmos.reaction.HotspotSpread;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class AtmosphereHotspotSpreadTest {
    private static final BlockPos SOURCE = new BlockPos(0, 64, 0);
    private static final BlockPos EAST = SOURCE.east();
    private static GasMixture gas(double temperature, double moles) {
        return new GasMixture(Map.of(GasType.TRITIUM, moles, GasType.OXYGEN, moles), temperature);
    }

    @Test
    void committedFireExposureUsesPostHotspotMetadataEligibility() {
        HotspotState firstSkippedPass = new HotspotState(0, 1000, true, false);
        assertTrue(AtmosphereService.shouldExposeCommittedHotspot(firstSkippedPass,
                Optional.of(new HotspotState(0, 1000, true, false)))); // zero volume remains a valid hot pass
        assertFalse(AtmosphereService.shouldExposeCommittedHotspot(
                new HotspotState(10, 1000, false, false), Optional.of(firstSkippedPass)));
        assertFalse(AtmosphereService.shouldExposeCommittedHotspot(firstSkippedPass, Optional.empty()));
        assertFalse(AtmosphereService.shouldExposeCommittedHotspot(null, Optional.of(firstSkippedPass)));
    }

    @Test
    void successfulFirePassPublishesMetadataWithoutHeatingOrDebitingNeighbor() {
        GasMixture before = gas(800, 12);
        GasMixture reacted = gas(900, 12);
        GasMixture receiver = new GasMixture(Map.of(GasType.TRITIUM, 1., GasType.OXYGEN, 1.), 300, .04);
        HotspotState sourceState = new HotspotState(960, 900, true, false);
        var step = new GasReactionStep.Result(reacted, Map.of(), 1, List.of(),
                Optional.of(sourceState), true, 0, true);
        Map<BlockPos, GasMixture> cells = new HashMap<>(Map.of(SOURCE, before, EAST, receiver));
        AtomicReference<HotspotState> offered = new AtomicReference<>();
        AtomicInteger gasPublications = new AtomicInteger();

        var committed = AtmosphereService.commitHotspotCyclePass(SOURCE, before, null, ignored -> step,
                List.of(new HotspotSpread.Neighbor(EAST, Direction.EAST, receiver, null)), () -> true,
                (pos, mixture) -> { cells.put(pos, mixture); return true; },
                changed -> gasPublications.incrementAndGet(), state -> { },
                (pos, state) -> { assertEquals(EAST, pos); offered.set(state); }).orElseThrow();

        assertSame(reacted, committed.result().mixture());
        assertEquals(1, gasPublications.get());
        assertSame(receiver, cells.get(EAST));
        assertEquals(765, offered.get().temperatureKelvin());
        assertEquals(250, offered.get().volumeLiters(), 1e-4);
        assertEquals(receiver.thermalEnergy(), cells.get(EAST).thermalEnergy(), 0);
    }

    @Test
    void spreadMetadataUsesWhetherReceiverAlreadyHadItsLindaPass() {
        GasMixture before = gas(800, 12);
        GasMixture receiver = new GasMixture(Map.of(GasType.TRITIUM, 1., GasType.OXYGEN, 1.), 300, .04);
        var step = new GasReactionStep.Result(gas(900, 12), Map.of(), 1, List.of(),
                Optional.of(new HotspotState(960, 900, true, false)), true, 0, true);

        for (boolean lindaProcessed : List.of(false, true)) {
            AtomicReference<HotspotState> offered = new AtomicReference<>();
            AtmosphereService.commitHotspotCyclePass(SOURCE, before, null, ignored -> step,
                    List.of(new HotspotSpread.Neighbor(EAST, Direction.EAST, receiver, null)), () -> true,
                    (pos, mixture) -> true, ignored -> { }, ignored -> { },
                    (pos, state) -> offered.set(state), pos -> lindaProcessed).orElseThrow();
            assertEquals(lindaProcessed, offered.get().skippedFirstProcess(),
                    "only a recipient with an actual LINDA pass skips its first hotspot process");
        }
    }

    @Test
    void partialHotLocalFlameSpreadsDespiteColdBulkAndKeepsQuarterTileExposure() {
        GasMixture before = gas(293, 12);
        GasMixture reacted = gas(293, 12);
        GasMixture receiver = new GasMixture(Map.of(GasType.TRITIUM, 1., GasType.OXYGEN, 1.), 300);
        var step = new GasReactionStep.Result(reacted, Map.of(), 1, List.of(),
                Optional.of(new HotspotState(250, 600, true, false)), true, 0, true);
        AtomicReference<HotspotState> offered = new AtomicReference<>();
        var committed = AtmosphereService.commitHotspotCyclePass(SOURCE, before, null, ignored -> step,
                List.of(new HotspotSpread.Neighbor(EAST, Direction.EAST, receiver, null)), () -> true,
                (pos, mixture) -> true, ignored -> { }, ignored -> { }, (pos, state) -> offered.set(state));
        assertTrue(committed.isPresent());
        assertEquals(510, offered.get().temperatureKelvin());
        assertEquals(6250, offered.get().volumeLiters(), 1e-3,
                "250 L exposure metadata retains the existing x25 hotspot-volume conversion");
    }

    @Test
    void partialFizzleAndColdBulkFullBypassDoNotSpread() {
        GasMixture before = gas(293, 12);
        GasMixture receiver = new GasMixture(Map.of(GasType.TRITIUM, 1., GasType.OXYGEN, 1.), 300);
        for (GasReactionStep.Result step : List.of(
                new GasReactionStep.Result(gas(293, 12), Map.of(), 0, List.of(),
                        Optional.of(new HotspotState(0, 600, true, false)), false, 0, false),
                new GasReactionStep.Result(gas(293, 12), Map.of(), 1, List.of(),
                        Optional.of(new HotspotState(1000, 600, true, true)), true, 0, true))) {
            AtomicInteger offered = new AtomicInteger();
            assertTrue(AtmosphereService.commitHotspotCyclePass(SOURCE, before, null, ignored -> step,
                    List.of(new HotspotSpread.Neighbor(EAST, Direction.EAST, receiver, null)), () -> true,
                    (pos, mixture) -> true, ignored -> { }, ignored -> { }, (pos, state) -> offered.incrementAndGet())
                    .isPresent());
            assertEquals(0, offered.get());
        }
    }

    @Test
    void rejectedSourceWritePublishesNoSourceOrReceiverMetadata() {
        GasMixture before = gas(800, 12);
        GasMixture receiver = new GasMixture(Map.of(GasType.TRITIUM, 1., GasType.OXYGEN, 1.), 300, .04);
        var step = new GasReactionStep.Result(gas(900, 12), Map.of(), 1, List.of(),
                Optional.of(new HotspotState(960, 900, true, false)), true, 0, true);
        AtomicInteger publications = new AtomicInteger();
        assertTrue(AtmosphereService.commitHotspotCyclePass(SOURCE, before, null, ignored -> step,
                List.of(new HotspotSpread.Neighbor(EAST, Direction.EAST, receiver, null)), () -> true,
                new java.util.function.BiPredicate<BlockPos, GasMixture>() {
                    private boolean reject = true;
                    @Override public boolean test(BlockPos pos, GasMixture mixture) {
                        if (reject) { reject = false; return false; }
                        return true;
                    }
                }, changed -> publications.incrementAndGet(),
                state -> publications.incrementAndGet(), (pos, state) -> publications.incrementAndGet()).isEmpty());
        assertEquals(0, publications.get());
    }

    @Test
    void exposurePlannerDoesNotOfferAlreadyBurningNeighbors() {
        GasMixture source = gas(900, 12);
        GasMixture receiver = gas(300, 1);
        HotspotState burning = new HotspotState(500, 500, true, false);
        var offers = HotspotSpread.plan(SOURCE, source, new HotspotState(1000, 900, true, true),
                List.of(new HotspotSpread.Neighbor(EAST, Direction.EAST, receiver, burning)));
        assertTrue(offers.offers().isEmpty());
    }

    @Test
    void rejectedRawPatchRollsBackEarlierCellAndNeverPublishes() {
        GasMixture original = gas(300, 1);
        GasMixture changed = gas(400, 1);
        Map<BlockPos, GasMixture> cells = new HashMap<>(Map.of(SOURCE, original, EAST, original));
        AtomicInteger attempts = new AtomicInteger();
        AtomicInteger publications = new AtomicInteger();
        assertFalse(AtmosphereService.applyHotspotPatch(List.of(SOURCE, EAST),
                Map.of(SOURCE, original, EAST, original), Map.of(SOURCE, changed, EAST, changed),
                (pos, mixture) -> {
                    if (pos.equals(EAST) && attempts.getAndIncrement() == 0) return false;
                    cells.put(pos, mixture);
                    return true;
                }, ignored -> publications.incrementAndGet()));
        assertSame(original, cells.get(SOURCE));
        assertSame(original, cells.get(EAST));
        assertEquals(0, publications.get());
    }
}
