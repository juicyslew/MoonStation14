package com.juicyslew.moonstation14.ms14.player_body_control.character;

import com.juicyslew.moonstation14.ms14.character.CharacterControlSystem;
import com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem;
import com.juicyslew.moonstation14.ms14.character.components.MovementSpeedModifierComponent;
import com.juicyslew.moonstation14.ms14.loose_objects.pulling.BodyPullService;
import com.juicyslew.moonstation14.ms14.movement.CharacterMovementEnvironment;
import com.juicyslew.moonstation14.ms14.movement.CharacterMovementPolicy;
import com.juicyslew.moonstation14.ms14.movement.CharacterMovementState;
import com.juicyslew.moonstation14.ms14.movement.MovementCollisionResolver;
import com.juicyslew.moonstation14.ms14.movement.MovementVector;
import com.juicyslew.moonstation14.ms14.player_body_control.movement.GroundedHarnessMotor;
import com.juicyslew.moonstation14.ms14.player_body_control.movement.GroundedHarnessPhysics;
import com.juicyslew.moonstation14.ms14.player_body_control.movement.ExternalBodyFlight;
import com.juicyslew.moonstation14.ms14.player_body_control.movement.OwnedHarnessWalkAnimation;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessEntity;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessRegistration;
import com.juicyslew.moonstation14.ms14.slip.SlidingFrictionSystem;
import com.juicyslew.moonstation14.ms14.slip.SlipSystem;
import com.juicyslew.moonstation14.ms14.standing.PostureSystem;
import com.juicyslew.moonstation14.ms14.containment.BodyContainmentService;
import com.juicyslew.moonstation14.ms14.containment.ContainedBodyOwner;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import com.juicyslew.moonstation14.MoonStation14;

import java.util.Optional;
import java.util.function.BooleanSupplier;
import com.juicyslew.moonstation14.ms14.animals.flight.FlightSystem;
import com.juicyslew.moonstation14.ms14.animals.flight.FlightState;
import com.juicyslew.moonstation14.ms14.animals.flight.FlightTransitionPolicy;
import com.juicyslew.moonstation14.ms14.character.components.FlightComponent;

/** One explicit server-side collision-resolved step for an enrolled, movement-owned grounded mob. */
public final class GroundedHarnessWorldStep {

    /**
     * Runs exactly one step. Empty means validation failed or the motor rejected the result; Entity.move
     * may already have moved the body in the latter case, so callers must not retry a rejected partial move.
     * Slip-launch override remains a stop gate before any live movement ownership transfer.
     */
    public Optional<CharacterMovementState> step(Mob body, int wishX, int wishZ,
                                                   boolean jump, boolean sprint, float yaw) {
        return step(body, wishX, wishZ, jump, sprint, yaw, 0L);
    }

    /** Controlled accepted input sequence is forwarded only to contained escape dispatch. */
    public Optional<CharacterMovementState> step(Mob body, int wishX, int wishZ,
                                                   boolean jump, boolean sprint, float yaw, long originSequence) {
        return step(body, wishX, wishZ, jump, false, sprint, yaw, originSequence);
    }

    public Optional<CharacterMovementState> step(Mob body, int wishX, int wishZ,
                                                   boolean jump, boolean sneak, boolean sprint, float yaw, long originSequence) {
        if (!(GroundedHarnessLease.isOwnedBodyEligible(body) || isOwnedPlayerCharacterEligible(body))
                || !((MindControlledMob) body).moonstation14$isMovementOwned()
                || wishX < -GroundedHarnessMotor.INPUT_QUANTIZATION
                || wishX > GroundedHarnessMotor.INPUT_QUANTIZATION
                || wishZ < -GroundedHarnessMotor.INPUT_QUANTIZATION
                || wishZ > GroundedHarnessMotor.INPUT_QUANTIZATION
                || !Float.isFinite(yaw) || yaw < -180f || yaw > 180f) return Optional.empty();
        return stepShared(body, wishX, wishZ, jump, sneak, sprint, yaw, originSequence, 0d, () -> true);
    }

