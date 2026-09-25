package com.juicyslew.moonstation14.ms14.movement;

/** World adapter boundary; implementations resolve requested displacement using their actual collision world. */
@FunctionalInterface
public interface MovementCollisionResolver {
    CollisionResult resolve(MovementVector position, MovementVector requestedDisplacement, boolean wasOnGround);

    record CollisionResult(MovementVector actualDisplacement, boolean onGround) {
        public CollisionResult {
            if (actualDisplacement == null) throw new IllegalArgumentException("actual displacement is required");
        }
    }
}
