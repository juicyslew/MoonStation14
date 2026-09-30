package com.juicyslew.moonstation14.ms14.player_body_control.character;

import com.juicyslew.moonstation14.ms14.character.CharacterControlSystem;
import com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem;
import com.juicyslew.moonstation14.ms14.character.components.MovementSpeedModifierComponent;
import com.juicyslew.moonstation14.ms14.movement.CharacterMovementEnvironment;
import com.juicyslew.moonstation14.ms14.movement.CharacterMovementPolicy;
import com.juicyslew.moonstation14.ms14.movement.CharacterMovementState;
import com.juicyslew.moonstation14.ms14.movement.MovementCollisionResolver;
import com.juicyslew.moonstation14.ms14.movement.MovementVector;
import com.juicyslew.moonstation14.ms14.player_body_control.movement.GroundedHarnessMotor;
import com.juicyslew.moonstation14.ms14.player_body_control.movement.GroundedHarnessPhysics;
import com.juicyslew.moonstation14.ms14.player_body_control.movement.OwnedHarnessWalkAnimation;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessEntity;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessRegistration;
import com.juicyslew.moonstation14.ms14.slip.SlidingFrictionSystem;
import com.juicyslew.moonstation14.ms14.slip.SlipSystem;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

/** One explicit server-side collision-resolved step for an enrolled, movement-owned grounded mob. */
public final class GroundedHarnessWorldStep {

    /**
     * Runs exactly one step. Empty means validation failed or the motor rejected the result; Entity.move
     * may already have moved the body in the latter case, so callers must not retry a rejected partial move.
     * Slip-launch override remains a stop gate before any live movement ownership transfer.
     */
    public Optional<CharacterMovementState> step(Mob body, int wishX, int wishZ,
                                                  boolean jump, boolean sprint, float yaw) {
        if (!(GroundedHarnessLease.isOwnedBodyEligible(body) || isOwnedPlayerCharacterEligible(body))
                || !((MindControlledMob) body).moonstation14$isMovementOwned()
                || wishX < -GroundedHarnessMotor.INPUT_QUANTIZATION
                || wishX > GroundedHarnessMotor.INPUT_QUANTIZATION
                || wishZ < -GroundedHarnessMotor.INPUT_QUANTIZATION
                || wishZ > GroundedHarnessMotor.INPUT_QUANTIZATION
                || !Float.isFinite(yaw) || yaw < -180f || yaw > 180f) return Optional.empty();

        var data = CharacterIdentitySystem.resolveForActor(body).orElse(null);
        if (data == null) return Optional.empty();
        CharacterMovementPolicy policy;
        try {
            policy = CharacterMovementPolicy.fromCharacterData(data);
        } catch (IllegalArgumentException | NullPointerException exception) {
            return Optional.empty();
        }

        Vec3 initial = body.position();
        Vec3 initialVelocity = body.getDeltaMovement();
        if (!finite(initial) || !finite(initialVelocity)) return Optional.empty();

        double surfaceFactor = SlidingFrictionSystem.frictionFactor(body);
        double voluntaryFactor = CharacterControlSystem.isKnockedDown(body)
                ? CharacterMovementPolicy.HUMAN_KNOCKDOWN_SPEED_FACTOR : 1d;
        CharacterMovementState state = new CharacterMovementState(vector(initial),
                new MovementVector(initialVelocity.x * 20d, initialVelocity.y * 20d, initialVelocity.z * 20d),
                body.onGround());
        MovementCollisionResolver resolver = (position, requested, wasOnGround) -> {
            Vec3 before = body.position();
            body.move(MoverType.SELF, new Vec3(requested.x(), requested.y(), requested.z()));
            Vec3 accepted = body.position().subtract(before);
            return new MovementCollisionResolver.CollisionResult(vector(accepted), body.onGround());
        };
        CharacterMovementEnvironment environment = new CharacterMovementEnvironment(GroundedHarnessPhysics.TICK_SECONDS,
                GroundedHarnessPhysics.GRAVITY_PER_SECOND_SQUARED, GroundedHarnessPhysics.JUMP_VELOCITY_PER_SECOND,
                GroundedHarnessPhysics.VERTICAL_DRAG,
                MovementVector.ZERO, 0d, body.maxUpStep(), resolver, surfaceFactor, voluntaryFactor);
        CharacterMovementState result;
        try {
            result = new GroundedHarnessMotor(policy).tick(state, wishX, wishZ, jump, sprint, yaw, environment,
                    CharacterControlSystem.isStunned(body));
        } catch (IllegalArgumentException | NullPointerException exception) {
            return Optional.empty();
        }
        body.setDeltaMovement(result.velocity().x() / 20d, result.velocity().y() / 20d,
                result.velocity().z() / 20d);
        Vec3 acceptedDisplacement = body.position().subtract(initial);
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

    private static boolean finite(Vec3 value) {
        return Double.isFinite(value.x) && Double.isFinite(value.y) && Double.isFinite(value.z);
    }
}