    /** NPC entry: authority is sampled again at the movement boundary; player claims always win. */
    public Optional<CharacterMovementState> stepNpc(Mob body, int wishX, int wishZ,
                                                       boolean jump, boolean sprint, float yaw,
                                                       BooleanSupplier authority) {
        return stepNpc(body, wishX, wishZ, jump, sprint, yaw, 0d, authority);
    }

    /** NPC task movement with an explicit natural-flight mode independent of signed altitude wish. */
    public Optional<CharacterMovementState> stepNpcAutonomousFlight(Mob body, int wishX, int wishZ,
                                                       boolean jump, boolean sprint, float yaw,
                                                       double desiredSpeedPerSecond, double verticalWish,
                                                       FlightState flightIntent, BooleanSupplier authority) {
        if (!FlightTransitionPolicy.autonomousIntentValid(flightIntent)) return Optional.empty();
        return stepNpcInternal(body, wishX, wishZ, jump, sprint, yaw, desiredSpeedPerSecond,
                verticalWish, flightIntent, authority);
    }

    /** NPC task speed is absolute in prototype units and converted against the resolved movement policy. */
    public Optional<CharacterMovementState> stepNpc(Mob body, int wishX, int wishZ,
                                                       boolean jump, boolean sprint, float yaw,
                                                       double desiredSpeedPerSecond, BooleanSupplier authority) {
        return stepNpcInternal(body, wishX, wishZ, jump, sprint, yaw, desiredSpeedPerSecond, null, authority);
    }

    private Optional<CharacterMovementState> stepNpcInternal(Mob body, int wishX, int wishZ,
                                                               boolean jump, boolean sprint, float yaw,
                                                                double desiredSpeedPerSecond, Double npcVerticalWish,
                                                                BooleanSupplier authority) {
        return stepNpcInternal(body, wishX, wishZ, jump, sprint, yaw, desiredSpeedPerSecond, npcVerticalWish, null, authority);
    }

    private Optional<CharacterMovementState> stepNpcInternal(Mob body, int wishX, int wishZ,
                                                                boolean jump, boolean sprint, float yaw,
                                                                double desiredSpeedPerSecond, Double npcVerticalWish,
                                                                FlightState npcFlightIntent, BooleanSupplier authority) {
        if (!Double.isFinite(desiredSpeedPerSecond) || desiredSpeedPerSecond < 0d
                || npcVerticalWish != null && (!Double.isFinite(npcVerticalWish)
                || npcVerticalWish < -1d || npcVerticalWish > 1d)) return Optional.empty();
        if (body == null || authority == null || !(body instanceof NpcMovementDriven driven)
                || !driven.moonstation14$isNpcMovementDriven()
                || body instanceof MindControlledMob owner && owner.moonstation14$isMovementOwned()
                || !isNpcBodyEligible(body)
                || !com.juicyslew.moonstation14.ms14.mobs.MobStateSystem.isAlive(body)
                || !(body.level() instanceof ServerLevel npcLevel)
                || com.juicyslew.moonstation14.ms14.containment.BodyContainmentService.forLevel(npcLevel)
                    .ownerOf(body).isPresent()
                || wishX < -GroundedHarnessMotor.INPUT_QUANTIZATION || wishX > GroundedHarnessMotor.INPUT_QUANTIZATION
                || wishZ < -GroundedHarnessMotor.INPUT_QUANTIZATION || wishZ > GroundedHarnessMotor.INPUT_QUANTIZATION
                || !Float.isFinite(yaw) || yaw < -180f || yaw > 180f || !authority.getAsBoolean()) return Optional.empty();
        return stepShared(body, wishX, wishZ, jump, false, sprint, yaw, 0L, desiredSpeedPerSecond, authority,
                npcVerticalWish, npcFlightIntent);
    }

    /** NPC task speed plus normalized natural-flight altitude intent in [-1, 1]. */
    public Optional<CharacterMovementState> stepNpc(Mob body, int wishX, int wishZ,
                                                       boolean jump, boolean sprint, float yaw,
                                                       double desiredSpeedPerSecond, double naturalFlightVerticalWish,
                                                       BooleanSupplier authority) {
        return stepNpcInternal(body, wishX, wishZ, jump, sprint, yaw, desiredSpeedPerSecond,
                naturalFlightVerticalWish, authority);
    }

