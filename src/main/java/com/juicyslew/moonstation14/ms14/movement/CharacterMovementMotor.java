package com.juicyslew.moonstation14.ms14.movement;

import java.util.Objects;
import com.juicyslew.moonstation14.ms14.player_body_control.movement.ExternalFlightPhysics;

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
        return tick(state, command, environment, stunned, externalFlight, MovementVector.ZERO, 0d, false);
    }

    /** Baseline is already expressed in this motor's velocity basis and is excluded from horizontal control only. */
    public CharacterMovementState tick(CharacterMovementState state, CharacterMovementCommand command,
                                       CharacterMovementEnvironment environment, boolean stunned,
                                       boolean externalFlight, MovementVector controlBaseline) {
        return tick(state, command, environment, stunned, externalFlight, controlBaseline, 0d, false);
    }

    /** Legacy convenience path with no support baseline and scripted-impulse flight policy. */
    public CharacterMovementState tick(CharacterMovementState state, CharacterMovementCommand command,
                                       CharacterMovementEnvironment environment, boolean stunned,
                                       boolean externalFlight, double verticalWish, boolean naturalFlight) {
        return tick(state, command, environment, stunned, externalFlight, MovementVector.ZERO,
                verticalWish, naturalFlight, ExternalFlightPhysics.SCRIPTED_IMPULSE);
    }

    /** Natural flight remains in this motor/collision route; verticalWish is bounded speed, never a teleport. */
    public CharacterMovementState tick(CharacterMovementState state, CharacterMovementCommand command,
                                       CharacterMovementEnvironment environment, boolean stunned,
                                       boolean externalFlight, MovementVector controlBaseline,
                                       double verticalWish, boolean naturalFlight) {
        return tick(state, command, environment, stunned, externalFlight, controlBaseline, verticalWish,
                naturalFlight, ExternalFlightPhysics.SCRIPTED_IMPULSE);
    }

    /** External-flight mode changes vertical ambient physics, not steering suppression or collision routing. */
    public CharacterMovementState tick(CharacterMovementState state, CharacterMovementCommand command,
                                       CharacterMovementEnvironment environment, boolean stunned,
                                       boolean externalFlight, MovementVector controlBaseline,
                                       double verticalWish, boolean naturalFlight, ExternalFlightPhysics flightPhysics) {
        if (!Double.isFinite(verticalWish) || Math.abs(verticalWish) > 20d)
            throw new IllegalArgumentException("vertical speed must be finite and bounded");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(environment, "environment");
        double dt = environment.secondsPerTick();
        Objects.requireNonNull(controlBaseline, "controlBaseline");
        Objects.requireNonNull(flightPhysics, "flightPhysics");
        GravityAirControl gravityAirControl = !state.onGround() && !naturalFlight && !externalFlight
                && environment.gravityPerSecondSquared() > 0d
                ? environment.gravityAirControl().orElse(null) : null;
        double horizontalRetention = gravityAirControl == null ? 1d : gravityAirControl.retentionFor(dt);
        MovementVector velocity = state.velocity().add(environment.externalImpulse());
        // Apply friction, steering and horizontal caps to relative motion, while preserving the
        // support baseline for the actual movement/collision request below.
        velocity = new MovementVector(velocity.x() - controlBaseline.x(), velocity.y() - controlBaseline.y(),
                velocity.z() - controlBaseline.z());
        CharacterMovementCommand effective = command.withStun(stunned);

        // The SS14-derived friction/wish policy is integrated in bounded arithmetic
        // substeps within Minecraft's 20 Hz adapter tick. This is an integration
        // cadence adaptation, not a claim that upstream guarantees 60 Hz simulation.
        boolean boundedGravityAirSteering = !state.onGround() && !naturalFlight
                && environment.gravityPerSecondSquared() > 0d && !externalFlight;
        double horizontalX = 0d, horizontalZ = 0d;
        boolean dampHorizontal = (state.onGround() || naturalFlight) && !externalFlight;
        // Grounded movement retains M1's single frame-time-scaled friction/acceleration step.
        // Natural flight keeps the incoming bounded integration cadence.
        int horizontalSubsteps = dampHorizontal && !state.onGround()
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

        if (state.onGround()) {
            // M1 applies the resulting grounded velocity for the full adapter tick.
            horizontalX = velocity.x() * dt;
            horizontalZ = velocity.z() * dt;
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
        if (naturalFlight && !externalFlight) {
            // Flight suppresses ambient gravity below, not force owners. The vertical target replaces
            // carried vertical momentum for stable hover, while adding each tick's singularity and
            // tether impulses exactly once, just as the grounded/throw paths do.
            velocity = new MovementVector(velocity.x(), verticalWish + environment.externalImpulse().y()
                    + environment.constraintImpulse().y(), velocity.z());
        }
        MovementVector movementRequested = velocity.scale(dt);
        MovementVector requested = new MovementVector(horizontalX + controlBaseline.x() * dt
                + environment.constraintImpulse().x() * dt, movementRequested.y(),
                horizontalZ + controlBaseline.z() * dt + environment.constraintImpulse().z() * dt);
        boolean supportProbeApplied = false;
        // Vanilla Entity.move only refreshes its grounded flag after a clipped downward request.
        // Probe just below a stationary grounded entity so real floor collision retains support.
        // Do not probe during jumps or upward impulses, and let an unsupported probe fall normally.
        if (state.onGround() && !effective.jumpRequested() && velocity.y() == 0d
                && !naturalFlight && environment.gravityPerSecondSquared() > 0d) {
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
        double relativeVx = velocity.x() - controlBaseline.x();
        double vx = !certifiedHorizontalContact && blocked(state.position().x(), requested.x(), actual.x())
                && pointsIntoBlockedHorizontalAxis(requested.x(), velocity.x()) ? 0d : relativeVx * horizontalRetention + controlBaseline.x();
        double retainedVerticalVelocity = movementRequested.y() != 0d
                && blocked(state.position().y(), movementRequested.y(), actual.y()) ? 0d : velocity.y();
        double relativeVertical = retainedVerticalVelocity - controlBaseline.y();
        boolean scriptedFlight = externalFlight && flightPhysics == ExternalFlightPhysics.SCRIPTED_IMPULSE;
        double vy = scriptedFlight || naturalFlight && !externalFlight ? retainedVerticalVelocity
                : (relativeVertical - environment.gravityPerSecondSquared() * dt) * environment.verticalDrag()
                + controlBaseline.y();
        double relativeVz = velocity.z() - controlBaseline.z();
        double vz = !certifiedHorizontalContact && blocked(state.position().z(), requested.z(), actual.z())
                && pointsIntoBlockedHorizontalAxis(requested.z(), velocity.z()) ? 0d : relativeVz * horizontalRetention + controlBaseline.z();
        if (resolved.onGround() && vy < 0d) vy = 0d;
        return new CharacterMovementState(position, new MovementVector(vx, vy, vz), resolved.onGround(),
                resolved.onGround() ? null : state.airMotionReference());
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
