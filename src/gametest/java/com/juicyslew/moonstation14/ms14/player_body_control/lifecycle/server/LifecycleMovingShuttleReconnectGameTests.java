package com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server;

import com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.ms14.hands.network.LifecycleHandActorFixture;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessEntity;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.persistence.SavedLifecycleProfile;
import com.juicyslew.moonstation14.ms14.shuttle.sable.MovingCabinAuthorityFixture;
import com.juicyslew.moonstation14.ms14.hands.live.FixtureHeldTool;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.joml.Vector3d;

/** Native moving-frame logout/rejoin regression; server-side only (does not exercise a connected camera). */
@GameTestTags({"lifecycle-reconnect", "shuttle-moving-reconnect"})
@PrefixGameTestTemplate(false)
public final class LifecycleMovingShuttleReconnectGameTests {
    private LifecycleMovingShuttleReconnectGameTests() { }

    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 500)
    public static void reconnectResumesExactBodyOnCurrentMovingFrame(GameTestHelper helper) {
        MovingCabinAuthorityFixture.start(helper, new net.minecraft.core.BlockPos(420, 300, 420),
                "moving-shuttle-reconnect", 2.5, 1, 2.5, 64, (fixture, body) -> {
            var owner = fixture.owner();
            var actor = owner.actor();
            var server = helper.getLevel().getServer();
            var account = actor.getUUID();
            var mind = owner.authority().mindId();
            var hands = owner.authority().handIds();
            var currentHands = body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());
            if (currentHands == null || !currentHands.compatible(body)) {
                helper.fail("initial Ready did not publish compatible live hand ownership"); return;
            }
            var issued = FixtureHeldTool.issue(actor, currentHands.activeHand(),
                    new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.GLASS_BOTTLE));
            var frame = fixture.plot().engineId();
            var handle = RigidBodyHandle.of(fixture.plot().subLevel());
            // A one-time native 20 m/s pulse sustains genuine deck motion through the longer offline proof;
            // the existing ground corridor supports that travel without recurring forces or physics changes.
            handle.addLinearAndAngularVelocity(new Vector3d(20, 0, 0), new Vector3d());
            var initialPoint = com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery.currentActorPoint(body.level(), body);
            BoundedGameTestWait.await(helper, 30, () -> "native frame did not move before logout",
                    () -> initialPoint != null && initialPoint.worldPoint().distanceToSqr(body.position()) > .01,
                    () -> {
            var logoutPoint = com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery.currentActorPoint(body.level(), body);
            if (logoutPoint == null || !frame.equals(logoutPoint.frameId())) { helper.fail("body left native cabin before logout"); return; }
             var context = LifecycleStartupRuntime.contextFor(server).orElseThrow();
             fixture.setIntentPumpEnabled(false);
             actor.connection.disconnect(Component.literal("moving shuttle reconnect fixture"));
            BoundedGameTestWait.await(helper, 80, () -> "logout did not persist OFFLINE claim",
                    () -> server.getPlayerList().getPlayer(account) != actor
                            && context.lifecycle().profile(account).map(p -> !p.active()
                            && p.state() == com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.PlayerLifecycleRegistry.LifecycleState.OFFLINE).orElse(false),
                    () -> {
                        final SavedLifecycleProfile offline;
                        try { offline = context.currentAccountProfile(account).orElseThrow(); }
                        catch (java.io.IOException failure) { helper.fail("cannot read OFFLINE saved pose: " + failure); return; }
                        var startPose = body.position();
                         BoundedGameTestWait.observeServerTicks(helper, 30, event -> { }, () -> {
                             var offlinePoint = com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery.currentActorPoint(body.level(), body);
                             if (offlinePoint == null || !frame.equals(offlinePoint.frameId())
                                     || offlinePoint.worldPoint().distanceToSqr(logoutPoint.worldPoint()) < .01
                                     || body.position().distanceToSqr(startPose) < .01) {
                                 helper.fail("native frame/body did not advance during 30 completed offline physics ticks: point="
                                         + offlinePoint + " logoutPoint=" + logoutPoint + " startPos=" + startPose
                                         + " bodyPos=" + body.position()); return;
                             }
                             LifecycleHandActorFixture.reconnect(helper, owner, joined -> {
                                    var readyPoint = com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery.currentActorPoint(body.level(), body);
                                    var authority = joined.authority();
                                    try {
                                        var active = context.currentAccountProfile(account).orElseThrow();
                                        if (authority.body() != body || !authority.mindId().equals(mind)
                                                 || !authority.handIds().equals(hands)
                                                || body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get()) == null
                                                || !body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get()).token(issued.hand()).filter(issued.token()::equals).isPresent()
                                                || !body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get()).stackCopy(issued.hand()).filter(stack -> net.minecraft.world.item.ItemStack.matches(stack, issued.stack())).isPresent()
                                                || readyPoint == null || !frame.equals(readyPoint.frameId())
                                                || readyPoint.storagePoint() == null
                                                || body.position().distanceToSqr(readyPoint.worldPoint()) > .04
                                                || body.position().distanceToSqr(new net.minecraft.world.phys.Vec3(offline.location().x(), offline.location().y(), offline.location().z())) < .25
                                                || active.state() != SavedLifecycleProfile.State.ACTIVE
                                                || !active.bodyId().equals(body.getUUID()) || !active.mindId().equals(mind.value())
                                                || !joined.actor().connection.isAcceptingMessages()) {
                                            helper.fail("rejoin replaced/teleported body or changed Mind/items/authority"); return;
                                        }
                                             var readyBodyPosition = body.position();
                                             var readyFramePose = com.juicyslew.moonstation14.ms14.shuttle.sable.NativeFrameInterval.PosePair
                                                     .capture(fixture.plot().subLevel().lastPose(), fixture.plot().subLevel().logicalPose());
                                              var readyVelocity = body.getDeltaMovement();
                                              driveReconnectTicks(helper, joined, body, 8, () -> {
                                             var afterReadyPoint = com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery.currentActorPoint(body.level(), body);
                                             var afterBodyPosition = body.position();
                                             var afterFramePose = com.juicyslew.moonstation14.ms14.shuttle.sable.NativeFrameInterval.PosePair
                                                     .capture(fixture.plot().subLevel().lastPose(), fixture.plot().subLevel().logicalPose());
                                             var afterVelocity = body.getDeltaMovement();
                                             boolean actorState = joined.authority().body() == body
                                                     && joined.actor().gameMode.getGameModeForPlayer() == net.minecraft.world.level.GameType.SPECTATOR
                                                     && joined.actor().getInventory().isEmpty();
                                             if (afterReadyPoint == null || !frame.equals(afterReadyPoint.frameId())
                                                     || afterBodyPosition.distanceToSqr(readyBodyPosition) < .01
                                                     || readyFramePose.equals(afterFramePose) || !actorState) {
                                                 helper.fail("same body did not continue moving after Ready: readyPos=" + readyBodyPosition
                                                         + " afterPos=" + afterBodyPosition + " bodyDelta=" + afterBodyPosition.subtract(readyBodyPosition)
                                                         + " readyVelocity=" + readyVelocity + " afterVelocity=" + afterVelocity
                                                         + " frameChanged=" + !readyFramePose.equals(afterFramePose) + " readyFrame=" + readyFramePose
                                                         + " afterFrame=" + afterFramePose + " readyPoint=" + readyPoint + " afterPoint=" + afterReadyPoint
                                                         + " frame=" + frame + " actorState=" + actorState + " actualHand=" + issued.hand()
                                                         + " authorityBodySame=" + (joined.authority().body() == body)); return;
                                             }
                                             BoundedGameTestWait.await(helper, 20, () -> "reconnect producer did not publish current native frame snapshot",
                                                     () -> {
                                                         joined.drainSnapshots();
                                                         joined.neutralIntent(body);
                                                         var snapshot = joined.latestSnapshot().get();
                                                         var point = com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery.currentActorPoint(body.level(), body);
                                                         if (snapshot == null || snapshot.frameState().isEmpty() || point == null
                                                                 || !frame.equals(snapshot.frameState().orElseThrow().frameId())
                                                                 || !frame.equals(point.frameId())) return false;
                                                         var savedPoint = snapshot.frameState().orElseThrow();
                                                         return Math.abs(savedPoint.x() - point.storagePoint().x) <= .25
                                                                 && Math.abs(savedPoint.y() - point.storagePoint().y) <= .25
                                                                 && Math.abs(savedPoint.z() - point.storagePoint().z) <= .25
                                                                 && body.position().distanceToSqr(new net.minecraft.world.phys.Vec3(
                                                                 offline.location().x(), offline.location().y(), offline.location().z())) >= .25;
                                                     },
                                                     () -> helper.succeed());
                                              });
                                    } catch (java.io.IOException failure) { helper.fail("cannot inspect current saved ownership: " + failure); }
                                 });
                         });
                    });
                    });
        });
    }

    private static void driveReconnectTicks(GameTestHelper helper, LifecycleHandActorFixture.Ready joined,
                                            PlayerCharacterHarnessEntity body, int remaining, Runnable evaluate) {
        if (remaining == 0) {
            evaluate.run();
            return;
        }
        BoundedGameTestWait.safeDelay(helper, 1, () -> {
            joined.neutralIntent(body);
            joined.drainSnapshots();
            driveReconnectTicks(helper, joined, body, remaining - 1, evaluate);
        });
    }
}