    /** Forced external momentum uses this resolved NPC motor without granting voluntary control. */
    public Optional<CharacterMovementState> stepNpcFlight(Mob body) {
        if (!isNpcFlightBodyEligible(body)) return Optional.empty();
        return stepShared(body, 0, 0, false, false, false, body.getYRot(), 0L, 0d,
                () -> isNpcFlightBodyEligible(body));
    }

    /** Single-owner world tick for natural flyers not owned by player/NPC intent motors. */
    public Optional<CharacterMovementState> stepNaturalFlight(Mob body) {
        if (body == null || FlightSystem.capability(body).isEmpty() || !FlightSystem.isFlying(body)
                || !isNaturalFlightBodyEligible(body)) return Optional.empty();
        double verticalWish = FlightTransitionPolicy.naturalFlightVerticalWish(FlightSystem.state(body), body.onGround());
        return stepShared(body, 0, 0, false, false, false, body.getYRot(), 0L, 0d,
                () -> isNaturalFlightBodyEligible(body), verticalWish);
    }

    public static boolean isNaturalFlightBodyEligible(Mob body) {
        if (body == null || body.isRemoved() || !body.isAlive() || body.isDeadOrDying()
                || body.level().isClientSide || !(body.level() instanceof ServerLevel level)
                || level.getServer() == null || !level.getServer().isSameThread() || !body.isAddedToLevel()
                || level.getEntity(body.getUUID()) != body || body.isPassenger()
                || com.juicyslew.moonstation14.ms14.mobs.MobStateSystem.isIncapacitated(body)
                || com.juicyslew.moonstation14.ms14.containment.BodyContainmentService.forLevel(level)
                    .ownerOf(body).isPresent()
                || com.juicyslew.moonstation14.ms14.player_body_control.movement.ExternalBodyFlight.active(body)) return false;
        if (body instanceof MindControlledMob owner && owner.moonstation14$isMovementOwned()) return false;
        return !(body instanceof NpcMovementDriven driven && driven.moonstation14$isNpcMovementDriven());
    }

    public static boolean isNpcFlightBodyEligible(Mob body) {
        if (body == null || body.isRemoved() || !body.isAlive() || body.isDeadOrDying()
                || body.level().isClientSide || !(body.level() instanceof ServerLevel level)
                || level.getServer() == null || !level.getServer().isSameThread() || !body.isAddedToLevel()
                || level.getEntity(body.getUUID()) != body || body.isPassenger()
                || !com.juicyslew.moonstation14.ms14.mobs.MobStateSystem.isAlive(body)
                || com.juicyslew.moonstation14.ms14.containment.BodyContainmentService.forLevel(level)
                    .ownerOf(body).isPresent()) return false;
        return hasNpcFlightCharacterProfile(body);
    }

    /** Read-only preflight for an exact body that will be released from custody before flight begins. */
    public static boolean hasNpcFlightCharacterProfile(Mob body) {
        if (body == null) return false;
        var data = CharacterIdentitySystem.resolveForActor(body).orElse(null);
        return data != null && data.component(com.juicyslew.moonstation14.ms14.character.components.NpcBehaviorComponent.class).isPresent()
                && data.component(MovementSpeedModifierComponent.class)
                    .filter(movement -> "grounded".equals(movement.mode())).isPresent();
    }

    private static boolean isNpcBodyEligible(Mob body) {
        if (body.isRemoved() || !body.isAlive() || body.isDeadOrDying() || body.level().isClientSide
                || !(body.level() instanceof ServerLevel level) || level.getServer() == null
                || !level.getServer().isSameThread() || !body.isAddedToLevel()
                || level.getEntity(body.getUUID()) != body || body.isPassenger() || !body.isNoAi()) return false;
        return CharacterIdentitySystem.resolveForActor(body).filter(data -> data.component(MovementSpeedModifierComponent.class)
                .filter(movement -> "grounded".equals(movement.mode())).isPresent()).isPresent();
    }

