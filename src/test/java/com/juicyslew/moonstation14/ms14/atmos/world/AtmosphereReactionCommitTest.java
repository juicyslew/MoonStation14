package com.juicyslew.moonstation14.ms14.atmos.world;

import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import com.juicyslew.moonstation14.ms14.atmos.reaction.GasReactionEvaluator;
import com.juicyslew.moonstation14.ms14.atmos.reaction.GasReactionStep;
import com.juicyslew.moonstation14.ms14.atmos.reaction.HotspotState;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class AtmosphereReactionCommitTest {
    private static final GasMixture BEFORE = new GasMixture(Map.of(GasType.TRITIUM, 2.0), 900);
    private static final GasMixture AFTER = new GasMixture(Map.of(GasType.WATER_VAPOR, 2.0), 1200);
    private static final GasReactionEvaluator.Result RESULT =
            new GasReactionEvaluator.Result(AFTER, Map.of(GasType.TRITIUM, -2.0, GasType.WATER_VAPOR, 2.0),
                     100, List.of());

    @Test
    void excitedGroupResetRequiresCommittedAlreadyProcessedHotspotIncludingFizzle() {
        assertFalse(AtmosphereService.shouldResetExcitedGroupCooldownsAfterCommit(
                new HotspotState(100, 900, true, false), false), "rejected preflight/write must not reset pacing");
        assertFalse(AtmosphereService.shouldResetExcitedGroupCooldownsAfterCommit(null, true));
        assertFalse(AtmosphereService.shouldResetExcitedGroupCooldownsAfterCommit(
                new HotspotState(100, 900, false, false), true), "first-pass skip is not reset");
        assertTrue(AtmosphereService.shouldResetExcitedGroupCooldownsAfterCommit(
                new HotspotState(0, 200, true, false), true), "committed fizzle still resets pacing");
    }

    @Test
    void reactionMixtureRequiresAttachedFiniteClaim() {
        BlockPos pos = new BlockPos(2, 64, 3);
        assertNull(AtmosphereService.reactionFiniteMixture(null, pos, BEFORE));
        AtmosphereChunkData data = new AtmosphereChunkData();
        assertNull(AtmosphereService.reactionFiniteMixture(data, pos, BEFORE));
        data.claimFinite(2, 64, 3);
        assertSame(BEFORE, AtmosphereService.reactionFiniteMixture(data, pos, BEFORE));
        data.put(2, 64, 3, AFTER, BEFORE);
        assertSame(AFTER, AtmosphereService.reactionFiniteMixture(data, pos, BEFORE));
    }

    @Test
    void lindaWholeAirIgnitionDiagnosticRequiresNewCommittedLocalFireMetadata() {
        BlockPos pos = new BlockPos(2, 64, 3);
        GasMixture shared = new GasMixture(Map.of(GasType.TRITIUM, 1.0, GasType.OXYGEN, 2.0), 900);
        var event = new GasReactionEvaluator.Event(net.minecraft.resources.ResourceLocation.parse("moonstation14:tritium_fire"),
                com.juicyslew.moonstation14.ms14.atmos.reaction.GasReactionData.EffectType.TRITIUM_FIRE,
                .25, Map.of(GasType.TRITIUM, -.25), 100);
        var whole = new GasReactionStep.WholeAirResult(AFTER, List.of(event), .25, 25000);
        HotspotState created = new HotspotState(4248, 9468, false, true);

        var ignition = AtmosphereService.lindaIgnitionDiagnostic(12, 345, pos, null, shared, whole, Optional.of(created))
                .orElseThrow();
        assertEquals("WHOLE_AIR_REACTION", ignition.origin());
        assertEquals(12, ignition.cycleIdentity());
        assertEquals(345, ignition.gameTime());
        assertEquals("moonstation14:tritium_fire", ignition.reactionId());
        assertEquals(.25, ignition.fireExtentMoles());
        assertEquals(shared.temperatureKelvin(), ignition.bulkBeforeKelvin(), 0.0);
        assertEquals(AFTER.temperatureKelvin(), ignition.bulkAfterKelvin(), 0.0);
        assertEquals(1.0, ignition.tritiumBefore());
        assertEquals(2.0, ignition.oxygenBefore());
        assertEquals(created.temperatureKelvin(), ignition.hotspotKelvin(), 0.0);
        assertEquals(created.volumeLiters(), ignition.hotspotVolumeLiters(), 0.0);
        assertEquals(9468, ignition.hotspotKelvin(), 0.0);
        assertEquals(4248, ignition.hotspotVolumeLiters(), 0.0);
        assertTrue(AtmosphereService.lindaIgnitionDiagnostic(12, 345, pos, created, shared, whole,
                Optional.of(created)).isEmpty(), "existing metadata must not produce a duplicate ignition record");
        assertTrue(AtmosphereService.lindaIgnitionDiagnostic(12, 345, pos, null, shared,
                new GasReactionStep.WholeAirResult(AFTER, List.of(event), 0, 25000), Optional.of(created)).isEmpty(),
                "a fire event without positive extent is not a committed ignition");
    }

    @Test
    void committedWholeAirDiagnosticExposesOnlyTheCommittedCellReactionEvents() {
        var event = new GasReactionEvaluator.Event(net.minecraft.resources.ResourceLocation.parse("moonstation14:tritium_fire"),
                com.juicyslew.moonstation14.ms14.atmos.reaction.GasReactionData.EffectType.TRITIUM_FIRE,
                .125, Map.of(GasType.TRITIUM, -.125), 50);
        var committed = AtmosphereService.committedWholeAirDiagnostic(19,
                new GasReactionStep.WholeAirResult(AFTER, List.of(event), .125, 25000)).orElseThrow();
        assertEquals(19, committed.cycleIdentity());
        assertEquals(.125, committed.fireExtentMoles());
        assertEquals(List.of(event), committed.events());
        assertTrue(AtmosphereService.committedWholeAirDiagnostic(19,
                new GasReactionStep.WholeAirResult(AFTER, List.of(), .125, 25000)).orElseThrow().events().isEmpty(),
                "a retained positive fire slot without a local reaction event remains distinguishable");
        assertTrue(AtmosphereService.committedWholeAirDiagnostic(19, null).isEmpty());
    }

    @Test
    void commitsCompleteMixtureExactlyOnceAndOnlyAfterPreflight() {
        AtomicReference<GasMixture> cell = new AtomicReference<>(BEFORE);
        AtomicInteger writes = new AtomicInteger();
        assertEquals(RESULT, AtmosphereService.commitReaction(BEFORE, before -> {
            assertSame(BEFORE, before);
            assertSame(BEFORE, cell.get());
            return RESULT;
        }, () -> cell.get() == BEFORE, mixture -> {
            writes.incrementAndGet();
            assertSame(AFTER, mixture);
            cell.set(mixture);
            return true;
        }).orElseThrow());
        assertEquals(1, writes.get());
        assertSame(AFTER, cell.get());
    }

    @Test
    void nonMutatingRejectedWriteLeavesOriginalCellAndPublishesNothing() {
        AtomicReference<GasMixture> cell = new AtomicReference<>(BEFORE);
        AtomicInteger writes = new AtomicInteger();
        assertTrue(AtmosphereService.commitReaction(BEFORE, before -> RESULT, () -> true, mixture -> {
            writes.incrementAndGet();
            return false;
        }).isEmpty());
        assertSame(BEFORE, cell.get());
        assertEquals(1, writes.get());
    }

    @Test
    void staleSnapshotAndNoOpNeverInvokeWriter() {
        AtomicInteger writes = new AtomicInteger();
        assertTrue(AtmosphereService.commitReaction(BEFORE, before -> RESULT, () -> false,
                mixture -> { writes.incrementAndGet(); return true; }).isEmpty());
        assertTrue(AtmosphereService.commitReaction(BEFORE,
                before -> new GasReactionEvaluator.Result(BEFORE, Map.of(), 0, List.of()),
                () -> { fail("no-op should not preflight"); return true; },
                mixture -> { writes.incrementAndGet(); return true; }).isEmpty());
        assertEquals(0, writes.get());
    }

    @Test
    void evaluatorOverflowAndInvalidResultFailBeforeWrite() {
        AtomicInteger writes = new AtomicInteger();
        assertTrue(AtmosphereService.commitReaction(BEFORE, before -> {
            throw new IllegalArgumentException("overflow");
        }, () -> true, mixture -> { writes.incrementAndGet(); return true; }).isEmpty());
        assertTrue(AtmosphereService.commitReaction(BEFORE,
                before -> new GasReactionEvaluator.Result(AFTER, Map.of(), Double.POSITIVE_INFINITY, List.of()),
                () -> true, mixture -> { writes.incrementAndGet(); return true; }).isEmpty());
        assertEquals(0, writes.get());
    }

    @Test
    void onePassPublishesCompleteResultAndHotspotOnlyAfterSuccessfulWrite() {
        BlockPos pos = new BlockPos(2, 64, 3);
        ReactionWorkQueue work = new ReactionWorkQueue();
        AtomicReference<GasMixture> cell = new AtomicReference<>(BEFORE);
        AtomicInteger evaluations = new AtomicInteger();
        AtomicInteger writes = new AtomicInteger();
        HotspotState next = new HotspotState(200, 1200, true, false);
        var event = new GasReactionEvaluator.Event(net.minecraft.resources.ResourceLocation.parse("moonstation14:tritium_fire"),
                com.juicyslew.moonstation14.ms14.atmos.reaction.GasReactionData.EffectType.TRITIUM_FIRE,
                .01, Map.of(GasType.TRITIUM, -.01), 100);
        var step = new GasReactionStep.Result(AFTER, RESULT.speciesDelta(), 100,
                List.of(event), Optional.of(next), true);
        var evaluator = (java.util.function.Function<GasMixture, GasReactionStep.Result>) before -> {
            evaluations.incrementAndGet();
            assertSame(BEFORE, cell.get());
            return step;
        };
        var commit = (java.util.function.Supplier<Optional<AtmosphereService.CommittedHotspot>>) () ->
                AtmosphereService.commitHotspotCyclePass(pos, BEFORE, work.hotspot(pos), evaluator, List.of(),
                        () -> cell.get() == BEFORE, (ignored, after) -> {
                    writes.incrementAndGet();
                    cell.set(after);
                    return true;
                }, changed -> { }, state -> work.setHotspot(pos, state), (receiver, state) -> { });
        var result = work.commitOnce(42, pos, commit).orElseThrow();
        assertSame(AFTER, result.result().mixture());
        assertEquals(List.of(event), result.result().events());
        assertEquals(RESULT.speciesDelta(), result.result().speciesDelta());
        assertEquals(100, result.result().energyDeltaJoules());
        assertSame(next, work.hotspot(pos));
        assertTrue(work.commitOnce(42, pos, commit).isEmpty());
        assertEquals(1, evaluations.get());
        assertEquals(1, writes.get());
    }

    @Test
    void failedWriterAndStalePreflightNeverPublishHotspotOrEvents() {
        BlockPos pos = new BlockPos(2, 64, 3);
        ReactionWorkQueue work = new ReactionWorkQueue();
        HotspotState old = new HotspotState(300, 900, true, false);
        work.setHotspot(pos, Optional.of(old));
        var step = new GasReactionStep.Result(AFTER, RESULT.speciesDelta(), 100,
                List.of(), Optional.of(new HotspotState(500, 1200, true, false)), true);
        for (boolean preflight : new boolean[] {false, true}) {
            AtomicInteger writes = new AtomicInteger();
            assertTrue(AtmosphereService.commitHotspotCyclePass(pos, BEFORE, work.hotspot(pos), before -> step,
                    List.of(), () -> preflight, (ignored, after) -> {
                        writes.incrementAndGet();
                        return after == BEFORE;
                    },
                    changed -> { }, state -> work.setHotspot(pos, state), (receiver, state) -> { }).isEmpty());
            assertEquals(preflight ? 2 : 0, writes.get(), "failed write must also restore the original mixture");
            assertSame(old, work.hotspot(pos));
        }
        AtomicInteger failedWrites = new AtomicInteger();
        assertTrue(AtmosphereService.commitHotspotCyclePass(pos, BEFORE, work.hotspot(pos), before -> step,
                List.of(), () -> true, (ignored, after) -> {
                    if (failedWrites.getAndIncrement() == 0) throw new IllegalStateException("writer failure");
                    return true;
                },
                changed -> { }, state -> work.setHotspot(pos, state), (receiver, state) -> { }).isEmpty());
        assertEquals(2, failedWrites.get(), "a transient failed write is restored before returning");
        assertSame(old, work.hotspot(pos));
    }

    @Test
    void verifiedNoOpQuenchClearsTransientStateWithoutWritingAndUnloadDropsIt() {
        BlockPos pos = new BlockPos(2, 64, 3);
        ReactionWorkQueue work = new ReactionWorkQueue();
        HotspotState old = new HotspotState(300, 900, true, false);
        work.setHotspot(pos, Optional.of(old));
        var quench = new GasReactionStep.Result(BEFORE, Map.of(), 0, List.of(), Optional.empty(), false);
        assertTrue(AtmosphereService.commitHotspotCyclePass(pos, BEFORE, old, before -> quench,
                List.of(), () -> false, (ignored, after) -> { fail("no write"); return true; },
                changed -> { }, state -> work.setHotspot(pos, state), (receiver, state) -> { }).isEmpty());
        assertSame(old, work.hotspot(pos));
        assertTrue(AtmosphereService.commitHotspotCyclePass(pos, BEFORE, old, before -> quench,
                List.of(), () -> true, (ignored, after) -> { fail("no write"); return true; },
                changed -> { }, state -> work.setHotspot(pos, state), (receiver, state) -> { }).isPresent());
        assertNull(work.hotspot(pos));
        work.setHotspot(pos, Optional.of(old));
        work.removeChunk(new net.minecraft.world.level.ChunkPos(pos));
        assertNull(work.hotspot(pos));
    }
}
