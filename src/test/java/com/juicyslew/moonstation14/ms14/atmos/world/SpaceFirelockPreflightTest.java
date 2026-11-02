package com.juicyslew.moonstation14.ms14.atmos.world;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

class SpaceFirelockPreflightTest {
    private static BlockPos root(BlockPos cell) { return cell.getY() == 5 ? cell.below() : cell; }

    @Test void visitsOnlyPatchCandidatesAndDeduplicatesBothDoorCells() {
        BlockPos lower = new BlockPos(3, 4, 5), upper = lower.above(), unrelated = new BlockPos(20, 4, 5);
        AtomicInteger attempts = new AtomicInteger();
        assertFalse(AtmosphereService.preflightSpaceFirelocks(AtmosphereService.PatchKernel.SPACE_FLOW,
                List.of(lower, upper), SpaceFirelockPreflightTest::root,
                candidate -> { assertEquals(lower, candidate); attempts.incrementAndGet(); return false; }));
        assertEquals(1, attempts.get());
        // A door outside the finite patch is never passed to the closer.
        assertFalse(AtmosphereService.preflightSpaceFirelocks(AtmosphereService.PatchKernel.SPACE_FLOW,
                List.of(lower), SpaceFirelockPreflightTest::root,
                candidate -> { assertNotEquals(unrelated, candidate); return false; }));
    }

    @Test void successfulCloseAbortsAndFailedCloseAllowsSpaceKernelPath() {
        BlockPos lower = new BlockPos(3, 4, 5);
        AtomicInteger calls = new AtomicInteger();
        boolean closed = AtmosphereService.preflightSpaceFirelocks(AtmosphereService.PatchKernel.SPACE_FLOW,
                Set.of(lower, lower.above()),
                SpaceFirelockPreflightTest::root, candidate -> { calls.incrementAndGet(); return true; });
        assertTrue(closed, "successful live close signals caller to abort and requeue before kernel/commit");
        assertEquals(1, calls.get());
        assertFalse(AtmosphereService.preflightSpaceFirelocks(AtmosphereService.PatchKernel.SPACE_FLOW,
                Set.of(lower), SpaceFirelockPreflightTest::root,
                candidate -> false), "obstruction/cooldown/no-open result preserves actual Space flow");
    }

    @Test void normalKernelSelectedInSpaceDimensionDoesNotInvokeEmergencyHook() {
        // This is the production classification for a Space dimension without a currently
        // verified exterior boundary face; do not infer the hook from dimension alone.
        var kernel=AtmosphereService.chooseKernelForPatch(Set.of(),true);
        assertEquals(AtmosphereService.PatchKernel.NORMAL_EQUALIZATION,kernel);
        AtomicInteger attempts=new AtomicInteger();
        assertFalse(AtmosphereService.preflightSpaceFirelocks(kernel,List.of(new BlockPos(1,2,3)),
                Function.identity(),root->{attempts.incrementAndGet();return true;}));
        assertEquals(0,attempts.get(),"normal kernel proceeds without emergency closure consideration");
    }
}
