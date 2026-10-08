package com.juicyslew.moonstation14.ms14.loose_objects.pulling;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.ms14.character.ModCharacters;
import com.juicyslew.moonstation14.ms14.damage.DamageKeys;
import com.juicyslew.moonstation14.ms14.damage.DamageSystem;
import com.juicyslew.moonstation14.ms14.hands.live.LiveHands;
import com.juicyslew.moonstation14.ms14.mobs.MobStateSystem;
import com.juicyslew.moonstation14.ms14.player_body_control.character.GroundedHarnessWorldStep;
import com.juicyslew.moonstation14.ms14.player_body_control.character.OwnedCorpsePhysics;
import com.juicyslew.moonstation14.ms14.player_body_control.character.MindControlledMob;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessBinder;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessEntity;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessRegistration;
import com.juicyslew.moonstation14.entities.LooseItemEntity;
import com.juicyslew.moonstation14.entities.LooseChunkBounds;
import com.juicyslew.moonstation14.item.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.Pose;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Map;
import java.util.UUID;

/** Exercises tether forces through ordinary owned-character movement and native corpse travel. */
@PrefixGameTestTemplate(false)
@GameTestTags({"pulling", "posture"})
public final class ElasticBodyPullGameTests {
    private ElasticBodyPullGameTests() { }

    @GameTest(templateNamespace = "moonstation14", template = "atmos_large_empty", timeoutTicks = 100)
    public static void trappedDeadBodyRestrainsForwardMovement(GameTestHelper helper) {
        floor(helper);
        PlayerCharacterHarnessEntity actor = harness(helper, new BlockPos(4, 2, 4));
        PlayerCharacterHarnessEntity target = harness(helper, new BlockPos(4, 2, 2));
        kill(target);
        PullFixtureAim.aimAtVisibleInteractionPoint(actor, target);
        attach(actor, target);
        // The obstruction is installed after admission, so the fixture uses the same legitimate initial
        // body-pull selection as the existing trusted body-pull fixtures.
        helper.setBlock(new BlockPos(4, 2, 3), Blocks.STONE);
        GroundedHarnessWorldStep stepper = new GroundedHarnessWorldStep();
        final int[] ticks = {0};
        double startZ = actor.getZ();
        boolean[] advanced = {false};
        helper.onEachTick(() -> {
            ticks[0]++;
            var movement = stepper.step(actor, 0, 1000, false, false, 0f);
            require(movement.isPresent(), "grounded owned puller accepts every commanded movement step");
            if (actor.getZ() > startZ + .25) advanced[0] = true;
            if (ticks[0] >= 40) {
                require(BodyPullService.isPulling(actor), "stuck target retains active pull lease");
                require(advanced[0], "puller demonstrates nonzero forward movement before restraint");
                require(actor.getZ() - startZ < 3.0, "tether reaction restrains actor displacement");
                require(Math.abs(actor.getZ() - target.getZ()) < 5.0,
                        "endpoints remain within finite elastic lag");
                detach(actor);
                helper.succeed();
                return;
            }
        });
    }

