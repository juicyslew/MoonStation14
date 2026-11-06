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
import java.util.function.Consumer;

/** One explicit server-side collision-resolved step for an enrolled, movement-owned grounded mob. */
public final class GroundedHarnessWorldStep {
    private final boolean observeGeometry;

    public GroundedHarnessWorldStep() { this(false); }

    private GroundedHarnessWorldStep(boolean observeGeometry) { this.observeGeometry = observeGeometry; }

    /** Diagnostic-only native geometry observation for the focused exterior-wall fixture. */
    public static GroundedHarnessWorldStep observingNativeGeometry() {
        return new GroundedHarnessWorldStep(true);
    }

    /**
     * Runs exactly one step. Empty means validation failed or the motor rejected the result; Entity.move
     * may already have moved the body in the latter case, so callers must not retry a rejected partial move.
     * Slip-launch override remains a stop gate before any live movement ownership transfer.
     */
    public Optional<CharacterMovementState> step(Mob body, int wishX, int wishZ,
                                                  boolean jump, boolean sprint, float yaw) {
        return step(body, wishX, wishZ, jump, sprint, yaw, null);
    }

    /** Typed native-result observation for focused fixtures; normal gameplay uses the observer-free overload. */
    public Optional<CharacterMovementState> step(Mob body, int wishX, int wishZ,
                                                   boolean jump, boolean sprint, float yaw,
                                                   Consumer<com.juicyslew.moonstation14.ms14.shuttle.sable.SableOwnedBodyMotion.MoveResult> observer) {
        return step(body, wishX, wishZ, jump, sprint, yaw, observer, Optional.empty());
    }

    public Optional<CharacterMovementState> step(Mob body, int wishX, int wishZ,
            boolean jump, boolean sprint, float yaw,
            Consumer<com.juicyslew.moonstation14.ms14.shuttle.sable.SableOwnedBodyMotion.MoveResult> observer,
            Optional<com.juicyslew.moonstation14.ms14.player_body_control.network.GhostControlPayloads.SupportedMovementReference> supportedMovementReference) {
        return stepCore(body, wishX, wishZ, jump, sprint, yaw, observer, false, supportedMovementReference);
    }

    private Optional<CharacterMovementState> stepCore(Mob body, int wishX, int wishZ,
            boolean jump, boolean sprint, float yaw,
            Consumer<com.juicyslew.moonstation14.ms14.shuttle.sable.SableOwnedBodyMotion.MoveResult> observer,
            boolean passive) {
        return stepCore(body, wishX, wishZ, jump, sprint, yaw, observer, passive, Optional.empty());
    }

    private Optional<CharacterMovementState> stepCore(Mob body, int wishX, int wishZ,
            boolean jump, boolean sprint, float yaw,
            Consumer<com.juicyslew.moonstation14.ms14.shuttle.sable.SableOwnedBodyMotion.MoveResult> observer,
            boolean passive,
            Optional<com.juicyslew.moonstation14.ms14.player_body_control.network.GhostControlPayloads.SupportedMovementReference> supportedMovementReference) {
        if (body == null) return stepCoreUnprofiled(body, wishX, wishZ, jump, sprint, yaw, observer, passive, supportedMovementReference);
        var profiler = body.level().getProfiler();
        profiler.push("moonstation14_body_step");
        try {
            return stepCoreUnprofiled(body, wishX, wishZ, jump, sprint, yaw, observer, passive, supportedMovementReference);
        } finally {
            profiler.pop();
        }
    }

