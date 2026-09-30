package com.juicyslew.moonstation14.ms14.atmos.visual.network;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AtmosphereVisualPendingSnapshotsTest {
    private static final ResourceLocation DIM = ResourceLocation.withDefaultNamespace("overworld");

    @Test void packetBeforeWorldAndOrderedSnapshotFragmentsThenDelta() {
        var pending = new AtmosphereVisualPendingSnapshots();
        var received = new ArrayList<AtmosphereVisualPayload>();
        var first = payload(1, true, false);
        var second = payload(1, false, true);
        var delta = payload(2, false, true);
        pending.stage(first); // no client world/player yet
        pending.stage(second);
        pending.stage(delta);
        assertEquals(0, pending.flush(DIM, ignored -> false, received::add));
        assertEquals(3, pending.flush(DIM, ignored -> true, received::add));
        assertEquals(List.of(first, second, delta), received);
        assertEquals(0, pending.packetCount());
    }

    @Test void dimensionUnloadLogoutAndOverflowAreBounded() {
        var pending = new AtmosphereVisualPendingSnapshots();
        var other = ResourceLocation.withDefaultNamespace("the_nether");
        pending.stage(payload(1, true, true));
        assertEquals(0, pending.flush(other, ignored -> true, ignored -> fail("wrong dimension")));
        pending.unload(DIM, 0, 0);
        assertEquals(0, pending.chunkCount());
        for (int i = 0; i < AtmosphereVisualPendingSnapshots.MAX_PACKETS_PER_CHUNK + 5; i++)
            pending.stage(payload(i + 1, true, true));
        assertEquals(AtmosphereVisualPendingSnapshots.MAX_PACKETS_PER_CHUNK, pending.packetCount());
        pending.clear();
        assertEquals(0, pending.packetCount());
    }

    @Test void overflowRetainsBoundedResyncCandidateAndDimensionAndUnloadClearIt() {
        var pending = new AtmosphereVisualPendingSnapshots();
        for (int i = 0; i < AtmosphereVisualPendingSnapshots.MAX_PACKETS_PER_CHUNK + 1; i++)
            pending.stage(payload(i + 1, true, true));
        var requests = new ArrayList<Long>();
        assertEquals(0, pending.resyncCandidates(ResourceLocation.withDefaultNamespace("the_nether"),
                ignored -> true, requests::add, 2));
        assertEquals(1, pending.resyncCandidates(DIM, ignored -> true, requests::add, 2));
        assertEquals(ChunkPos.asLong(0, 0), requests.get(0));
        pending.unload(DIM, 0, 0);
        assertEquals(0, pending.resyncCandidates(DIM, ignored -> true, requests::add, 2));
    }

    @Test void retainDimensionDropsStagedPacketsAcrossWorldSwitch() {
        var pending = new AtmosphereVisualPendingSnapshots();
        pending.stage(payload(1, true, true));
        pending.retainDimension(ResourceLocation.withDefaultNamespace("the_nether"));
        assertEquals(0, pending.packetCount());
        assertEquals(0, pending.flush(DIM, ignored -> true, ignored -> fail("stale dimension")));
    }

    @Test void orphanFinalDoesNotAcknowledgeButFreshResetPublicationDoes() {
        var pending = overflowedPending();
        var orphanFinal = payload(500, false, true);
        pending.noteReset(orphanFinal);
        pending.acknowledgeApplied(orphanFinal);
        assertEquals(1, requestCount(pending));

        pending.flush(DIM, ignored -> true, ignored -> { });
        var cache = new AtmosphereVisualClientCache();

        var first = payload(501, true, false);
        var last = payload(501, false, true);
        pending.stage(first);
        pending.stage(last);
        assertEquals(2, pending.flush(DIM, ignored -> true, p -> {
            boolean applied = cache.apply(p);
            if (applied) pending.acknowledgeApplied(p);
        }));
        assertEquals(0, requestCount(pending));
    }

    @Test void freshOnePacketResetAcknowledgesButDeltaDoesNot() {
        var pending = overflowedPending();
        var delta = payload(600, false, true);
        pending.noteReset(delta);
        pending.acknowledgeApplied(delta);
        assertEquals(1, requestCount(pending));

        var reset = payload(601, true, true);
        pending.stage(reset);
        var cache = new AtmosphereVisualClientCache();
        if (cache.apply(reset)) pending.acknowledgeApplied(reset);
        assertEquals(0, requestCount(pending));
        assertEquals(0, requestCount(pending)); // subsequent retry tick sends no request
    }

    private static AtmosphereVisualPendingSnapshots overflowedPending() {
        var pending = new AtmosphereVisualPendingSnapshots();
        for (int i = 0; i < AtmosphereVisualPendingSnapshots.MAX_PACKETS_PER_CHUNK + 1; i++)
            pending.stage(payload(i + 1, false, false));
        return pending;
    }

    private static int requestCount(AtmosphereVisualPendingSnapshots pending) {
        var requests = new ArrayList<Long>();
        return pending.resyncCandidates(DIM, ignored -> true, requests::add, 8);
    }

    private static AtmosphereVisualPayload payload(long revision, boolean reset, boolean last) {
        return new AtmosphereVisualPayload(DIM, 0, 0, revision, reset, last, List.of());
    }
}
