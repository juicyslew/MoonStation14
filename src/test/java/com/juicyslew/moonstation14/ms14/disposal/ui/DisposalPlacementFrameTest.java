package com.juicyslew.moonstation14.ms14.disposal.ui;

import com.juicyslew.moonstation14.ms14.disposal.domain.DisposalOrientation;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DisposalPlacementFrameTest {
    @Test void clickedFaceDeterminesForwardForAllAxes() {
        for (Direction face : Direction.values()) for(Direction look:Direction.values()) {
            var frame = DisposalOrientation.placement(face, look);
            assertEquals(face, frame.forward());
            assertNotEquals(face.getAxis(), frame.up().getAxis());
            if(look.getAxis()!=face.getAxis())assertEquals(look,frame.up());
        }
    }
}
