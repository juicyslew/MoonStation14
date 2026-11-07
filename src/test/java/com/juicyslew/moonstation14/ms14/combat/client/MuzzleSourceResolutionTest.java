package com.juicyslew.moonstation14.ms14.combat.client;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class MuzzleSourceResolutionTest {
    private static ActualMuzzleFrame frame(double x,long generation){return new ActualMuzzleFrame(new Vec3(x,0,0),new Vec3(0,0,1),new Vec3(0,1,0),generation);}
    @Test void waitsOneGenerationThenFreezesAndNeverReattaches() {
        var state=new MuzzleSourceResolution();var calls=new AtomicInteger();
        assertNull(state.resolve(10,()->true,()->null));
        var captured=state.resolve(11,()->true,()->{calls.incrementAndGet();return frame(3,11);});
        assertEquals(frame(3,11),captured);
        assertEquals(frame(3,11),state.resolve(30,()->false,()->{calls.incrementAndGet();return frame(9,30);}));
        assertEquals(1,calls.get());
    }
    @Test void sourceMismatchCannotCaptureAndWaitExpiresAfterTwoGenerations() {
        var mismatch=new MuzzleSourceResolution();var called=new AtomicInteger();
        assertNull(mismatch.resolve(4,()->false,()->{called.incrementAndGet();return frame(1,4);}));assertEquals(0,called.get());
        var missing=new MuzzleSourceResolution();assertNull(missing.resolve(4,()->true,()->null));
        assertNull(missing.resolve(5,()->true,()->null));assertFalse(missing.expired(5));assertTrue(missing.expired(6));
    }
}
