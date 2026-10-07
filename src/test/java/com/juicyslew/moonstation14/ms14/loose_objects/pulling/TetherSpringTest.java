package com.juicyslew.moonstation14.ms14.loose_objects.pulling;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TetherSpringTest {
    @Test void springIsSlack3dProgressiveAndDamped() {
        Vec3 first = Vec3.ZERO;
        assertEquals(Vec3.ZERO, TetherSpring.acceleration(first, Vec3.ZERO,
                new Vec3(0, 1, 0), Vec3.ZERO, 1.1, 18, 12, 7, 48));
        Vec3 slight = TetherSpring.acceleration(first, Vec3.ZERO, new Vec3(0, 2, 0),
                Vec3.ZERO, 1, 18, 12, 7, 48);
        Vec3 stretched = TetherSpring.acceleration(first, Vec3.ZERO, new Vec3(0, 3, 0),
                Vec3.ZERO, 1, 18, 12, 7, 48);
        assertEquals(0, slight.x);
        assertTrue(stretched.y > slight.y);
        double restDamping = TetherSpring.acceleration(first, Vec3.ZERO, new Vec3(2, 0, 0),
                Vec3.ZERO, 1, 18, 12, 7, 48).x;
        assertTrue(TetherSpring.acceleration(first, Vec3.ZERO, new Vec3(2, 0, 0),
                new Vec3(2, 0, 0), 1, 18, 12, 7, 48).x > restDamping);
        assertTrue(TetherSpring.acceleration(first, Vec3.ZERO, new Vec3(2, 0, 0),
                new Vec3(-2, 0, 0), 1, 18, 12, 7, 48).x < restDamping);
        Vec3 reaction = TetherSpring.acceleration(new Vec3(2, 0, 0), Vec3.ZERO, first, Vec3.ZERO,
                1, 18, 12, 7, 48);
        assertEquals(-restDamping, reaction.x, 1e-9);
        assertEquals(Vec3.ZERO, TetherSpring.acceleration(first, Vec3.ZERO, new Vec3(.5, 0, 0),
                new Vec3(10, 0, 0), 1, 18, 12, 7, 48), "slack does not push or attract");
        assertEquals(48, TetherSpring.acceleration(first, Vec3.ZERO,
                new Vec3(0, 100, 0), Vec3.ZERO, 1, 18, 12, 7, 48).length(), 1e-9);
        assertEquals(Vec3.ZERO, TetherSpring.acceleration(first, Vec3.ZERO,
                new Vec3(Double.NaN, 0, 0), Vec3.ZERO, 1, 18, 12, 7, 48));
    }

    @Test void springIsTranslationInvariantAndStableAtTwentyHertz() {
        Vec3 offset = new Vec3(37, -12, 91);
        Vec3 a = new Vec3(0, 0, 0), b = new Vec3(3, 2, 1);
        Vec3 base = TetherSpring.acceleration(a, Vec3.ZERO, b, Vec3.ZERO,
                1, 60, 40, 20, 160);
        assertEquals(base, TetherSpring.acceleration(a.add(offset), Vec3.ZERO, b.add(offset), Vec3.ZERO,
                1, 60, 40, 20, 160));

        Vec3 first = Vec3.ZERO, second = new Vec3(3.5, 0, 0);
        Vec3 firstVelocity = Vec3.ZERO, secondVelocity = Vec3.ZERO;
        double maxDistance = 0;
        var settings = com.juicyslew.moonstation14.ms14.character.components.TetherSettings.DEFAULT;
        for (int tick = 0; tick < 400; tick++) {
            Vec3 acceleration = TetherSpring.acceleration(first, firstVelocity, second, secondVelocity,
                    2, settings.stiffness(), settings.progressiveStiffness(), settings.damping(),
                    settings.maximumAcceleration());
            Vec3 dv = acceleration.scale(.05);
            firstVelocity = firstVelocity.add(dv).scale(.98);
            secondVelocity = secondVelocity.subtract(dv).scale(.98);
            first = first.add(firstVelocity.scale(.05));
            second = second.add(secondVelocity.scale(.05));
            assertTrue(Double.isFinite(firstVelocity.length()) && firstVelocity.length() < 100);
            assertTrue(Double.isFinite(secondVelocity.length()) && secondVelocity.length() < 100);
            maxDistance = Math.max(maxDistance, first.distanceTo(second));
        }
        assertTrue(maxDistance < 8, "progressive tether remains bounded with ordinary drag");
        assertTrue(firstVelocity.length() + secondVelocity.length() < 1,
                "equal-mass endpoints dissipate the initial stretch response over 20 seconds");
    }

    @Test void fixedAnchorStandingInputSettlesWithoutFrameToFrameVelocityReversal() {
        Vec3 actor = new Vec3(1, 0, 0), actorVelocity = Vec3.ZERO;
        Vec3 anchor = Vec3.ZERO;
        double maximumDistance = 0;
        double minFinal = Double.POSITIVE_INFINITY, maxFinal = Double.NEGATIVE_INFINITY;
        var tuning = com.juicyslew.moonstation14.ms14.character.components.TetherSettings.DEFAULT;
        for (int tick = 0; tick < 300; tick++) {
            Vec3 acceleration = TetherSpring.acceleration(actor, actorVelocity, anchor, Vec3.ZERO, 1,
                    tuning.stiffness(), tuning.progressiveStiffness(), tuning.damping(),
                    tuning.maximumAcceleration());
            // Ground friction removes last tick's horizontal velocity; the motor then applies
            // the standing actor's full desired-speed input before the tether's constraint phase.
            actorVelocity = new Vec3(2.5 + acceleration.x * .05, 0, 0);
            actor = actor.add(actorVelocity.scale(.05));
            maximumDistance = Math.max(maximumDistance, actor.x);
            if (tick >= 280) {
                minFinal = Math.min(minFinal, actor.x);
                maxFinal = Math.max(maxFinal, actor.x);
            }
        }
        assertTrue(maximumDistance < 3, "stuck target progressively restrains the standing puller");
        assertTrue(Math.abs(actorVelocity.x) < .05, "steady tether balances ordinary input");
        assertTrue(maxFinal - minFinal < .02, "settled response has no visible frame-to-frame jitter");
    }

    @Test void admittedTetherSlackStillAllowsBoundedUpwardForceAtStretchedLedgePosition() {
        var tuning = com.juicyslew.moonstation14.ms14.character.components.TetherSettings.DEFAULT;
        // At the ledge edge, actor is on the raised platform; corpse is still on the lower floor.
        // This checks the actual production spring with the fixture's 3D anchor geometry, not a
        // fabricated vertical impulse or altered force parameters.
        // Tether anchors are entity feet, not eye/rope-render attachment points. Derive admitted
        // slack from the fixture's actual initial foot separation (sqrt(13) + configured allowance).
        Vec3 initialActorFeet = new Vec3(4.5, 5.0, 4.5);
        Vec3 initialCorpseFeet = new Vec3(4.5, 2.0, 2.5);
        double admittedSlack = initialActorFeet.distanceTo(initialCorpseFeet) + tuning.slackAllowance();
        // Both positions are reachable in the fixture: actor stands near the far platform edge,
        // corpse remains below the ledge. This is a spring-force sample, not a trajectory proof.
        Vec3 actorFeet = new Vec3(4.5, 5.0, 7.7);
        Vec3 corpseFeet = new Vec3(4.5, 2.0, 2.5);
        Vec3 acceleration = TetherSpring.acceleration(corpseFeet, Vec3.ZERO, actorFeet, Vec3.ZERO,
                admittedSlack, tuning.stiffness(), tuning.progressiveStiffness(), tuning.damping(),
                tuning.maximumAcceleration());
        assertTrue(acceleration.y > 0, "spring force points upward toward the actor: " + acceleration);
        assertTrue(acceleration.length() <= tuning.maximumAcceleration());
    }

}
