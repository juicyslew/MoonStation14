package com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server;

import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.ms14.hands.network.LifecycleHandActorFixture;
import com.juicyslew.moonstation14.ms14.player_body_control.character.MindControlledMob;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessEntity;
import com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem;
import com.juicyslew.moonstation14.ms14.movement.CharacterMovementEnvironment;
import com.juicyslew.moonstation14.ms14.movement.CharacterMovementPolicy;
import com.juicyslew.moonstation14.ms14.movement.CharacterMovementState;
import com.juicyslew.moonstation14.ms14.movement.MovementCollisionResolver;
import com.juicyslew.moonstation14.ms14.movement.MovementVector;
import com.juicyslew.moonstation14.ms14.player_body_control.movement.GroundedHarnessMotor;
import com.juicyslew.moonstation14.ms14.player_body_control.movement.GroundedHarnessPhysics;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Exercises native LivingEntity.aiStep velocity writes on actual owned and unowned mobs. */
@GameTestTags("owned-velocity-authority")
@PrefixGameTestTemplate(false)
public final class OwnedLivingVelocityGameTests {
    private OwnedLivingVelocityGameTests() { }

    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 300)
    public static void preservesOnlyMovementOwnedLivingVelocity(GameTestHelper helper) {
        LifecycleHandActorFixture.start(helper, "owned-velocity", owner -> {
            var body = (PlayerCharacterHarnessEntity) owner.authority().body();
            var control = EntityType.VILLAGER.create(helper.getLevel());
            if (control == null) { helper.fail("could not create unowned villager control"); return; }
            control.moveTo(body.getX() + 8, body.getY(), body.getZ() + 8, 0, 0);
            control.setNoAi(true);
            control.setNoGravity(true);
            if (!helper.getLevel().addFreshEntity(control)) { helper.fail("could not add villager control"); return; }
            try {
                MindControlledMob bodyMarker = (MindControlledMob) (Object) body;
                MindControlledMob controlMarker = (MindControlledMob) (Object) control;
                if (!body.isNoAi() || !bodyMarker.moonstation14$isMovementOwned())
                    throw new AssertionError("lifecycle body is not a NoAI movement-owned harness");
                if (!control.isNoAi() || controlMarker.moonstation14$isMovementOwned()
                        || !control.isAlive()
                        || com.juicyslew.moonstation14.ms14.player_body_control.character.OwnedCorpsePhysics.eligible(control))
                    throw new AssertionError("villager control is not alive and unowned");

                Vec3 original = body.getDeltaMovement();
                try {
                    Vec3 medium = new Vec3(.25, .42, -.15);
                    body.setDeltaMovement(medium);
                    body.aiStep();
                    assertMovement(helper, body.getDeltaMovement(), medium, "medium owner velocity");

                    Vec3 tiny = new Vec3(.002, -.001, .0025);
                    body.setDeltaMovement(tiny);
                    body.aiStep();
                    assertMovement(helper, body.getDeltaMovement(), tiny, "tiny owner velocity");

                    var resolved = CharacterIdentitySystem.resolveForActor(body)
                            .orElseThrow(() -> new AssertionError("resolved character data unavailable"));
                    var policy = CharacterMovementPolicy.fromCharacterData(resolved);
                    var open = (MovementCollisionResolver) (position, requested, wasGrounded) ->
                            new MovementCollisionResolver.CollisionResult(requested, false);
                    var environment = new CharacterMovementEnvironment(GroundedHarnessPhysics.TICK_SECONDS,
                            GroundedHarnessPhysics.GRAVITY_PER_SECOND_SQUARED,
                            GroundedHarnessPhysics.JUMP_VELOCITY_PER_SECOND, GroundedHarnessPhysics.VERTICAL_DRAG,
                            MovementVector.ZERO, 32d, body.maxUpStep(), open);
                    var motor = new GroundedHarnessMotor(policy);
                    Vec3 position = body.position();
                    Vec3 initialPosition = position;
                    CharacterMovementState state = new CharacterMovementState(
                            new MovementVector(position.x, position.y, position.z),
                            new MovementVector(2, 0, 0), false);
                    for (int step = 1; step <= 3; step++) {
                        body.setDeltaMovement(state.velocity().x() / 20d, state.velocity().y() / 20d,
                                state.velocity().z() / 20d);
                        Vec3 suppliedVelocity = body.getDeltaMovement();
                        body.aiStep();
                        assertMovement(helper, body.getDeltaMovement(), suppliedVelocity,
                                "shared adapter step " + step + " native aiStep");
                        state = motor.tick(state, 0, 0, false, false, 0, environment, false);
                        double expectedX = 2 * Math.pow((double) .91F, step);
                        // Cumulative displacement is measured from the original position, not the prior tick.
                        double expectedPositionX = initialPosition.x + .1 * (1 - Math.pow((double) .91F, step))
                                / (1 - (double) .91F);
                        assertNear(state.velocity().x(), expectedX, "shared adapter coast velocity step " + step);
                        assertNear(state.position().x(), expectedPositionX, "prior-velocity displacement step " + step);
                        position = new Vec3(state.position().x(), state.position().y(), state.position().z());
                    }

                    var snappedState = motor.tick(new CharacterMovementState(
                                    new MovementVector(position.x, position.y, position.z),
                                    new MovementVector(.04, 0, .05), false),
                            0, 0, false, false, 0, environment, false);
                    assertNear(snappedState.position().x(), position.x,
                            "adapter pre-clean prevents tiny horizontal X displacement");
                    assertNear(snappedState.position().z(), position.z,
                            "adapter pre-clean prevents tiny horizontal Z displacement");

                    Vec3 controlTiny = new Vec3(.002, 0, .0025);
                    control.setDeltaMovement(controlTiny);
                    control.aiStep();
                    Vec3 snapped = control.getDeltaMovement();
                    if (snapped.x != 0 || snapped.y != 0 || snapped.z != 0)
                        throw new AssertionError("unowned control did not retain native tiny-axis snapping: " + snapped);
                } finally {
                    body.setDeltaMovement(original);
                }
                helper.succeed();
            } catch (RuntimeException | AssertionError failure) {
                helper.fail("owned LivingEntity.aiStep velocity authority check failed: " + failure);
            } finally {
                control.discard();
            }
        });
    }

    private static void assertMovement(GameTestHelper helper, Vec3 actual, Vec3 expected, String label) {
        if (actual.x != expected.x || actual.y != expected.y || actual.z != expected.z)
            throw new AssertionError(label + " changed by native aiStep: expected=" + expected + " actual=" + actual);
    }

    private static void assertNear(double actual, double expected, String label) {
        if (Math.abs(actual - expected) > 1e-10)
            throw new AssertionError(label + " expected=" + expected + " actual=" + actual);
    }
}
