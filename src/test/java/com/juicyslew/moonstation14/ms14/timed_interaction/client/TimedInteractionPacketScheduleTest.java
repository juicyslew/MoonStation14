package com.juicyslew.moonstation14.ms14.timed_interaction.client;

import com.juicyslew.moonstation14.ms14.timed_interaction.TimedInteractionSnapshot;
import com.juicyslew.moonstation14.ms14.timed_interaction.TimedOperation;
import com.juicyslew.moonstation14.ms14.testsupport.DeterministicPacketScheduler;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TimedInteractionPacketScheduleTest {
    private static final ResourceLocation DIM = ResourceLocation.withDefaultNamespace("overworld");
    private static final UUID BODY = UUID.randomUUID();
    private static TimedInteractionSnapshot snap(long revision, long sequence, UUID body, TimedOperation.Phase phase) {
        return new TimedInteractionSnapshot(DIM, 7, body, revision, revision,
                List.of(new TimedInteractionSnapshot.View(sequence, 0, 20, phase,
                        phase == TimedOperation.Phase.ACTIVE ? -1 : revision, sequence,
                        Optional.of(new BlockPos(1, 2, 3)))));
    }

    @Test void productionOwnerReconcilesPredictionLatencyOrderingAndRefresh() {
        for (long rtt : List.of(0L, 100L, 200L, 300L)) {
            var owner = new TimedInteractionReconciler();
            var scheduler = new DeterministicPacketScheduler<TimedInteractionSnapshot>();
            owner.predictStart(9, BODY, DIM, 20, 0);
            assertEquals(0, owner.provisionalView(BODY, 0).get(9L).elapsedTicks());
            scheduler.send(rtt / 2, snap(1, 9, BODY, TimedOperation.Phase.ACTIVE));
            scheduler.send(rtt, snap(2, 9, BODY, TimedOperation.Phase.COMPLETED));
            var got = new java.util.ArrayList<TimedInteractionReconciler.Acceptance>();
            scheduler.advance(rtt / 2, packet -> {
                var result = owner.accept(packet, DIM, BODY, BODY, DIM);
                assertTrue(result.accepted());
                assertEquals(packet.views().getFirst().phase() == TimedOperation.Phase.COMPLETED, result.refreshInventory());
                got.add(result);
            });
            if (rtt == 0) {
                assertEquals(2, got.size());
                assertEquals(2, owner.snapshots().get(7).revision());
                assertTrue(owner.provisionalView(BODY, 1).isEmpty());
            } else {
                scheduler.advance(rtt, packet -> got.add(owner.accept(packet, DIM, BODY, BODY, DIM)));
                assertEquals(2, got.size());
                assertTrue(got.getLast().refreshInventory());
                assertTrue(owner.provisionalView(BODY, rtt).isEmpty());
            }
        }
    }

    @Test void actualOwnerRejectsDuplicatesStaleForeignAndWrongContextAndClears() {
        var owner = new TimedInteractionReconciler();
        var scheduler = new DeterministicPacketScheduler<TimedInteractionSnapshot>();
        var active = snap(3, 4, BODY, TimedOperation.Phase.ACTIVE);
        owner.predictStart(4, BODY, DIM, 20, 0);
        scheduler.duplicate(10, active);
        var results = new java.util.ArrayList<TimedInteractionReconciler.Acceptance>();
        scheduler.advance(10, packet -> results.add(owner.accept(packet, DIM, BODY, BODY, DIM)));
        assertTrue(results.getFirst().accepted());
        assertTrue(results.getLast().accepted());
        assertFalse(results.getLast().refreshInventory());
        assertFalse(owner.accept(snap(2, 4, BODY, TimedOperation.Phase.ACTIVE), DIM, BODY, BODY, DIM).accepted());
        assertFalse(owner.accept(snap(4, 4, UUID.randomUUID(), TimedOperation.Phase.COMPLETED), DIM, BODY, BODY, DIM).accepted());
        assertFalse(owner.accept(snap(4, 4, BODY, TimedOperation.Phase.COMPLETED),
                ResourceLocation.withDefaultNamespace("the_nether"), BODY, BODY, DIM).accepted());
        var foreignRefreshContext = new TimedInteractionReconciler();
        assertFalse(foreignRefreshContext.accept(snap(4, 4, BODY, TimedOperation.Phase.COMPLETED),
                DIM, null, BODY, ResourceLocation.withDefaultNamespace("the_nether")).refreshInventory());
        assertTrue(owner.provisionalView(BODY, 1).isEmpty(), "matching active snapshot acknowledges provisional progress");
        assertTrue(owner.accept(snap(4, 4, BODY, TimedOperation.Phase.COMPLETED), DIM, BODY, BODY, DIM).refreshInventory());
        assertFalse(owner.accept(snap(5, 4, BODY, TimedOperation.Phase.COMPLETED), DIM, BODY, BODY, DIM).refreshInventory());
        owner.clear();
        assertTrue(owner.snapshots().isEmpty());
        assertTrue(owner.provisionalView(BODY, 2).isEmpty());
    }

    @Test void dropsReorderingPredictionExpiryAndDimensionCorrelationAreOwned() {
        var owner = new TimedInteractionReconciler();
        var scheduler = new DeterministicPacketScheduler<TimedInteractionSnapshot>();
        owner.predictStart(21, BODY, DIM, 20, 0);
        var active = snap(1, 21, BODY, TimedOperation.Phase.ACTIVE);
        scheduler.send(50, active);
        scheduler.dropNext();
        scheduler.advance(50, packet -> owner.accept(packet, DIM, BODY, BODY, DIM));
        assertEquals(1, owner.provisionalView(BODY, 50).size());
        owner.tick(81, BODY);
        assertTrue(owner.provisionalView(BODY, 81).isEmpty());

        owner.predictStart(22, BODY, ResourceLocation.withDefaultNamespace("the_nether"), 20, 100);
        assertTrue(owner.accept(snap(2, 22, BODY, TimedOperation.Phase.ACTIVE), DIM, BODY, BODY, DIM).accepted());
        assertEquals(1, owner.provisionalView(BODY, 101).size());
        owner.tick(102, UUID.randomUUID());
        assertTrue(owner.provisionalView(BODY, 102).isEmpty());

        owner.predictStart(23, BODY, DIM, 20, 103);
        owner.clearPredictions();
        assertTrue(owner.provisionalView(BODY, 104).isEmpty());
        owner.predictStart(24, BODY, DIM, 20, 105);
        owner.clear();
        assertTrue(owner.snapshots().isEmpty());
        assertTrue(owner.provisionalView(BODY, 106).isEmpty());

        var reordered = new TimedInteractionReconciler();
        var packets = new DeterministicPacketScheduler<TimedInteractionSnapshot>();
        packets.send(20, snap(4, 30, BODY, TimedOperation.Phase.COMPLETED));
        packets.send(10, snap(3, 30, BODY, TimedOperation.Phase.ACTIVE));
        var accepted = new java.util.ArrayList<TimedInteractionReconciler.Acceptance>();
        packets.advance(10, packet -> accepted.add(reordered.accept(packet, DIM, BODY, BODY, DIM)));
        packets.advance(20, packet -> accepted.add(reordered.accept(packet, DIM, BODY, BODY, DIM)));
        assertTrue(accepted.getFirst().accepted());
        assertTrue(accepted.getLast().accepted());
        assertTrue(accepted.getLast().refreshInventory());
        assertFalse(reordered.accept(snap(3, 30, BODY, TimedOperation.Phase.ACTIVE), DIM, BODY, BODY, DIM).accepted());
    }
}
