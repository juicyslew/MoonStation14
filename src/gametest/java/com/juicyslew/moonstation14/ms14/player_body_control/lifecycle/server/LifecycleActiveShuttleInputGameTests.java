package com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server;

import com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery;
import com.juicyslew.moonstation14.ms14.player_body_control.character.MindControlledMob;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterBinding;
import com.juicyslew.moonstation14.ms14.player_body_control.movement.GroundedHarnessMotor;
import com.juicyslew.moonstation14.ms14.shuttle.sable.MovingCabinAuthorityFixture;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.joml.Vector3d;

/** Ordinary active walking input must be consumed before the server-post physics fallback. */
@GameTestTags({"shuttle-active-input"})
@PrefixGameTestTemplate(false)
public final class LifecycleActiveShuttleInputGameTests {
    private LifecycleActiveShuttleInputGameTests() { }

    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 500)
    public static void activeInputWinsBeforeServerPostFallback(GameTestHelper helper) {
        MovingCabinAuthorityFixture.start(helper, new BlockPos(1300, 300, 1100),
                "active-shuttle-input", 2.5, 1, 2.5, (fixture, body) -> {
            var owner = fixture.owner();
            PlayerCharacterBinding binding = body.playerCharacterBinding();
            var mind = owner.authority().mindId();
            var bodyId = body.getUUID();
            var frame = fixture.plot().engineId();
            var point = LoadedFrameRayQuery.currentActorPoint(body.level(), body);
            if (binding == null || point == null || !frame.equals(point.frameId())) {
                helper.fail("controlled human did not begin on resolved stationary cabin frame");
                return;
            }
            fixture.setIntentPumpEnabled(false);
            Vector3d initialLocal = local(point);
            sendInputs(helper, fixture, body, frame, initialLocal, binding, mind, bodyId,
                    8);
        });
    }

    private static void sendInputs(GameTestHelper helper, MovingCabinAuthorityFixture.Ready fixture,
            com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessEntity body,
            java.util.UUID frame, Vector3d initialLocal, PlayerCharacterBinding binding,
            com.juicyslew.moonstation14.ms14.player_body_control.MindId mind, java.util.UUID bodyId,
            int remaining) {
        BoundedGameTestWait.safeDelay(helper, 1, () -> {
            fixture.owner().setWish(0, GroundedHarnessMotor.INPUT_QUANTIZATION);
            long sequence = fixture.owner().neutralIntent(body);
            final int[] samples = {0};
            BoundedGameTestWait.observeServerTicks(helper, 2, event -> {
                long tick = helper.getLevel().getServer().getTickCount();
                var current = LoadedFrameRayQuery.currentActorPoint(body.level(), body);
                if (!((Object) body instanceof MindControlledMob marker)
                        || !marker.moonstation14$physicalStepConsumed(helper.getLevel(), tick)
                        || current == null || !frame.equals(current.frameId())
                        || body.isRemoved() || !body.getUUID().equals(bodyId)
                        || !binding.equals(body.playerCharacterBinding())
                        || !fixture.owner().authority().mindId().equals(mind)
                        || fixture.owner().authority().body() != body
                        || fixture.owner().intentSequence().get() != sequence) {
                    helper.fail("active input was not acknowledged/consumed on server tick " + tick);
                    return;
                }
                if (++samples[0] == 2 && fixture.owner().drainAppliedSequence() < sequence)
                    helper.fail("active input sequence was not acknowledged by the lifecycle snapshot producer");
            }, () -> {
                if (remaining > 1) sendInputs(helper, fixture, body, frame, initialLocal, binding,
                        mind, bodyId, remaining - 1);
                else {
                    var finalPoint = LoadedFrameRayQuery.currentActorPoint(body.level(), body);
                    if (finalPoint == null || !frame.equals(finalPoint.frameId())
                            || local(finalPoint).z - initialLocal.z <= .1)
                        helper.fail("eight active inputs did not produce frame-local forward motion on stationary frame");
                    else BoundedGameTestWait.succeedAfterObservation(helper);
                }
            });
        });
    }

    private static Vector3d local(LoadedFrameRayQuery.ActorPoint point) {
        return new Vector3d(point.storagePoint().x, point.storagePoint().y, point.storagePoint().z);
    }
}