    @GameTestTags("suite-stabilization")
    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 90)
    public static void jumpingTautPullerFallsAndLandsAfterInputStops(GameTestHelper helper) {
        floor(helper);
        PlayerCharacterHarnessEntity actor = harness(helper, new BlockPos(4, 2, 4));
        PlayerCharacterHarnessEntity target = harness(helper, new BlockPos(4, 2, 2));
        kill(target);
        PullFixtureAim.aimAtVisibleInteractionPoint(actor, target);
        attach(actor, target);
        helper.setBlock(new BlockPos(4, 2, 3), Blocks.STONE);
        GroundedHarnessWorldStep stepper = new GroundedHarnessWorldStep();
        double floorY = actor.getY();
        final int[] ticks = {0};
        double[] maxY = {actor.getY()};
        helper.onEachTick(() -> {
            ticks[0]++;
            int wishZ = ticks[0] >= 6 && ticks[0] <= 17 ? 1000 : 0;
            boolean jump = ticks[0] == 6;
            var movement = stepper.step(actor, 0, wishZ, jump, false, 0f);
            require(movement.isPresent(), "owned puller remains movement-eligible through jump and landing");
            maxY[0] = Math.max(maxY[0], actor.getY());
            if (ticks[0] >= 55) {
                require(maxY[0] > floorY + .2, "taut puller actually jumps above floor before descending");
                require(actor.onGround(), "gravity returns puller to supporting floor");
                require(Math.abs(actor.getY() - floorY) < .08, "puller settles at original floor height");
                require(MobStateSystem.isDead(target) && !target.isRemoved(), "medical corpse remains retained");
                detach(actor);
                helper.succeed();
            }
        });
    }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 70)
    public static void detachedDeadBodyFallsAndSettlesOnNativeTick(GameTestHelper helper) {
        floor(helper);
        PlayerCharacterHarnessEntity body = harness(helper, new BlockPos(4, 8, 4));
        kill(body);
        ((MindControlledMob) (Object) body).moonstation14$setMovementOwned(false);
        double startY = body.getY();
        final int[] ticks = {0};
        helper.onEachTick(() -> {
            ticks[0]++;
            require(!((MindControlledMob) (Object) body).moonstation14$isMovementOwned(),
                    "detached corpse never regains movement ownership");
            if (ticks[0] == 40) {
                require(helper.getLevel().getEntity(body.getUUID()) == body && !body.isRemoved()
                                && MobStateSystem.isDead(body), "native-ticked medical corpse identity is retained");
                require(startY - body.getY() > 4.0 && body.onGround(),
                        "detached corpse falls and settles under passive server physics");
                require(Double.isFinite(body.getDeltaMovement().x) && Double.isFinite(body.getDeltaMovement().y)
                        && Double.isFinite(body.getDeltaMovement().z), "corpse velocity remains finite");
                helper.succeed();
            }
        });
    }

    @GameTestTags({"merge-main-regression", "suite-stabilization", "detached-pull-stabilization"})
    @GameTest(templateNamespace = "moonstation14", template = "atmos_large_empty", timeoutTicks = 100)
    public static void detachedDeadBodyMovesFromLiveActorsNativePull(GameTestHelper helper) {
        // This fixture moves the puller across the whole corridor; require the declared template volume
        // to provide that headroom instead of inheriting undeclared neighboring GameTest blocks.
        for (int x = 1; x <= 14; x++) for (int z = 3; z <= 5; z++) for (int y = 2; y <= 4; y++) {
            BlockPos pos = helper.absolutePos(new BlockPos(x, y, z));
            require(helper.getLevel().getBlockState(pos).isAir(), "detached-pull corridor must be owned air: "
                    + pos + " state=" + helper.getLevel().getBlockState(pos));
        }
        for (int x = 1; x <= 14; x++) for (int z = 1; z <= 7; z++)
            helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
        PlayerCharacterHarnessEntity actor = harness(helper, new BlockPos(5, 2, 4));
        PlayerCharacterHarnessEntity body = harness(helper, new BlockPos(2, 2, 4));
        kill(body);
        PullFixtureAim.aimAtVisibleInteractionPoint(actor, body);
        var actorData = com.juicyslew.moonstation14.ms14.player_body_control.server.ActiveCharacterPolicy
                .resolveActor(actor).orElse(null);
        var bounds = com.juicyslew.moonstation14.ms14.standing.BodyInteractionBounds.of(body);
        var eye = actor.getEyePosition();
        var rayEnd = eye.add(actor.getLookAngle().scale(5));
        var rayPoint = com.juicyslew.moonstation14.ms14.standing.BodyInteractionRay
                .hit(bounds, eye, rayEnd, 5, Double.POSITIVE_INFINITY).orElse(null);
        var blockHit = rayPoint == null ? null : helper.getLevel().clip(new net.minecraft.world.level.ClipContext(
                eye, rayPoint, net.minecraft.world.level.ClipContext.Block.COLLIDER,
                net.minecraft.world.level.ClipContext.Fluid.NONE, actor));
        LiveHands actorHands = actor.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        require(actorData != null && actorData.component(
                        com.juicyslew.moonstation14.ms14.character.components.PullerComponent.class).isPresent()
                        && com.juicyslew.moonstation14.ms14.character.CharacterControlSystem.canAct(actor)
                        && BodyPullService.eligibleTarget(body)
                        && BodyPullService.probeSelected(helper.getLevel(), actor, body.getUUID()) == body
                        && actorHands != null && actorHands.compatible(actor)
                        && actorHands.reservation("left").isEmpty() && actorHands.stackCopy("left").isEmpty()
                        && !BodyPullService.isPulling(actor),
                "detached pull preflight must satisfy canonical admission: "
                        + pullAdmissionDiagnostic(helper, actor, body, bounds, eye, rayEnd, rayPoint, blockHit,
                        actorData, actorHands));
        attach(actor, body);
        ((MindControlledMob) (Object) body).moonstation14$setMovementOwned(false);
        require(OwnedCorpsePhysics.eligible(body) && !body.isNoGravity() && !body.noPhysics
                        && MobStateSystem.isDead(body)
                        && !com.juicyslew.moonstation14.ms14.containment.BodyContainmentService
                        .forLevel(helper.getLevel()).ownerOf(body).isPresent(),
                "detached corpse satisfies native tether-physics eligibility: eligible="
                        + OwnedCorpsePhysics.eligible(body) + ", noGravity=" + body.isNoGravity()
                    + ", noPhysics=" + body.noPhysics + ", alive=" + body.isAlive()
                        + ", dead=" + MobStateSystem.isDead(body));
        double startX = body.getX();
        GroundedHarnessWorldStep stepper = new GroundedHarnessWorldStep();
        final int[] ticks = {0};
        final double[] maxEndpointDistance = {0};
        final double[] maxTetherImpulse = {0};
        final boolean[] taut = {false};
        final int[] tautTicks = {0};
        helper.onEachTick(() -> {
            ticks[0]++;
            var movement = stepper.step(actor, 1000, 0, false, false, 0f);
            require(movement.isPresent(), "live actor continues stepping while pulling detached corpse");
            require(OwnedCorpsePhysics.eligible(body), "corpse remains eligible during tether travel: "
                    + "noGravity=" + body.isNoGravity() + ", noPhysics=" + body.noPhysics
                    + ", alive=" + body.isAlive() + ", med=" + body.getExistingDataOrNull(
                    ModDataAttachments.MOB_STATE.get()));
            require(!((MindControlledMob) (Object) body).moonstation14$isMovementOwned(),
                    "pull lease does not restore corpse movement ownership");
            var rope = body.getExistingDataOrNull(ModDataAttachments.ROPE_CONSTRAINT.get());
            if (rope != null && !rope.constraints().isEmpty()) {
                var constraint = rope.constraints().get(0);
                double distance = constraint.self().position().distanceTo(constraint.otherSnapshot().position());
                maxEndpointDistance[0] = Math.max(maxEndpointDistance[0], distance);
                if (distance > constraint.slack()) {
                    taut[0] = true;
                    tautTicks[0]++;
                }
                var impulse = com.juicyslew.moonstation14.ms14.loose_objects.pulling.TetherMotion
                        .deltaVelocityPerSecond(body);
                maxTetherImpulse[0] = Math.max(maxTetherImpulse[0], impulse.length());
            }
            if (ticks[0] == 85) {
                require(helper.getLevel().getEntity(body.getUUID()) == body && !body.isRemoved()
                        && MobStateSystem.isDead(body), "pulled corpse identity remains retained");
                require(taut[0] && tautTicks[0] >= 5 && maxTetherImpulse[0] > 0,
                        "actor input sustains taut tether and produces native corpse impulse: tautTicks="
                                + tautTicks[0] + ", maxDistance="
                                + maxEndpointDistance[0] + ", maxImpulse=" + maxTetherImpulse[0]
                                + ", rope=" + body.getExistingDataOrNull(ModDataAttachments.ROPE_CONSTRAINT.get()));
                require(Math.abs(body.getX() - startX) > .5,
                        "corpse moves horizontally through native entity ticks under live-actor tether; actor="
                                + entityDiagnostic(actor) + " corpse=" + entityDiagnostic(body)
                                + " startX=" + startX + " dx=" + (body.getX() - startX));
                require(Double.isFinite(body.getDeltaMovement().x) && Double.isFinite(body.getDeltaMovement().y)
                        && Double.isFinite(body.getDeltaMovement().z), "pulled corpse velocity remains finite");
                require(BodyPullService.isPulling(actor), "body pull lease remains active during native movement; actor="
                        + entityDiagnostic(actor) + " corpse=" + entityDiagnostic(body));
                detach(actor);
                helper.succeed();
            }
        });
    }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 80)
    @GameTestTags({"merge-main-regression"})
    public static void looseJugFollowsNativeTickUnderElasticPull(GameTestHelper helper) {
        floor(helper);
        for (int z = 8; z <= 12; z++) for (int x = 1; x <= 7; x++)
            helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
        PlayerCharacterHarnessEntity actor = harness(helper, new BlockPos(4, 2, 5));
        actor.setYRot(0);
        actor.setYHeadRot(0);
        ItemStack stack = new ItemStack(ModItems.JUG.get());
        Vec3 start = helper.absoluteVec(new Vec3(4.5, 2, 4.5));
        LooseItemEntity jug = LooseItemEntity.create(helper.getLevel(), start, stack);
        jug.setNoPickUpDelay();
        require(helper.getLevel().addFreshEntity(jug), "loose jug spawned");
        int initialJugTickCount = jug.tickCount;
        var motionArea = jug.getBoundingBox().expandTowards(0, 0, 8).inflate(1.0);
        var chunks = LooseChunkBounds.from(motionArea);
        for (int cx = chunks.minX(); cx <= chunks.maxX(); cx++)
            for (int cz = chunks.minZ(); cz <= chunks.maxZ(); cz++)
                helper.getLevel().getChunk(cx, cz);
        // Register once before ticking; mutate no scheduled-callback map from its iteration.
        final int[] ticks = {0};
        final double[] startZ = {0};
        GroundedHarnessWorldStep stepper = new GroundedHarnessWorldStep();
        helper.onEachTick(() -> {
            ticks[0]++;
            if (ticks[0] == 3) {
                startZ[0] = jug.getZ();
                PullFixtureAim.aimAtPoint(actor, jug.getBoundingBox().getCenter());
                attach(actor, jug);
                return;
            }
            if (ticks[0] < 3) return;
            var movement = stepper.step(actor, 0, 1000, false, false, 0f);
            require(movement.isPresent(), "loose-item puller accepts each world-step command");
            if (ticks[0] >= 3) require(helper.getLevel().isPositionEntityTicking(jug.blockPosition()),
                    "jug remains entity-ticking during measured pull travel");
            if (ticks[0] >= 38) {
                require(jug.tickCount - initialJugTickCount >= 30,
                        "native jug ticks advance throughout measured travel; tickCountDelta="
                                + (jug.tickCount - initialJugTickCount));
                require(jug.getZ() > startZ[0] + .5, "native loose-item tick integrates pull force into travel; actor="
                        + entityDiagnostic(actor) + " jug=" + entityDiagnostic(jug) + " startZ=" + startZ[0]
                        + " dz=" + (jug.getZ() - startZ[0]) + " removed=" + jug.isRemoved()
                        + " present=" + (helper.getLevel().getEntity(jug.getUUID()) == jug) + " tickCount="
                        + jug.tickCount + " motionBlockedByUnloaded=" + jug.motionBlockedByUnloaded()
                        + " hasChunkAt=" + helper.getLevel().hasChunkAt(jug.blockPosition()) + " entityTicking="
                        + helper.getLevel().isPositionEntityTicking(jug.blockPosition()));
                require(BodyPullService.isPulling(actor), "elastic lag retains loose-item pull lease");
                require(Math.abs(actor.getZ() - jug.getZ()) < 5,
                        "moving loose item remains within finite elastic lag");
                require(helper.getLevel().getEntity(jug.getUUID()) == jug && !jug.isRemoved(),
                        "native motion preserves loose entity identity");
                detach(actor);
                helper.succeed();
            }
        });
    }

    @GameTestTags("suite-stabilization")
    @GameTest(templateNamespace = "moonstation14", template = "atmos_large_empty", timeoutTicks = 40)
    public static void stoppingOneChainLinkPreservesTheOtherUntilAllEndpointsClear(GameTestHelper helper) {
        for (int x = 1; x <= 7; x++) for (int z = 1; z <= 7; z++) for (int y = 2; y <= 4; y++) {
            BlockPos pos = helper.absolutePos(new BlockPos(x, y, z));
            require(helper.getLevel().getBlockState(pos).isAir(), "pull-chain selector space must be declared air: "
                    + pos + " state=" + helper.getLevel().getBlockState(pos));
        }
        floor(helper);
        PlayerCharacterHarnessEntity a = harness(helper, new BlockPos(4, 2, 6));
        PlayerCharacterHarnessEntity b = harness(helper, new BlockPos(4, 2, 4));
        PlayerCharacterHarnessEntity c = harness(helper, new BlockPos(4, 2, 2));
        PullFixtureAim.aimAtVisibleInteractionPoint(a, b);
        PullFixtureAim.aimAtVisibleInteractionPoint(b, c);
        attach(a, b);
        attach(b, c);
        final int[] ticks = {0};
        helper.onEachTick(() -> {
            ticks[0]++;
            if (ticks[0] == 1) {
            require(endpointCount(a) == 1 && endpointCount(b) == 2 && endpointCount(c) == 1,
                    "three-body chain publishes both tether endpoints");
            detach(a);
            } else if (ticks[0] == 2) {
                require(endpointCount(a) == 0 && endpointCount(b) == 1 && endpointCount(c) == 1,
                        "stopping incoming link preserves outgoing link");
                require(BodyPullService.isPulling(b), "second lease remains active");
                detach(b);
            } else if (ticks[0] == 3) {
                require(endpointCount(a) == 0 && endpointCount(b) == 0 && endpointCount(c) == 0,
                        "stopping final link clears every attachment");
                helper.succeed();
            }
        });
    }

    private static void attach(PlayerCharacterHarnessEntity actor, LooseItemEntity target) {
        LiveHands hands = actor.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        var result = BodyPullService.toggleFixture(actor, target.getUUID(), "left", hands.revision(), () -> true);
        require(result == BodyPullService.Result.SUCCESS, "trusted loose-item pull fixture admitted: " + result);
    }

    private static int endpointCount(PlayerCharacterHarnessEntity entity) {
        RopeConstraintAttachment attachment = entity.getExistingDataOrNull(ModDataAttachments.ROPE_CONSTRAINT.get());
        return attachment == null ? 0 : attachment.constraints().size();
    }

    private static String entityDiagnostic(net.minecraft.world.entity.Entity entity) {
        RopeConstraintAttachment rope = entity.getExistingDataOrNull(ModDataAttachments.ROPE_CONSTRAINT.get());
        String constraint = "none";
        if (rope != null && !rope.constraints().isEmpty()) {
            var c = rope.constraints().get(0);
            constraint = "n=" + rope.constraints().size() + ",slack=" + c.slack() + ",role=" + c.role();
        }
        return "p=" + entity.position() + ",dv=" + entity.getDeltaMovement() + ",pose=" + entity.getPose()
                + ",down=" + (entity instanceof net.minecraft.world.entity.LivingEntity living
                && com.juicyslew.moonstation14.ms14.standing.PostureSystem.isDown(living))
                + ",med=" + entity.getExistingDataOrNull(ModDataAttachments.MOB_STATE.get())
                + ",rope=" + constraint;
    }

    private static String pullAdmissionDiagnostic(GameTestHelper helper, PlayerCharacterHarnessEntity actor,
                                                   PlayerCharacterHarnessEntity target,
                                                   net.minecraft.world.phys.AABB bounds, Vec3 eye, Vec3 rayEnd,
                                                   Vec3 rayPoint, net.minecraft.world.phys.BlockHitResult blockHit,
                                                   com.juicyslew.moonstation14.component.codec.json.CharacterData actorData,
                                                   LiveHands hands) {
        String puller = actorData == null ? "unresolved" : "puller=" + actorData.component(
                com.juicyslew.moonstation14.ms14.character.components.PullerComponent.class).isPresent();
        String hand = hands == null ? "missing" : "revision=" + hands.revision() + ",ids=" + hands.handIds()
                + ",reservation=" + hands.reservation("left") + ",stack=" + hands.stackCopy("left");
        return "actor=" + actor.position() + ", eye=" + eye + ", look=" + actor.getLookAngle()
                + ", canAct=" + com.juicyslew.moonstation14.ms14.character.CharacterControlSystem.canAct(actor)
                + ", " + puller + ", target=" + target.position() + ", targetAlive=" + target.isAlive()
                + ", targetDead=" + MobStateSystem.isDead(target) + ", eligibleTarget="
                + BodyPullService.eligibleTarget(target) + ", selected="
                + (BodyPullService.probeSelected(helper.getLevel(), actor, target.getUUID()) == target)
                + ", hand=" + hand + ", rayEnd=" + rayEnd + ", bounds=" + bounds + ", rayPoint=" + rayPoint
                + ", blockHit=" + (blockHit == null ? "none" : blockHit.getType() + "@" + blockHit.getBlockPos())
                + ", actorPulling=" + BodyPullService.isPulling(actor);
    }

    private static void floor(GameTestHelper helper) {
        for (int x = 1; x <= 7; x++) for (int z = 1; z <= 7; z++)
            helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
    }

    private static PlayerCharacterHarnessEntity harness(GameTestHelper helper, BlockPos pos) {
        PlayerCharacterHarnessEntity body = PlayerCharacterHarnessRegistration.getEntityType().create(helper.getLevel());
        require(body != null, "registered character harness created");
        body.setPos(helper.absolutePos(pos).getX() + .5, helper.absolutePos(pos).getY(),
                helper.absolutePos(pos).getZ() + .5);
        body.setNoAi(true);
        body.setNoGravity(false);
        require(helper.getLevel().addFreshEntity(body), "harness added to world");
        require(PlayerCharacterHarnessBinder.bind(body, UUID.randomUUID(), "elastic-pull",
                UUID.randomUUID(), ModCharacters.HUMAN_ID), "harness bound as human character");
        ((MindControlledMob) (Object) body).moonstation14$setMovementOwned(true);
        LiveHands.initialize(body).orElseThrow();
        body.setYRot(180);
        body.setYHeadRot(180);
        return body;
    }

    private static void kill(PlayerCharacterHarnessEntity body) {
        require(DamageSystem.applyHealthChange(body, Map.of(DamageKeys.BLUNT, 200f), 1f, true)
                == DamageSystem.Result.APPLIED && MobStateSystem.isDead(body), "real damage kills target medically");
    }

    private static void attach(PlayerCharacterHarnessEntity actor, PlayerCharacterHarnessEntity target) {
        LiveHands hands = actor.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        var result = BodyPullService.toggleFixture(actor, target.getUUID(), "left", hands.revision(), () -> true);
        require(result == BodyPullService.Result.SUCCESS, "trusted body-pull fixture admission=" + result
                + ", actorLook=" + actor.getLookAngle() + ", actorEye=" + actor.getEyePosition()
                + ", targetBounds=" + com.juicyslew.moonstation14.ms14.standing.BodyInteractionBounds.of(target)
                + ", targetPose=" + target.getPose() + ", actorCanAct="
                + com.juicyslew.moonstation14.ms14.character.CharacterControlSystem.canAct(actor));
    }

    private static void detach(PlayerCharacterHarnessEntity actor) {
        BodyPullService.stopFixture(actor);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
