package com.juicyslew.moonstation14.ms14.movement;

import java.util.Objects;

/** Deterministic single-tick kernel. This is math only, not Minecraft travel/collision simulation. */
public final class CharacterMovementMotor {
    private static final double MAX_WORLD_COORDINATE_ULP = Math.ulp(30_000_000d);
    // Large enough to survive world-coordinate subtraction, but too small to be meaningful movement.
    private static final double GROUNDED_SUPPORT_PROBE_BLOCKS = .001d;
    private final CharacterMovementPolicy policy;

    public CharacterMovementMotor(CharacterMovementPolicy policy) {
        this.policy = Objects.requireNonNull(policy, "policy");
    }

    public CharacterMovementPolicy policy() {
        return policy;
    }

    public CharacterMovementState tick(CharacterMovementState state, CharacterMovementCommand command,
                                       CharacterMovementEnvironment environment, boolean stunned) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(environment, "environment");
        double dt = environment.secondsPerTick();
        MovementVector velocity = state.velocity().add(environment.externalImpulse());
        CharacterMovementCommand effective = command.withStun(stunned);

        // SS14 applies a linear per-tick friction factor, clamped to [0, 1].
        // Minecraft's collision, gravity and jump values are deliberately injected by the adapter.
        if (state.onGround()) {
            boolean hasWish = effective.wishX() != 0d || effective.wishZ() != 0d;
            double friction = hasWish ? policy.groundFrictionWithInputPerSecond()
                    : policy.groundFrictionNoInputPerSecond();
            double frictionFactor = Math.max(0d, Math.min(1d,
                    1d - friction * environment.surfaceMovementFactor() * dt));
            double horizontalSpeed = Math.hypot(velocity.x(), velocity.z());
            if (horizontalSpeed >= policy.minimumFrictionSpeed()) {
                velocity = new MovementVector(velocity.x() * frictionFactor,
                        velocity.y(), velocity.z() * frictionFactor);
            }
        }

        if (effective.wishX() != 0d || effective.wishZ() != 0d) {
            double requestedSpeed = (effective.sprint() ? policy.sprintSpeedPerSecond() : policy.walkSpeedPerSecond())
                    * environment.voluntarySpeedFactor();
            double targetWishX = effective.wishX() * requestedSpeed;
            double targetWishZ = effective.wishZ() * requestedSpeed;
            double wishSpeed = Math.hypot(targetWishX, targetWishZ);
            if (wishSpeed > 0d) {
                double wishDirectionX = targetWishX / wishSpeed;
                double wishDirectionZ = targetWishZ / wishSpeed;
                double currentSpeed = velocity.x() * wishDirectionX + velocity.z() * wishDirectionZ;
                double addSpeed = wishSpeed - currentSpeed;
                if (addSpeed > 0d) {
                    double surfaceFactor = state.onGround() ? environment.surfaceMovementFactor() : 1d;
                    double acceleration = Math.min(policy.accelerationPerSecondSquared() * surfaceFactor * dt * wishSpeed,
                            addSpeed);
                    velocity = new MovementVector(velocity.x() + acceleration * wishDirectionX, velocity.y(),
                            velocity.z() + acceleration * wishDirectionZ);
                }
            }
        }

        if (effective.jumpRequested() && state.onGround()) {
            velocity = new MovementVector(velocity.x(), environment.jumpVelocityPerSecond(), velocity.z());
        }
        // Entity.travel moves using the current velocity, then applies gravity and vertical drag
        // for the next tick. Jump velocity therefore contributes its full first-tick displacement.
        MovementVector movementRequested = velocity.scale(dt);
        MovementVector requested = movementRequested;
        // Vanilla Entity.move only refreshes its grounded flag after a clipped downward request.
        // Probe just below a stationary grounded entity so real floor collision retains support.
        // Do not probe during jumps or upward impulses, and let an unsupported probe fall normally.
        if (state.onGround() && !effective.jumpRequested() && velocity.y() == 0d
                && environment.gravityPerSecondSquared() > 0d) {
            requested = new MovementVector(requested.x(), requested.y() - GROUNDED_SUPPORT_PROBE_BLOCKS,
                    requested.z());
        }
        MovementCollisionResolver.CollisionResult resolved = Objects.requireNonNull(
                environment.collisionResolver().resolve(state.position(), requested, state.onGround()),
                "collision resolver result");
        MovementVector actual = resolved.actualDisplacement();
        ensureBoundedResolution(state.position(), requested, actual,
                environment.maxUpwardStepBlocks(), state.onGround());
        MovementVector position = state.position().add(actual);

        // A blocked axis cannot retain velocity into the blocking surface. The resolver reports displacement,
        // so compare each axis to the requested displacement rather than fabricating collision response.
        double vx = blocked(state.position().x(), requested.x(), actual.x()) ? 0d : velocity.x();
        double vy = movementRequested.y() != 0d
                && blocked(state.position().y(), movementRequested.y(), actual.y()) ? 0d
                : (velocity.y() - environment.gravityPerSecondSquared() * dt) * environment.verticalDrag();
        double vz = blocked(state.position().z(), requested.z(), actual.z()) ? 0d : velocity.z();
        if (resolved.onGround() && vy < 0d) vy = 0d;
        return new CharacterMovementState(position, new MovementVector(vx, vy, vz), resolved.onGround());
    }

    private static boolean blocked(double position, double requested, double actual) {
        return Math.abs(requested - actual) > roundingAllowance(position, requested);
    }

    private static void ensureBoundedResolution(MovementVector position, MovementVector requested, MovementVector actual,
                                                 double maxUpwardStepBlocks, boolean wasOnGround) {
        boolean requestedHorizontally = Math.hypot(requested.x(), requested.z()) >
                Math.hypot(roundingAllowance(position.x(), requested.x()),
                        roundingAllowance(position.z(), requested.z()));
        boolean movedHorizontally = Math.hypot(actual.x(), actual.z()) >
                Math.hypot(roundingAllowance(position.x(), requested.x()),
                        roundingAllowance(position.z(), requested.z()));
        double yAllowance = roundingAllowance(position.y(), requested.y());
        // Entity.move bounds its upward candidate by the larger of maxUpStep and the upward
        // displacement already requested.
        boolean steppedUp = wasOnGround && requestedHorizontally && movedHorizontally
                && actual.y() >= -yAllowance
                && actual.y() <= Math.max(maxUpwardStepBlocks, Math.max(0d, requested.y())) + yAllowance;
        if (!within(position.x(), requested.x(), actual.x())
                || (!steppedUp && !within(position.y(), requested.y(), actual.y()))
                || !within(position.z(), requested.z(), actual.z())) {
            throw new IllegalArgumentException("collision resolution must be finite and bounded by requested displacement");
        }
    }

    private static boolean within(double position, double requested, double actual) {
        double allowance = roundingAllowance(position, requested);
        return Double.isFinite(actual) && Math.abs(actual) <= Math.abs(requested) + allowance
                && (requested == 0d || Math.signum(actual) == 0d
                || Math.signum(actual) == Math.signum(requested) || Math.abs(actual) <= allowance);
    }

    /** Bounds displacement error from adding the request to a world coordinate then subtracting it. */
    private static double roundingAllowance(double position, double requested) {
        double endpoint = position + requested;
        if (!Double.isFinite(endpoint)) return 0d;
        return Math.min(Math.ulp(position) + Math.ulp(endpoint), MAX_WORLD_COORDINATE_ULP);
    }
}
