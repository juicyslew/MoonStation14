package com.juicyslew.moonstation14.ms14.atmos.world;

import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import com.juicyslew.moonstation14.ms14.atmos.reaction.GasReactionData;
import com.juicyslew.moonstation14.ms14.atmos.reaction.GasReactionStep;
import com.juicyslew.moonstation14.ms14.atmos.reaction.HotspotKernel;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeCatalog;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ReactionWorkQueueTest {
    private static final BlockPos COLD = new BlockPos(0, 64, 0);

    @Test void burningCoordinatesAreChunkScopedAndLocallyNavigable() {
        ReactionWorkQueue queue = new ReactionWorkQueue();
        BlockPos first = new BlockPos(1, 64, 2), otherChunk = new BlockPos(17, 64, 2);
        queue.setBurning(first, 40);
        queue.setBurning(otherChunk, 80);
        var cursor = new AtmosphereChunkData.CellPosition(0, 64, 2);
        assertEquals(new AtmosphereChunkData.CellPosition(1, 64, 2),
                queue.nextBurningAfter(new ChunkPos(first), cursor).orElseThrow());
        assertEquals(new AtmosphereChunkData.CellPosition(1, 64, 2),
                queue.nextBurningAfter(new ChunkPos(otherChunk), cursor).orElseThrow());
        assertEquals(40, queue.fireIntensity(first));
        assertEquals(80, queue.fireIntensity(otherChunk));
        queue.removeChunk(new ChunkPos(first));
        assertEquals(0, queue.fireIntensity(first));
        assertEquals(80, queue.fireIntensity(otherChunk));
    }

    @Test void deduplicatesAndRoundRobinsChunks() {
        ReactionWorkQueue queue = new ReactionWorkQueue();
        BlockPos other = new BlockPos(16, 64, 0);
        queue.offer(COLD);
        queue.offer(COLD);
        queue.offer(other);
        queue.offer(new BlockPos(0, 65, 0));
        assertEquals(2, List.of(queue.poll(), queue.poll()).stream().distinct().count());
        assertEquals(new BlockPos(0, 65, 0), queue.poll());
        assertNull(queue.poll());
    }

    @Test void overflowRetainsEveryCellAndServicesOtherChunks() {
        ReactionWorkQueue queue = new ReactionWorkQueue();
        for (int i = 0; i < ReactionWorkQueue.ACTIVE_CAPACITY + 80; i++)
            queue.offer(new BlockPos(i % 16, i / 16, 0));
        BlockPos other = new BlockPos(32, 70, 0);
        queue.offer(other);
        assertEquals(ReactionWorkQueue.ACTIVE_CAPACITY, queue.activeSize());
        assertEquals(81, queue.backlogSize());
        boolean sawOther = false;
        int count = 0;
        for (BlockPos pos; (pos = queue.poll()) != null;) {
            count++;
            if (pos.equals(other)) sawOther = true;
        }
        assertTrue(sawOther);
        assertEquals(ReactionWorkQueue.ACTIVE_CAPACITY + 81, count);
        assertEquals(0, queue.backlogSize());
    }

    @Test void requeueIsDeferredByCallerAndIdleQueueDoesNotPoll() {
        ReactionWorkQueue queue = new ReactionWorkQueue();
        assertNull(queue.poll());
        queue.offer(COLD);
        assertEquals(COLD, queue.poll());
        assertNull(queue.poll());
        queue.offer(COLD);
        assertEquals(COLD, queue.poll());
    }

    @Test void loadCursorIsIndependentAndUnloadDiscardsBothKindsOfWork() {
        ReactionWorkQueue queue = new ReactionWorkQueue();
        ChunkPos chunk = new ChunkPos(COLD);
        queue.offer(COLD);
        queue.startLoad(chunk);
        assertEquals(chunk, queue.pollLoad());
        var cursor = new AtmosphereChunkData.CellPosition(0, 64, 0);
        queue.continueLoad(chunk, cursor);
        assertEquals(cursor, queue.cursor(chunk));
        assertEquals(chunk, queue.pollLoad());
        queue.removeChunk(chunk);
        assertNull(queue.pollLoad());
        assertNull(queue.poll());
        assertEquals(0, queue.activeSize());
        queue.startLoad(chunk);
        assertNull(queue.cursor(chunk));
    }

    @Test void successfulReactionStampIsSharedAcrossCallersButFailuresCanRetry() {
        ReactionWorkQueue queue = new ReactionWorkQueue();
        AtomicInteger attempts = new AtomicInteger();
        BlockPos other = new BlockPos(17, 64, 0);
        assertTrue(queue.commitOnce(20, COLD, () -> { attempts.incrementAndGet(); return Optional.empty(); }).isEmpty());
        assertEquals("explicit", queue.commitOnce(20, COLD, () -> {
            attempts.incrementAndGet(); return Optional.of("explicit");
        }).orElseThrow());
        assertTrue(queue.commitOnce(20, COLD, () -> { fail("scheduler must not evaluate again"); return Optional.of("bad"); }).isEmpty());
        assertTrue(queue.reacted(20, COLD));
        assertEquals("other", queue.commitOnce(20, other, () -> Optional.of("other")).orElseThrow());
        assertEquals(2, attempts.get());
        assertEquals("next", queue.commitOnce(21, COLD, () -> Optional.of("next")).orElseThrow());
        assertFalse(queue.reacted(21, other));
        queue.removeChunk(new ChunkPos(COLD));
        assertTrue(queue.reacted(21, COLD));
        assertFalse(queue.reacted(22, COLD));
    }

    @Test void oddTickNeverEntersCommitAndNextDueStepSharesStampAfterReload() {
        ReactionWorkQueue queue = new ReactionWorkQueue();
        AtomicInteger writes = new AtomicInteger();
        assertFalse(AtmosphereService.isTickDue(true, 41));
        if (AtmosphereService.isTickDue(true, 41))
            queue.commitOnce(AtmosphereService.reactionDueStep(41), COLD,
                    () -> { writes.incrementAndGet(); return Optional.of(true); });
        assertEquals(0, writes.get());
        assertTrue(AtmosphereService.isTickDue(true, 42));
        long step = AtmosphereService.reactionDueStep(42);
        assertEquals(20, AtmosphereService.reactionDueStep(41));
        assertEquals(21, step);
        assertTrue(queue.commitOnce(step, COLD, () -> {
            writes.incrementAndGet(); return Optional.of(true);
        }).isPresent());
        queue.removeChunk(new ChunkPos(COLD));
        queue.startLoad(new ChunkPos(COLD));
        assertTrue(queue.reacted(step, COLD));
        assertTrue(queue.commitOnce(step, COLD, () -> { fail("reload cannot repeat due pass");
            return Optional.of(true); }).isEmpty());
        assertEquals(1, writes.get());
        assertTrue(queue.commitOnce(AtmosphereService.reactionDueStep(44), COLD,
                () -> Optional.of(true)).isPresent());
    }

    @Test void schedulerQuenchesCooledAndOxygenDepletedCellsBeforeRestock() {
        var fire = new GasReactionData(Map.of(GasType.TRITIUM, .01, GasType.OXYGEN, .01),
                373.149, 2000, 0, -1,
                List.of(new GasReactionData.Effect(GasReactionData.EffectType.TRITIUM_FIRE)));
        var catalog = new PrototypeCatalog<>(Map.of(ResourceLocation.parse("moonstation14:tritium_fire"), fire));
        for (GasMixture quench : List.of(
                new GasMixture(Map.of(GasType.TRITIUM, 1., GasType.OXYGEN, 1.), 300),
                new GasMixture(Map.of(GasType.TRITIUM, 1.), 400))) {
            ReactionWorkQueue queue = new ReactionWorkQueue();
            GasMixture hot = new GasMixture(Map.of(GasType.TRITIUM, 1., GasType.OXYGEN, 1.), 400);
            HotspotKernel.HotspotState grown = new HotspotKernel.HotspotState(.8, 400);
            queue.setHotspot(COLD, Optional.of(grown));
            queue.setBurning(COLD, 128);
            queue.offer(COLD);
            assertEquals(COLD, queue.poll());
            assertFalse(AtmosphereService.schedulerReactionEligible(queue, COLD, quench, catalog.asMap().values()));
            assertNull(queue.hotspot(COLD));
            assertEquals(0, queue.fireIntensity(COLD));
            // Scheduler gate only touches transient state; the supplied mixture is unchanged.
            assertTrue(AtmosphereService.schedulerReactionEligible(queue, COLD, hot, catalog.asMap().values()));
            var fresh = HotspotKernel.evaluate(hot, catalog, queue.hotspot(COLD));
            var stale = HotspotKernel.evaluate(hot, catalog, grown);
            assertEquals(.001, fresh.totalFireExtentMoles(), 1e-12);
            assertTrue(stale.totalFireExtentMoles() > fresh.totalFireExtentMoles());
            var committed = queue.commitOnce(2, COLD, () -> AtmosphereService.commitReactionStep(
                    hot, queue.hotspot(COLD), before -> GasReactionStep.evaluate(catalog, before, queue.hotspot(COLD)),
                    () -> true, after -> true, state -> queue.setHotspot(COLD, state))).orElseThrow();
            assertEquals(fresh.totalFireExtentMoles(), committed.events().getFirst().extentMoles(), 1e-12);
            assertEquals(fresh.nextState().orElseThrow().fraction(), queue.hotspot(COLD).fraction(), 1e-12);
            assertFalse(AtmosphereService.schedulerReactionEligible(queue, COLD, null, catalog.asMap().values()));
            queue.setHotspot(COLD, Optional.of(grown));
            assertFalse(AtmosphereService.schedulerReactionEligible(queue, COLD, null, catalog.asMap().values()));
            assertSame(grown, queue.hotspot(COLD)); // unloaded/unclaimed is not a verified quench
            queue.setHotspot(COLD, Optional.empty());
            assertFalse(AtmosphereService.schedulerReactionEligible(queue, COLD, quench, catalog.asMap().values()));
            assertNull(queue.hotspot(COLD));
        }
    }

    @Test void stampCapacityNeverEvictsAnEarlierSuccessWithinTheTick() {
        ReactionWorkQueue queue = new ReactionWorkQueue();
        for (int i = 0; i < ReactionWorkQueue.MAX_REACTED_CELLS_PER_TICK; i++)
            assertTrue(queue.commitOnce(30, new BlockPos(i % 16, i / 16, 0),
                    () -> Optional.of(true)).isPresent());
        BlockPos excess = new BlockPos(0, ReactionWorkQueue.MAX_REACTED_CELLS_PER_TICK, 0);
        assertTrue(queue.reactionCapacityReached(30));
        assertTrue(queue.commitOnce(30, excess, () -> { fail("at capacity"); return Optional.of(true); }).isEmpty());
        assertTrue(queue.commitOnce(30, COLD, () -> { fail("original stamp retained"); return Optional.of(true); }).isEmpty());
        assertTrue(queue.commitOnce(31, excess, () -> Optional.of(true)).isPresent());
    }

    @Test void loadAttemptsAreBoundedEvenWhenManyChunksHaveNoOverrides() {
        ReactionWorkQueue queue = new ReactionWorkQueue();
        for (int i = 0; i < 100; i++) queue.startLoad(new ChunkPos(i, 0));
        int[] probes = {0};
        assertEquals(32, queue.drainLoads(32, 32, chunk -> {
            assertEquals(probes[0]++, chunk.x);
            return false; // empty or exhausted chunk
        }));
        assertEquals(32, probes[0]);
        assertEquals(32, queue.drainLoads(32, 32, chunk -> {
            assertEquals(probes[0]++, chunk.x);
            return chunk.x == 40; // one real override among empty buckets
        }));
        assertEquals(64, probes[0]);
        assertEquals(32, queue.drainLoads(32, 32, chunk -> {
            assertEquals(probes[0]++, chunk.x);
            return false;
        }));
        assertEquals(4, queue.drainLoads(32, 32, chunk -> {
            assertEquals(probes[0]++, chunk.x);
            return false;
        }));
        assertEquals(100, probes[0]);
    }

    @Test void unfinishedLoadCursorRotatesBehindOtherChunks() {
        ReactionWorkQueue queue = new ReactionWorkQueue();
        ChunkPos first = new ChunkPos(0, 0), second = new ChunkPos(1, 0);
        queue.startLoad(first);
        queue.startLoad(second);
        assertEquals(1, queue.drainLoads(1, 1, chunk -> {
            assertEquals(first, chunk);
            queue.continueLoad(chunk, new AtmosphereChunkData.CellPosition(0, 64, 0));
            return true;
        }));
        assertEquals(1, queue.drainLoads(1, 1, chunk -> {
            assertEquals(second, chunk);
            return false;
        }));
        assertEquals(first, queue.pollLoad());
    }

    @Test void catalogGateIncludesColdProductionAndCoolantButRejectsInert() {
        var production = new GasReactionData(Map.of(GasType.TRITIUM, .01, GasType.OXYGEN, .01,
                GasType.NITROGEN, .01), 2.7, 73.15, 0, 2,
                List.of(new GasReactionData.Effect(GasReactionData.EffectType.FREZON_PRODUCTION)));
        var coolant = new GasReactionData(Map.of(GasType.FREZON, .01), 23.15, 500, 0, 1,
                List.of(new GasReactionData.Effect(GasReactionData.EffectType.FREZON_COOLANT)));
        var hot = new GasReactionData(Map.of(GasType.NITROUS_OXIDE, .01), 850, 2000, 0, 0,
                List.of(new GasReactionData.Effect(GasReactionData.EffectType.N2O_DECOMPOSITION)));
        var catalog = List.of(production, coolant, hot);
        assertTrue(AtmosphereService.potentiallyReactive(new GasMixture(Map.of(GasType.TRITIUM, 1.,
                GasType.OXYGEN, 1., GasType.NITROGEN, 1.), 73.15), catalog));
        assertTrue(AtmosphereService.potentiallyReactive(new GasMixture(Map.of(GasType.FREZON, 1.), 60), catalog));
        assertTrue(AtmosphereService.potentiallyReactive(new GasMixture(Map.of(GasType.NITROUS_OXIDE, 1.), 850), catalog));
        assertFalse(AtmosphereService.potentiallyReactive(new GasMixture(Map.of(GasType.NITROGEN, 1.), 900), catalog));
    }

    @Test void catalogRestartRediscoversPreviouslyInertSavedCellsWithoutMaterializingAmbient() {
        var tracked = new AtmosphereService.LoadedReactionChunks();
        var work = new ReactionWorkQueue();
        ChunkPos savedChunk = new ChunkPos(4, 0);
        tracked.add(savedChunk);
        GasMixture saved = new GasMixture(Map.of(GasType.NITROUS_OXIDE, 1.), 900);
        AtmosphereChunkData data = new AtmosphereChunkData();
        data.claimFinite(0, 64, 0);
        data.put(0, 64, 0, saved, GasMixture.vacuum());
        var newReaction = new GasReactionData(Map.of(GasType.NITROUS_OXIDE, .01), 850, 2000, 0, 0,
                List.of(new GasReactionData.Effect(GasReactionData.EffectType.N2O_DECOMPOSITION)));
        // The initial catalog left this saved override inert and its load cursor exhausted.
        work.startLoad(savedChunk);
        assertEquals(savedChunk, work.pollLoad());
        var initial = data.nextAfter(work.cursor(savedChunk)).orElseThrow();
        assertFalse(AtmosphereService.potentiallyReactive(initial.getValue(), List.of()));
        work.continueLoad(savedChunk, initial.getKey());
        assertEquals(savedChunk, work.pollLoad());
        assertTrue(data.nextAfter(work.cursor(savedChunk)).isEmpty());
        tracked.restart();
        assertEquals(1, tracked.drain(32, work::startLoad));
        assertEquals(savedChunk, work.pollLoad());
        var rediscovered = data.nextAfter(work.cursor(savedChunk)).orElseThrow();
        assertTrue(data.isFiniteClaimed(rediscovered.getKey().x(), rediscovered.getKey().y(), rediscovered.getKey().z()));
        assertTrue(AtmosphereService.potentiallyReactive(rediscovered.getValue(), List.of(newReaction)));
        work.offer(new BlockPos(savedChunk.getMinBlockX(), 64, 0));
        assertEquals(new BlockPos(savedChunk.getMinBlockX(), 64, 0), work.poll());
        assertNull(work.poll()); // no ambient cells were queued
    }

    @Test void catalogTraversalIsBoundedAndUnloadedChunksCannotReenterAfterRestart() {
        var tracked = new AtmosphereService.LoadedReactionChunks();
        for (int i = 0; i < 113; i++) tracked.add(new ChunkPos(i, 0));
        tracked.restart();
        var seen = new ArrayList<ChunkPos>();
        assertEquals(32, tracked.drain(32, seen::add));
        tracked.remove(new ChunkPos(45, 0));
        assertEquals(32, tracked.drain(32, seen::add));
        assertEquals(32, tracked.drain(32, seen::add));
        assertEquals(16, tracked.drain(32, seen::add));
        assertEquals(112, seen.size());
        assertFalse(seen.contains(new ChunkPos(45, 0)));
        assertFalse(tracked.hasPending());
        tracked.restart();
        assertEquals(32, tracked.drain(32, chunk -> {}));
        for (int i = 0; i < 113; i++) tracked.remove(new ChunkPos(i, 0));
        assertEquals(0, tracked.drain(32, chunk -> fail("unloaded chunk revisited")));
        tracked.restart();
        assertFalse(tracked.hasPending());
    }
}