    private Optional<CharacterMovementState> stepShared(Mob body, int wishX, int wishZ,
                                                          boolean jump, boolean sneak, boolean sprint, float yaw, long originSequence,
                                                          double desiredSpeedPerSecond, BooleanSupplier authority) {
        return stepShared(body, wishX, wishZ, jump, sneak, sprint, yaw, originSequence, desiredSpeedPerSecond,
                authority, null);
    }

    private Optional<CharacterMovementState> stepShared(Mob body, int wishX, int wishZ,
                                                          boolean jump, boolean sneak, boolean sprint, float yaw, long originSequence,
                                                          double desiredSpeedPerSecond, BooleanSupplier authority,
                                                           Double npcVerticalWish) {
        return stepShared(body, wishX, wishZ, jump, sneak, sprint, yaw, originSequence,
                desiredSpeedPerSecond, authority, npcVerticalWish, null);
    }

    private Optional<CharacterMovementState> stepShared(Mob body, int wishX, int wishZ,
                                                          boolean jump, boolean sneak, boolean sprint, float yaw, long originSequence,
                                                          double desiredSpeedPerSecond, BooleanSupplier authority,
                                                          Double npcVerticalWish, FlightState npcFlightIntent) {

        var data = CharacterIdentitySystem.resolveForActor(body).orElse(null);
        if (data == null) return Optional.empty();
        FlightComponent flight = data.component(FlightComponent.class).orElse(null);
        double flightSpeed = flight == null ? 0d : data.component(MovementSpeedModifierComponent.class)
                .map(movement -> movement.walkSpeed() * CharacterMovementPolicy.MINECRAFT_GROUND_SPEED_SCALE)
                .orElse(0d);
        FlightState flightState = FlightSystem.state(body);
        boolean naturalFlight = flight != null && FlightSystem.normalizeFlightEligibility(body);
        flightState = FlightSystem.state(body);
        if (flight != null && npcFlightIntent == null && (jump || npcVerticalWish != null && npcVerticalWish > 0d)
                && body.onGround() && !naturalFlight) {
            FlightSystem.requestTakeoff(body);
            flightState = FlightSystem.state(body);
            naturalFlight = FlightSystem.normalizeFlightEligibility(body);
            flightState = FlightSystem.state(body);
        }
        if (flight != null && naturalFlight) {
            FlightState desired = npcFlightIntent != null ? npcFlightIntent : npcVerticalWish != null
                    ? FlightTransitionPolicy.explicitVerticalState(true, npcVerticalWish)
                    : FlightTransitionPolicy.heldVerticalState(true, jump, sneak);
            if (desired == FlightState.LANDING) FlightSystem.requestLanding(body);
            else FlightSystem.setState(body, desired);
            flightState = FlightSystem.state(body);
        }
        double verticalWish = !naturalFlight ? 0d
                : npcFlightIntent == FlightState.LANDING ? -Math.max(0.1d, Math.abs(npcVerticalWish == null ? 1d : npcVerticalWish)) * flightSpeed
                : npcFlightIntent == FlightState.FLYING ? npcVerticalWish * flightSpeed
                : npcVerticalWish != null ? npcVerticalWish * flightSpeed
                : FlightTransitionPolicy.verticalWish(flightState, jump, sneak, body.onGround(), flightSpeed);
        boolean trackedFlightMotion = ExternalBodyFlight.tracked(body);
        boolean externalFlight = ExternalBodyFlight.active(body);
        if (trackedFlightMotion && ExternalBodyFlight.alreadyAdvancedThisTick(body)) {
            Vec3 position = body.position();
            Vec3 velocity = body.getDeltaMovement();
            if (!externalFlight && ExternalBodyFlight.settled(body)) ExternalBodyFlight.cancel(body);
            return Optional.of(new CharacterMovementState(vector(position),
                    new MovementVector(velocity.x * 20d, velocity.y * 20d, velocity.z * 20d), body.onGround()));
        }
        CharacterMovementPolicy policy;
        try {
            policy = CharacterMovementPolicy.fromCharacterData(data);
        } catch (IllegalArgumentException | NullPointerException exception) {
            return Optional.empty();
        }
        double taskSpeedFactor = desiredSpeedPerSecond == 0d ? 1d
                : policy.factorForAbsoluteSpeed(desiredSpeedPerSecond, sprint);

        Vec3 initial = body.position();
        PostureSystem.onOwnCrawlInput(body, wishX != 0 || wishZ != 0);
        BodyPullService.onMovementInput(body, wishX != 0 || wishZ != 0);
        Vec3 initialVelocity = body.getDeltaMovement();
        if (!finite(initial) || !finite(initialVelocity)) return Optional.empty();

        if (body.level() instanceof ServerLevel level) {
            var service = BodyContainmentService.forLevel(level);
            var containment = service.ownerOf(body).orElse(null);
            if (containment != null) {
                boolean voluntaryEscapeRequest = containment.stage() == ContainedBodyOwner.Stage.UNIT
                        ? wishX != 0 || wishZ != 0 || jump
                        : containment.stage() == ContainedBodyOwner.Stage.STATIONARY && (wishX != 0 || wishZ != 0);
                if (voluntaryEscapeRequest) {
                    containment.tryEscape(body, originSequence);
                    containment = service.ownerOf(body).orElse(null);
                    if (containment == null) {
                        Vec3 exit = body.position();
                        if (!finite(exit)) return Optional.empty();
                        body.setDeltaMovement(Vec3.ZERO);
                        return Optional.of(new CharacterMovementState(vector(exit), MovementVector.ZERO,
                                body.onGround()));
                    }
                }
                Vec3 pinned = service.pinContainedBody(body, containment).orElse(null);
                if (!finite(pinned)) return Optional.empty();
                body.teleportTo(pinned.x, pinned.y, pinned.z);
                body.setDeltaMovement(Vec3.ZERO);
                return Optional.of(new CharacterMovementState(vector(pinned), MovementVector.ZERO, true));
            }
        }

        double surfaceFactor = SlidingFrictionSystem.frictionFactor(body);
        boolean down = PostureSystem.isDown(body);
        if (down) policy = policy.withDownFriction();
        double voluntaryFactor = com.juicyslew.moonstation14.ms14.movement.PullMovementPolicy
                .voluntaryMovementFactor(CharacterControlSystem.isKnockedDown(body), down,
                        PostureSystem.crawlMultiplier(body), BodyPullService.movementMultiplier(body, sprint))
                * taskSpeedFactor * com.juicyslew.moonstation14.ms14.animals.flight.FlightGroundSpeedPolicy
                        .voluntaryFactor(flight, flightState, externalFlight);
        if (!com.juicyslew.moonstation14.ms14.mobs.MobStateSystem.isAlive(body)) voluntaryFactor = 0d;
        CharacterMovementState state = new CharacterMovementState(vector(initial),
                new MovementVector(initialVelocity.x * 20d, initialVelocity.y * 20d, initialVelocity.z * 20d),
                body.onGround());
        MovementVector[] requestedForDiagnostics = {MovementVector.ZERO};
        MovementVector[] actualForDiagnostics = {MovementVector.ZERO};
        boolean wasGrounded = body.onGround();
        boolean[] authorityRejected = {false};
        boolean naturalFlightStep = naturalFlight;
        MovementCollisionResolver resolver = (position, requested, wasOnGround) -> {
            requestedForDiagnostics[0] = requested;
            Vec3 before = body.position();
            if (!authority.getAsBoolean()) {
                authorityRejected[0] = true;
                return new MovementCollisionResolver.CollisionResult(MovementVector.ZERO, body.onGround());
            }
            // Entity.move invokes checkFallDamage after collision resolution. Clear only for an
            // alive, actively powered natural-flight step, before that callback (including landing).
            if (naturalFlightStep && FlightSystem.isActivePoweredFlight(body))
                body.fallDistance = 0f;
            body.move(MoverType.SELF, new Vec3(requested.x(), requested.y(), requested.z()));
            Vec3 accepted = body.position().subtract(before);
            actualForDiagnostics[0] = vector(accepted);
            return new MovementCollisionResolver.CollisionResult(vector(accepted), body.onGround());
        };
        MovementVector gravityPulse = vector(com.juicyslew.moonstation14.ms14.singularity.SingularityGravityPulse
                .deltaVelocity(body.level(), initial, body.level().getGameTime()));
        double impulseBound = Math.max(32d, Math.hypot(Math.hypot(gravityPulse.x(), gravityPulse.y()), gravityPulse.z()));
        CharacterMovementEnvironment environment = new CharacterMovementEnvironment(GroundedHarnessPhysics.TICK_SECONDS,
                GroundedHarnessPhysics.GRAVITY_PER_SECOND_SQUARED,
                GroundedHarnessPhysics.JUMP_VELOCITY_PER_SECOND,
                GroundedHarnessPhysics.VERTICAL_DRAG,
                gravityPulse, impulseBound, body.maxUpStep(), resolver, surfaceFactor, voluntaryFactor,
                 tetherImpulse(body), GroundedHarnessPhysics.gravityAirControl(sprint));
        CharacterMovementState result;
        try {
            result = new GroundedHarnessMotor(policy).tick(state, wishX, wishZ, jump && !naturalFlight, sprint, yaw, environment,
                    !CharacterControlSystem.canMove(body), externalFlight, verticalWish, naturalFlight);
        } catch (IllegalArgumentException | NullPointerException exception) {
            MoonStation14.LOGGER.warn("Rejected grounded harness world step body={} before={} after={} velocity={} requested={} actual={} wasGrounded={} postGrounded={} maxStep={} jump={} wish=({}, {}) yaw={} reason={}",
                    body.getUUID(), initial, body.position(), initialVelocity, requestedForDiagnostics[0],
                    actualForDiagnostics[0], wasGrounded, body.onGround(), body.maxUpStep(), jump, wishX, wishZ,
                    yaw, exception.getMessage());
            return Optional.empty();
        }
        if (authorityRejected[0]) return Optional.empty();
        if (flight != null && naturalFlight && body.onGround() && result.velocity().y() <= 0d) {
            FlightSystem.setState(body, FlightState.GROUNDED);
            naturalFlight = false;
        }
        body.setDeltaMovement(result.velocity().x() / 20d, result.velocity().y() / 20d,
                result.velocity().z() / 20d);
        if (trackedFlightMotion) {
            ExternalBodyFlight.advanced(body);
            if (!externalFlight && ExternalBodyFlight.settled(body)) ExternalBodyFlight.cancel(body);
        }
        Vec3 acceptedDisplacement = body.position().subtract(initial);
        PostureSystem.onOwnMovement(body, acceptedDisplacement);
        SlipSystem.onAcceptedHarnessMovement(body, acceptedDisplacement);
        OwnedHarnessWalkAnimation.update(body, acceptedDisplacement);
        return Optional.of(result);
    }

