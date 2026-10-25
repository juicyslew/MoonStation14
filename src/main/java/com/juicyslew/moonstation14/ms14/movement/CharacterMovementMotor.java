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
        return tick(state, command, environment, stunned, false);
    }

    /** External flight preserves launch momentum while suppressing voluntary steering and ground damping. */
    public CharacterMovementState tick(CharacterMovementState state, CharacterMovementCommand command,
                                        CharacterMovementEnvironment environment, boolean stunned,
                                        boolean externalFlight) {
        return tick(state, command, environment, stunned, externalFlight, 0d, false);
    }

    /** Natural flight remains in this motor/collision route; verticalWish is bounded speed, never a teleport. */
    public CharacterMovementState tick(CharacterMovementState state, CharacterMovementCommand command,
                                        CharacterMovementEnvironment environment, boolean stunned,
                                        boolean externalFlight, double verticalWish, boolean naturalFlight) {
        if (!Double.isFinite(verticalWish) || Math.abs(verticalWish) > 20d)
            throw new IllegalArgumentException("vertical speed must be finite and bounded");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(environment, "environment");
        double dt = environment.secondsPerTick();
        GravityAirControl gravityAirControl = !state.onGround() && !naturalFlight && !externalFlight
                && environment.gravityPerSecondSquared() > 0d
                ? environment.gravityAirControl().orElse(null) : null;
        double horizontalRetention = gravityAirControl == null ? 1d : gravityAirControl.retentionFor(dt);
        MovementVector velocity = state.velocity().add(environment.externalImpulse());
        CharacterMovementCommand effective = command.withStun(stunned);

        // SS14's horizontal friction/wish motor runs at 60 Hz, while the Minecraft
        // adapter invokes this kernel at 20 Hz. Integrate grounded and natural-flight
        // horizontal behavior in bounded substeps; retain one native collision sweep.
        double horizontalX = 0d, horizontalZ = 0d;
        boolean dampHorizontal = (state.onGround() || naturalFlight) && !externalFlight;
        int horizontalSubsteps = dampHorizontal
                ? Math.max(1, (int) Math.ceil(dt * 60d)) : 1;
        double horizontalDt = dt / horizontalSubsteps;
        for (int substep = 0; substep < horizontalSubsteps; substep++) {
            if (dampHorizontal) {
                boolean hasWish = effective.wishX() != 0d || effective.wishZ() != 0d;
                double friction = hasWish ? policy.groundFrictionWithInputPerSecond()
                        : policy.groundFrictionNoInputPerSecond();
                double surfaceFrictionFactor = state.onGround() ? environment.surfaceMovementFactor() : 1d;
                double frictionFactor = Math.max(0d, Math.min(1d,
                        1d - friction * surfaceFrictionFactor * horizontalDt));
                double horizontalSpeed = Math.hypot(velocity.x(), velocity.z());
                if (horizontalSpeed >= policy.minimumFrictionSpeed()) {
                    velocity = new MovementVector(velocity.x() * frictionFactor, velocity.y(),
                            velocity.z() * frictionFactor);
                } else if (naturalFlight && !externalFlight && !hasWish) {
                    // The normal friction threshold intentionally avoids tiny-speed
                    // damping. Natural flight must nevertheless settle at rest.
                    velocity = new MovementVector(0d, velocity.y(), 0d);
                }
            }
            if (!externalFlight && (effective.wishX() != 0d || effective.wishZ() != 0d)) {
                double preSteeringSpeed = Math.hypot(velocity.x(), velocity.z());
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
                        double acceleration = gravityAirControl == null
                                ? policy.accelerationPerSecondSquared() * surfaceFactor * horizontalDt * wishSpeed
                                : gravityAirControl.inputAccelerationPerSecondSquared() * horizontalDt
                                * Math.hypot(effective.wishX(), effective.wishZ()) * environment.voluntarySpeedFactor();
                        acceleration = Math.min(acceleration, addSpeed);
                        // The legacy empty-profile path preserves its original SS14 scalar budget.
                        // Adapter gravity-air budgets account for the post-move damping that creates steering room.
                        if (!state.onGround() && !naturalFlight && environment.gravityPerSecondSquared() > 0d) {
                            double budget = Math.max(preSteeringSpeed, wishSpeed) / horizontalRetention;
                            double remainingSquared = Math.max(0d, budget * budget
                                    - velocity.x() * velocity.x() - velocity.z() * velocity.z());
                            double allowableAcceleration = Math.max(0d,
                                    Math.sqrt(currentSpeed * currentSpeed + remainingSquared) - currentSpeed);
                            acceleration = Math.min(acceleration, allowableAcceleration);
                        }
                        velocity = new MovementVector(velocity.x() + acceleration * wishDirectionX, velocity.y(),
                                velocity.z() + acceleration * wishDirectionZ);
                    }
                }
            }
            horizontalX += velocity.x() * horizontalDt;
            horizontalZ += velocity.z() * horizontalDt;
        }

        if (!externalFlight && effective.jumpRequested() && state.onGround()) {
            velocity = new MovementVector(velocity.x(), environment.jumpVelocityPerSecond(), velocity.z());
        }
        // Tether forces act after voluntary inputs and jump, so standing friction cannot erase them.
        velocity = velocity.add(environment.constraintImpulse());
        // Entity.travel moves using the current velocity, then applies gravity and vertical drag
        // for the next tick. Jump velocity therefore contributes its full first-tick displacement.
        if (naturalFlight && !externalFlight) {
            // Flight suppresses ambient gravity below, not force owners. The vertical target replaces
            // carried vertical momentum for stable hover, while adding each tick's singularity and
            // tether impulses exactly once, just as the grounded/throw paths do.
            velocity = new MovementVector(velocity.x(), verticalWish + environment.externalImpulse().y()
                    + environment.constraintImpulse().y(), velocity.z());
        }
        MovementVector movementRequested = velocity.scale(dt);
        MovementVector requested = new MovementVector(horizontalX + environment.constraintImpulse().x() * dt,
                movementRequested.y(), horizontalZ + environment.constraintImpulse().z() * dt);
        // Vanilla Entity.move only refreshes its grounded flag after a clipped downward request.
        // Probe just below a stationary grounded entity so real floor collision retains support.
        // Do not probe during jumps or upward impulses, and let an unsupported probe fall normally.
        if (state.onGround() && !effective.jumpRequested() && velocity.y() == 0d
                && !naturalFlight && environment.gravityPerSecondSquared() > 0d) {
            requested = new MovementVector(requested.x(), requested.y() - GROUNDED_SUPPORT_PROBE_BLOCKS,
                    requested.z());
        }
        MovementCollisionResolver.CollisionResult resolved = Objects.requireNonNull(
                environment.collisionResolver().resolve(state.position(), requested, state.onGround()),
                "collision resolver result");
        MovementVector actual = resolved.actualDisplacement();
        ensureBoundedResolution(state.position(), requested, actual,
                environment.maxUpwardStepBlocks(), state.onGround(), resolved.onGround());
        MovementVector position = state.position().add(actual);

        // A blocked axis cannot retain velocity into the blocking surface. The resolver reports displacement,
        // so compare each axis to the requested displacement rather than fabricating collision response.
        boolean blockedX = blocked(state.position().x(), requested.x(), actual.x());
        double vx = blockedX && pointsIntoBlockedHorizontalAxis(requested.x(), velocity.x()) ? 0d : velocity.x();
        double vy = movementRequested.y() != 0d
                && blocked(state.position().y(), movementRequested.y(), actual.y()) ? 0d
                : naturalFlight ? velocity.y()
                : (velocity.y() - environment.gravityPerSecondSquared() * dt) * environment.verticalDrag();
        boolean blockedZ = blocked(state.position().z(), requested.z(), actual.z());
        double vz = blockedZ && pointsIntoBlockedHorizontalAxis(requested.z(), velocity.z()) ? 0d : velocity.z();
        vx *= horizontalRetention;
        vz *= horizontalRetention;
        if (resolved.onGround() && vy < 0d) vy = 0d;
        return new CharacterMovementState(position, new MovementVector(vx, vy, vz), resolved.onGround());
    }

    private static boolean blocked(double position, double requested, double actual) {
        return Math.abs(requested - actual) > roundingAllowance(position, requested);
    }

    /** The sweep follows average substep displacement; only erase terminal velocity into that wall. */
    private static boolean pointsIntoBlockedHorizontalAxis(double requestedDisplacement, double terminalVelocity) {
        return requestedDisplacement != 0d && terminalVelocity != 0d
                && Math.signum(requestedDisplacement) == Math.signum(terminalVelocity);
    }

    private static void ensureBoundedResolution(MovementVector position, MovementVector requested, MovementVector actual,
                                                 double maxUpwardStepBlocks, boolean wasOnGround, boolean resolvedOnGround) {
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
