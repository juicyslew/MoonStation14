package com.juicyslew.moonstation14.ms14.slip;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SlipEpisodeReconciliationTest {
    @Test
    void unknownPreservesLatchKnownEmptyExitRearmsAndFrameIsPartOfIdentity() {
        UUID frameA = UUID.randomUUID(), frameB = UUID.randomUUID();
        BlockPos source = new BlockPos(1, 2, 3);
        var keyA = new SlipSystem.SourceKey(frameA, source);
        var keyB = new SlipSystem.SourceKey(frameB, source);
        var episodes = new HashSet<SlipSystem.SourceKey>();

        episodes.add(keyA); // CONTACT
        assertEquals(1, SlipSystem.reconcileEpisodeKeys(episodes,
                new SharedPuddleContactQuery.Result(SharedPuddleContactQuery.State.UNAVAILABLE, frameA, List.of())));
        assertEquals(1, SlipSystem.reconcileEpisodeKeys(episodes,
                new SharedPuddleContactQuery.Result(SharedPuddleContactQuery.State.UNSUPPORTED, frameA, List.of())));
        episodes.add(keyA); // CONTACT again: existing latch rejects duplicate
        assertEquals(1, episodes.size());

        SlipSystem.reconcileEpisodeKeys(episodes,
                new SharedPuddleContactQuery.Result(SharedPuddleContactQuery.State.AVAILABLE, frameA, List.of()));
        assertTrue(episodes.isEmpty()); // known empty is an exit
        episodes.add(keyA); // subsequent CONTACT can arm again
        assertEquals(1, episodes.size());

        episodes.add(keyB);
        assertEquals(2, episodes.size()); // same position in distinct frames is distinct
        SlipSystem.reconcileEpisodeKeys(episodes,
                new SharedPuddleContactQuery.Result(SharedPuddleContactQuery.State.AVAILABLE, frameA, List.of()));
        assertTrue(episodes.isEmpty());
    }
}
