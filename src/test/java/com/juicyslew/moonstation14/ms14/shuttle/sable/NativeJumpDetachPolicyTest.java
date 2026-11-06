package com.juicyslew.moonstation14.ms14.shuttle.sable;

import org.junit.jupiter.api.Test;
import com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class NativeJumpDetachPolicyTest {
    private static boolean accepted(boolean motor, boolean grounded, double requested, double moved,
                                    boolean fresh, boolean postGrounded) {
        return SableOwnedBodyMotion.confirmsNativeJumpDetach(motor, grounded, requested, moved, fresh, postGrounded);
    }

    @Test
    void detachesOnlyOnFreshFullNativeMotorJump() {
        assertTrue(accepted(true, true, .42, .4200000000000159, true, false));
        assertFalse(accepted(false, true, .42, .42, true, false));
        assertFalse(accepted(true, false, .42, .42, true, false));
        assertFalse(accepted(true, true, .42, .12, true, false)); // ceiling clip
        assertFalse(accepted(true, true, .42, .42, false, false));
        assertFalse(accepted(true, true, .42, .42, true, true));
        assertFalse(accepted(true, true, -.1, -.1, true, false));
    }

    @Test
    void requiresLiveSameFrameEvidenceBeforeReleasingTracking() {
        UUID frame = UUID.randomUUID();
        var before = new LoadedFrameRayQuery.ActorPoint(Vec3.ZERO, Vec3.ZERO, frame);
        var carried = new LoadedFrameRayQuery.ActorPoint(new Vec3(1, 2, 3), Vec3.ZERO, frame);
        assertTrue(SableOwnedBodyMotion.confirmsMatchingJumpFrame(before, carried, frame, frame));
        assertFalse(SableOwnedBodyMotion.confirmsMatchingJumpFrame(before, null, frame, frame));
        assertFalse(SableOwnedBodyMotion.confirmsMatchingJumpFrame(before,
                new LoadedFrameRayQuery.ActorPoint(Vec3.ZERO, Vec3.ZERO, UUID.randomUUID()), frame, frame));
        assertFalse(SableOwnedBodyMotion.confirmsMatchingJumpFrame(before, carried, frame, UUID.randomUUID()));
        assertFalse(SableOwnedBodyMotion.confirmsMatchingJumpFrame(before, carried, null, frame));
    }

    @Test
    void ascendingReleaseAllowsWorldOriginButOnlyClearsExactFreshContactFrame() {
        UUID frame = UUID.randomUUID();
        var worldOrigin = new LoadedFrameRayQuery.ActorPoint(Vec3.ZERO, Vec3.ZERO, null);
        var carried = new LoadedFrameRayQuery.ActorPoint(Vec3.ZERO, Vec3.ZERO, frame);
        assertTrue(SableOwnedBodyMotion.ascendingTrackingReleaseMatches(frame, worldOrigin, carried, frame));
        assertFalse(SableOwnedBodyMotion.ascendingTrackingReleaseMatches(UUID.randomUUID(), worldOrigin, carried, frame));
        assertFalse(SableOwnedBodyMotion.ascendingTrackingReleaseMatches(frame, worldOrigin,
                new LoadedFrameRayQuery.ActorPoint(Vec3.ZERO, Vec3.ZERO, UUID.randomUUID()), frame));
        assertFalse(SableOwnedBodyMotion.ascendingTrackingReleaseMatches(frame, worldOrigin, carried, UUID.randomUUID()));
        assertTrue(SableOwnedBodyMotion.ascendingTrackingReleaseMatches(frame, carried, carried, frame));
        assertFalse(SableOwnedBodyMotion.ascendingTrackingReleaseMatches(frame, null, null, frame));
    }

    @Test
    void ascendingReleaseRequiresRefreshedWorldPointPostcondition() {
        UUID frame = UUID.randomUUID();
        assertTrue(SableOwnedBodyMotion.confirmsAscendingWorldRelease(
                new LoadedFrameRayQuery.ActorPoint(Vec3.ZERO, Vec3.ZERO, null)));
        assertFalse(SableOwnedBodyMotion.confirmsAscendingWorldRelease(null));
        assertFalse(SableOwnedBodyMotion.confirmsAscendingWorldRelease(
                new LoadedFrameRayQuery.ActorPoint(Vec3.ZERO, Vec3.ZERO, frame)));
    }
}
