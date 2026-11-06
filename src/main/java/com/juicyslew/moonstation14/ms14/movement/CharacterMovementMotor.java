package com.juicyslew.moonstation14.ms14.movement;

import java.util.Objects;

/**
 * Deterministic single-tick kernel. This is math only, not Minecraft travel/collision simulation.
 * Parity note (SS14-dev/space-station-14 c9df5ef5d675b0d1d226828bddf6b78c28502d91,
 * Content.Shared/Movement/Systems/SharedMoverController.cs): SS14 computes and applies mover velocity;
 * its collision solver does not supply this Minecraft adapter's per-axis accepted-displacement evidence.
 * HorizontalContactEvidence is a narrow Sable-native Minecraft adaptation, not a claim of full Robust
 * solver parity (that exact solver audit remains unverified). Gravity, step allowance, movement policy
 * values, and prototypes are unchanged; vertical resolution remains on the existing strict adapter path.
 */
public final class CharacterMovementMotor {
    private static final double MAX_WORLD_COORDINATE_ULP = Math.ulp(30_000_000d);
    // Large enough to survive world-coordinate subtraction, but too small to be meaningful movement.
    public static final double GROUNDED_SUPPORT_PROBE_BLOCKS = .001d;
    private final CharacterMovementPolicy policy;

    public CharacterMovementMotor(CharacterMovementPolicy policy) {
        this.policy = Objects.requireNonNull(policy, "policy");
    }

    public CharacterMovementPolicy policy() {
        return policy;
    }

    public CharacterMovementState tick(CharacterMovementState state, CharacterMovementCommand command,
                                       CharacterMovementEnvironment environment, boolean stunned) {
        return tick(state, command, environment, stunned, false);
    }

    /** External flight preserves launch momentum while suppressing voluntary steering and ground damping. */
    public CharacterMovementState tick(CharacterMovementState state, CharacterMovementCommand command,
                                       CharacterMovementEnvironment environment, boolean stunned,
                                       boolean externalFlight) {
        return tick(state, command, environment, stunned, externalFlight, MovementVector.ZERO);
    }

    /** Baseline is already expressed in this motor's velocity basis and is excluded from horizontal control only. */
    public CharacterMovementState tick(CharacterMovementState state, CharacterMovementCommand command,
                                       CharacterMovementEnvironment environment, boolean stunned,
                                       boolean externalFlight, MovementVector controlBaseline) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(environment, "environment");
        double dt = environment.secondsPerTick();
        Objects.requireNonNull(controlBaseline, "controlBaseline");
        MovementVector velocity = state.velocity().add(environment.externalImpulse());
        // Apply friction, steering and horizontal caps to relative motion, while preserving the
        // support baseline for the actual movement/collision request below.
        velocity = new MovementVector(velocity.x() - controlBaseline.x(), velocity.y() - controlBaseline.y(),
                velocity.z() - controlBaseline.z());
        CharacterMovementCommand effective = command.withStun(stunned);

        // SS14 applies a linear per-tick friction factor, clamped to [0, 1].
        // Minecraft's collision, gravity and jump values are deliberately injected by the adapter.
        if (state.onGround() && !externalFlight) {
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

        boolean boundedGravityAirSteering = !state.onGround() && environment.gravityPerSecondSquared() > 0d
                && !externalFlight;
        double preAccelerationHorizontalSpeed = Math.hypot(velocity.x(), velocity.z());
        if (!externalFlight && (effective.wishX() != 0d || effective.wishZ() != 0d)) {
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
                // In gravity-driven air, redirecting the projected wish must not accumulate horizontal speed.
                // Weightless projected-wish movement intentionally retains its existing behavior.
                if (boundedGravityAirSteering) {
                    double maximumHorizontalSpeed = Math.max(preAccelerationHorizontalSpeed, wishSpeed);
                    double horizontalSpeed = Math.hypot(velocity.x(), velocity.z());
                    if (horizontalSpeed > maximumHorizontalSpeed) {
                        double scale = maximumHorizontalSpeed / horizontalSpeed;
                        velocity = new MovementVector(velocity.x() * scale, velocity.y(), velocity.z() * scale);
                    }
                }
            }
        }

