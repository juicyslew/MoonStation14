package com.juicyslew.moonstation14.ms14.doors;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class FirelockGeometryTest {
    @Test void usesAuthoredRigAndClipsTranslatedGeometry() {
        assertEquals(14,FirelockGeometry.upperLeaf().size()+FirelockGeometry.lowerLeaf().size());
        assertEquals(4,FirelockGeometry.indicator().size());
        for(double q:new double[]{0,1}) for(var b:FirelockGeometry.localBoxes(q)) {
            assertTrue(b.x0()>=-.25/16-1e-9 && b.x1()<=16.25/16+1e-9);
            assertTrue(b.y0()>=0 && b.y1()<=2);
            assertTrue(b.z0()>=0 && b.z1()<=1);
        }
        assertTrue(FirelockGeometry.upperLeaf().stream().anyMatch(b->b.y1()+1>2));
        assertTrue(FirelockGeometry.lowerLeaf().stream().anyMatch(b->b.y0()-1<0));
    }

    @Test void worldBoundsApplyAirlockFacingTransformAndQuarterUnitPadding() {
        var bounds=FirelockGeometry.worldBounds(new BlockPos(4,5,6),Direction.NORTH,0);
        assertFalse(bounds.isEmpty());
        assertTrue(bounds.stream().allMatch(b->b.minY>=5 && b.maxY<=7));
        assertTrue(bounds.stream().anyMatch(b->b.minX<4 || b.maxX>5));
    }

    @Test void exactFullyOpenDropsBoundaryCoincidentCoverBoxesForBothHostModes() {
        // The shared authored geometry path clips translated boxes to the same
        // [0,2] vertical envelope and strictly discards zero-volume intersections.
        for (var facing : List.of(Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST)) {
            var open = FirelockGeometry.worldBounds(new BlockPos(4, 5, 6), facing, 1.0);
            assertTrue(open.isEmpty(), "q=1 cover must have no selectable/outline volume: " + facing);
        }
        // At a representable near-open pose the translated cover remains selectable.
        assertTrue(FirelockGeometry.worldBounds(new BlockPos(4, 5, 6), Direction.NORTH, 1.0 - 1.0e-6).size() > 0,
                "near-open authored cover geometry remains selectable");
    }
}
