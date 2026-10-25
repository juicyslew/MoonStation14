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
        boolean trackedFlight = com.juicyslew.moonstation14.ms14.player_body_control.movement.ExternalBodyFlight.tracked(body);
        boolean externalFlight = com.juicyslew.moonstation14.ms14.player_body_control.movement.ExternalBodyFlight.active(body);
        if (trackedFlight && com.juicyslew.moonstation14.ms14.player_body_control.movement.ExternalBodyFlight
                .alreadyAdvancedThisTick(body)) return true;

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
        CharacterMovementState state = new CharacterMovementState(vector(initial),
                new MovementVector(velocity.x * 20d, velocity.y * 20d, velocity.z * 20d), body.onGround());
        MovementCollisionResolver resolver = (position, requested, wasOnGround) -> {
            Vec3 before = body.position();
            body.move(MoverType.SELF, new Vec3(requested.x(), requested.y(), requested.z()));
            Vec3 accepted = body.position().subtract(before);
            return new MovementCollisionResolver.CollisionResult(vector(accepted), body.onGround());
        };
        CharacterMovementEnvironment environment = new CharacterMovementEnvironment(
                GroundedHarnessPhysics.TICK_SECONDS, GroundedHarnessPhysics.GRAVITY_PER_SECOND_SQUARED,
                GroundedHarnessPhysics.JUMP_VELOCITY_PER_SECOND, GroundedHarnessPhysics.VERTICAL_DRAG,
                MovementVector.ZERO, 32d, body.maxUpStep(), resolver, 1d, 0d, tetherImpulse(body));
        try {
            CharacterMovementState result = new GroundedHarnessMotor(policy).tick(state, 0, 0,
                    false, false, 0f, environment, false, externalFlight);
            body.setDeltaMovement(result.velocity().x() / 20d, result.velocity().y() / 20d,
                    result.velocity().z() / 20d);
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
        return hasCorpseCharacterProfile(body);
    }

    /** Resolved movement composition and incapacitated mob state, before portable custody restores physics flags. */
    public static boolean hasCorpseCharacterProfile(net.minecraft.world.entity.LivingEntity entity) {
        if (!(entity instanceof Mob body)
                || com.juicyslew.moonstation14.ms14.mobs.MobStateSystem.policy(body) == null
                || !com.juicyslew.moonstation14.ms14.mobs.MobStateSystem.isIncapacitated(body)) return false;
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