    /** Explicit alternative for the per-instance bound custom CHARACTER body, not a host mapping. */
    private static boolean isOwnedPlayerCharacterEligible(Mob body) {
        if (!(body instanceof PlayerCharacterHarnessEntity character)
                || body.getType() != PlayerCharacterHarnessRegistration.getEntityType()
                || body.isRemoved() || !body.isAlive() || body.isDeadOrDying()
                || body.level().isClientSide || !(body.level() instanceof ServerLevel level)
                || level.getServer() == null || !level.getServer().isSameThread()
                || !body.isAddedToLevel() || level.getEntity(body.getUUID()) != body || body.isPassenger()
                || body.isInWaterOrBubble() || body.isInLava() || !body.isNoAi()
                || character.hasInvalidSavedBinding() || character.playerCharacterBinding() == null
                || !(body instanceof MindControlledMob owner) || !owner.moonstation14$isMovementOwned()) {
            return false;
        }
        return CharacterIdentitySystem.resolveForActor(body)
                .filter(data -> data.component(MovementSpeedModifierComponent.class)
                        .filter(movement -> "grounded".equals(movement.mode())).isPresent())
                .isPresent();
    }

    private static MovementVector vector(Vec3 value) {
        return new MovementVector(value.x, value.y, value.z);
    }

    private static MovementVector tetherImpulse(Mob body) {
        Vec3 impulse = com.juicyslew.moonstation14.ms14.loose_objects.pulling.TetherMotion.deltaVelocityPerSecond(body);
        return vector(impulse);
    }

    private static boolean finite(Vec3 value) {
        return Double.isFinite(value.x) && Double.isFinite(value.y) && Double.isFinite(value.z);
    }
}
