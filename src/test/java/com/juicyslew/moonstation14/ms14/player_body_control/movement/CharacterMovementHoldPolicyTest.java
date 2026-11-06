package com.juicyslew.moonstation14.ms14.player_body_control.movement;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class CharacterMovementHoldPolicyTest {
    @Test void unknownPoseHoldsExactOwnerAndKnownPointResumesButIdentityLossIsTerminal() {
        assertEquals(CharacterMovementHoldPolicy.Decision.HOLD,
                CharacterMovementHoldPolicy.evaluate(true, false));
        assertEquals(CharacterMovementHoldPolicy.Decision.RESUME,
                CharacterMovementHoldPolicy.evaluate(true, true));
        assertEquals(CharacterMovementHoldPolicy.Decision.TERMINAL_GUARD,
                CharacterMovementHoldPolicy.evaluate(false, true));
    }

    @Test void snapshotsRequireFiniteBoundedWorldAndStoragePointsAndKnownFrameContext() {
        assertTrue(CharacterMovementSafePose.finiteBounded(new Vec3(1, 2, 3)));
        assertFalse(CharacterMovementSafePose.finiteBounded(new Vec3(Double.NaN, 2, 3)));
        assertFalse(CharacterMovementSafePose.finiteBounded(new Vec3(Double.POSITIVE_INFINITY, 2, 3)));
        assertTrue(CharacterMovementSafePose.snapshotCoordinatesAdmissible(
                new Vec3(1, 2, 3), null, true));
        assertFalse(CharacterMovementSafePose.snapshotCoordinatesAdmissible(
                new Vec3(Double.NaN, 2, 3), null, true));
        assertFalse(CharacterMovementSafePose.snapshotCoordinatesAdmissible(
                new Vec3(1, 2, 3), new Vec3(Double.NaN, 0, 0), true));
        assertFalse(CharacterMovementSafePose.snapshotCoordinatesAdmissible(
                new Vec3(1, 2, 3), null, false));
        assertFalse(CharacterMovementSafePose.snapshotAdmissible(null, new Vec3(1, 2, 3), null, true));
        assertFalse(CharacterMovementSafePose.snapshotAdmissible(null,
                new Vec3(Double.NaN, 2, 3), null, true));
        assertFalse(CharacterMovementSafePose.snapshotAdmissible(null,
                new Vec3(1, 2, 3), new Vec3(Double.NaN, 0, 0), true));
        assertFalse(CharacterMovementSafePose.snapshotAdmissible(null,
                new Vec3(1, 2, 3), null, false), "unknown frame context never becomes world-coordinate authority");
    }
}
