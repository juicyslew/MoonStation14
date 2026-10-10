package com.juicyslew.moonstation14.ms14.stripping;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StripInteractionRangeTest {
    @Test void sharedRootRangeIncludesBoundaryButRejectsAnythingBeyond() {
        assertTrue(StripInteractionRange.contains(new Vec3(0,0,0),new Vec3(3,0,0)));
        assertFalse(StripInteractionRange.contains(new Vec3(0,0,0),new Vec3(3.000001,0,0)));
        assertTrue(StripInteractionRange.contains(new Vec3(0,0,0),new Vec3(0,0,0)));
    }

    @Test void sharedReachUsesFlatAdditionRatherThanScaling() {
        assertEquals(3, com.juicyslew.moonstation14.ms14.interaction.InteractionReach.fromSs14(1.5));
        assertEquals(4.5, com.juicyslew.moonstation14.ms14.interaction.InteractionReach.fromSs14(3));
        assertEquals(1.5, com.juicyslew.moonstation14.ms14.interaction.InteractionReach.fromSs14(0));
        assertTrue(Double.isNaN(com.juicyslew.moonstation14.ms14.interaction.InteractionReach.fromSs14(Double.NaN)));
    }
}
