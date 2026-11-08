package com.juicyslew.moonstation14.gametest.support;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.world.phys.AABB;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FixtureChunkTicketsTest {
    @Test void publicAcquireReleasesEachOverlappingLeaseClaimWithoutUnforcingSharedAnchorEarly() {
        var level = mock(ServerLevel.class);
        var chunkSource = mock(ServerChunkCache.class);
        var forced = new it.unimi.dsi.fastutil.longs.LongOpenHashSet();
        when(level.getChunkSource()).thenReturn(chunkSource);
        when(level.getChunk(anyInt(), anyInt())).thenReturn(mock(LevelChunk.class));
        when(level.getForcedChunks()).thenReturn(forced);
        doAnswer(call -> {
            long pos = new ChunkPos(call.getArgument(0), call.getArgument(1)).toLong();
            if (call.getArgument(2)) forced.add(pos); else forced.remove(pos);
            return true;
        }).when(level).setChunkForced(anyInt(), anyInt(), anyBoolean());
        var info = mock(GameTestInfo.class);
        when(info.getLevel()).thenReturn(level);
        var helper = new GameTestHelper(info);

        var bounds = new AABB(1, 1, 1, 2, 2, 2);
        var first = FixtureChunkTickets.acquire(helper, level, bounds);
        var second = FixtureChunkTickets.acquire(helper, level, bounds);
        var anchor = first.chunkPositions().iterator().next();
        assertTrue(first.levelAwake());
        first.close();
        assertTrue(first.isClosed());
        assertTrue(first.wakeAnchorClaimReleased());
        assertTrue(second.levelAwake());
        assertTrue(forced.contains(anchor.toLong()));
        verify(level, never()).setChunkForced(anyInt(), anyInt(), eq(false));

        second.close();
        second.close();
        assertTrue(second.wakeAnchorClaimReleased());
        assertFalse(forced.contains(anchor.toLong()));
        verify(level, times(1)).setChunkForced(anyInt(), anyInt(), eq(false));
    }

    @Test void overlappingLeasesShareOneAnchorAndOnlyLastReleaseUnforcesIt() {
        var level = mock(ServerLevel.class);
        var forced = new it.unimi.dsi.fastutil.longs.LongOpenHashSet();
        when(level.getForcedChunks()).thenReturn(forced);
        doAnswer(call -> { long pos = new ChunkPos(call.getArgument(0), call.getArgument(1)).toLong();
            if (call.getArgument(2)) forced.add(pos); else forced.remove(pos); return true;
        }).when(level).setChunkForced(anyInt(), anyInt(), anyBoolean());
        var footprint = Set.of(new ChunkPos(2, 3));

        var first = FixtureChunkTickets.acquireWakeAnchor(level, footprint);
        var second = FixtureChunkTickets.acquireWakeAnchor(level, footprint);
        assertTrue(first.owns());
        assertTrue(first.active());
        verify(level, times(1)).setChunkForced(anyInt(), anyInt(), eq(true));
        first.close();
        assertTrue(second.active());
        verify(level, never()).setChunkForced(anyInt(), anyInt(), eq(false));
        second.close();
        second.close();
        assertFalse(second.active());
        verify(level, times(1)).setChunkForced(anyInt(), anyInt(), eq(false));
    }

    @Test void preforcedAnchorIsNeverReleasedByFixtureClaim() {
        var level = mock(ServerLevel.class);
        var forced = new it.unimi.dsi.fastutil.longs.LongOpenHashSet();
        var chunk = new ChunkPos(0, 0);
        forced.add(chunk.toLong());
        when(level.getForcedChunks()).thenReturn(forced);
        var claim = FixtureChunkTickets.acquireWakeAnchor(level, Set.of(chunk));
        assertFalse(claim.owns());
        claim.close();
        assertTrue(forced.contains(chunk.toLong()));
        verify(level, never()).setChunkForced(anyInt(), anyInt(), anyBoolean());
    }

    @Test void cleanupPrecedesReleaseAndBothRunOnlyOnceAcrossRepeatedClose() {
        var events = new ArrayList<String>();
        var action = new FixtureChunkTickets.LeaseCloseAction(() -> events.add("cleanup"), () -> events.add("release"));

        action.close();
        action.close();

        assertEquals(List.of("cleanup", "release"), events);
    }

    @Test void cleanupFailureStillReleasesAndPropagatesOriginalFailure() {
        var events = new ArrayList<String>();
        var cleanupFailure = new IllegalStateException("cleanup");
        var action = new FixtureChunkTickets.LeaseCloseAction(() -> {
            events.add("cleanup");
            throw cleanupFailure;
        }, () -> events.add("release"));

        assertSame(cleanupFailure, assertThrows(IllegalStateException.class, action::close));
        action.close();

        assertEquals(List.of("cleanup", "release"), events);
    }

    @Test void releaseFailureIsSuppressedOnCleanupFailure() {
        var cleanupFailure = new IllegalStateException("cleanup");
        var releaseFailure = new IllegalArgumentException("release");
        var action = new FixtureChunkTickets.LeaseCloseAction(() -> { throw cleanupFailure; }, () -> { throw releaseFailure; });

        assertSame(cleanupFailure, assertThrows(IllegalStateException.class, action::close));
        assertArrayEquals(new Throwable[]{releaseFailure}, cleanupFailure.getSuppressed());
    }
}
