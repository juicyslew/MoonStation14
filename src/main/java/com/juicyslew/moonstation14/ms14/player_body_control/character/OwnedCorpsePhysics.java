package com.juicyslew.moonstation14.ms14.player_body_control.character;

import com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem;
import com.juicyslew.moonstation14.ms14.movement.CharacterMovementEnvironment;
import com.juicyslew.moonstation14.ms14.movement.CharacterMovementPolicy;
import com.juicyslew.moonstation14.ms14.movement.CharacterMovementState;
import com.juicyslew.moonstation14.ms14.movement.MovementCollisionResolver;
import com.juicyslew.moonstation14.ms14.movement.MovementVector;
import com.juicyslew.moonstation14.ms14.player_body_control.movement.GroundedHarnessMotor;
import com.juicyslew.moonstation14.ms14.player_body_control.movement.GroundedHarnessPhysics;
import com.juicyslew.moonstation14.ms14.standing.PostureSystem;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;

/** Server physics for retained incapacitated medical bodies, independent of mind ownership. */
public final class OwnedCorpsePhysics {
    private OwnedCorpsePhysics() { }

    public static boolean step(Mob body) {
        if (!eligible(body) || !(body.level() instanceof ServerLevel level)
                || level.getServer() == null || !level.getServer().isSameThread()
                ) return false;
        long serverTick = level.getServer().getTickCount();
        if (body instanceof MindControlledMob owner && owner.moonstation14$physicalStepConsumed(level, serverTick))
            return true;
        boolean trackedFlight = com.juicyslew.moonstation14.ms14.player_body_control.movement.ExternalBodyFlight.tracked(body);
        boolean externalFlight = com.juicyslew.moonstation14.ms14.player_body_control.movement.ExternalBodyFlight.active(body);
        if (trackedFlight && com.juicyslew.moonstation14.ms14.player_body_control.movement.ExternalBodyFlight
                .alreadyAdvancedThisTick(body)) {
            if (body instanceof MindControlledMob owner) owner.moonstation14$markPhysicalStep(level, serverTick);
            return true;
        }

        var data = CharacterIdentitySystem.resolveForActor(body).orElse(null);
        if (data == null) return false;
        final CharacterMovementPolicy policy;
        try {
            policy = CharacterMovementPolicy.fromCharacterData(data).withDownFriction();
        } catch (IllegalArgumentException | NullPointerException exception) {
            return false;
        }

        Vec3 initial = body.position();
        Vec3 velocity = body.getDeltaMovement();
        if (!finite(initial) || !finite(velocity)) return false;
        if (body instanceof com.juicyslew.moonstation14.ms14.shuttle.sable.FrameAnchorPending pending
                && pending.sable$frameAnchorInitializationPending()
                && !pending.sable$frameAnchorState().initializeForOwnedMovement(level, body)) return false;
        initial = body.position();
        velocity = body.getDeltaMovement();
        if (!finite(initial) || !finite(velocity)) return false;
        CharacterMovementState state = new CharacterMovementState(vector(initial),
                new MovementVector(velocity.x * 20d, velocity.y * 20d, velocity.z * 20d), body.onGround());
        MovementVector[] frameVelocityDelta = {MovementVector.ZERO};
        com.juicyslew.moonstation14.ms14.shuttle.sable.SableOwnedBodyMotion.MoveResult[] nativeMove = {null};
        int[] nativeMoveCount = {0};
        MovementCollisionResolver resolver = (position, requested, wasOnGround) -> {
            if (body instanceof MindControlledMob owner) owner.moonstation14$markPhysicalStep(level, serverTick);
            var moved = com.juicyslew.moonstation14.ms14.shuttle.sable.SableOwnedBodyMotion.move(body, requested);
            nativeMove[0] = moved;
            nativeMoveCount[0]++;
            frameVelocityDelta[0] = moved.frameVelocityDeltaPerSecond();
            return moved.toCollisionResult(body.onGround());
        };
        CharacterMovementEnvironment environment = new CharacterMovementEnvironment(
                GroundedHarnessPhysics.TICK_SECONDS, GroundedHarnessPhysics.GRAVITY_PER_SECOND_SQUARED,
                GroundedHarnessPhysics.JUMP_VELOCITY_PER_SECOND, GroundedHarnessPhysics.VERTICAL_DRAG,
                MovementVector.ZERO, 32d, body.maxUpStep(), resolver, 1d, 0d, tetherImpulse(body));
        try {
            CharacterMovementState result = new GroundedHarnessMotor(policy).tick(state, 0, 0,
                    false, false, 0f, environment, false, externalFlight);
            var target = nativeMove[0] == null ? null
                    : com.juicyslew.moonstation14.ms14.shuttle.sable.NativeNormalContactFinalizer.targetForStep(
                            nativeMove[0].normalTarget(), nativeMoveCount[0], nativeMove[0].beforePoint(), nativeMove[0].afterPoint());
            MovementVector finalVelocity = com.juicyslew.moonstation14.ms14.shuttle.sable.NativeNormalContactFinalizer
                    .finalizeWorld(result.velocity(), frameVelocityDelta[0], target);
            body.setDeltaMovement(finalVelocity.x() / 20d, finalVelocity.y() / 20d,
                    finalVelocity.z() / 20d);
            if (trackedFlight) {
                com.juicyslew.moonstation14.ms14.player_body_control.movement.ExternalBodyFlight.advanced(body);
                if (!externalFlight && com.juicyslew.moonstation14.ms14.player_body_control.movement.ExternalBodyFlight.settled(body))
                    com.juicyslew.moonstation14.ms14.player_body_control.movement.ExternalBodyFlight.cancel(body);
            }
            return true;
        } catch (IllegalArgumentException | NullPointerException exception) {
            return false;
        }
    }

    /** Shared gate for both native travel suppression and the passive server movement consumer. */
    public static boolean eligible(net.minecraft.world.entity.LivingEntity entity) {
        if (!(entity instanceof Mob body) || body.level().isClientSide || body.isRemoved() || !body.isAddedToLevel()
                || body.isPassenger() || body.isNoGravity() || body.noPhysics
                || com.juicyslew.moonstation14.ms14.mobs.MobStateSystem.policy(body) == null
                || !com.juicyslew.moonstation14.ms14.mobs.MobStateSystem.isIncapacitated(body)) return false;
        if (body.level() instanceof ServerLevel level
                && com.juicyslew.moonstation14.ms14.containment.BodyContainmentService.forLevel(level)
                .ownerOf(body).isPresent()) return false;
        var data = CharacterIdentitySystem.resolveForActor(body).orElse(null);
        if (data == null) return false;
        try {
            CharacterMovementPolicy.fromCharacterData(data).withDownFriction();
            return true;
        } catch (IllegalArgumentException | NullPointerException exception) {
            return false;
        }
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
