package com.juicyslew.moonstation14.ms14.loose_objects.pulling;

import com.juicyslew.moonstation14.ms14.character.components.TetherSettings;
import com.juicyslew.moonstation14.ms14.movement.CharacterMovementEnvironment;
import com.juicyslew.moonstation14.ms14.movement.CharacterMovementPolicy;
import com.juicyslew.moonstation14.ms14.movement.CharacterMovementState;
import com.juicyslew.moonstation14.ms14.movement.MovementCollisionResolver;
import com.juicyslew.moonstation14.ms14.movement.MovementVector;
import com.juicyslew.moonstation14.ms14.player_body_control.movement.GroundedHarnessMotor;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TwoEndpointStandingTetherTest {
    private static final double DT = .05;
    private static final MovementCollisionResolver FLOOR = (position, requested, wasGrounded) ->
            new MovementCollisionResolver.CollisionResult(
                    new MovementVector(requested.x(), Math.max(0, requested.y()), requested.z()), true);

    @Test
    void standingHumanDraggingStandingHumanSettlesWithBothEndpointsMoving() {
        assertEquals(6d, TetherSettings.DEFAULT.damping());
        Simulation result = simulate(TetherSettings.DEFAULT);
        System.out.println("damping=6 maxForce=" + result.maxForce + " reverse=" + result.actorReverseTicks
                + " spread=" + result.finalTargetVelocityVariation + " mean=" + result.meanTargetSpeed);
        assertTrue(result.maxForce <= TetherSettings.DEFAULT.maximumAcceleration());
        assertTrue(result.actorReverseTicks < 5,
                "actor should recover from initial taut-rope impulse while continuously walking away: "
                        + result.actorReverseTicks);
        assertTrue(result.finalTargetVelocityVariation < .02,
                "target velocity should converge, not alternate: " + result.finalTargetVelocityVariation);
        assertTrue(Math.abs(result.meanTargetSpeed) > .1, "target must physically move under the drag force");
    }

    // Diagnostic regression control: current parameters other than damping with the former value.
    @Test
    void formerTenDampingRemainsStableWithSixtyHertzGroundMotorSubsteps() {
        Simulation result = simulate(new TetherSettings(60, 40, 10, 160, 1.5));
        System.out.println("damping=10 maxForce=" + result.maxForce + " reverse=" + result.actorReverseTicks
                + " spread=" + result.finalTargetVelocityVariation + " mean=" + result.meanTargetSpeed);
        assertTrue(result.finalTargetVelocityVariation < .02,
                "grounded substeps avoid artificial endpoint alternation without changing tether forces: "
                        + result.finalTargetVelocityVariation);
    }

    private static Simulation simulate(TetherSettings tuning) {
        GroundedHarnessMotor motor = new GroundedHarnessMotor(CharacterMovementPolicy.HUMAN);
        CharacterMovementState actor = state(0, 0, 0), target = state(3, 0, 0);
        double targetSpeedSum = 0, maxForce = 0, finalMin = Double.POSITIVE_INFINITY;
        double finalMax = Double.NEGATIVE_INFINITY;
        int reverseTicks = 0;
        for (int tick = 0; tick < 400; tick++) {
            Vec3 a = vec(actor.position()), av = vec(actor.velocity());
            Vec3 b = vec(target.position()), bv = vec(target.velocity());
            Vec3 force = TetherSpring.acceleration(a, av, b, bv, tuning.slackAllowance(),
                    tuning.stiffness(), tuning.progressiveStiffness(), tuning.damping(), tuning.maximumAcceleration());
            maxForce = Math.max(maxForce, force.length());
            CharacterMovementEnvironment actorEnv = environment(new MovementVector(force.x * DT, 0, force.z * DT));
            CharacterMovementEnvironment targetEnv = environment(new MovementVector(-force.x * DT, 0, -force.z * DT));
            // Actor wishes away from target. Constraint impulses are computed from simultaneous pre-tick snapshots.
            actor = motor.tick(actor, -1000, 0, false, true, 0, actorEnv, false);
            target = motor.tick(target, 0, 0, false, false, 0, targetEnv, false);
            if (actor.velocity().x() > 0) reverseTicks++;
            if (tick >= 380) {
                finalMin = Math.min(finalMin, target.velocity().x());
                finalMax = Math.max(finalMax, target.velocity().x());
            }
            if (tick >= 200) targetSpeedSum += target.velocity().x();
        }
        return new Simulation(maxForce, reverseTicks, finalMax - finalMin, targetSpeedSum / 200);
    }

    private static CharacterMovementEnvironment environment(MovementVector impulse) {
        return new CharacterMovementEnvironment(DT, 0, 0, .98, MovementVector.ZERO, 8,
                0, FLOOR, 1, 1, impulse);
    }
    private static CharacterMovementState state(double x, double y, double z) {
        return new CharacterMovementState(new MovementVector(x, y, z), MovementVector.ZERO, true);
    }
    private static Vec3 vec(MovementVector v) { return new Vec3(v.x(), v.y(), v.z()); }
    private record Simulation(double maxForce, int actorReverseTicks,
                              double finalTargetVelocityVariation, double meanTargetSpeed) { }
}
