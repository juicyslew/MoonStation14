package com.juicyslew.moonstation14.ms14.shuttle.sable;

import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery;
import com.juicyslew.moonstation14.ms14.shuttle.fixture.ShuttleFixtureMotionService;
import com.juicyslew.moonstation14.ms14.shuttle.fixture.ShuttleMotionRequest;
import com.juicyslew.moonstation14.ms14.standing.PostureData;
import com.juicyslew.moonstation14.ms14.standing.PostureSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.joml.Quaterniond;

/** Production prone-facing regression against actual native rigid-body frame rotation. */
@PrefixGameTestTemplate(false)
@GameTestTags({"shuttle-posture-recovery", "shuttle"})
public final class ProneSupportedFacingGameTests {
    private ProneSupportedFacingGameTests() { }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 420)
    @GameTestTags({"shuttle-posture-recovery", "prone-supported-facing"})
    public static void proneHumanFollowsNativeYawAndOwnCrawlWins(GameTestHelper helper) {
        MovingCabinAuthorityFixture.start(helper, new BlockPos(7800, 300, 6000),
                "prone-supported-facing", 2.5, 1, 2.5, (fixture, body) -> {
            var level = fixture.level();
            var frame = fixture.plot().subLevel();
            var initialPoint = LoadedFrameRayQuery.currentActorPoint(level, body);
            require(body.onGround() && initialPoint != null && frame.getUniqueId().equals(initialPoint.frameId()),
                    "Human begins grounded on known live native frame");
            require(PostureSystem.voluntaryToggle(body) && PostureSystem.isDownPosture(body),
                    "production posture API enters voluntary prone");
            ShuttleFixtureMotionService.registerFixture(frame);
            require(ShuttleFixtureMotionService.start(level, frame,
                    new ShuttleMotionRequest(0, 0, 0, .75, 160)), "bounded native yaw request starts");
            helper.runAfterDelay(1, () -> new Observer(helper, fixture, body, frame.getUniqueId()).sample(0, null));
        });
    }

    private static final class Observer {
        private final GameTestHelper helper;
        private final MovingCabinAuthorityFixture.Ready fixture;
        private final com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessEntity body;
        private final java.util.UUID frame;
        private Quaterniond startOrientation;
        private float startYaw;
        private double observedTurn;
        Observer(GameTestHelper helper, MovingCabinAuthorityFixture.Ready fixture,
                 com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessEntity body,
                 java.util.UUID frame) {
            this.helper=helper; this.fixture=fixture; this.body=body; this.frame=frame;
        }
        void sample(int ticks, Quaterniond previous) {
            var level = fixture.level();
            var point = LoadedFrameRayQuery.currentActorPoint(level, body);
            require(body.onGround() && point != null && frame.equals(point.frameId()),
                    "prone Human remains physically supported by same known frame");
            var orientation = LoadedFrameRayQuery.frameOrientation(level, frame, point.storagePoint(), false, 0);
            require(orientation != null, "logical frame orientation remains known");
            var data = body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.POSTURE.get()).data();
            if (startOrientation == null) {
                startOrientation = orientation;
                startYaw = data.movementFacingYaw();
            } else {
                var delta = new Quaterniond(orientation).mul(new Quaterniond(previous).conjugate()).normalize();
                observedTurn += com.juicyslew.moonstation14.ms14.standing.WorldUpTwist.degrees(delta);
                double bodyTurn = net.minecraft.util.Mth.wrapDegrees(data.movementFacingYaw() - startYaw);
                require(Math.abs(bodyTurn - observedTurn) < 4.0,
                        "server prone world yaw follows observed frame turn; frame=" + observedTurn + " posture=" + bodyTurn);
            }
            if (ticks < 70) { helper.runAfterDelay(1, () -> sample(ticks + 1, orientation)); return; }
            require(Math.abs(observedTurn) > 1.0,
                    "actual native frame accumulated measurable yaw; observedDegrees=" + observedTurn);
            PostureSystem.onOwnCrawlInput(body, true);
            require(body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.POSTURE.get()).data().ownCrawling(),
                    "production crawl input enables owner-authored crawl facing");
            PostureSystem.onOwnMovement(body, new net.minecraft.world.phys.Vec3(.1, 0, 0));
            var finalData = body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.POSTURE.get()).data();
            require(Math.abs(net.minecraft.util.Mth.wrapDegrees(finalData.movementFacingYaw() - (-90))) < .1,
                    "accepted owner world movement wins over carrier heading");
            require(ShuttleFixtureMotionService.stop(level, fixture.plot().subLevel()), "native motion controller stopped");
            helper.succeed();
        }
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
