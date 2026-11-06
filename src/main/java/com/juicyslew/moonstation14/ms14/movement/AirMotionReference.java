package com.juicyslew.moonstation14.ms14.movement;

import java.util.Objects;
import java.util.UUID;

/** Frozen world-space velocity of the support point at takeoff; Minecraft adaptation, not SS14 component parity. */
public record AirMotionReference(UUID frameId, MovementVector pointVelocity) {
    public AirMotionReference {
        Objects.requireNonNull(frameId, "frameId");
        Objects.requireNonNull(pointVelocity, "pointVelocity");
    }

    /** Decompose world velocity into frozen support motion B and controllable relative motion U. */
    public MovementVector relativeVelocity(MovementVector worldVelocity) {
        Objects.requireNonNull(worldVelocity, "worldVelocity");
        return new MovementVector(worldVelocity.x() - pointVelocity.x(), worldVelocity.y() - pointVelocity.y(),
                worldVelocity.z() - pointVelocity.z());
    }

    /** Recompose after changing U; support motion itself is never accelerated or capped by the motor. */
    public MovementVector worldVelocity(MovementVector relativeVelocity) {
        Objects.requireNonNull(relativeVelocity, "relativeVelocity");
        return new MovementVector(relativeVelocity.x() + pointVelocity.x(), relativeVelocity.y() + pointVelocity.y(),
                relativeVelocity.z() + pointVelocity.z());
    }
}
