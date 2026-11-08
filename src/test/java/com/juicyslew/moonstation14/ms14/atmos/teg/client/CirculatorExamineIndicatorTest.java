package com.juicyslew.moonstation14.ms14.atmos.teg.client;

import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CirculatorExamineIndicatorTest {
    @Test void renderGeometryRotatesResolvedLocalPortCentersAndDirectionsTogether() {
        Direction[] facings = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
        for (Direction facing : facings) {
            var plan = CirculatorExamineRenderPlan.forFacing(facing);
            Direction inlet = CirculatorExamineIndicator.rotate(Direction.NORTH, facing);
            Direction outlet = CirculatorExamineIndicator.rotate(Direction.SOUTH, facing);
            assertEquals(inlet.getStepX() * .30f, plan.inlet().centerX());
            assertEquals(inlet.getStepZ() * .30f, plan.inlet().centerZ());
            assertEquals(inlet, plan.inlet().forward());
            assertEquals(outlet.getStepX() * .30f, plan.outlet().centerX());
            assertEquals(outlet.getStepZ() * .30f, plan.outlet().centerZ());
            assertEquals(outlet, plan.outlet().forward());
        }
    }
    @Test void profileNorthSouthPortsRotateWithAllFourFacings() {
        assertEquals(Direction.NORTH, CirculatorExamineIndicator.rotate(Direction.NORTH, Direction.NORTH));
        assertEquals(Direction.SOUTH, CirculatorExamineIndicator.rotate(Direction.SOUTH, Direction.NORTH));
        assertEquals(Direction.EAST, CirculatorExamineIndicator.rotate(Direction.NORTH, Direction.EAST));
        assertEquals(Direction.WEST, CirculatorExamineIndicator.rotate(Direction.SOUTH, Direction.EAST));
        assertEquals(Direction.SOUTH, CirculatorExamineIndicator.rotate(Direction.NORTH, Direction.SOUTH));
        assertEquals(Direction.NORTH, CirculatorExamineIndicator.rotate(Direction.SOUTH, Direction.SOUTH));
        assertEquals(Direction.WEST, CirculatorExamineIndicator.rotate(Direction.NORTH, Direction.WEST));
        assertEquals(Direction.EAST, CirculatorExamineIndicator.rotate(Direction.SOUTH, Direction.WEST));
    }
}
