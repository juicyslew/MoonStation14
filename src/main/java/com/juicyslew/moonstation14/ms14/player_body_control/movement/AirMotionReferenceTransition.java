package com.juicyslew.moonstation14.ms14.player_body_control.movement;

import com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery;
import com.juicyslew.moonstation14.ms14.movement.AirMotionReference;
import com.juicyslew.moonstation14.ms14.movement.MovementVector;

/** Shared takeoff-reference transition applied only after a successful native/motor step. */
public final class AirMotionReferenceTransition {
    private AirMotionReferenceTransition() { }

    /** Converts the frozen world-space point velocity into the current motor basis (all values m/s). */
    public static MovementVector baselineInMotorBasis(AirMotionReference reference,
            MovementVector currentBasisPointVelocity) {
        if (reference == null) return null;
        if (currentBasisPointVelocity == null) return null;
        return new MovementVector(reference.pointVelocity().x() - currentBasisPointVelocity.x(),
                reference.pointVelocity().y() - currentBasisPointVelocity.y(),
                reference.pointVelocity().z() - currentBasisPointVelocity.z());
    }

    public static AirMotionReference departure(boolean nativeMoveFresh, boolean wasGrounded, boolean isGrounded,
            LoadedFrameRayQuery.ActorPoint before, LoadedFrameRayQuery.ActorPoint after,
            MovementVector frameVelocityDelta) {
        if (!nativeMoveFresh || !wasGrounded || isGrounded || before == null || before.frameId() == null
                || after == null || after.frameId() != null || frameVelocityDelta == null) return null;
        return new AirMotionReference(before.frameId(), frameVelocityDelta);
    }

    public static AirMotionReference afterSuccessfulMotor(boolean isGrounded, AirMotionReference motorReference,
                                                           AirMotionReference departure) {
        if (isGrounded) return null;
        return departure != null ? departure : motorReference;
    }
}
