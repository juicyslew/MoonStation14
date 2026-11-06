package com.juicyslew.moonstation14.ms14.shuttle.sable;

import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery;
import com.juicyslew.moonstation14.ms14.standing.PostureSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Native-frame regression for standing-sized posture recovery against real cabin collision. */
@PrefixGameTestTemplate(false)
@GameTestTags({"shuttle-posture-recovery", "shuttle"})
public final class SableShuttlePostureRecoveryGameTests {
    private SableShuttlePostureRecoveryGameTests() { }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 650)
    @GameTestTags({"shuttle-posture-recovery", "shuttle-posture-floor"})
    public static void proneHumanRecoversStandingOnNativeCabinFloor(GameTestHelper helper) {
        MovingCabinAuthorityFixture.start(helper, new BlockPos(2200, 300, 1660),
                "shuttle-posture-recovery", 2.5, 1, 2.5, (fixture, body) -> {
            var level = fixture.level();
            var plot = fixture.plot();
            var point = LoadedFrameRayQuery.currentActorPoint(level, body);
            require(point != null && plot.engineId().equals(point.frameId()), "human admitted to native cabin frame");
            require(body.isAlive() && body.onGround() && !body.isNoGravity() && !body.noPhysics,
                    "normal-gravity Human begins supported on authored cabin deck");
            var feet = body.position();
            var box = body.getBoundingBox();
            PostureSystem.forceDown(body, level.getServer().overworld().getGameTime());
            require(PostureSystem.isDownPosture(body), "production posture system transitions Human down");
            require(body.getBbHeight() < body.getDimensions(Pose.STANDING).height(),
                    "down posture exposes prone native dimensions");
            require(PostureSystem.requestStand(body, null), "production get-up starts when standing clearance exists");
            helper.runAfterDelay(60, () -> {
                require(!PostureSystem.isDownPosture(body), "timed production get-up completes");
                require(body.getBbHeight() == body.getDimensions(Pose.STANDING).height(),
                        "standing dimensions restored by posture system");
                require(body.position().distanceTo(feet) < .12,
                        "posture transition preserves physical feet/frame anchor; before=" + feet + " after=" + body.position());
                var after = LoadedFrameRayQuery.currentActorPoint(level, body);
                require(after != null && plot.engineId().equals(after.frameId()), "body remains tracked to same native frame");
                require(Math.abs(after.storagePoint().y - point.storagePoint().y) < .12,
                        "frame-local floor anchor is retained across posture transition");
                require(body.onGround(), "standing Human remains supported by native cabin floor");
                require(body.getBoundingBox().minY >= box.minY - .12,
                        "standing bounds do not move feet materially below original floor");
                helper.succeed();
            });
        });
    }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 650)
    @GameTestTags({"shuttle-posture-recovery", "shuttle-posture-ceiling"})
    public static void proneHumanCannotStandThroughNativeCabinCeiling(GameTestHelper helper) {
        MovingCabinAuthorityFixture.start(helper, new BlockPos(2260, 300, 1660),
                "shuttle-posture-ceiling", 2.5, 1, 2.5, (fixture, body) -> {
            var level = fixture.level();
            var plot = fixture.plot();
            var floor = fixture.cabin().origin();
            // The furnishing is real plot collision; place it overhead without moving the body.
            require(plot.setStorageBlock(floor.offset(2, 2, 2), Blocks.STONE.defaultBlockState()),
                    "low ceiling authored in native cabin storage");
            PostureSystem.forceDown(body, level.getServer().overworld().getGameTime());
            require(PostureSystem.isDownPosture(body), "Human transitions down beneath low ceiling");
            require(!PostureSystem.requestStand(body, null), "standing-sized native box is rejected by cabin ceiling");
            require(PostureSystem.isDownPosture(body), "blocked get-up leaves Human prone");
            helper.succeed();
        });
    }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 750)
    @GameTestTags({"shuttle-posture-recovery", "shuttle-puddle-slip-regression"})
    public static void nativePuddleSlipHumanGetsUpAndKeepsCabinFloorAnchor(GameTestHelper helper) {
        MovingCabinAuthorityFixture.start(helper, new BlockPos(2320, 300, 1660),
                "shuttle-puddle-posture", 2.5, 1, 2.5, (fixture, body) -> {
            var level = fixture.level();
            var plot = fixture.plot();
            var source = fixture.cabin().origin().offset(2, 1, 2);
            // Keep the authored deck intact; puddle occupies the cabin air cell immediately above it.
            require(plot.setStorageBlock(source, com.juicyslew.moonstation14.block.ModBlocks.PUDDLE.get().defaultBlockState()),
                    "real native cabin-floor puddle authored");
            var puddle = level.getBlockEntity(source);
            require(puddle instanceof com.juicyslew.moonstation14.block.block_entity.PuddleBlockEntity,
                    "native floor puddle block entity exists");
            var water = com.juicyslew.moonstation14.ms14.reagent.ModReagents.createKey("water");
            var contents = new com.juicyslew.moonstation14.ms14.reagent.ReagentAttachment();
            contents.specificAdd(water, 20f,
                    ((com.juicyslew.moonstation14.block.block_entity.PuddleBlockEntity) puddle).getCapacity());
            com.juicyslew.moonstation14.ms14.MS14Provider.update(puddle,
                    com.juicyslew.moonstation14.ms14.MS14Bridges.REAGENT, contents);
            var point = LoadedFrameRayQuery.currentActorPoint(level, body);
            require(point != null && plot.engineId().equals(point.frameId()) && body.onGround()
                            && !body.isNoGravity() && !body.noPhysics,
                    "ordinary controlled Human starts supported in native frame");
            require(com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem.resolveForHost(body)
                            .flatMap(c -> c.component(com.juicyslew.moonstation14.ms14.character.components.NoSlipComponent.class)).isEmpty(),
                    "resolved Human has no slip immunity");
            var listenerEvents = new java.util.concurrent.atomic.AtomicReference<com.juicyslew.moonstation14.ms14.slip.SlipEvent>();
            java.util.function.Consumer<com.juicyslew.moonstation14.ms14.slip.SlipEvent> listener = event -> {
                if (event.target() == body) {
                    listenerEvents.set(event);
                    fixture.owner().setWish(0, 0);
                    fixture.owner().setSprint(false);
                }
            };
            require(com.juicyslew.moonstation14.ms14.slip.SlipSystem.addListener(listener), "slip observer registered");
            var before = body.position();
            fixture.owner().setWish(1000, 0);
            fixture.owner().setSprint(true);
            awaitSlipMovement(helper, fixture, body, before, listenerEvents, listener, 80);
        });
    }

    private static void awaitSlipMovement(GameTestHelper helper, MovingCabinAuthorityFixture.Ready fixture,
            com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessEntity body,
            net.minecraft.world.phys.Vec3 start,
            java.util.concurrent.atomic.AtomicReference<com.juicyslew.moonstation14.ms14.slip.SlipEvent> events,
            java.util.function.Consumer<com.juicyslew.moonstation14.ms14.slip.SlipEvent> listener, int ticksLeft) {
        if (events.get() != null) {
            require(body.position().distanceTo(start) > 0,
                    "canonical lifecycle owner physically moved before real slip admission");
            require(com.juicyslew.moonstation14.ms14.slip.SlipSystem.isSliding(body)
                            && com.juicyslew.moonstation14.ms14.character.CharacterControlSystem.isKnockedDown(body),
                    "real puddle contact admits slip, sliding and knockdown status");
            awaitSlipPostureDown(helper, fixture.level(), fixture.plot(), body, listener, 20);
            return;
        }
        if (ticksLeft <= 0) throw new GameTestAssertException("canonical lifecycle input failed to slip; body="
                + body.position() + " velocity=" + body.getDeltaMovement() + " onGround=" + body.onGround()
                + " displacement=" + body.position().distanceTo(start) + " acceptedSequence="
                + fixture.owner().drainAppliedSequence());
        helper.runAfterDelay(1, () -> awaitSlipMovement(helper, fixture, body, start, events, listener, ticksLeft - 1));
    }

    private static void awaitSlipPostureDown(GameTestHelper helper, net.minecraft.server.level.ServerLevel level,
            SableFixturePlot plot,
            com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessEntity body,
            java.util.function.Consumer<com.juicyslew.moonstation14.ms14.slip.SlipEvent> listener, int ticksLeft) {
        if (!PostureSystem.isDownPosture(body) && ticksLeft > 0) {
            helper.runAfterDelay(1, () -> awaitSlipPostureDown(helper, level, plot, body, listener, ticksLeft - 1));
            return;
        }
        require(PostureSystem.isDownPosture(body),
                "real slip knockdown status is projected to down posture by production consumer within bounded wait");
        var anchor = LoadedFrameRayQuery.currentActorPoint(level, body);
        com.juicyslew.moonstation14.ms14.slip.SlipSystem.removeListener(listener);
        awaitAutomaticGetUpStart(helper, level, plot, body, anchor, 160);
    }

    private static void awaitAutomaticGetUpStart(GameTestHelper helper, net.minecraft.server.level.ServerLevel level,
            SableFixturePlot plot,
            com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessEntity body,
            LoadedFrameRayQuery.ActorPoint anchor, int ticksLeft) {
        var data = body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.POSTURE.get());
        long getUpKey = data == null ? 0 : data.data().getUpKey();
        if (getUpKey == 0 && ticksLeft > 0) {
            helper.runAfterDelay(1, () -> awaitAutomaticGetUpStart(helper, level, plot, body, anchor, ticksLeft - 1));
            return;
        }
        require(PostureSystem.isDownPosture(body) && getUpKey > 0,
                "production automatic get-up is observed starting from a real slip-down state");
        awaitSlipGetUp(helper, level, plot, body, anchor, 100);
    }

    private static void awaitSlipGetUp(GameTestHelper helper, net.minecraft.server.level.ServerLevel level,
            SableFixturePlot plot,
            com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessEntity body,
            LoadedFrameRayQuery.ActorPoint anchor, int ticksLeft) {
        if (PostureSystem.isDownPosture(body) && ticksLeft > 0) {
            helper.runAfterDelay(1, () -> awaitSlipGetUp(helper, level, plot, body, anchor, ticksLeft - 1));
            return;
        }
        require(!PostureSystem.isDownPosture(body), "slip-triggered down posture completes get-up within timeout");
        var after = LoadedFrameRayQuery.currentActorPoint(level, body);
        require(anchor != null && after != null && plot.engineId().equals(after.frameId())
                        && Math.abs(anchor.storagePoint().y - after.storagePoint().y) < .15,
                "immediate post-growth frame floor anchor is retained");
        require(body.onGround() && body.getBoundingBox().minY > 299.5,
                "Human remains supported at cabin altitude, not recovered on underlying world terrain");
        helper.runAfterDelay(10, () -> {
            var settled = LoadedFrameRayQuery.currentActorPoint(level, body);
            require(settled != null && plot.engineId().equals(settled.frameId())
                            && Math.abs(anchor.storagePoint().y - settled.storagePoint().y) < .15
                            && body.onGround() && body.getBoundingBox().minY > 299.5,
                    "posture recovery remains native-floor supported over subsequent ticks");
            helper.succeed();
        });
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
