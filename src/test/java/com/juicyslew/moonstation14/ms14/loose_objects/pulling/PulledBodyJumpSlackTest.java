package com.juicyslew.moonstation14.ms14.loose_objects.pulling;

import com.juicyslew.moonstation14.ms14.movement.*;
import com.juicyslew.moonstation14.ms14.player_body_control.movement.GroundedHarnessMotor;
import com.juicyslew.moonstation14.ms14.player_body_control.movement.GroundedHarnessPhysics;
import com.juicyslew.moonstation14.ms14.character.components.TetherSettings;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PulledBodyJumpSlackTest {
    private static final MovementCollisionResolver OPEN = (p, requested, grounded) ->
            new MovementCollisionResolver.CollisionResult(requested, false);

    @Test
    void resolvedAllowanceDoesNotRestrainFullVerticalJumpButStillActsBeyondSlack() {
        var settings = TetherSettings.DEFAULT;
        assertEquals(1.5, settings.slackAllowance());
        GroundedHarnessMotor motor = new GroundedHarnessMotor(CharacterMovementPolicy.HUMAN);
        var env = new CharacterMovementEnvironment(GroundedHarnessPhysics.TICK_SECONDS,
                GroundedHarnessPhysics.GRAVITY_PER_SECOND_SQUARED,
                GroundedHarnessPhysics.JUMP_VELOCITY_PER_SECOND, GroundedHarnessPhysics.VERTICAL_DRAG,
                MovementVector.ZERO, 32, 0, OPEN);
        CharacterMovementState pulled = new CharacterMovementState(MovementVector.ZERO, MovementVector.ZERO, true);
        CharacterMovementState free = pulled;
        double slack = settings.slackAllowance();
        double stationaryTargetY = 0;
        boolean reachedApex = false;
        for (int tick = 0; tick < 20; tick++) {
            Vec3 actor = new Vec3(0, pulled.position().y(), 0);
            Vec3 velocity = new Vec3(0, pulled.velocity().y(), 0);
            Vec3 acceleration = TetherSpring.acceleration(actor, velocity, new Vec3(0, stationaryTargetY, 0),
                    Vec3.ZERO, slack, settings.stiffness(), settings.progressiveStiffness(), settings.damping(),
                    settings.maximumAcceleration());
            assertEquals(Vec3.ZERO, acceleration, "jump trajectory must remain inside resolved leash slack");
            pulled = motor.tick(pulled, 0, 0, tick == 0, false, 0,
                    withConstraint(env, acceleration.scale(GroundedHarnessPhysics.TICK_SECONDS)), false);
            free = motor.tick(free, 0, 0, tick == 0, false, 0, env, false);
            assertEquals(free.position().y(), pulled.position().y(), 1e-12);
            if (tick > 0 && pulled.velocity().y() <= 0) { reachedApex = true; break; }
        }
        assertTrue(reachedApex, "fixture should include the jump apex");
        Vec3 stretched = TetherSpring.acceleration(Vec3.ZERO, Vec3.ZERO,
                new Vec3(0, settings.slackAllowance() + .1, 0), Vec3.ZERO,
                settings.slackAllowance(), settings.stiffness(), settings.progressiveStiffness(),
                settings.damping(), settings.maximumAcceleration());
        assertTrue(stretched.y() > 0, "a lower endpoint is attracted upward beyond slack");
        assertTrue(stretched.length() <= settings.maximumAcceleration());
        Vec3 reaction = TetherSpring.acceleration(new Vec3(0, settings.slackAllowance() + .1, 0), Vec3.ZERO,
                Vec3.ZERO, Vec3.ZERO, settings.slackAllowance(), settings.stiffness(),
                settings.progressiveStiffness(), settings.damping(), settings.maximumAcceleration());
        Vec3 opposite = stretched.scale(-1);
        assertEquals(opposite.x, reaction.x, 0, "endpoint response is equal and opposite on X");
        assertEquals(opposite.y, reaction.y, 0, "endpoint response is equal and opposite on Y");
        assertEquals(opposite.z, reaction.z, 0, "endpoint response is equal and opposite on Z");
    }

    private static CharacterMovementEnvironment withConstraint(CharacterMovementEnvironment base, Vec3 impulse) {
        return new CharacterMovementEnvironment(base.secondsPerTick(), base.gravityPerSecondSquared(),
                base.jumpVelocityPerSecond(), base.verticalDrag(), base.externalImpulse(),
                base.maximumExternalImpulsePerTick(), base.maxUpwardStepBlocks(), base.collisionResolver(),
                base.surfaceMovementFactor(), base.voluntarySpeedFactor(),
                new MovementVector(impulse.x, impulse.y, impulse.z));
    }
}