    private Optional<CharacterMovementState> stepCoreUnprofiled(Mob body, int wishX, int wishZ,
            boolean jump, boolean sprint, float yaw,
            Consumer<com.juicyslew.moonstation14.ms14.shuttle.sable.SableOwnedBodyMotion.MoveResult> observer,
            boolean passive,
            Optional<com.juicyslew.moonstation14.ms14.player_body_control.network.GhostControlPayloads.SupportedMovementReference> supportedMovementReference) {
        if (!(GroundedHarnessLease.isOwnedBodyEligible(body) || isOwnedPlayerCharacterEligible(body)
                    || (passive && isPassivePlayerCharacterEligible(body)))
                || (!passive && !((MindControlledMob) body).moonstation14$isMovementOwned())
                || wishX < -GroundedHarnessMotor.INPUT_QUANTIZATION
                || wishX > GroundedHarnessMotor.INPUT_QUANTIZATION
                || wishZ < -GroundedHarnessMotor.INPUT_QUANTIZATION
                || wishZ > GroundedHarnessMotor.INPUT_QUANTIZATION
                || !Float.isFinite(yaw) || yaw < -180f || yaw > 180f) return Optional.empty();

        var data = CharacterIdentitySystem.resolveForActor(body).orElse(null);
        if (data == null) return Optional.empty();
        if (body.level() instanceof ServerLevel level && body instanceof MindControlledMob owner) {
            long serverTick = level.getServer().getTickCount();
            if (owner.moonstation14$physicalStepConsumed(level, serverTick))
                return Optional.of(currentState(body));
        }
        boolean trackedFlightMotion = ExternalBodyFlight.tracked(body);
        boolean externalFlight = ExternalBodyFlight.active(body);
        if (trackedFlightMotion && ExternalBodyFlight.alreadyAdvancedThisTick(body)) {
            if (body.level() instanceof ServerLevel level && body instanceof MindControlledMob owner)
                owner.moonstation14$markPhysicalStep(level, level.getServer().getTickCount());
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

        Vec3 initial = body.position();
        Vec3 initialVelocity = body.getDeltaMovement();
        if (!finite(initial) || !finite(initialVelocity)) return Optional.empty();

        if (body.level() instanceof ServerLevel level) {
            var service = BodyContainmentService.forLevel(level);
            var containment = service.ownerOf(body).orElse(null);
            if (body instanceof com.juicyslew.moonstation14.ms14.shuttle.sable.FrameAnchorPending pending
                    && pending.sable$frameAnchorInitializationPending() && containment != null)
                return Optional.empty();
            if (body instanceof com.juicyslew.moonstation14.ms14.shuttle.sable.FrameAnchorPending pending
                    && pending.sable$frameAnchorInitializationPending()
                    && !pending.sable$frameAnchorState().initializeForOwnedMovement(level, body)) return Optional.empty();
            if (containment != null) {
                if (body instanceof PlayerCharacterHarnessEntity character)
                    character.setAngularRecoveryState(Optional.empty());
                if (containment.stage() == ContainedBodyOwner.Stage.UNIT
                        && (wishX != 0 || wishZ != 0 || jump)) {
                    containment.tryEscape(body);
                    containment = service.ownerOf(body).orElse(null);
                    if (containment == null) {
                        Vec3 exit = body.position();
                        if (!finite(exit)) return Optional.empty();
                        body.setDeltaMovement(Vec3.ZERO);
                        return Optional.of(new CharacterMovementState(vector(exit), MovementVector.ZERO,
                                body.onGround()));
                    }
                }
                Vec3 pinned = containment.positionFor(body);
                if (!finite(pinned)) return Optional.empty();
                body.teleportTo(pinned.x, pinned.y, pinned.z);
                body.setDeltaMovement(Vec3.ZERO);
                return Optional.of(new CharacterMovementState(vector(pinned), MovementVector.ZERO, true));
            }
        }

        PostureSystem.onOwnCrawlInput(body, wishX != 0 || wishZ != 0);
        BodyPullService.onMovementInput(body, wishX != 0 || wishZ != 0);
        initial = body.position();
        initialVelocity = body.getDeltaMovement();
        if (!finite(initial) || !finite(initialVelocity)) return Optional.empty();

        double surfaceFactor = SlidingFrictionSystem.frictionFactor(body);
        boolean down = PostureSystem.isDown(body);
        if (down) policy = policy.withDownFriction();
        double voluntaryFactor = com.juicyslew.moonstation14.ms14.movement.PullMovementPolicy
                .voluntaryMovementFactor(CharacterControlSystem.isKnockedDown(body), down,
                        PostureSystem.crawlMultiplier(body), BodyPullService.movementMultiplier(body, sprint));
        if (!com.juicyslew.moonstation14.ms14.mobs.MobStateSystem.isAlive(body)) voluntaryFactor = 0d;
        var storedAirReference = body instanceof PlayerCharacterHarnessEntity character
                ? character.airMotionReference().orElse(null) : null;
        if (body.onGround()) storedAirReference = null;
        CharacterMovementState state = new CharacterMovementState(vector(initial),
                new MovementVector(initialVelocity.x * 20d, initialVelocity.y * 20d, initialVelocity.z * 20d),
                body.onGround(), storedAirReference);
        MovementVector[] requestedForDiagnostics = {MovementVector.ZERO};
        MovementVector[] actualForDiagnostics = {MovementVector.ZERO};
        MovementVector[] frameVelocityDelta = {MovementVector.ZERO};
        MovementVector baselineInMotorBasis = null;
        if (storedAirReference != null) {
            var basisPoint = com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery
                    .currentActorPoint(body.level(), body);
            if (basisPoint == null) return Optional.empty();
            Vec3 pointVelocity = basisPoint.frameId() == null ? Vec3.ZERO
                    : com.juicyslew.moonstation14.ms14.shuttle.sable.SableFrameVelocity
                            .pointVelocityAt(body.level(), basisPoint);
            if (pointVelocity == null) return Optional.empty();
            baselineInMotorBasis = com.juicyslew.moonstation14.ms14.player_body_control.movement.AirMotionReferenceTransition
                    .baselineInMotorBasis(storedAirReference,
                            new MovementVector(pointVelocity.x, pointVelocity.y, pointVelocity.z));
        }
        com.juicyslew.moonstation14.ms14.shuttle.sable.SableOwnedBodyMotion.MoveResult[] nativeMove = {null};
        int[] nativeMoveCount = {0};
        boolean wasGrounded = body.onGround();
        var angularSupport = body instanceof PlayerCharacterHarnessEntity
                ? com.juicyslew.moonstation14.ms14.character.CharacterAngularRecoverySystem.beforeWorldMove(body) : null;
        float fallbackMovementYaw = body instanceof PlayerCharacterHarnessEntity character
                ? com.juicyslew.moonstation14.ms14.character.AngularRecoveryLook.worldPlanarYaw(
                        character.angularRecoveryState().orElse(null), yaw) : yaw;
        float movementYaw = body instanceof PlayerCharacterHarnessEntity
                ? com.juicyslew.moonstation14.ms14.player_body_control.character.SupportedMovementYaw.resolve(
                        supportedMovementReference.orElse(null), fallbackMovementYaw, wasGrounded,
                        angularSupport == null ? null : angularSupport.actualFrameId(),
                        angularSupport == null ? 0f : angularSupport.worldFrameYaw())
                : fallbackMovementYaw;
        MovementCollisionResolver resolver = MovementCollisionResolver.withMotorContext(
                (position, requested, wasOnGround, idleSupportProbe, motorJumpInitiated) -> {
            requestedForDiagnostics[0] = requested;
            var motionOptions = body instanceof com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessEntity
                    ? new com.juicyslew.moonstation14.ms14.shuttle.sable.SableOwnedBodyMotion.MoveOptions(true, null,
                            com.juicyslew.moonstation14.ms14.shuttle.sable.SableOwnedBodyMotion.CollisionPosePolicy.STOCK_NATIVE_INTERVAL,
                            com.juicyslew.moonstation14.ms14.shuttle.sable.SableOwnedBodyMotion.BodyPosePolicy.TRACKED_CHARACTER,
                            observeGeometry)
                    : com.juicyslew.moonstation14.ms14.shuttle.sable.SableOwnedBodyMotion.MoveOptions.serverDefaults();
            if (body.level() instanceof ServerLevel level && body instanceof MindControlledMob owner)
                owner.moonstation14$markPhysicalStep(level, level.getServer().getTickCount());
            var moved = com.juicyslew.moonstation14.ms14.shuttle.sable.SableOwnedBodyMotion.move(
                    body, requested, motionOptions, idleSupportProbe, motorJumpInitiated);
            nativeMove[0] = moved;
            nativeMoveCount[0]++;
            if (observer != null) observer.accept(moved);
            var collisionResult = moved.toCollisionResult(body.onGround());
            MovementVector accepted = collisionResult.actualDisplacement();
            frameVelocityDelta[0] = moved.frameVelocityDeltaPerSecond();
            actualForDiagnostics[0] = accepted;
            return collisionResult;
        });
        CharacterMovementEnvironment environment = new CharacterMovementEnvironment(GroundedHarnessPhysics.TICK_SECONDS,
                GroundedHarnessPhysics.GRAVITY_PER_SECOND_SQUARED, GroundedHarnessPhysics.JUMP_VELOCITY_PER_SECOND,
                GroundedHarnessPhysics.VERTICAL_DRAG,
                MovementVector.ZERO, 32d, body.maxUpStep(), resolver, surfaceFactor, voluntaryFactor,
                tetherImpulse(body));
        CharacterMovementState result;
        try {
            result = new GroundedHarnessMotor(policy).tick(state, wishX, wishZ, jump, sprint, movementYaw, environment,
                    !CharacterControlSystem.canMove(body), externalFlight, baselineInMotorBasis);
        } catch (IllegalArgumentException | NullPointerException exception) {
            boolean detailedRejection = com.juicyslew.moonstation14.ms14.shuttle.sable.OwnedNativeMoveDiagnostic
                    .shouldDescribeRejection(observeGeometry);
            String nativeDetail = detailedRejection ? com.juicyslew.moonstation14.ms14.shuttle.sable.OwnedNativeMoveDiagnostic
                    .describe(body, nativeMove[0]) : "detail=debug-only";
            String rejection = com.juicyslew.moonstation14.ms14.shuttle.sable.OwnedNativeMoveDiagnostic.rejectionSummary(
                    requestedForDiagnostics[0], actualForDiagnostics[0], body.onGround(), exception.getMessage(), nativeMove[0]);
            MoonStation14.LOGGER.warn("Rejected grounded harness world step {} body={} before={} after={} velocity={} acceptedOwn={} native={} wasGrounded={} postGrounded={} maxStep={} jump={} wish=({}, {}) yaw={}",
                    rejection,
                    body.getUUID(), initial, body.position(), initialVelocity,
                    actualForDiagnostics[0], observeGeometry ? nativeDetail : "detail=opt-in",
                    wasGrounded, body.onGround(), body.maxUpStep(), jump, wishX, wishZ, yaw);
            return Optional.empty();
        }
        var normalTarget = nativeMove[0] == null ? null
                : com.juicyslew.moonstation14.ms14.shuttle.sable.NativeNormalContactFinalizer.targetForStep(
                        nativeMove[0].normalTarget(), nativeMoveCount[0], nativeMove[0].beforePoint(), nativeMove[0].afterPoint());
        MovementVector finalVelocity = com.juicyslew.moonstation14.ms14.shuttle.sable.NativeNormalContactFinalizer
                 .finalizeWorld(result.velocity(), frameVelocityDelta[0], normalTarget,
                         nativeMove[0] == null ? null : com.juicyslew.moonstation14.ms14.shuttle.sable.NativeNormalContactFinalizer.landingForStep(
                                 nativeMove[0].landingWitness(), nativeMoveCount[0], nativeMove[0].beforePoint(), nativeMove[0].afterPoint()));
        body.setDeltaMovement(finalVelocity.x() / 20d, finalVelocity.y() / 20d,
                finalVelocity.z() / 20d);
        var resultAirReference = result.airMotionReference();
        var moveResult = nativeMove[0];
        if (body instanceof PlayerCharacterHarnessEntity character) {
            var departure = moveResult == null ? null
                    : com.juicyslew.moonstation14.ms14.player_body_control.movement.AirMotionReferenceTransition.departure(
                            moveResult.collisionInfoFresh(), wasGrounded, body.onGround(), moveResult.beforePoint(),
                            moveResult.afterPoint(), frameVelocityDelta[0]);
            // The already-finalized native frame delta is retained as B; it is not added a second time.
            resultAirReference = com.juicyslew.moonstation14.ms14.player_body_control.movement.AirMotionReferenceTransition
                    .afterSuccessfulMotor(body.onGround(), resultAirReference, departure);
            character.setAirMotionReference(resultAirReference);
        }
        if (body instanceof PlayerCharacterHarnessEntity) {
            com.juicyslew.moonstation14.ms14.character.CharacterAngularRecoverySystem.afterWorldMove(
                    body, data, wasGrounded, body.onGround(), angularSupport);
        }
        if (trackedFlightMotion) {
            ExternalBodyFlight.advanced(body);
            if (!externalFlight && ExternalBodyFlight.settled(body)) ExternalBodyFlight.cancel(body);
        }
        Vec3 actualPosition = body.position();
        MovementVector acceptedOwn = actualForDiagnostics[0];
        Vec3 acceptedDisplacement = new Vec3(acceptedOwn.x(), acceptedOwn.y(), acceptedOwn.z());
        PostureSystem.onOwnMovement(body, acceptedDisplacement);
        SlipSystem.onAcceptedHarnessMovement(body, acceptedDisplacement);
        OwnedHarnessWalkAnimation.update(body, acceptedDisplacement);
        // Keep authoritative world-space snapshots on the carried position, without
        // exposing inherited movement to own-walk posture/slip/animation accounting.
        return Optional.of(new CharacterMovementState(vector(actualPosition), finalVelocity, body.onGround(), resultAirReference));
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

    /** Passive physical existence tick for bound custom bodies, independent of current control. */
    public Optional<CharacterMovementState> passiveStep(PlayerCharacterHarnessEntity body) {
        if (!isPassivePlayerCharacterEligible(body)) return Optional.empty();
        return stepCore(body, 0, 0, false, false, body.getYRot(), null, true);
    }

    /** Loaded, bound, grounded-prototype physical body gate; intentionally no account/session gate. */
    public static boolean isPassivePlayerCharacterEligible(Mob body) {
        return isCustomPhysicalOwner(body) && !body.isNoGravity() && !body.noPhysics
                && com.juicyslew.moonstation14.ms14.containment.BodyContainmentService
                        .forLevel((ServerLevel) body.level()).ownerOf(body).isEmpty();
    }

    /** Custom physical ownership does not depend on the current Mind or vanilla travel flags. */
    public static boolean isCustomPhysicalOwner(Mob body) {
        if (!(body instanceof PlayerCharacterHarnessEntity character)
                || body.getType() != PlayerCharacterHarnessRegistration.getEntityType()
                || body.isRemoved() || !body.isAlive() || body.isDeadOrDying()
                || body.level().isClientSide || !(body.level() instanceof ServerLevel level)
                || level.getServer() == null || !level.getServer().isSameThread()
                || !body.isAddedToLevel() || level.getEntity(body.getUUID()) != body || body.isPassenger()
                || body.isInWaterOrBubble() || body.isInLava()
                || character.hasInvalidSavedBinding() || character.playerCharacterBinding() == null
                || !(body instanceof MindControlledMob)) return false;
        var data = CharacterIdentitySystem.resolveForActor(body).orElse(null);
        return data != null && data.component(MovementSpeedModifierComponent.class)
                .filter(movement -> "grounded".equals(movement.mode())).isPresent();
    }

    private static CharacterMovementState currentState(Mob body) {
        Vec3 position = body.position();
        Vec3 velocity = body.getDeltaMovement();
        return new CharacterMovementState(vector(position),
                new MovementVector(velocity.x * 20d, velocity.y * 20d, velocity.z * 20d), body.onGround());
    }

}