        boolean motorJumpInitiated = !externalFlight && effective.jumpRequested() && state.onGround();
        if (motorJumpInitiated) {
            velocity = new MovementVector(velocity.x(), environment.jumpVelocityPerSecond(), velocity.z());
        }
        // Tether forces act after voluntary inputs and jump, so standing friction cannot erase them.
        velocity = velocity.add(environment.constraintImpulse());
        velocity = new MovementVector(velocity.x() + controlBaseline.x(), velocity.y() + controlBaseline.y(),
                velocity.z() + controlBaseline.z());
        // Entity.travel moves using the current velocity, then applies gravity and vertical drag
        // for the next tick. Jump velocity therefore contributes its full first-tick displacement.
        MovementVector movementRequested = velocity.scale(dt);
        MovementVector requested = movementRequested;
        boolean supportProbeApplied = false;
        // Vanilla Entity.move only refreshes its grounded flag after a clipped downward request.
        // Probe just below a stationary grounded entity so real floor collision retains support.
        // Do not probe during jumps or upward impulses, and let an unsupported probe fall normally.
        if (state.onGround() && !effective.jumpRequested() && velocity.y() == 0d
                && environment.gravityPerSecondSquared() > 0d) {
            requested = new MovementVector(requested.x(), requested.y() - GROUNDED_SUPPORT_PROBE_BLOCKS,
                    requested.z());
            supportProbeApplied = true;
        }
        boolean idleSupportProbe = supportProbeApplied && movementRequested.equals(MovementVector.ZERO)
                && effective.wishX() == 0d && effective.wishZ() == 0d && !externalFlight
                && environment.externalImpulse().equals(MovementVector.ZERO)
                && environment.constraintImpulse().equals(MovementVector.ZERO);
        MovementCollisionResolver.CollisionResult resolved = Objects.requireNonNull(
                environment.collisionResolver().resolve(state.position(), requested, state.onGround(), idleSupportProbe,
                        motorJumpInitiated),
                "collision resolver result");
        MovementVector actual = resolved.actualDisplacement();
        HorizontalContactEvidence contactEvidence = resolved.horizontalContactEvidence();
        boolean certifiedHorizontalContact = contactEvidence != null && contactEvidence.matches(requested, actual);
        ensureBoundedResolution(state.position(), requested, actual,
                environment.maxUpwardStepBlocks(), state.onGround(), resolved.onGround(), certifiedHorizontalContact);
        var positionCorrection = resolved.positionCorrection();
        boolean certifiedPositionCorrection = positionCorrection != null
                && positionCorrection.matches(requested, actual, resolved.onGround());
        MovementVector position = state.position().add(actual).add(certifiedPositionCorrection
                ? positionCorrection.geometryCorrection() : MovementVector.ZERO);

        // A blocked axis cannot retain velocity into the blocking surface. The resolver reports displacement,
        // so compare each axis to the requested displacement rather than fabricating collision response.
        if (certifiedHorizontalContact) velocity = contactEvidence.projectVelocityTangent(velocity);
        double vx = !certifiedHorizontalContact && blocked(state.position().x(), requested.x(), actual.x())
                ? 0d : velocity.x();
        double retainedVerticalVelocity = movementRequested.y() != 0d
                && blocked(state.position().y(), movementRequested.y(), actual.y()) ? 0d : velocity.y();
        double vy = externalFlight ? retainedVerticalVelocity
                : (retainedVerticalVelocity - environment.gravityPerSecondSquared() * dt) * environment.verticalDrag()
                + controlBaseline.y() * (1d - environment.verticalDrag());
        double vz = !certifiedHorizontalContact && blocked(state.position().z(), requested.z(), actual.z())
                ? 0d : velocity.z();
        if (resolved.onGround() && vy < 0d) vy = 0d;
        return new CharacterMovementState(position, new MovementVector(vx, vy, vz), resolved.onGround(),
                resolved.onGround() ? null : state.airMotionReference());
    }

    private static boolean blocked(double position, double requested, double actual) {
        return Math.abs(requested - actual) > roundingAllowance(position, requested);
    }

    private static void ensureBoundedResolution(MovementVector position, MovementVector requested, MovementVector actual,
                                                 double maxUpwardStepBlocks, boolean wasOnGround, boolean resolvedOnGround,
                                                 boolean certifiedHorizontalContact) {
        boolean requestedHorizontally = Math.hypot(requested.x(), requested.z()) >
                Math.hypot(roundingAllowance(position.x(), requested.x()),
                        roundingAllowance(position.z(), requested.z()));
        boolean movedHorizontally = Math.hypot(actual.x(), actual.z()) >
                Math.hypot(roundingAllowance(position.x(), requested.x()),
                        roundingAllowance(position.z(), requested.z()));
        double yAllowance = roundingAllowance(position.y(), requested.y());
        // Entity.move bounds its upward candidate by the larger of maxUpStep and the upward
        // displacement already requested.
        // Vanilla Entity.move may step when already grounded, or when descending movement
        // clips against the floor and leaves the entity grounded after the move.
        boolean steppedUp = (wasOnGround || (requested.y() < 0d && resolvedOnGround))
                && requestedHorizontally && movedHorizontally
                && actual.y() >= -yAllowance
                && actual.y() <= Math.max(maxUpwardStepBlocks, Math.max(0d, requested.y())) + yAllowance;
        if ((!certifiedHorizontalContact && !within(position.x(), requested.x(), actual.x()))
                || (!steppedUp && !within(position.y(), requested.y(), actual.y()))
                || (!certifiedHorizontalContact && !within(position.z(), requested.z(), actual.z()))) {
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
