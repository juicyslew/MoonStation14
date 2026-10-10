package com.juicyslew.moonstation14.ms14.loose_objects.pulling;

import com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.ms14.character.ModCharacters;
import com.juicyslew.moonstation14.ms14.damage.DamageKeys;
import com.juicyslew.moonstation14.ms14.damage.DamageSystem;
import com.juicyslew.moonstation14.ms14.hands.live.LiveHands;
import com.juicyslew.moonstation14.ms14.mobs.MobStateSystem;
import com.juicyslew.moonstation14.ms14.player_body_control.character.GroundedHarnessWorldStep;
import com.juicyslew.moonstation14.ms14.player_body_control.character.MindControlledMob;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessBinder;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessEntity;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessRegistration;
import com.juicyslew.moonstation14.entities.LooseItemEntity;
import com.juicyslew.moonstation14.gametest.support.FixtureChunkTickets;
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
        var ticket = fixtureTickets(helper, new BlockPos(4, 2, 2), new BlockPos(4, 2, 10), 1);
        PlayerCharacterHarnessEntity actor = harness(helper, new BlockPos(4, 2, 4));
        PlayerCharacterHarnessEntity target = harness(helper, new BlockPos(4, 2, 2));
        require(ticket.entityTicking(actor.blockPosition()) && ticket.entityTicking(target.blockPosition()),
                "puller, target, and selection footprint are entity-ticking before admission");
        kill(target);
        aimAt(actor, visibleInteractionPoint(actor, target));
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
                ticket.close();
                helper.succeed();
                return;
            }
        });
    }

    @GameTestTags("full-suite-movement-regression")
    @GameTest(templateNamespace = "moonstation14", template = "power_receiver_tall_clear", timeoutTicks = 90)
    public static void jumpingTautPullerFallsAndLandsAfterInputStops(GameTestHelper helper) {
        floor(helper);
        var ticket = FixtureChunkTickets.around(helper, helper.absolutePos(new BlockPos(0, 0, 0)),
                helper.absolutePos(new BlockPos(15, 12, 15)), 0);
        FixtureChunkTickets.awaitEntityTicking(helper, ticket, 8, () -> {
        PlayerCharacterHarnessEntity actor = harness(helper, new BlockPos(4, 2, 4));
        PlayerCharacterHarnessEntity target = harness(helper, new BlockPos(4, 2, 2));
        BlockPos selection = helper.absolutePos(new BlockPos(4, 2, 3));
        require(ticket.entityTicking(actor.blockPosition()) && ticket.entityTicking(target.blockPosition())
                        && ticket.entityTicking(selection),
                "puller, target, and selection footprint are entity-ticking before admission");
        kill(target);
        aimAt(actor, visibleInteractionPoint(actor, target));
        attach(actor, target);
        helper.setBlock(new BlockPos(4, 2, 3), Blocks.STONE);
        GroundedHarnessWorldStep stepper = new GroundedHarnessWorldStep();
        double floorY = actor.getY();
        int initialActorTick = actor.tickCount;
        final int[] ticks = {0};
        double[] maxY = {actor.getY()};
        BoundedGameTestWait.safeOnEachTick(helper, () -> {
            ticks[0]++;
            int wishZ = ticks[0] >= 6 && ticks[0] <= 17 ? 1000 : 0;
            boolean jump = ticks[0] == 6;
            var movement = stepper.step(actor, 0, wishZ, jump, false, 0f);
            require(movement.isPresent(), "owned puller remains movement-eligible through jump and landing");
            maxY[0] = Math.max(maxY[0], actor.getY());
            if (ticks[0] >= 56) {
                require(maxY[0] > floorY + .2, "taut puller actually jumps above floor before descending");
                require(actor.onGround(), "gravity returns puller to supporting floor");
                require(actor.tickCount - initialActorTick >= 55,
                        "native actor ticks advance throughout jump and landing");
                require(Math.abs(actor.getY() - floorY) < .08, "puller settles at original floor height");
                require(MobStateSystem.isDead(target) && !target.isRemoved(), "medical corpse remains retained");
                detach(actor);
                ticket.close();
                helper.succeed();
            }
        });
        }, helper.absolutePos(new BlockPos(4, 2, 4)), helper.absolutePos(new BlockPos(4, 2, 2)),
                helper.absolutePos(new BlockPos(4, 2, 3)));
    }

    // Ticket readiness <=8 ticks + one callback handoff + 40 measured ticks = <=49 of 70.
    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 70)
    public static void detachedDeadBodyFallsAndSettlesOnNativeTick(GameTestHelper helper) {
        floor(helper);
        var ticket = fixtureTickets(helper, new BlockPos(3, 1, 3), new BlockPos(5, 8, 5), 1);
        PlayerCharacterHarnessEntity body = harness(helper, new BlockPos(4, 8, 4));
        FixtureChunkTickets.awaitEntityTicking(helper, ticket, 8, () -> {
            require(ticket.entityTicking(body.blockPosition()), "detached corpse chunk is entity-ticking before native fall: "
                    + ticket.diagnostic(body.blockPosition()));
            kill(body);
            ((MindControlledMob) (Object) body).moonstation14$setMovementOwned(false);
            double startY = body.getY();
            final int[] ticks = {0};
            BoundedGameTestWait.safeOnEachTick(helper, () -> {
                ticks[0]++;
                require(ticket.entityTicking(body.blockPosition()), "detached corpse remains entity-ticking through fall: "
                        + ticket.diagnostic(body.blockPosition()));
                require(!((MindControlledMob) (Object) body).moonstation14$isMovementOwned(),
                        "detached corpse never regains movement ownership");
                if (ticks[0] == 40) {
                    require(helper.getLevel().getEntity(body.getUUID()) == body && !body.isRemoved()
                                    && MobStateSystem.isDead(body), "native-ticked medical corpse identity is retained");
                    require(startY - body.getY() > 4.0 && body.onGround(),
                            "detached corpse falls and settles under passive server physics");
                    require(Double.isFinite(body.getDeltaMovement().x) && Double.isFinite(body.getDeltaMovement().y)
                            && Double.isFinite(body.getDeltaMovement().z), "corpse velocity remains finite");
                    ticket.close();
                    helper.succeed();
                }
            });
        }, body.blockPosition());
    }

    @GameTestTags({"merge-main-regression"})
    // Ticket/ground readiness <=8 ticks + one callback handoff + 65 native tether steps = <=74 of 100.
    @GameTest(templateNamespace = "moonstation14", template = "power_receiver_tall_clear", timeoutTicks = 100)
    public static void detachedDeadBodyMovesFromLiveActorsNativePull(GameTestHelper helper) {
        for (int x = 1; x <= 14; x++) for (int z = 1; z <= 7; z++)
            helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
        for (int x = 1; x <= 14; x++) for (int z = 1; z <= 7; z++)
            for (int y = 2; y <= 5; y++) helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
        PlayerCharacterHarnessEntity actor = harness(helper, new BlockPos(5, 2, 4));
        var ticket = fixtureTickets(helper, new BlockPos(1, 1, 1), new BlockPos(14, 4, 7), 1);
        PlayerCharacterHarnessEntity body = harness(helper, new BlockPos(2, 2, 4));
        double floorY = helper.absolutePos(new BlockPos(5, 1, 4)).getY() + 1d;
        body.setPos(body.getX(), floorY, body.getZ());
        require(ticket.entityTicking(actor.blockPosition()) && ticket.entityTicking(body.blockPosition()),
                "both tether endpoints and motion footprint are entity-ticking before admission");
        // The ray from the live puller to its prone target crosses this corridor. Keep its
        // supporting floor intact while removing incidental template/scene occluders only here.
        for (int x = 2; x <= 5; x++) for (int y = 2; y <= 4; y++)
            helper.setBlock(new BlockPos(x, y, 4), Blocks.AIR);
        // Establish the live puller's actual feet at the supporting floor top. Grounding is a
        // fixture prerequisite here, not measured behavior; don't toggle onGround or freeze travel.
        var floorTop = helper.absolutePos(new BlockPos(5, 1, 4)).getY() + 1d;
        actor.setPos(actor.getX(), floorTop, actor.getZ());
        FixtureChunkTickets.awaitEntityTicking(helper, ticket, 8,
                () -> actor.onGround() && body.onGround(), "puller and live target grounded", () -> {
            kill(body);
            aimAt(actor, visibleInteractionPoint(actor, body));
            attach(actor, body);
            ((MindControlledMob) (Object) body).moonstation14$setMovementOwned(false);
            double startX = body.getX();
            GroundedHarnessWorldStep stepper = new GroundedHarnessWorldStep();
            final int[] ticks = {0};
            BoundedGameTestWait.safeOnEachTick(helper, () -> {
                ticks[0]++;
                double actorLocalX = actor.getX() - helper.absolutePos(new BlockPos(0, 0, 0)).getX();
                int wishX = actorLocalX < 13 ? 1000 : 0;
                var movement = stepper.step(actor, wishX, 0, false, false, 0f);
                require(movement.isPresent(), "live actor continues stepping while pulling detached corpse; actorMedical="
                        + actor.getExistingDataOrNull(ModDataAttachments.MOB_STATE.get()) + " actorPosition=" + actor.position());
                require(!((MindControlledMob) (Object) body).moonstation14$isMovementOwned(),
                        "pull lease does not restore corpse movement ownership");
                require(ticket.entityTicking(actor.blockPosition()) && ticket.entityTicking(body.blockPosition()),
                        "both tether endpoints remain entity-ticking along the measured floor corridor: "
                                + ticket.diagnostic(actor.blockPosition()) + "; " + ticket.diagnostic(body.blockPosition()));
                if (ticks[0] == 65) {
                    require(helper.getLevel().getEntity(body.getUUID()) == body && !body.isRemoved()
                                    && MobStateSystem.isDead(body), "pulled corpse identity remains retained");
                    require(Math.abs(body.getX() - startX) > .5,
                            "corpse moves horizontally through native entity ticks under live-actor tether; actor="
                                    + entityDiagnostic(actor) + " corpse=" + entityDiagnostic(body)
                                    + " startX=" + startX + " dx=" + (body.getX() - startX));
                    require(Double.isFinite(body.getDeltaMovement().x) && Double.isFinite(body.getDeltaMovement().y)
                            && Double.isFinite(body.getDeltaMovement().z), "pulled corpse velocity remains finite");
                    require(BodyPullService.isPulling(actor), "body pull lease remains active during native movement; actor="
                            + entityDiagnostic(actor) + " corpse=" + entityDiagnostic(body));
                    detach(actor);
                    ticket.close();
                    helper.succeed();
                }
            });
        }, actor.blockPosition(), body.blockPosition());
    }

    @GameTest(templateNamespace = "moonstation14", template = "power_receiver_tall_clear", timeoutTicks = 80)
    @GameTestTags({"merge-main-regression", "jug-corridor"})
    public static void looseJugFollowsNativeTickUnderElasticPull(GameTestHelper helper) {
        floor(helper);
        for (int z = 1; z <= 12; z++) for (int x = 1; x <= 7; x++) {
            for (int y = 2; y <= 4; y++) helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
            if (z >= 8) helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
        }
        PlayerCharacterHarnessEntity actor = harness(helper, new BlockPos(4, 2, 5));
        actor.setYRot(0);
        actor.setYHeadRot(0);
        ItemStack stack = new ItemStack(ModItems.JUG.get());
        Vec3 start = helper.absoluteVec(new Vec3(4.5, 2, 4.5));
        LooseItemEntity jug = LooseItemEntity.create(helper.getLevel(), start, stack);
        var ticket = FixtureChunkTickets.acquire(helper, jug.getBoundingBox().expandTowards(0, 1, 9).inflate(2));
        jug.setNoPickUpDelay();
        require(helper.getLevel().addFreshEntity(jug), "loose jug spawned");
        FixtureChunkTickets.awaitEntityTicking(helper, ticket, 8, () -> {
            require(ticket.entityTicking(actor.blockPosition()) && ticket.entityTicking(jug.blockPosition()),
                    "puller, jug, and complete native motion corridor are entity-ticking before admission");
            int initialJugTickCount = jug.tickCount;
        // Register once before ticking; mutate no scheduled-callback map from its iteration.
        final int[] ticks = {0};
        final double[] startZ = {0};
        GroundedHarnessWorldStep stepper = new GroundedHarnessWorldStep();
        BoundedGameTestWait.safeOnEachTick(helper, () -> {
            ticks[0]++;
            if (ticks[0] == 3) {
                startZ[0] = jug.getZ();
                aimAt(actor, jug.getBoundingBox().getCenter());
                attach(actor, jug);
                return;
            }
            if (ticks[0] < 3) return;
            // Walk past the resolved tether slack before expecting native spring force to move the jug.
            var movement = stepper.step(actor, 0, ticks[0] <= 35 ? 1000 : 0, false, false, 0f);
            require(movement.isPresent(), "loose-item puller accepts each world-step command");
            if (ticks[0] >= 3) require(helper.getLevel().isPositionEntityTicking(jug.blockPosition()),
                    "jug remains entity-ticking during measured pull travel: "
                            + ticket.diagnostic(actor.blockPosition()) + "; " + ticket.diagnostic(jug.blockPosition()));
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
                ticket.close();
            helper.succeed();
            }
        });
        }, actor.blockPosition(), jug.blockPosition());
    }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 40)
    public static void stoppingOneChainLinkPreservesTheOtherUntilAllEndpointsClear(GameTestHelper helper) {
        floor(helper);
        PlayerCharacterHarnessEntity a = harness(helper, new BlockPos(4, 2, 6));
        PlayerCharacterHarnessEntity b = harness(helper, new BlockPos(4, 2, 4));
        PlayerCharacterHarnessEntity c = harness(helper, new BlockPos(4, 2, 2));
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

    private static void aimAt(PlayerCharacterHarnessEntity actor, Vec3 point) {
        Vec3 eye = actor.getEyePosition();
        double dx = point.x - eye.x, dy = point.y - eye.y, dz = point.z - eye.z;
        actor.setYRot((float) Math.toDegrees(Math.atan2(-dx, dz)));
        actor.setYHeadRot(actor.getYRot());
        actor.setXRot((float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz))));
    }

    private static Vec3 visibleInteractionPoint(PlayerCharacterHarnessEntity actor,
                                                PlayerCharacterHarnessEntity target) {
        var box = com.juicyslew.moonstation14.ms14.standing.BodyInteractionBounds.of(target);
        Vec3 eye = actor.getEyePosition();
        return new Vec3(net.minecraft.util.Mth.clamp(eye.x, box.minX, box.maxX),
                net.minecraft.util.Mth.clamp(eye.y, box.minY, box.maxY),
                net.minecraft.util.Mth.clamp(eye.z, box.minZ, box.maxZ));
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
        // Establish real collision grounding before the movement-ownership marker suppresses native travel.
        body.move(net.minecraft.world.entity.MoverType.SELF, new Vec3(0d, -.05d, 0d));
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
        require(result == BodyPullService.Result.SUCCESS, "trusted body-pull fixture admitted: " + result + "; "
                + BodyPullGameTests.pullAdmissionDiagnostic(actor, target, () -> true));
    }

    private static FixtureChunkTickets.Lease fixtureTickets(GameTestHelper helper, BlockPos first, BlockPos second,
                                                              double padding) {
        return FixtureChunkTickets.around(helper, helper.absolutePos(first), helper.absolutePos(second), padding);
    }

    private static void detach(PlayerCharacterHarnessEntity actor) {
        BodyPullService.stopFixture(actor);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
