package com.juicyslew.moonstation14.ms14.chat.server;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class LocalSpeechOcclusionTest {
    private static final Vec3 START = new Vec3(0.5, 1.6, 0.5);

    private static final class Room implements LocalSpeechOcclusion.BlockSampler {
        final Map<BlockPos, VoxelShape> blocks = new HashMap<>();
        final Set<BlockPos> unloaded = new HashSet<>();
        final Set<BlockPos> sampled = new HashSet<>();
        int calls;

        Room block(int x, int y, int z, VoxelShape shape) {
            blocks.put(new BlockPos(x, y, z), shape);
            return this;
        }

        @Override public LocalSpeechOcclusion.Cell sample(BlockPos pos) {
            calls++;
            sampled.add(pos.immutable());
            return new LocalSpeechOcclusion.Cell(!unloaded.contains(pos), blocks.getOrDefault(pos, Shapes.empty()));
        }
    }

    @Test void openRoomAndNoncollidingFluidDoNotDampen() {
        Room room = new Room().block(1, 1, 0, Shapes.empty());
        assertTrue(LocalSpeechOcclusion.audible(START, new Vec3(2.5, 1.6, 0.5), room));
        assertEquals(room.sampled.size(), room.calls, "each traversed cell is inspected once");
    }

    @Test void solidThicknessControlsDistanceRatherThanWallCount() {
        Room room = new Room().block(1, 1, 0, Shapes.block());
        assertTrue(LocalSpeechOcclusion.audible(START, new Vec3(2.5, 1.6, 0.5), room));
        assertTrue(LocalSpeechOcclusion.audible(START, new Vec3(7.0, 1.6, 0.5), room),
                "one full block at the 6.5 metre effective-range boundary");
        assertFalse(LocalSpeechOcclusion.audible(START, new Vec3(7.01, 1.6, 0.5), room));
        room.block(2, 1, 0, Shapes.block());
        assertTrue(LocalSpeechOcclusion.audible(new Vec3(0.9, 1.6, 0.5),
                new Vec3(3.1, 1.6, 0.5), room), "two walls pass only with eyes close to both faces");
        assertFalse(LocalSpeechOcclusion.audible(START, new Vec3(3.5, 1.6, 0.5), room),
                "two walls at three metres exceed the effective range");
        room.block(3, 1, 0, Shapes.block());
        assertFalse(LocalSpeechOcclusion.audible(new Vec3(0.99, 1.6, 0.5),
                new Vec3(4.01, 1.6, 0.5), room), "three full blocks deny even against both faces");
    }

    @Test void obliqueLongWallUsesMaterialThicknessNotNumberOfCells() {
        Room room = new Room();
        for (int z = -20; z <= 20; z++) room.block(1, 1, z, Shapes.block());
        Vec3 oblique = new Vec3(2.5, 1.6, 2.5);
        assertTrue(LocalSpeechOcclusion.audible(START, oblique, room), "two intersected cells but only one sheet");
        for (int z = -20; z <= 20; z++) room.block(2, 1, z, Shapes.block());
        assertFalse(LocalSpeechOcclusion.audible(START, new Vec3(3.5, 1.6, 2.5), room),
                "two complete sheets cannot be bypassed by close probes");
        for (int z = -20; z <= 20; z++) room.blocks.remove(new BlockPos(2, 1, z));
        assertTrue(LocalSpeechOcclusion.audible(START, new Vec3(6.5, 1.6, 0.5), room),
                "one continuous sheet still passes at six metres");
        assertFalse(LocalSpeechOcclusion.audible(START, new Vec3(8.5, 1.6, 0.5), room),
                "remote ends of the long wall cannot provide a detour");
        for (int z = -20; z <= 20; z++) room.block(2, 1, z, Shapes.block());
        assertTrue(LocalSpeechOcclusion.audible(new Vec3(0.9, 1.6, 0.5),
                new Vec3(3.1, 1.6, 1.1), room),
                "near-face off-axis rays can pass two continuous sheets");
    }

    @Test void offsetProbesResolveCornerGrazesButNotRightAngleTurns() {
        Room room = new Room().block(1, 1, 0, Shapes.block());
        assertTrue(LocalSpeechOcclusion.audible(new Vec3(0.5, 1.6, 1.12),
                new Vec3(7.5, 1.6, 1.12), room), "nearby line passes the grazing corner");
        Room corner = new Room();
        for (int z = -20; z <= 1; z++) corner.block(1, 1, z, Shapes.block());
        for (int x = -20; x <= 1; x++) corner.block(x, 1, 1, Shapes.block());
        assertFalse(LocalSpeechOcclusion.audible(new Vec3(0.5, 1.6, 0.5),
                new Vec3(4.5, 1.6, 4.5), corner), "no acoustic turn around opaque L");
    }

    @Test void verticalShaftPassesButEvenOneFloorBlocks() {
        Vec3 above = new Vec3(0.5, 4.6, 0.5);
        Room room = new Room();
        assertTrue(LocalSpeechOcclusion.audible(START, above, room));
        room.block(0, 2, 0, Shapes.block());
        assertFalse(LocalSpeechOcclusion.audible(START, above, room));
        room.block(0, 3, 0, Shapes.block());
        assertFalse(LocalSpeechOcclusion.audible(START, above, room));
    }

    @Test void stackedFullFloorsHaveInternalSeamButExposedOuterFacesDenyBothDirections() {
        Vec3 above = new Vec3(0.5, 4.6, 0.5);
        Room room = new Room();
        assertTrue(LocalSpeechOcclusion.audible(START, above, room), "open upward shaft passes");
        assertTrue(LocalSpeechOcclusion.audible(above, START, room), "open downward shaft passes");
        room.block(0, 2, 0, Shapes.block()).block(0, 3, 0, Shapes.block());
        assertFalse(LocalSpeechOcclusion.audible(START, above, room),
                "internal seam does not hide the exposed bottom of the lower floor");
        assertFalse(LocalSpeechOcclusion.audible(above, START, room),
                "internal seam does not hide the exposed top of the upper floor");
    }

    @Test void nearVerticalFloorAndSlabFacesNeverUseWallException() {
        Vec3 justAbove = new Vec3(0.5, 2.7, 0.5);
        Room room = new Room();
        assertTrue(LocalSpeechOcclusion.audible(START, justAbove, room));
        room.block(0, 2, 0, Shapes.box(0, 0, 0, 1, 0.5, 1));
        assertFalse(LocalSpeechOcclusion.audible(START, justAbove, room), "bottom slab top face");
        room.block(0, 2, 0, Shapes.block());
        assertFalse(LocalSpeechOcclusion.audible(START, justAbove, room), "floor bottom face");
        room.blocks.clear();
        room.block(0, 1, 0, Shapes.box(0, 0.75, 0, 1, 1, 1));
        assertFalse(LocalSpeechOcclusion.audible(justAbove, START, room), "ceiling top face");
    }

    @Test void diagonalExitThroughFloorTopIsNotAThinWall() {
        Room room = new Room().block(1, 2, 0, Shapes.block());
        Vec3 above = new Vec3(1.3, 3.1, 0.5);
        assertFalse(LocalSpeechOcclusion.audible(START, above, room),
                "side entry followed by top exit crosses a floor, even at short range");
        room.block(1, 2, 0, Shapes.box(0, 0, 0, 1, 0.5, 1));
        assertFalse(LocalSpeechOcclusion.audible(START, new Vec3(1.5, 2.7, 0.5), room),
                "diagonal side entry and top exit through a bottom slab is not a wall");
        room.blocks.clear();
        assertTrue(LocalSpeechOcclusion.audible(START, above, room), "open diagonal shaft passes");
    }

    @Test void endpointInsideFloorStillDetectsVerticalEntryOrExit() {
        Room room = new Room().block(1, 2, 0, Shapes.block());
        Vec3 below = new Vec3(1.2, 1.6, 0.5);
        Vec3 inside = new Vec3(1.3, 2.8, 0.5);
        assertFalse(LocalSpeechOcclusion.audible(below, inside, room),
                "ray ending inside the floor entered through its bottom");
        assertFalse(LocalSpeechOcclusion.audible(inside, below, room),
                "ray beginning inside the floor and exiting vertically is symmetric");
    }

    @Test void partialShapesOnlyCountWhenRayActuallyIntersects() {
        Room room = new Room().block(1, 1, 0, Shapes.box(0, 0, 0, 1, 0.5, 1)); // bottom slab
        Vec3 far = new Vec3(6.5, 1.6, 0.5);
        assertTrue(LocalSpeechOcclusion.audible(START, far, room));
        room.block(1, 1, 0, Shapes.box(0, 0, 0, 0.1875, 1, 1)); // closed door panel
        assertTrue(LocalSpeechOcclusion.audible(START, far, room), "thin door permits six metres");
        room.block(1, 1, 0, Shapes.box(0, 0, 0, 1, 1, 0.1875)); // open panel beside ray
        assertTrue(LocalSpeechOcclusion.audible(START, far, room));
    }

    @Test void quarterBlockBarrierHasTenPointSevenFiveMetreBoundary() {
        Room room = new Room().block(1, 1, 0, Shapes.box(0, 0, 0, 0.25, 1, 1));
        assertTrue(LocalSpeechOcclusion.audible(START, new Vec3(11.25, 1.6, 0.5), room));
        assertFalse(LocalSpeechOcclusion.audible(START, new Vec3(11.26, 1.6, 0.5), room));
        room.blocks.clear();
        assertTrue(LocalSpeechOcclusion.audible(START, new Vec3(11.26, 1.6, 0.5), room),
                "zero thickness preserves the clear ray");
    }

    @Test void heightDifferenceAloneNeverCapsSideWall() {
        Room room = new Room().block(1, 1, 0, Shapes.block());
        assertTrue(LocalSpeechOcclusion.audible(new Vec3(0.5, 1.1, 0.5),
                new Vec3(3.5, 2.1, 0.5), room),
                "one block of eye-height difference is allowed when no Y face is crossed");
    }

    @Test void raisedRoomAcrossFullHeightWallPassesBothWaysButExposedRoofDenies() {
        Room room = new Room();
        for (int z = -1; z <= 1; z++) {
            room.block(1, 1, z, Shapes.block()).block(1, 2, z, Shapes.block());
            room.block(2, 1, z, Shapes.block()); // raised room floor
        }
        Vec3 lower = new Vec3(0.5, 1.6, 0.5);
        Vec3 raised = new Vec3(2.5, 2.6, 0.5);
        assertTrue(LocalSpeechOcclusion.audible(lower, raised, room));
        assertTrue(LocalSpeechOcclusion.audible(raised, lower, room));
        for (int z = -1; z <= 1; z++) room.blocks.remove(new BlockPos(1, 2, z));
        assertFalse(LocalSpeechOcclusion.audible(lower, raised, room), "exposed top of low wall is a roof crossing");
        assertFalse(LocalSpeechOcclusion.audible(raised, lower, room));
    }

    @Test void internalSeamRequiresMaterialAtCrossingAndAvailableNeighbor() {
        Vec3 lower = new Vec3(0.5, 1.6, 0.5);
        Vec3 raised = new Vec3(2.5, 2.6, 0.5);
        Room room = new Room();
        for (int z = -1; z <= 1; z++) room.block(1, 1, z, Shapes.block());
        room.block(1, 2, 0, Shapes.box(0.6, 0, 0, 1, 1, 1));
        assertFalse(LocalSpeechOcclusion.audible(lower, raised, room),
                "a shape elsewhere in the upper cell cannot close the seam");
        room.block(1, 2, 0, Shapes.block());
        room.unloaded.add(new BlockPos(1, 2, 0));
        assertFalse(LocalSpeechOcclusion.audible(lower, raised, room));
        assertTrue(room.sampled.contains(new BlockPos(1, 2, 0)));
        room.unloaded.clear();
        assertFalse(LocalSpeechOcclusion.audible(lower, raised, room, 2),
                "the adjacent seam cell must share the global cache budget");
    }

    @Test void raisedWallCountsBothSolidSegmentsAndFloorStillBlocks() {
        Vec3 lower = new Vec3(0.5, 1.6, 0.5);
        Vec3 raised = new Vec3(2.5, 2.6, 0.5);
        Room room = new Room();
        for (int z = -1; z <= 1; z++)
            for (int x = 1; x <= 2; x++)
                room.block(x, 1, z, Shapes.block()).block(x, 2, z, Shapes.block());
        assertFalse(LocalSpeechOcclusion.audible(lower, new Vec3(3.5, 2.6, 0.5), room),
                "two full sheets at this distance exceed the thickness budget");
        for (int z = -1; z <= 1; z++)
            room.block(3, 1, z, Shapes.block()).block(3, 2, z, Shapes.block());
        assertFalse(LocalSpeechOcclusion.audible(lower, new Vec3(4.5, 2.6, 0.5), room));
        Room floor = new Room().block(1, 2, 0, Shapes.block());
        assertFalse(LocalSpeechOcclusion.audible(lower, new Vec3(1.5, 3.1, 0.5), floor));
        floor.block(1, 2, 0, Shapes.box(0, 0, 0, 1, 0.5, 1));
        assertFalse(LocalSpeechOcclusion.audible(lower, new Vec3(1.5, 2.7, 0.5), floor));
    }

    @Test void unloadedCellsRejectWithoutFurtherSampling() {
        Room room = new Room();
        room.unloaded.add(new BlockPos(1, 1, 0));
        assertFalse(LocalSpeechOcclusion.audible(START, new Vec3(3.5, 1.6, 0.5), room));
        assertFalse(room.sampled.contains(new BlockPos(2, 1, 0)));
    }

    @Test void acceptedDirectRayDoesNotSampleUnusedUnloadedOffset() {
        Room room = new Room();
        room.unloaded.add(new BlockPos(1, 1, -1));
        assertTrue(LocalSpeechOcclusion.audible(new Vec3(0.5, 1.6, 0.1),
                new Vec3(4.5, 1.6, 0.1), room));
        assertFalse(room.sampled.contains(new BlockPos(1, 1, -1)));
    }

    @Test void acceptedDirectRayDoesNotExhaustUnusedOffsetBudget() {
        Room room = new Room();
        assertTrue(LocalSpeechOcclusion.audible(new Vec3(0.5, 1.6, 0.1),
                new Vec3(4.5, 1.6, 0.1), room, 5));
        assertEquals(5, room.calls);
    }

    @Test void acceptedOffsetRayDoesNotSampleLaterMissingCell() {
        Room room = new Room().block(1, 1, 0, Shapes.box(0, 0, 0, 1, 1, 0.15));
        room.unloaded.add(new BlockPos(1, 1, -1));
        assertTrue(LocalSpeechOcclusion.audible(new Vec3(0.5, 1.6, 0.1),
                new Vec3(6.5, 1.6, 0.1), room), "+Z is clear after direct and X probes hit panel");
        assertFalse(room.sampled.contains(new BlockPos(1, 1, -1)));
    }

    @Test void capAndSamplerFailureFailClosed() {
        Room room = new Room();
        assertFalse(LocalSpeechOcclusion.audible(START, new Vec3(2.5, 1.6, 0.5), room, 2));
        assertEquals(2, room.calls, "never sample beyond the cell limit");
        AtomicInteger calls = new AtomicInteger();
        assertFalse(LocalSpeechOcclusion.audible(START, new Vec3(2.5, 1.6, 0.5), pos -> {
            calls.incrementAndGet();
            throw new IllegalStateException("unavailable collision shape");
        }));
        assertEquals(1, calls.get());
    }

    @Test void collisionBoxCapsFailClosed() {
        VoxelShape thirtyTwo = Shapes.empty();
        for (int i = 0; i < 32; i++) {
            double min = i / 32.0;
            thirtyTwo = Shapes.or(thirtyTwo, Shapes.box(min, 0, 0, min + 1.0 / 64, 1, 1));
        }
        Room tooManyInOneCell = new Room().block(1, 1, 0,
                Shapes.or(thirtyTwo, Shapes.box(0.99, 0, 0, 1, 1, 1)));
        assertFalse(LocalSpeechOcclusion.audible(START, new Vec3(2.5, 1.6, 0.5), tooManyInOneCell),
                "more than 32 collision boxes in a needed cell must fail closed");

        Room tooManyAcrossRays = new Room();
        for (int x = 1; x <= 11; x++) tooManyAcrossRays.block(x, 1, 0, thirtyTwo);
        assertFalse(LocalSpeechOcclusion.audible(START, new Vec3(12.5, 1.6, 0.5), tooManyAcrossRays),
                "the 1024 box-check budget is shared across the five probes");
        assertEquals(13, tooManyAcrossRays.calls, "all probes reuse cached cells");
    }

    @Test void rangeInvalidCoordinatesAndCornersHaveNoAlternativePath() {
        Room room = new Room().block(1, 1, 0, Shapes.block());
        assertFalse(LocalSpeechOcclusion.audible(START, new Vec3(17.4, 1.6, 0.5), room));
        assertFalse(LocalSpeechOcclusion.audible(START, new Vec3(Double.NaN, 1, 0), room));
        assertFalse(LocalSpeechOcclusion.audible(START, new Vec3(Double.POSITIVE_INFINITY, 1, 0), room));
        assertFalse(LocalSpeechOcclusion.audible(START, new Vec3(30_000_001, 1, 0), room));
        assertEquals(0, room.calls, "reject before sampling");
        // A walkable detour exists, but not within any of the five straight probes.
        assertFalse(LocalSpeechOcclusion.audible(START, new Vec3(8.5, 1.6, 0.5), room));
    }

    @Test void eyeToEyeRangeIncludesHeightDifferenceAndRejectsBeforeSampling() {
        Room room = new Room();
        assertTrue(LocalSpeechOcclusion.audible(START, new Vec3(10.4, 3.4, 0.5), room),
                "9.9 horizontal and 1.8 vertical should remain in range");
        assertTrue(LocalSpeechOcclusion.audible(START, new Vec3(15.5, 1.6, 0.5), room),
                "clear ray passes exactly at the 15-block boundary");
        int sampled = room.calls;
        assertFalse(LocalSpeechOcclusion.audible(START, new Vec3(15.501, 1.6, 0.5), room));
        assertFalse(LocalSpeechOcclusion.audible(START, new Vec3(15.5, 3.4, 0.5), room),
                "15 horizontal blocks plus 1.8 eye-height difference exceeds eye-to-eye range");
        assertFalse(LocalSpeechOcclusion.audible(START, new Vec3(17.4, 1.6, 0.5), room));
        assertEquals(sampled, room.calls, "overlong rays must fail before any additional sampling");
        assertTrue(LocalSpeechOcclusion.audible(START, new Vec3(15.3, 3.4, 0.5), room),
                "mixed-level body positions with a 14.8 horizontal gap can still pass");
    }

    @Test void everyProbeSharesSamplesAndCapsAreGlobal() {
        Room room = new Room();
        assertTrue(LocalSpeechOcclusion.audible(START, new Vec3(15.5, 1.6, 0.5), room));
        assertEquals(room.sampled.size(), room.calls);
        assertTrue(room.calls <= LocalSpeechOcclusion.MAX_VISITED_CELLS);
        assertFalse(LocalSpeechOcclusion.audible(START, new Vec3(15.5, 1.6, 0.5), new Room(), 5));
        Room missingOnOffset = new Room();
        // The direct and X-offset rays fail on a narrow panel; +Z visits the
        // missing cell before the later, open -Z candidate can be accepted.
        missingOnOffset.block(1, 1, 0, Shapes.box(0, 0, 0.85, 1, 1, 1));
        missingOnOffset.unloaded.add(new BlockPos(1, 1, 1));
        assertFalse(LocalSpeechOcclusion.audible(new Vec3(0.5, 1.6, 0.9),
                new Vec3(8.5, 1.6, 0.9), missingOnOffset));
    }
}
