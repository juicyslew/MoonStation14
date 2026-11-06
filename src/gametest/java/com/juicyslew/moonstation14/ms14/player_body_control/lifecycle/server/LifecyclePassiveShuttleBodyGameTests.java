package com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server;

import com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.ms14.player_body_control.character.MindControlledMob;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterBinding;
import com.juicyslew.moonstation14.ms14.shuttle.sable.MovingCabinAuthorityFixture;
import com.juicyslew.moonstation14.ms14.hands.network.LifecycleHandActorFixture;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.GameType;
import net.minecraft.server.players.ServerOpListEntry;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.joml.Vector3d;

/** Passive native-frame carry with no lifecycle movement intents. */
@GameTestTags({"shuttle-passive-body"})
@PrefixGameTestTemplate(false)
public final class LifecyclePassiveShuttleBodyGameTests {
    private LifecyclePassiveShuttleBodyGameTests() { }

    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 500)
    public static void bodyPhysicallyCarriesWithoutFreshIntent(GameTestHelper helper) {
        MovingCabinAuthorityFixture.start(helper, new BlockPos(1100, 300, 1100),
                "passive-shuttle-body", 2.5, 1, 2.5, (fixture, body) -> {
            var owner = fixture.owner();
            PlayerCharacterBinding binding = body.playerCharacterBinding();
            var mind = owner.authority().mindId();
            var bodyId = body.getUUID();
            long intentSequence = owner.intentSequence().get();
            var frameId = fixture.plot().engineId();
            var initial = com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery
                    .currentActorPoint(body.level(), body);
            if (binding == null || initial == null || !frameId.equals(initial.frameId())) {
                helper.fail("fixture actor did not begin on its resolved cabin frame"); return;
            }

            // Stop the fixture's ordinary intent pump before applying the one native frame pulse.
            fixture.setIntentPumpEnabled(false);
            RigidBodyHandle.of(fixture.plot().subLevel())
                    .addLinearAndAngularVelocity(new Vector3d(4, 0, 0), new Vector3d());
            var startPosition = body.position();
            long startTick = helper.getLevel().getServer().getTickCount();
            watchPassiveTicks(helper, fixture, body, startPosition,
                    new Vector3d(initial.storagePoint().x, initial.storagePoint().y, initial.storagePoint().z), frameId,
                    binding, mind, bodyId, intentSequence, startTick, 30);
        });
    }

    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 500)
    public static void creativeModeBodyContinuesNativeCarryWithoutInput(GameTestHelper helper) {
        MovingCabinAuthorityFixture.start(helper, new BlockPos(1200, 300, 1100),
                "passive-creative-shuttle", 2.5, 1, 2.5, (fixture, body) -> {
            var actor = fixture.owner().actor();
            var binding = body.playerCharacterBinding();
            var mind = fixture.owner().authority().mindId();
            var bodyId = body.getUUID();
            long sequence = fixture.owner().intentSequence().get();
            var frame = fixture.plot().engineId();
            var initial = com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery
                    .currentActorPoint(body.level(), body);
            if (initial == null || !frame.equals(initial.frameId())) {
                helper.fail("controlled human did not begin on cabin frame"); return;
            }
            fixture.setIntentPumpEnabled(false);
            dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle.of(fixture.plot().subLevel())
                    .addLinearAndAngularVelocity(new Vector3d(4, 0, 0), new Vector3d());
            // Exercise the registered lifecycle development command: raw GameMode changes are
            // deliberately denied while a lifecycle character session is active.
            var server = helper.getLevel().getServer();
            boolean addedOperator = !server.getPlayerList().isOp(actor.getGameProfile());
            if (addedOperator) {
                server.getPlayerList().getOps().add(new ServerOpListEntry(actor.getGameProfile(), 2, false));
                helper.testInfo.addListener(new net.minecraft.gametest.framework.GameTestListener() {
                    private void cleanup() { server.getPlayerList().getOps().remove(actor.getGameProfile()); }
                    @Override public void testStructureLoaded(net.minecraft.gametest.framework.GameTestInfo test) { }
                    @Override public void testPassed(net.minecraft.gametest.framework.GameTestInfo test, net.minecraft.gametest.framework.GameTestRunner runner) { cleanup(); }
                    @Override public void testFailed(net.minecraft.gametest.framework.GameTestInfo test, net.minecraft.gametest.framework.GameTestRunner runner) { cleanup(); }
                    @Override public void testAddedForRerun(net.minecraft.gametest.framework.GameTestInfo oldTest, net.minecraft.gametest.framework.GameTestInfo newTest, net.minecraft.gametest.framework.GameTestRunner runner) { cleanup(); }
                });
            }
            int result;
            try {
                result = server.getCommands().getDispatcher().execute("ms14dev creative", actor.createCommandSourceStack());
            } catch (com.mojang.brigadier.exceptions.CommandSyntaxException exception) {
                helper.fail("normal lifecycle development-mode Creative command failed: " + exception.getMessage());
                return;
            }
            if (result == 0) {
                helper.fail("normal lifecycle development-mode Creative command was rejected"); return;
            }
            if (actor.gameMode.getGameModeForPlayer() != GameType.CREATIVE) {
                helper.fail("normal player GameMode transition did not enter Creative"); return;
            }
            creativeCarryTicks(helper, fixture, body, frame, new Vector3d(initial.storagePoint().x,
                            initial.storagePoint().y, initial.storagePoint().z), body.position(),
                    binding, mind, bodyId, sequence, server.getTickCount(), 30);
        });
    }

    private static void creativeCarryTicks(GameTestHelper helper, MovingCabinAuthorityFixture.Ready fixture,
            com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessEntity body,
            java.util.UUID frame, Vector3d initialStorage, net.minecraft.world.phys.Vec3 startPosition,
            PlayerCharacterBinding binding, com.juicyslew.moonstation14.ms14.player_body_control.MindId mind,
            java.util.UUID bodyId, long sequence, long startTick, int remaining) {
        BoundedGameTestWait.observeServerTicks(helper, remaining, event -> {
            // ServerTickEvent.Post runs after the native fallback body step.
            long tick = helper.getLevel().getServer().getTickCount();
            var point = com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery
                    .currentActorPoint(body.level(), body);
            if (!((Object) body instanceof MindControlledMob marker)
                        || !marker.moonstation14$physicalStepConsumed(helper.getLevel(), tick)
                    || point == null || !frame.equals(point.frameId())
                    || new Vector3d(point.storagePoint().x, point.storagePoint().y,
                            point.storagePoint().z).distance(initialStorage) > .02
                    || body.isRemoved() || !body.getUUID().equals(bodyId)
                    || !binding.equals(body.playerCharacterBinding())
                    || !fixture.owner().authority().mindId().equals(mind)
                    || fixture.owner().intentSequence().get() != sequence
                    || fixture.owner().authority().body() != body
                    || fixture.owner().actor().gameMode.getGameModeForPlayer() != GameType.CREATIVE) {
                helper.fail("Creative passive tick failed: serverTick=" + tick + ", levelTime="
                        + helper.getLevel().getGameTime() + ", eligible="
                        + com.juicyslew.moonstation14.ms14.player_body_control.character.GroundedHarnessWorldStep
                        .isPassivePlayerCharacterEligible(body) + ", markerCurrent="
                        + markerState(body, helper, tick) + ", markerPrevious="
                        + markerState(body, helper, tick - 1) + ", pos=" + body.position() + ", point=" + point
                        + ", binding=" + body.playerCharacterBinding()); return;
            }
        }, () -> {
            double moved = body.position().distanceTo(startPosition);
            if (helper.getLevel().getServer().getTickCount() - startTick < 30 || moved <= .1) {
                helper.fail("Creative body did not physically move during 30 input-free ticks: " + moved); return;
            }
            var beforeReturn = com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery
                    .currentActorPoint(body.level(), body);
            try {
                int returned = helper.getLevel().getServer().getCommands().getDispatcher()
                        .execute("ms14dev return", fixture.owner().actor().createCommandSourceStack());
                if (returned == 0) {
                    helper.fail("normal lifecycle development-mode return command was rejected"); return;
                }
            } catch (com.mojang.brigadier.exceptions.CommandSyntaxException exception) {
                helper.fail("normal lifecycle development-mode return command failed: " + exception.getMessage()); return;
            }
            LifecycleHandActorFixture.completeReconnect(helper, fixture.owner().actor(), fixture.owner().channel(),
                    bodyId, mind, fixture.owner().authority().handIds(),
                    () -> "normal-mode return did not send reconnect Begin", joined ->
                        BoundedGameTestWait.observeServerTicks(helper, 1, event -> { }, () -> {
                            var ready = com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery
                                    .currentActorPoint(body.level(), body);
                            if (joined.authority().body() != body || !joined.authority().mindId().equals(mind)
                                    || !joined.authority().handIds().equals(fixture.owner().authority().handIds())
                                    || joined.actor().gameMode.getGameModeForPlayer() != GameType.SPECTATOR
                                    || !joined.actor().getInventory().isEmpty()
                                    || beforeReturn == null || ready == null || !frame.equals(ready.frameId())
                                    || new Vector3d(beforeReturn.storagePoint().x, beforeReturn.storagePoint().y,
                                            beforeReturn.storagePoint().z).distance(new Vector3d(ready.storagePoint().x,
                                            ready.storagePoint().y, ready.storagePoint().z)) > .25) {
                                helper.fail("normal-mode Ready changed lifecycle ownership, inert carrier state, or cabin-local pose"); return;
                            }
                            BoundedGameTestWait.succeedAfterObservation(helper);
                        }));
        });
    }

    private static boolean markerState(com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessEntity body,
                                       GameTestHelper helper, long tick) {
        return ((Object) body instanceof MindControlledMob marker)
                && marker.moonstation14$physicalStepConsumed(helper.getLevel(), tick);
    }

    private static void watchPassiveTicks(GameTestHelper helper, MovingCabinAuthorityFixture.Ready fixture,
            com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessEntity body,
            net.minecraft.world.phys.Vec3 startPosition, Vector3d initialStorage, java.util.UUID frameId,
            PlayerCharacterBinding binding, com.juicyslew.moonstation14.ms14.player_body_control.MindId mind,
            java.util.UUID bodyId, long intentSequence, long startTick, int remaining) {
        BoundedGameTestWait.observeServerTicks(helper, remaining, event -> {
            long tick = helper.getLevel().getServer().getTickCount();
            if (tick > startTick) {
                if (!((Object) body instanceof MindControlledMob marker)
                        || !marker.moonstation14$physicalStepConsumed(helper.getLevel(), tick)) {
                    helper.fail("native step marker missing: serverTick=" + tick + ", levelTime="
                            + helper.getLevel().getGameTime() + ", eligible="
                            + com.juicyslew.moonstation14.ms14.player_body_control.character.GroundedHarnessWorldStep
                            .isPassivePlayerCharacterEligible(body) + ", markerCurrent="
                            + markerState(body, helper, tick) + ", markerPrevious="
                            + markerState(body, helper, tick - 1) + ", pos=" + body.position()); return;
                }
                var point = com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery
                        .currentActorPoint(body.level(), body);
                if (body.isRemoved() || !body.getUUID().equals(bodyId)
                        || !frameId.equals(point == null ? null : point.frameId())
                        || !binding.equals(body.playerCharacterBinding())
                        || !fixture.owner().authority().mindId().equals(mind)
                        || fixture.owner().intentSequence().get() != intentSequence) {
                    helper.fail("passive interval changed body/frame/binding/Mind or sent a fresh intent"); return;
                }
                if (new Vector3d(point.storagePoint().x, point.storagePoint().y,
                        point.storagePoint().z).distance(initialStorage) > .02) {
                    helper.fail("body drifted relative to cabin deck during passive carry: " + point.storagePoint()); return;
                }
            }
        }, () -> {
            var moved = body.position().distanceTo(startPosition);
            if (helper.getLevel().getServer().getTickCount() - startTick < 30 || moved <= .1
                    || fixture.owner().intentSequence().get() != intentSequence) {
                helper.fail("native passive carry lacked 30 ticks of input-free physical motion; delta=" + moved); return;
            }
            BoundedGameTestWait.succeedAfterObservation(helper);
        });
    }
}
