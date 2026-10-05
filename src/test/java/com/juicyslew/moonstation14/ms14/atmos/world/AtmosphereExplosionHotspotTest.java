package com.juicyslew.moonstation14.ms14.atmos.world;

import com.juicyslew.moonstation14.ms14.atmos.reaction.ExplosionHotspotExposure;
import com.juicyslew.moonstation14.ms14.atmos.reaction.HotspotState;
import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import java.util.Optional;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AtmosphereExplosionHotspotTest {
    private static ExplosionHotspotExposure.Decision offer(double temperature, double fraction) {
        return new ExplosionHotspotExposure.Decision(true, temperature, fraction);
    }

    @Test void serviceExplosionOfferUsesUnscaledInputLitersBeforeKernelMultiplier() {
        GasMixture gas = new GasMixture(Map.of(GasType.OXYGEN, 1.0, GasType.TRITIUM, 1.0), 300);
        var decision = ExplosionHotspotExposure.decide(gas, 800, 2, 0, 0);
        assertEquals(0.8, decision.exposedVolumeLiters());
        HotspotState state = AtmosphereService.explosionHotspotState(gas, null, decision, false).orElseThrow();
        assertEquals(20.0, state.volumeLiters(), 1e-8,
                "the service passes 0.8 L input and the kernel applies the ×25 factor exactly once");
    }

    @Test void quenchingHotspotAlsoClearsPendingExposureAndBurning() {
        var queue = new ReactionWorkQueue();
        BlockPos pos = new BlockPos(2, 64, 3);
        queue.setHotspot(pos, Optional.of(new HotspotState(200, 900, false, false)));
        assertTrue(queue.offerExposure(pos, offer(900, .2), 11));
        queue.setBurning(pos, 4);

        assertTrue(queue.quenchIfPresent(pos));
        assertNull(queue.hotspot(pos));
        assertFalse(queue.hasExposure(pos));
        assertEquals(0, queue.exposureCount());
        assertEquals(0, queue.fireIntensity(pos));
        assertFalse(queue.quenchIfPresent(pos));
    }

    @Test void futureExposureStaysQueuedUntilItsDueStep() {
        var queue = new ReactionWorkQueue();
        BlockPos pos = new BlockPos(2, 64, 3);
        assertTrue(queue.offerExposure(pos, offer(900, .2), 11));
        assertEquals(pos, queue.poll());
        assertTrue(queue.exposureIsFuture(pos, 10));
        assertEquals(0, queue.expireExposures(10));
        assertNull(queue.exposure(pos, 10));
        queue.offer(pos); // scheduler's future-only requeue
        assertEquals(pos, queue.poll());
        assertFalse(queue.exposureIsFuture(pos, 11));
        assertEquals(offer(900, .2), queue.exposure(pos, 11));
        queue.consumeExposure(pos, 11);
        assertFalse(queue.hasExposure(pos));
        assertNull(queue.exposure(pos, 12));
    }

    @Test void blastBacklogSurvivesManyDueStepsAndEachCellConsumesOnlyOnce() {
        var queue = new ReactionWorkQueue();
        BlockPos last = new BlockPos(255, 64, 0);
        for (int i = 0; i < 256; i++)
            assertTrue(queue.offerExposure(new BlockPos(i, 64, 0), offer(900, .2), 11));

        for (long step = 11; step < 75; step++) {
            assertEquals(0, queue.expireExposures(step));
            for (int i = 0; i < 8; i++) {
                BlockPos pos = queue.poll();
                if (pos == null) break;
                assertEquals(offer(900, .2), queue.exposure(pos, step));
                queue.consumeExposure(pos, step); // successful gas commit
                assertNull(queue.exposure(pos, step));
            }
            if (step == 31) {
                assertEquals(offer(900, .2), queue.exposure(last, step));
                assertEquals(offer(900, .2), queue.exposure(last, 43));
            }
        }
        assertEquals(0, queue.exposureCount());
    }

    @Test void failedAttemptCanRetryOnLaterStepButNotAfterExpiry() {
        var queue = new ReactionWorkQueue();
        BlockPos pos = new BlockPos(2, 64, 3);
        assertTrue(queue.offerExposure(pos, offer(900, .2), 11));
        for (long step = 11; step < 11 + ReactionWorkQueue.EXPOSURE_TTL_DUE_STEPS; step++) {
            assertEquals(0, queue.expireExposures(step));
            assertEquals(pos, queue.poll());
            assertEquals(offer(900, .2), queue.exposure(pos, step));
            queue.offer(pos); // scheduler retry after a rejected/no-op commit
        }
        assertEquals(1, queue.expireExposures(11 + ReactionWorkQueue.EXPOSURE_TTL_DUE_STEPS));
        assertNull(queue.exposure(pos, 11 + ReactionWorkQueue.EXPOSURE_TTL_DUE_STEPS));
    }

    @Test void repeatedOffersMergeIndependentStrongestFieldsWithoutPostponing() {
        var queue = new ReactionWorkQueue();
        BlockPos pos = new BlockPos(2, 64, 3);
        assertTrue(queue.offerExposure(pos, offer(900, .1), 11));
        assertTrue(queue.offerExposure(pos, offer(800, .7), 12));
        assertEquals(offer(900, .7), queue.exposure(pos, 11));
        assertFalse(queue.exposureIsFuture(pos, 11));
        assertTrue(queue.offerExposure(pos, offer(1200, .2), 11));
        assertEquals(offer(1200, .7), queue.exposure(pos, 11));
        assertEquals(offer(1200, .7), queue.exposure(pos, 43));
        queue.consumeExposure(pos, 43);
        assertNull(queue.exposure(pos, 43));
    }

    @Test void staleEntriesExpireAndReleaseBoundedAdmissionCapacity() {
        var queue = new ReactionWorkQueue();
        for (int i = 0; i < 256; i++)
            assertTrue(queue.offerExposure(new BlockPos(i, 64, 0), offer(900, .1), 3));
        BlockPos extra = new BlockPos(1000, 64, 0);
        assertFalse(queue.offerExposure(extra, offer(900, .1), 3));
        assertEquals(0, queue.expireExposures(4));
        assertFalse(queue.offerExposure(extra, offer(900, .1), 4));
        assertEquals(0, queue.expireExposures(3 + ReactionWorkQueue.EXPOSURE_TTL_DUE_STEPS - 1));
        assertEquals(256, queue.expireExposures(3 + ReactionWorkQueue.EXPOSURE_TTL_DUE_STEPS));
        assertEquals(0, queue.exposureCount());
        assertTrue(queue.offerExposure(extra, offer(900, .1), 3 + ReactionWorkQueue.EXPOSURE_TTL_DUE_STEPS));
    }

    @Test void unloadingChunkDropsPendingExposure() {
        var queue = new ReactionWorkQueue();
        BlockPos pos = new BlockPos(34, 64, 3);
        assertTrue(queue.offerExposure(pos, offer(900, .1), 11));
        queue.removeChunk(new ChunkPos(pos));
        assertFalse(queue.hasExposure(pos));
        assertEquals(0, queue.exposureCount());
    }
}
