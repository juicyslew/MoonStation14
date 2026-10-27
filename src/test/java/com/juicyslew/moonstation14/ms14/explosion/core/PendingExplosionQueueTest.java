package com.juicyslew.moonstation14.ms14.explosion.core;

import com.juicyslew.moonstation14.ms14.explosion.prototype.ExplosiveSourceData;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PendingExplosionQueueTest {
    private static final ResourceLocation DIM = ResourceLocation.withDefaultNamespace("overworld");
    private static final ResourceLocation TYPE = ResourceLocation.withDefaultNamespace("default");
    private static PendingExplosionQueue.Request request(int x, double total, double slope, double cap) {
        return new PendingExplosionQueue.Request(DIM, new BlastPlanner.Pos(x, 0, 0),
                new ExplosiveSourceData(total, slope, cap, TYPE), 1);
    }
    @Test void mergePreservesFirstPayloadExceptIntensityAndDequeuedIsNoLongerMergeable() {
        var queue = new PendingExplosionQueue();
        assertEquals(PendingExplosionQueue.Status.ACCEPTED, queue.add(request(0, 2, 3, 10)).status());
        assertEquals(PendingExplosionQueue.Status.MERGED, queue.add(request(1, 4, 8, 20)).status());
        var merged = queue.poll();
        assertEquals(6, merged.source().totalIntensity());
        assertEquals(3, merged.source().intensitySlope());
        assertEquals(10, merged.source().maxIntensity());
        assertEquals(PendingExplosionQueue.Status.ACCEPTED, queue.add(request(0, 1, 3, 10)).status());
    }
    @Test void overflowRejectsWithoutChangingPendingRequest() {
        var queue = new PendingExplosionQueue();
        queue.add(request(0, 1_000_000, 3, 10));
        assertEquals(PendingExplosionQueue.Status.REJECTED, queue.add(request(0, 1, 8, 20)).status());
        assertEquals(1, queue.size());
        assertEquals(1_000_000, queue.snapshot().get(0).source().totalIntensity());
    }
    @Test void firstMatchingInsertionOrderAndExactThreeDimensionalBoundary() {
        var queue = new PendingExplosionQueue();
        queue.add(request(0, 2, 3, 10));
        queue.add(new PendingExplosionQueue.Request(DIM, new BlastPlanner.Pos(0, 0, 0),
                new ExplosiveSourceData(3, 4, 9, ResourceLocation.withDefaultNamespace("other")), 1));
        queue.add(new PendingExplosionQueue.Request(ResourceLocation.withDefaultNamespace("nether"), new BlastPlanner.Pos(0, 0, 0),
                new ExplosiveSourceData(3, 4, 9, TYPE), 1));
        queue.add(new PendingExplosionQueue.Request(DIM, new BlastPlanner.Pos(0, 2, 0),
                new ExplosiveSourceData(4, 8, 20, TYPE), 50)); // Y is deliberately part of local 3D distance.
        assertEquals(PendingExplosionQueue.Status.MERGED, queue.add(request(1, 5, 9, 20)).status());
        assertEquals(4, queue.size());
        assertEquals(7, queue.snapshot().get(0).source().totalIntensity());
        assertEquals(3, queue.snapshot().get(0).source().intensitySlope());
        assertEquals(10, queue.snapshot().get(0).source().maxIntensity());
        assertEquals(3, queue.snapshot().get(1).source().totalIntensity());
        assertEquals(3, queue.snapshot().get(2).source().totalIntensity());
        assertEquals(4, queue.snapshot().get(3).source().totalIntensity());
    }
    @Test void zeroThresholdMergesOnlyCoincidentAndPollRemovesBeforeNextAdmission() {
        var queue = new PendingExplosionQueue();
        var zero = new PendingExplosionQueue.Request(DIM, new BlastPlanner.Pos(2, 0, 0),
                new ExplosiveSourceData(2, 3, 10, TYPE), 0);
        queue.add(zero);
        assertEquals(PendingExplosionQueue.Status.ACCEPTED, queue.add(request(3, 2, 3, 10)).status());
        assertEquals(2, queue.size());
        var active = queue.poll();
        assertEquals(PendingExplosionQueue.Status.ACCEPTED, queue.add(new PendingExplosionQueue.Request(DIM,
                new BlastPlanner.Pos(5, 0, 0), new ExplosiveSourceData(1, 3, 10, TYPE), 1)).status());
        assertEquals(2, queue.size());
        assertEquals(2, active.source().totalIntensity());
    }
}
