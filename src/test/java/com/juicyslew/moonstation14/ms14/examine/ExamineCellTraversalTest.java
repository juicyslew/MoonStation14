package com.juicyslew.moonstation14.ms14.examine;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ExamineCellTraversalTest {
    @Test void traversesNegativeVerticalAndThinDiagonalCells() {
        assertTrue(ExamineTargetResolver.crossedCells(new Vec3(-0.2, 2.5, 0.5), new Vec3(-2.2, 2.5, 0.5))
                .contains(new BlockPos(-2, 2, 0)));
        assertTrue(ExamineTargetResolver.crossedCells(new Vec3(.5, 4, .5), new Vec3(.5, 1, .5))
                .contains(new BlockPos(0, 2, 0)));
        var diagonal = ExamineTargetResolver.crossedCells(new Vec3(.1, .1, .5), new Vec3(2.9, 2.9, .5));
        assertTrue(diagonal.contains(new BlockPos(1, 0, 0)));
        assertTrue(diagonal.contains(new BlockPos(0, 1, 0)));
        assertEquals(new BlockPos(2, 2, 0), diagonal.get(diagonal.size() - 1));
    }

    @Test void handlesAxisAlignedAndExactEndpoint() {
        var cells = ExamineTargetResolver.crossedCells(new Vec3(0.5, 1.5, 0.5), new Vec3(2, 1.5, 0.5));
        assertEquals(new BlockPos(2, 1, 0), cells.get(cells.size() - 1));
        assertEquals(3, cells.size());
    }

    @Test void productionOcclusionHelperUsesActualThinGeometryAndTreatsEmptyAsMiss() {
        Vec3 from = new Vec3(.1, .5, .5), to = new Vec3(.9, .5, .5);
        var pane = Shapes.box(.48, 0, 0, .52, 1, 1);
        assertTrue(ExamineTargetResolver.shapeOccludes(pane, from, to, BlockPos.ZERO, from.distanceTo(to)));
        var offRay = Shapes.box(0, 0, 0, .2, .2, .2);
        assertFalse(ExamineTargetResolver.shapeOccludes(offRay, from, to, BlockPos.ZERO, from.distanceTo(to)));
        assertFalse(ExamineTargetResolver.shapeOccludes(Shapes.empty(), from, to, BlockPos.ZERO, from.distanceTo(to)));
        assertFalse(ExamineTargetResolver.shapeOccludes(null, from, to, BlockPos.ZERO, from.distanceTo(to)));
    }

    @Test void nearButDistinctBoundaryCrossingsDoNotVisitUncrossedSideCell() {
        var cells = ExamineTargetResolver.crossedCells(new Vec3(.5, .5, .5),
                new Vec3(1.5, 1.500000000001, .5));
        assertTrue(cells.contains(new BlockPos(0, 1, 0)));
        assertFalse(cells.contains(new BlockPos(1, 0, 0)));
        assertTrue(ExamineTargetResolver.crossedCells(new Vec3(.5,.5,.5), new Vec3(1.5,1.5,1.5))
                .contains(new BlockPos(1, 1, 0)));
    }

    @Test void endpointContainingCollisionBoundsAreIgnoredButIntermediateWallsStillOcclude() {
        var wall = Shapes.block();
        Vec3 inside = new Vec3(.2, .2, .2), outside = new Vec3(2, .2, .2);
        assertFalse(ExamineTargetResolver.shapeOccludes(wall, inside, outside, BlockPos.ZERO,
                inside.distanceTo(outside)), "actor endpoint inside a blocker matches SS14 ignoreInsideBlocker");
        assertTrue(ExamineTargetResolver.shapeOccludes(wall, inside, outside, BlockPos.ZERO,
                inside.distanceTo(outside), false, false),
                "virtual scene LOS can disable the synthetic far-endpoint exemption");
        assertFalse(ExamineTargetResolver.shapeOccludes(wall, inside, outside, BlockPos.ZERO,
                inside.distanceTo(outside), true, false),
                "virtual scene LOS still ignores a shape containing the physical actor eye");

        // Closest point at a target's corner can touch the adjacent collision voxel exactly.
        Vec3 corner = new Vec3(1, 1, .5), source = new Vec3(2, 1, .5);
        assertFalse(ExamineTargetResolver.shapeOccludes(wall, source, corner, BlockPos.ZERO,
                source.distanceTo(corner)), "inclusive endpoint-on-corner containment is ignored");

        Vec3 endpointInNeighbor = new Vec3(1, .5, .5), sourceInNeighbor = new Vec3(2, .5, .5);
        var unrelatedEndpointCellShape = Shapes.box(.1, 0, 0, .2, 1, 1);
        assertTrue(ExamineTargetResolver.shapeOccludes(unrelatedEndpointCellShape, sourceInNeighbor,
                        endpointInNeighbor, new BlockPos(1, 0, 0), sourceInNeighbor.distanceTo(endpointInNeighbor)),
                "opaque shape in endpoint voxel still occludes when it contains neither endpoint");

        Vec3 clearFrom = new Vec3(-1, .5, .5), clearTo = new Vec3(2, .5, .5);
        assertTrue(ExamineTargetResolver.shapeOccludes(wall, clearFrom, clearTo, BlockPos.ZERO,
                clearFrom.distanceTo(clearTo)), "opaque intermediate collision remains blocking");
    }
}
