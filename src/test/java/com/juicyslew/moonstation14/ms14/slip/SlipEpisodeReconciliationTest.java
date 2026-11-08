package com.juicyslew.moonstation14.ms14.slip;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SlipEpisodeReconciliationTest {
    @Test
    void sharedOwnerUsesFrameAwareIdentityAndActorLifecycleClearsEpisodes() {
        ServerLevel level = mock(ServerLevel.class);
        when(level.getGameTime()).thenReturn(42L);
        UUID actor = UUID.randomUUID();
        UUID frame = UUID.randomUUID();
        BlockPos source = new BlockPos(1, 2, 3);

        assertTrue(StepContactEpisodes.latchPuddle(level, actor, null, source));
        assertTrue(StepContactEpisodes.latchPuddle(level, actor, frame, source));
        assertFalse(StepContactEpisodes.latchPuddle(level, actor, null, source));
        assertTrue(StepContactEpisodes.containsPuddle(level, actor, null, source));
        assertTrue(StepContactEpisodes.containsPuddle(level, actor, frame, source));

        StepContactEpisodes.clearActor(level, actor);

        assertFalse(StepContactEpisodes.containsPuddle(level, actor, null, source));
        assertFalse(StepContactEpisodes.containsPuddle(level, actor, frame, source));
    }

    @Test
    void observedContactsProveRemovalAndOwnershipButUnknownDoesNotProveExit() {
        ServerLevel level = mock(ServerLevel.class);
        when(level.getGameTime()).thenReturn(42L);
        Entity actorEntity = mock(Entity.class);
        UUID actor = UUID.randomUUID();
        when(actorEntity.getUUID()).thenReturn(actor);
        BlockPos source = new BlockPos(1, 2, 3);
        UUID frame = UUID.randomUUID();
        UUID otherFrame = UUID.randomUUID();
        AABB bounds = new AABB(0, 0, 0, 1, 2, 1);

        assertTrue(StepContactEpisodes.latchPuddle(level, actor, frame, source));
        StepContactEpisodes.reconcile(level, actorEntity, bounds,
                SharedPuddleContactQuery.unavailable(otherFrame));
        assertTrue(StepContactEpisodes.containsPuddle(level, actor, frame, source));

        var sameFrameContact = new SharedPuddleContactQuery.Contact(mock(com.juicyslew.moonstation14.block.block_entity.PuddleBlockEntity.class), source, frame, bounds, bounds, 1d);
        StepContactEpisodes.reconcile(level, actorEntity, bounds,
                new SharedPuddleContactQuery.Result(SharedPuddleContactQuery.State.AVAILABLE, frame, java.util.List.of(sameFrameContact)));
        assertTrue(StepContactEpisodes.containsPuddle(level, actor, frame, source));

        var worldContact = new SharedPuddleContactQuery.Contact(mock(com.juicyslew.moonstation14.block.block_entity.PuddleBlockEntity.class), source, null, bounds, bounds, 1d);
        StepContactEpisodes.reconcile(level, actorEntity, bounds,
                new SharedPuddleContactQuery.Result(SharedPuddleContactQuery.State.AVAILABLE, null, java.util.List.of(worldContact)));
        assertFalse(StepContactEpisodes.containsPuddle(level, actor, frame, source));
        assertTrue(StepContactEpisodes.latchPuddle(level, actor, frame, source));
        StepContactEpisodes.reconcile(level, actorEntity, bounds,
                new SharedPuddleContactQuery.Result(SharedPuddleContactQuery.State.AVAILABLE, otherFrame, java.util.List.of()));
        assertFalse(StepContactEpisodes.containsPuddle(level, actor, frame, source));
        assertTrue(StepContactEpisodes.latchPuddle(level, actor, null, source));
        StepContactEpisodes.reconcile(level, actorEntity, bounds,
                new SharedPuddleContactQuery.Result(SharedPuddleContactQuery.State.AVAILABLE, null, java.util.List.of()));
        assertFalse(StepContactEpisodes.containsPuddle(level, actor, null, source));
    }
}
