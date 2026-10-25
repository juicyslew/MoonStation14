package com.juicyslew.moonstation14.ms14.explosion.grenade.network;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GrenadeSoundPolicyTest {
    private static final ResourceLocation WORLD = ResourceLocation.parse("minecraft:overworld");
    private static final ResourceLocation OTHER = ResourceLocation.parse("minecraft:the_nether");

    private GrenadeSoundPolicy.Decision at(double distance) {
        return GrenadeSoundPolicy.classify(WORLD, WORLD, 0, 0, 0, distance, 0, 0);
    }

    @Test
    void boundariesSelectExactlyOneMode() {
        assertEquals(GrenadeSoundPolicy.Mode.NEAR, at(0).mode());
        assertEquals(GrenadeSoundPolicy.Mode.NEAR, at(32).mode());
        assertEquals(GrenadeSoundPolicy.NEAR_GAIN, at(32).gain());
        assertEquals(GrenadeSoundPolicy.Mode.FAR, at(Math.nextUp(32.0)).mode());
        assertEquals(GrenadeSoundPolicy.Mode.FAR, at(64).mode());
        assertEquals(GrenadeSoundPolicy.Mode.FAR, at(Math.nextDown(96.0)).mode());
        assertEquals(GrenadeSoundPolicy.Mode.NONE, at(96).mode()); // zero gain at inclusive outer boundary
        assertEquals(GrenadeSoundPolicy.Mode.NONE, at(97).mode());
        assertEquals(0, at(96).gain());
    }

    @Test
    void farGainDecreasesSmoothlyTowardZero() {
        assertTrue(at(32.01).gain() > at(48).gain());
        assertTrue(at(48).gain() > at(80).gain());
        assertTrue(at(80).gain() > at(95).gain());
        assertTrue(at(95).gain() > 0);
        assertTrue(at(32.01).gain() <= GrenadeSoundPolicy.FAR_GAIN);
        assertEquals(GrenadeSoundPolicy.FAR_GAIN, at(Math.nextUp(32.0)).gain(), 0.001f);
        assertTrue(at(95).gain() < at(80).gain());
    }

    @Test
    void cameraDimensionMissingCameraAndNonFiniteInputAreSilent() {
        assertEquals(GrenadeSoundPolicy.Mode.NONE,
                GrenadeSoundPolicy.classify(WORLD, OTHER, 0, 0, 0, 0, 0, 0).mode());
        assertEquals(GrenadeSoundPolicy.Mode.NONE,
                GrenadeSoundPolicy.classify(WORLD, null, 0, 0, 0, 0, 0, 0).mode());
        assertEquals(GrenadeSoundPolicy.Mode.NONE,
                GrenadeSoundPolicy.classify(WORLD, WORLD, Double.NaN, 0, 0, 0, 0, 0).mode());
        assertEquals(GrenadeSoundPolicy.Mode.NONE,
                GrenadeSoundPolicy.classify(WORLD, WORLD, 0, 0, 0, Double.POSITIVE_INFINITY, 0, 0).mode());
    }

    @Test
    void payloadOnlyPermitsValidNearOrFarData() {
        assertEquals(GrenadeSoundPayload.Mode.NEAR, GrenadeSoundPayload.near(WORLD, 1.5, 2.5, 3.5).mode());
        assertEquals(GrenadeSoundPayload.Mode.FAR, GrenadeSoundPayload.far(WORLD, .2f).mode());
        assertThrows(IllegalArgumentException.class, () -> GrenadeSoundPayload.far(WORLD, Float.NaN));
        assertThrows(IllegalArgumentException.class, () -> GrenadeSoundPayload.far(WORLD, 0));
        assertThrows(IllegalArgumentException.class, () -> GrenadeSoundPayload.near(WORLD, Double.NaN, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new GrenadeSoundPayload(WORLD,
                GrenadeSoundPayload.Mode.FAR, 1, 0, 0, .2f));
    }

    @Test
    void smallVariantCarriesSourceGainsAndAllowsNearFarOverlap() {
        var near = GrenadeSoundPayload.smallNear(WORLD, 0, 0, 0);
        var far = GrenadeSoundPayload.smallFar(WORLD);
        assertEquals(GrenadeSoundPayload.SoundSet.SMALL, near.soundSet());
        assertEquals(1f, near.gain());
        assertEquals(1.258925f, far.gain());
        assertThrows(IllegalArgumentException.class, () -> new GrenadeSoundPayload(WORLD, GrenadeSoundPayload.Mode.FAR, 0, 0, 0, 1.3f, GrenadeSoundPayload.SoundSet.SMALL));
    }
}
