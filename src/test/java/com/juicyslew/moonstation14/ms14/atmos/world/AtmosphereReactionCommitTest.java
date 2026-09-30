package com.juicyslew.moonstation14.ms14.atmos.world;

import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import com.juicyslew.moonstation14.ms14.atmos.reaction.GasReactionEvaluator;
import com.juicyslew.moonstation14.ms14.atmos.reaction.GasReactionStep;
import com.juicyslew.moonstation14.ms14.atmos.reaction.HotspotKernel;
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
        HotspotKernel.HotspotState next = new HotspotKernel.HotspotState(.2, 1200);
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
        var commit = (java.util.function.Supplier<Optional<GasReactionEvaluator.Result>>) () ->
                AtmosphereService.commitReactionStep(BEFORE, work.hotspot(pos), evaluator,
                        () -> cell.get() == BEFORE, after -> {
                            writes.incrementAndGet();
                            cell.set(after);
                            return true;
                        }, state -> work.setHotspot(pos, state));
        var result = work.commitOnce(42, pos, commit).orElseThrow();
        assertSame(AFTER, result.mixture());
        assertEquals(List.of(event), result.events());
        assertEquals(RESULT.speciesDelta(), result.speciesDelta());
        assertEquals(100, result.energyDeltaJoules());
        assertSame(next, work.hotspot(pos));
        assertTrue(work.commitOnce(42, pos, commit).isEmpty());
        assertEquals(1, evaluations.get());
        assertEquals(1, writes.get());
    }

    @Test
    void failedWriterAndStalePreflightNeverPublishHotspotOrEvents() {
        BlockPos pos = new BlockPos(2, 64, 3);
        ReactionWorkQueue work = new ReactionWorkQueue();
        HotspotKernel.HotspotState old = new HotspotKernel.HotspotState(.3, 900);
        work.setHotspot(pos, Optional.of(old));
        var step = new GasReactionStep.Result(AFTER, RESULT.speciesDelta(), 100,
                List.of(), Optional.of(new HotspotKernel.HotspotState(.5, 1200)), true);
        for (boolean preflight : new boolean[] {false, true}) {
            AtomicInteger writes = new AtomicInteger();
            assertTrue(AtmosphereService.commitReactionStep(BEFORE, work.hotspot(pos), before -> step,
                    () -> preflight, after -> { writes.incrementAndGet(); return false; },
                    state -> work.setHotspot(pos, state)).isEmpty());
            assertEquals(preflight ? 1 : 0, writes.get());
            assertSame(old, work.hotspot(pos));
        }
        assertTrue(AtmosphereService.commitReactionStep(BEFORE, work.hotspot(pos), before -> step,
                () -> true, after -> { throw new IllegalStateException("writer failure"); },
                state -> work.setHotspot(pos, state)).isEmpty());
        assertSame(old, work.hotspot(pos));
    }

    @Test
    void verifiedNoOpQuenchClearsTransientStateWithoutWritingAndUnloadDropsIt() {
        BlockPos pos = new BlockPos(2, 64, 3);
        ReactionWorkQueue work = new ReactionWorkQueue();
        HotspotKernel.HotspotState old = new HotspotKernel.HotspotState(.3, 900);
        work.setHotspot(pos, Optional.of(old));
        var quench = new GasReactionStep.Result(BEFORE, Map.of(), 0, List.of(), Optional.empty(), false);
        assertTrue(AtmosphereService.commitReactionStep(BEFORE, old, before -> quench,
                () -> false, after -> { fail("no write"); return true; },
                state -> work.setHotspot(pos, state)).isEmpty());
        assertSame(old, work.hotspot(pos));
        assertTrue(AtmosphereService.commitReactionStep(BEFORE, old, before -> quench,
                () -> true, after -> { fail("no write"); return true; },
                state -> work.setHotspot(pos, state)).isEmpty());
        assertNull(work.hotspot(pos));
        work.setHotspot(pos, Optional.of(old));
        work.removeChunk(new net.minecraft.world.level.ChunkPos(pos));
        assertNull(work.hotspot(pos));
    }
}
