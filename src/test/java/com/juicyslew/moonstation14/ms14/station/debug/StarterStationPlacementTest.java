package com.juicyslew.moonstation14.ms14.station.debug;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

final class StarterStationPlacementTest {
    @Test void newWorldInitializationIsOverworldOnlyAndNeverResetsExistingState() {
        assertEquals(StarterStationPlacementState.State.DISABLED,
                StarterStationPlacementState.afterNewWorldSpawnInitialization(StarterStationPlacementState.State.DISABLED, false));
        assertEquals(StarterStationPlacementState.State.PENDING,
                StarterStationPlacementState.afterNewWorldSpawnInitialization(StarterStationPlacementState.State.DISABLED, true));
        assertEquals(StarterStationPlacementState.State.COMPLETED,
                StarterStationPlacementState.afterNewWorldSpawnInitialization(StarterStationPlacementState.State.COMPLETED, true));
    }

    @Test void plannedWritesRejectFluidEvenWhenReplaceableUnlessAlreadyExactIntendedState() {
        // Water, lava, and waterlogged replaceable foliage all have fluid and must not be cleared to AIR.
        assertFalse(StarterStationService.canReplaceWriteTarget(false, true, true), "water");
        assertFalse(StarterStationService.canReplaceWriteTarget(false, true, true), "lava");
        assertFalse(StarterStationService.canReplaceWriteTarget(false, true, true), "waterlogged foliage");
        assertTrue(StarterStationService.canReplaceWriteTarget(false, false, true), "dry replaceable foliage");
        assertFalse(StarterStationService.canReplaceWriteTarget(false, false, false), "solid obstruction");
        assertTrue(StarterStationService.canReplaceWriteTarget(true, false, false), "exact intended state");
    }

    @Test void savedDataRoundTripsPendingSelectedAndCompletedWithoutRepeating() {
        StarterStationSavedData pending = StarterStationSavedData.createPending();
        assertEquals(StarterStationPlacementState.State.PENDING, StarterStationSavedData.load(pending.save(new CompoundTag(), null), null).state());

        pending.select(new BlockPos(12, 64, -7));
        StarterStationSavedData selected = StarterStationSavedData.load(pending.save(new CompoundTag(), null), null);
        assertEquals(StarterStationPlacementState.State.SELECTED, selected.state());
        assertEquals(new BlockPos(12, 64, -7), selected.selectedOrigin());
        selected.complete();
        StarterStationSavedData completed = StarterStationSavedData.load(selected.save(new CompoundTag(), null), null);
        assertEquals(StarterStationPlacementState.State.COMPLETED, completed.state());
        assertFalse(StarterStationPlacementState.shouldAttempt(completed.state()));
        completed.select(new BlockPos(0, 0, 0));
        assertEquals(StarterStationPlacementState.State.COMPLETED, completed.state());
    }

    @Test void everyLayoutFloorMapsToTiledStationFloorAndClearanceMapsToAir() {
        StarterStationLayout layout = StarterStationLayout.standard();
        for (StarterStationLayout.Cell cell : layout.plan()) {
            assertEquals(cell.material(), layout.materialAt(cell.x(), cell.y(), cell.z()));
            StarterStationBlockMapping.BlockRole role = StarterStationBlockMapping.role(cell);
            if (cell.y() == StarterStationLayout.FLOOR_Y) {
                assertTrue(role == StarterStationBlockMapping.BlockRole.STEEL_FLOOR
                        || role == StarterStationBlockMapping.BlockRole.WHITE_FLOOR);
            } else if (cell.material() == StarterStationLayout.Material.AIR) {
                assertEquals(StarterStationBlockMapping.BlockRole.AIR, role);
            } else {
                assertEquals(StarterStationBlockMapping.BlockRole.STEEL_WALL, role);
            }
        }
        assertEquals(StarterStationBlockMapping.BlockRole.WHITE_FLOOR,
                StarterStationBlockMapping.role(layout.plan().stream()
                        .filter(cell -> cell.x() == 0 && cell.y() == 0 && cell.z() == 3).findFirst().orElseThrow()));
        assertEquals(StarterStationBlockMapping.BlockRole.STEEL_FLOOR,
                StarterStationBlockMapping.role(layout.plan().stream()
                        .filter(cell -> cell.x() == 1 && cell.y() == 0 && cell.z() == 1).findFirst().orElseThrow()));
        assertEquals(StarterStationLayout.Material.WHITE_TILE, layout.materialAt(0, 0, 3));
        assertEquals(StarterStationLayout.Material.STEEL, layout.materialAt(1, 0, 1));
        assertEquals(StarterStationLayout.Material.AIR, layout.materialAt(1, 1, 1));
        assertNull(layout.materialAt(-1, 0, 0));
        assertNull(layout.materialAt(1, layout.roofY() + 1, 1));
        assertEquals(StarterStationLayout.Material.STEEL, layout.materialAt(0, 1, 0));
    }

