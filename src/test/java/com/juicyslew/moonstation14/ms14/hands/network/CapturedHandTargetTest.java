package com.juicyslew.moonstation14.ms14.hands.network;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class CapturedHandTargetTest {
    @Test void coordinatesAreFiniteImmutableAndCoordinateSpaceIsPaired() {
        BlockPos source = new BlockPos(20_000_000, 2, 3);
        var frame = CapturedHandTarget.frame(new Vec3(20_000_000.25, 4, 6), UUID.randomUUID(), source);
        source = null;
        assertEquals(new BlockPos(20_000_000, 2, 3), frame.frameCell());
        assertEquals(20_000_000.25, frame.point().x);
        assertNull(CapturedHandTarget.world(Vec3.ZERO).frameCell());
        assertThrows(IllegalArgumentException.class, () -> new CapturedHandTarget(Vec3.ZERO, UUID.randomUUID(), null));
        assertThrows(IllegalArgumentException.class, () -> CapturedHandTarget.world(new Vec3(Double.NaN, 0, 0)));
    }
}
