package com.juicyslew.moonstation14.ms14.atmos.world;

import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import com.juicyslew.moonstation14.ms14.atmos.reaction.GasReactionStep;
import com.juicyslew.moonstation14.ms14.atmos.reaction.GasReactionEvaluator;
import com.juicyslew.moonstation14.ms14.atmos.reaction.GasReactionData;
import com.juicyslew.moonstation14.ms14.atmos.reaction.HotspotEntityExposure;
import com.juicyslew.moonstation14.ms14.atmos.reaction.HotspotSpread;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class AtmosphereHotspotSpreadTest {
    private static final BlockPos SOURCE = new BlockPos(0, 64, 0);
    private static final BlockPos EAST = SOURCE.east();
    private static final GasMixture HOT = new GasMixture(Map.of(GasType.TRITIUM, 12.0, GasType.OXYGEN, 12.0), 900);
    private static final GasMixture COLD = new GasMixture(Map.of(GasType.TRITIUM, 1.0, GasType.OXYGEN, 1.0), 300);

    private static GasReactionStep.Result step(boolean fire) {
        return new GasReactionStep.Result(HOT, Map.of(), 7, List.of(), Optional.empty(), fire);
    }

    @Test
    void throwingPostCommitPublisherStampsSourceAndNeverReportsCommittedGasAsEmpty() {
        GasMixture sourceBefore = new GasMixture(HOT.gasMoles(), 800);
        Map<BlockPos, GasMixture> cells = new HashMap<>(Map.of(SOURCE, sourceBefore, EAST, COLD));
        ReactionWorkQueue work = new ReactionWorkQueue();
        AtomicInteger writes = new AtomicInteger();
        AtomicInteger evaluations = new AtomicInteger();
        IllegalStateException failure = assertThrows(IllegalStateException.class, () ->
                work.commitOnce(8, SOURCE, () -> AtmosphereService.commitHotspotSpreadWithTemperature(
                        SOURCE, sourceBefore, null, ignored -> { evaluations.incrementAndGet(); return step(true); },
                        List.of(new HotspotSpread.Neighbor(EAST, Direction.EAST, COLD)), () -> true,
                        (pos, gas) -> { writes.incrementAndGet(); cells.put(pos, gas); return true; },
                        changed -> { throw new IllegalStateException("publication failed"); },
                        state -> fail("publisher should have stopped"),
                        (pos, state) -> fail("publisher should have stopped"),
                        heat -> fail("publisher should have stopped"))));
        assertEquals("publication failed", failure.getMessage());
        assertNotSame(sourceBefore, cells.get(SOURCE));
        assertNotSame(COLD, cells.get(EAST));
        assertTrue(work.reacted(8, SOURCE));
        assertTrue(work.commitOnce(8, SOURCE, () -> { fail("must not burn again");
            return Optional.of(new AtmosphereService.CommittedHotspot(null, 900)); }).isEmpty());
        assertEquals(1, evaluations.get());
        assertEquals(2, writes.get());
    }

    @Test
    void committedFireExposesPreSpreadHeatEvenWhenFaceTransferCoolsSourceBelowStackThreshold() {
        // The receiver takes almost all heat above the source's 374.15 K spread reserve.
        GasMixture receiver = new GasMixture(Map.of(GasType.TRITIUM, 12.0 * 7.09,
                GasType.OXYGEN, 12.0 * 7.09), 300);
        GasMixture sourceBefore = new GasMixture(HOT.gasMoles(), 800);
        var fire = new GasReactionEvaluator.Event(ResourceLocation.parse("moonstation14:exposure_test"),
                GasReactionData.EffectType.TRITIUM_FIRE, 1, Map.of(), 7);
        var reaction = new GasReactionStep.Result(HOT, Map.of(), 7, List.of(fire), Optional.empty(), true);
        for (boolean rejectReceiver : List.of(false, true)) {
            Map<BlockPos, GasMixture> cells = new HashMap<>(Map.of(SOURCE, sourceBefore, EAST, receiver));
            AtomicInteger publications = new AtomicInteger();
            AtomicInteger receiverWrites = new AtomicInteger();
            var committed = AtmosphereService.commitHotspotSpreadWithTemperature(SOURCE, sourceBefore, null,
                    ignored -> reaction, List.of(new HotspotSpread.Neighbor(EAST, Direction.EAST, receiver)),
                    () -> true, (pos, gas) -> {
                        if (pos.equals(EAST) && rejectReceiver && receiverWrites.getAndIncrement() == 0) return false;
                        cells.put(pos, gas);
                        return true;
                    }, changed -> publications.incrementAndGet(), state -> publications.incrementAndGet(),
                    (pos, state) -> publications.incrementAndGet(), heat -> publications.incrementAndGet());
            if (rejectReceiver) {
                assertTrue(committed.isEmpty());
                assertSame(sourceBefore, cells.get(SOURCE));
                assertSame(receiver, cells.get(EAST));
                assertTrue(committed.isEmpty(), "rejected patch must not expose a fire");
                assertEquals(0, publications.get());
            } else {
                var result = committed.orElseThrow().result();
                assertSame(cells.get(SOURCE), result.mixture());
                assertEquals(List.of(fire), result.events());
                assertEquals(7, result.energyDeltaJoules());
                assertEquals(900, committed.orElseThrow().preSpreadTemperature());
                assertTrue(HotspotEntityExposure.target(committed.orElseThrow().preSpreadTemperature()) > 0f);
                assertEquals(0f, HotspotEntityExposure.target(result.mixture().temperatureKelvin()));
                assertEquals(374.15, result.mixture().temperatureKelvin(), 0.2);
                assertTrue(cells.get(EAST).temperatureKelvin() > 373.15);
                assertEquals(4, publications.get());
            }
        }
    }

    @Test
    void secondCellRejectRollsBackWithoutPublishingStateOrFaceHeat() {
        Map<BlockPos, GasMixture> cells = new HashMap<>(Map.of(SOURCE, COLD, EAST, COLD));
        // Source before is distinct from the post-fire source; the first patch write must be undone.
        GasMixture sourceBefore = new GasMixture(HOT.gasMoles(), 800);
        cells.put(SOURCE, sourceBefore);
        List<BlockPos> writes = new ArrayList<>();
        AtomicInteger publications = new AtomicInteger();
        AtomicInteger eastAttempts = new AtomicInteger();
        var result = AtmosphereService.commitHotspotSpread(SOURCE, sourceBefore, null,
                ignored -> step(true), List.of(new HotspotSpread.Neighbor(EAST, Direction.EAST, COLD)),
                () -> cells.get(SOURCE) == sourceBefore && cells.get(EAST) == COLD,
                (pos, gas) -> {
                    writes.add(pos);
                    if (pos.equals(EAST) && eastAttempts.getAndIncrement() == 0) return false;
                    cells.put(pos, gas);
                    return true;
                }, changed -> publications.incrementAndGet(), state -> publications.incrementAndGet(), (pos, state) -> publications.incrementAndGet(),
                heat -> publications.incrementAndGet());
        assertTrue(result.isEmpty());
        assertEquals(List.of(SOURCE, EAST, EAST, SOURCE), writes);
        assertSame(sourceBefore, cells.get(SOURCE));
        assertSame(COLD, cells.get(EAST));
        assertEquals(0, publications.get());
    }

    @Test
    void committedResultKeepsReactionEnergySeparateFromInteriorFaceTransfer() {
        GasMixture sourceBefore = new GasMixture(HOT.gasMoles(), 800);
        Map<BlockPos, GasMixture> cells = new HashMap<>(Map.of(SOURCE, sourceBefore, EAST, COLD));
        Map<Direction, Double> faceHeat = new HashMap<>();
        ReactionWorkQueue work = new ReactionWorkQueue();
        var result = AtmosphereService.commitHotspotSpread(SOURCE, sourceBefore, null,
                ignored -> step(true), List.of(new HotspotSpread.Neighbor(EAST, Direction.EAST, COLD)),
                () -> true, (pos, gas) -> { cells.put(pos, gas); return true; }, changed -> {
                    assertEquals(List.of(SOURCE, EAST), changed);
                },
                state -> work.setHotspot(SOURCE, state),
                (pos, state) -> { work.setHotspot(pos, Optional.of(state)); work.deferReceiver(4, pos); },
                faceHeat::putAll).orElseThrow();
        assertSame(cells.get(SOURCE), result.mixture());
        assertEquals(7, result.energyDeltaJoules());
        assertTrue(faceHeat.get(Direction.EAST) > 0);
        assertEquals(HOT.thermalEnergy() - cells.get(SOURCE).thermalEnergy(), faceHeat.get(Direction.EAST), 1e-6);
        assertTrue(work.reacted(4, EAST));
        assertFalse(work.reacted(5, EAST));
        assertNotNull(work.hotspot(EAST));
    }

    @Test
    void noFireNoTransferAndStalePreflightMakesNoWrites() {
        AtomicInteger writes = new AtomicInteger();
        var neighbor = new HotspotSpread.Neighbor(EAST, Direction.EAST, COLD);
        assertTrue(AtmosphereService.commitHotspotSpread(SOURCE, HOT, null, ignored -> step(false),
                List.of(neighbor), () -> true, (pos, gas) -> { writes.incrementAndGet(); return true; }, changed -> fail(),
                state -> fail(), (pos, state) -> fail(), heat -> fail()).isEmpty());
        assertTrue(AtmosphereService.commitHotspotSpread(SOURCE, COLD, null, ignored -> step(true),
                List.of(neighbor), () -> false, (pos, gas) -> { writes.incrementAndGet(); return true; }, changed -> fail(),
                state -> fail(), (pos, state) -> fail(), heat -> fail()).isEmpty());
        assertEquals(0, writes.get());
    }

    @Test
    void verifiedNoOpQuenchPublishesVisualClearWithoutGasWriteButRejectedPreflightPreservesFlame() {
        var grown = new com.juicyslew.moonstation14.ms14.atmos.reaction.HotspotKernel.HotspotState(.8, 400);
        GasReactionStep.Result quench = new GasReactionStep.Result(COLD, Map.of(), 0, List.of(), Optional.empty(), false);
        var eligibleReaction = new GasReactionData(Map.of(GasType.TRITIUM, .01, GasType.OXYGEN, .01),
                0, 2000, 0, -1,
                List.of(new GasReactionData.Effect(GasReactionData.EffectType.TRITIUM_FIRE)));
        assertTrue(AtmosphereService.potentiallyReactive(COLD, List.of(eligibleReaction)));
        for (boolean allowPreflight : List.of(true, false)) {
            ReactionWorkQueue work = new ReactionWorkQueue();
            work.setHotspot(SOURCE, Optional.of(grown));
            work.setBurning(SOURCE, 128);
            AtomicInteger gasWrites = new AtomicInteger();
            AtomicInteger visualClears = new AtomicInteger();
            var result = AtmosphereService.commitHotspotSpreadWithTemperature(SOURCE, COLD, grown,
                    ignored -> quench, List.of(), () -> allowPreflight,
                    (pos, gas) -> { gasWrites.incrementAndGet(); return true; }, changed -> fail(),
                    state -> {
                        boolean changed = state.isEmpty()
                                && (work.hotspot(SOURCE) != null || work.fireIntensity(SOURCE) != 0);
                        work.setHotspot(SOURCE, state);
                        if (state.isEmpty()) work.setBurning(SOURCE, 0);
                        if (changed) visualClears.incrementAndGet();
                    }, (pos, state) -> fail(), heat -> fail());
            assertTrue(result.isEmpty()); // verified no-op quench deliberately has no committed result
            assertEquals(0, gasWrites.get());
            if (allowPreflight) {
                assertNull(work.hotspot(SOURCE));
                assertEquals(0, work.fireIntensity(SOURCE));
                assertEquals(1, visualClears.get());
            } else {
                assertSame(grown, work.hotspot(SOURCE));
                assertEquals(128, work.fireIntensity(SOURCE));
                assertEquals(0, visualClears.get());
            }
        }
    }

    @Test
    void rawPatchPublishesChangedCellsOnceAfterBothWrites() {
        GasMixture sourceAfter = HOT;
        GasMixture receiverAfter = new GasMixture(COLD.gasMoles(), 400);
        Map<BlockPos, GasMixture> cells = new HashMap<>(Map.of(SOURCE, COLD, EAST, COLD));
        AtomicInteger publications = new AtomicInteger();
        assertTrue(AtmosphereService.applyHotspotPatch(List.of(SOURCE, EAST),
                Map.of(SOURCE, COLD, EAST, COLD), Map.of(SOURCE, sourceAfter, EAST, receiverAfter),
                (pos, gas) -> { cells.put(pos, gas); return true; }, changed -> {
                    assertEquals(List.of(SOURCE, EAST), changed);
                    assertSame(sourceAfter, cells.get(SOURCE));
                    assertSame(receiverAfter, cells.get(EAST));
                    publications.incrementAndGet();
                }));
        assertEquals(1, publications.get());
    }

    @Test
    void rejectedOrThrowingSecondCellRestoresBothWithoutPublishingOrUndoingUnrelatedGas() {
        for (boolean throwsAfterMutation : List.of(false, true)) {
            BlockPos unrelated = SOURCE.west();
            GasMixture unrelatedGas = new GasMixture(HOT.gasMoles(), 500);
            Map<BlockPos, GasMixture> cells = new HashMap<>(Map.of(SOURCE, COLD, EAST, COLD, unrelated, unrelatedGas));
            List<BlockPos> writes = new ArrayList<>();
            AtomicInteger publications = new AtomicInteger();
            AtomicInteger eastAttempts = new AtomicInteger();
            Runnable run = () -> assertFalse(AtmosphereService.applyHotspotPatch(List.of(SOURCE, EAST),
                    Map.of(SOURCE, COLD, EAST, COLD), Map.of(SOURCE, HOT, EAST, HOT),
                    (pos, gas) -> {
                        writes.add(pos);
                        if (pos.equals(EAST) && eastAttempts.getAndIncrement() == 0) {
                            cells.put(pos, HOT);
                            cells.put(unrelated, new GasMixture(unrelatedGas.gasMoles(), 600));
                            if (throwsAfterMutation) throw new IllegalStateException("failed after mutation");
                            return false;
                        }
                        cells.put(pos, gas);
                        return true;
                    }, changed -> publications.incrementAndGet()));
            if (throwsAfterMutation) assertThrows(IllegalStateException.class, run::run);
            else run.run();
            assertEquals(List.of(SOURCE, EAST, EAST, SOURCE), writes);
            assertSame(COLD, cells.get(SOURCE));
            assertSame(COLD, cells.get(EAST));
            assertEquals(600, cells.get(unrelated).temperatureKelvin());
            assertEquals(0, publications.get());
        }
    }

    @Test
    void mutatedThenRejectedSpreadDoesNotPublishPhantomReceiverOrFaceHeat() {
        GasMixture sourceBefore = new GasMixture(HOT.gasMoles(), 800);
        Map<BlockPos, GasMixture> cells = new HashMap<>(Map.of(SOURCE, sourceBefore, EAST, COLD));
        ReactionWorkQueue work = new ReactionWorkQueue();
        Map<Direction, Double> faceHeat = new HashMap<>();
        AtomicInteger attempts = new AtomicInteger();
        assertTrue(AtmosphereService.commitHotspotSpread(SOURCE, sourceBefore, null,
                ignored -> step(true), List.of(new HotspotSpread.Neighbor(EAST, Direction.EAST, COLD)),
                () -> true, (pos, gas) -> {
                    cells.put(pos, gas);
                    return !pos.equals(EAST) || attempts.getAndIncrement() > 0;
                }, changed -> fail(), state -> work.setHotspot(SOURCE, state),
                (pos, state) -> { work.setHotspot(pos, Optional.of(state)); work.deferReceiver(4, pos); },
                faceHeat::putAll).isEmpty());
        assertSame(sourceBefore, cells.get(SOURCE));
        assertSame(COLD, cells.get(EAST));
        assertNull(work.hotspot(EAST));
        assertFalse(work.reacted(4, EAST));
        assertTrue(faceHeat.isEmpty());
    }

    @Test
    void rollbackFailureEscapesSpreadCommitAndNeverPublishesTransients() {
        GasMixture sourceBefore = new GasMixture(HOT.gasMoles(), 800);
        Map<BlockPos, GasMixture> cells = new HashMap<>(Map.of(SOURCE, sourceBefore, EAST, COLD));
        AtomicInteger publications = new AtomicInteger();
        List<BlockPos> writes = new ArrayList<>();
        IllegalStateException failure = assertThrows(IllegalStateException.class, () ->
                AtmosphereService.commitHotspotSpread(SOURCE, sourceBefore, null,
                        ignored -> step(true), List.of(new HotspotSpread.Neighbor(EAST, Direction.EAST, COLD)),
                        () -> true, (pos, gas) -> {
                            writes.add(pos);
                            if (pos.equals(EAST) && gas == COLD) return false; // Recovery is impossible.
                            if (pos.equals(EAST)) throw new IllegalArgumentException("writer failed after mutation");
                            cells.put(pos, gas);
                            return true;
                        }, changed -> publications.incrementAndGet(), state -> publications.incrementAndGet(),
                        (pos, state) -> publications.incrementAndGet(), heat -> publications.incrementAndGet()));
        assertTrue(failure.getMessage().contains("rollback failed"));
        assertEquals(List.of(SOURCE, EAST, EAST, SOURCE), writes);
        assertSame(sourceBefore, cells.get(SOURCE));
        assertEquals(0, publications.get());
    }

    @Test
    void throwingWriterWithSuccessfulRollbackReturnsEmptyWithoutPublication() {
        GasMixture sourceBefore = new GasMixture(HOT.gasMoles(), 800);
        Map<BlockPos, GasMixture> cells = new HashMap<>(Map.of(SOURCE, sourceBefore, EAST, COLD));
        AtomicInteger eastAttempts = new AtomicInteger();
        AtomicInteger publications = new AtomicInteger();
        assertTrue(AtmosphereService.commitHotspotSpread(SOURCE, sourceBefore, null,
                ignored -> step(true), List.of(new HotspotSpread.Neighbor(EAST, Direction.EAST, COLD)),
                () -> true, (pos, gas) -> {
                    cells.put(pos, gas);
                    if (pos.equals(EAST) && eastAttempts.getAndIncrement() == 0)
                        throw new IllegalStateException("forward write failed");
                    return true;
                }, changed -> publications.incrementAndGet(), state -> publications.incrementAndGet(),
                (pos, state) -> publications.incrementAndGet(), heat -> publications.incrementAndGet()).isEmpty());
        assertSame(sourceBefore, cells.get(SOURCE));
        assertSame(COLD, cells.get(EAST));
        assertEquals(0, publications.get());
    }
}