    @Test void nearbyEastCandidatesNeverContainSharedSpawn() {
        StarterStationLayout layout = StarterStationLayout.standard();
        for (int eastOffset : new int[]{5, 8, 12}) {
            assertFalse(StarterStationPlacementGeometry.containsSpawn(eastOffset, -4, 0, 0, layout));
            assertTrue(eastOffset >= 5 && eastOffset <= 12); // west entrance offset from spawn
        }
        assertTrue(StarterStationPlacementGeometry.containsSpawn(-10, -4, 0, 0, layout));
    }

    @Test void raisedFloorSupportUsesSteelWallAndCountsOnlyTheGap() {
        assertEquals(StarterStationBlockMapping.BlockRole.STEEL_WALL, StarterStationBlockMapping.supportRole());
        assertEquals(0, StarterStationPlacementGeometry.supportBlockCount(70, 70));
        assertEquals(2, StarterStationPlacementGeometry.supportBlockCount(70, 68));
        assertEquals(0, StarterStationPlacementGeometry.supportBlockCount(68, 70));
    }

    @Test void terrainProbeIsBoundedToSupportPositionsAndOneGroundCell() {
        int floorY = 70;
        int minProbeY = StarterStationPlacementGeometry.terrainScanMinY(floorY, 3);
        assertEquals(66, minProbeY);
        assertEquals(4, floorY - minProbeY);
        assertEquals(3, StarterStationPlacementGeometry.supportBlockCount(floorY, minProbeY + 1));
        assertTrue(StarterStationPlacementGeometry.planSupports(floorY, 3, List.of(
                new StarterStationPlacementGeometry.SupportColumn(0, 0, minProbeY, Set.of()))).isEmpty(),
                "a ravine whose ground is below the final probe must fail closed");
    }

    @Test void frozenSupportPlanResumesAfterPartialWritesAndRejectsOutOfPlanEdits() {
        List<StarterStationPlacementGeometry.SupportColumn> terrain = List.of(
                new StarterStationPlacementGeometry.SupportColumn(0, 0, 68, Set.of()),
                new StarterStationPlacementGeometry.SupportColumn(1, 0, 69, Set.of()));
        var freshPlan = StarterStationPlacementGeometry.planSupports(70, 3, terrain).orElseThrow();
        assertEquals(List.of(
                new StarterStationPlacementGeometry.SupportBlock(0, 68, 0, false),
                new StarterStationPlacementGeometry.SupportBlock(0, 69, 0, false),
                new StarterStationPlacementGeometry.SupportBlock(1, 69, 0, false)), freshPlan);

        List<StarterStationPlacementGeometry.SupportColumn> partiallyWritten = List.of(
                new StarterStationPlacementGeometry.SupportColumn(0, 0, 68, Set.of(68)),
                new StarterStationPlacementGeometry.SupportColumn(1, 0, 69, Set.of(69)));
        var resumedPlan = StarterStationPlacementGeometry.planSupports(70, 3, partiallyWritten).orElseThrow();
        assertEquals(freshPlan.stream().map(block -> new StarterStationPlacementGeometry.SupportBlock(
                block.x(), block.y(), block.z(), block.x() == 0 && block.y() == 68
                        || block.x() == 1 && block.y() == 69)).toList(), resumedPlan);
        assertTrue(StarterStationPlacementGeometry.planSupports(70, 3, List.of(
                new StarterStationPlacementGeometry.SupportColumn(0, 0, 68, Set.of(67)))).isEmpty(),
                "a support edit below the intended range must fail closed");
        assertTrue(StarterStationPlacementGeometry.planSupports(70, 3, List.of(
                new StarterStationPlacementGeometry.SupportColumn(0, 0, 66, Set.of()))).isEmpty(),
                "terrain variation beyond the three-block bound must fail closed");
    }
}
