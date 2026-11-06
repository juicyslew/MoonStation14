package com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.ms14.hands.network.LifecycleHandActorFixture;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.PlayerLifecycleRegistry;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessEntity;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.persistence.SavedLifecycleProfile;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** End-to-end login/Ready proof for exact loaded abandoned-ACTIVE claims. */
@GameTestTags({"shuttle-movement-recovery", "lifecycle"})
@PrefixGameTestTemplate(false)
public final class LifecycleAbandonedActiveReconnectGameTests {
    private LifecycleAbandonedActiveReconnectGameTests() { }

    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 350)
    public static void normalLoginReclaimsExactBodyAfterOldTerminalCameraFailure(GameTestHelper helper) {
        LifecycleHandActorFixture.start(helper, "abandoned-active-camera-fault", owner -> {
            var server = helper.getLevel().getServer();
            var body = (PlayerCharacterHarnessEntity) owner.authority().body();
            var account = owner.actor().getUUID();
            var originalBody = body;
            var mind = owner.authority().mindId();
            var handRoots = owner.authority().handIds();
            var liveHands = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
            if (liveHands == null) { helper.fail("first Ready did not establish live hand roots"); return; }

            // Simulate the old producer's terminal controller failure (not native movement failure).
            // The committed-spectator guard rejects setCamera(player). Trigger the independent
            // passenger invariant on a separate vehicle so PlayerList logout cannot discard the Human.
            var vehicle = net.minecraft.world.entity.EntityType.PIG.create(helper.getLevel());
            if (vehicle == null) { helper.fail("could not create separate terminal-failure vehicle"); return; }
            vehicle.setPos(body.getX() + 2, body.getY(), body.getZ());
            if (!helper.getLevel().addFreshEntity(vehicle)) {
                helper.fail("could not add separate terminal-failure vehicle"); return;
            }
            helper.testInfo.addListener(new net.minecraft.gametest.framework.GameTestListener() {
                private void cleanupVehicle() { if (!vehicle.isRemoved()) vehicle.discard(); }
                @Override public void testStructureLoaded(net.minecraft.gametest.framework.GameTestInfo test) { }
                @Override public void testPassed(net.minecraft.gametest.framework.GameTestInfo test,
                                                 net.minecraft.gametest.framework.GameTestRunner runner) { cleanupVehicle(); }
                @Override public void testFailed(net.minecraft.gametest.framework.GameTestInfo test,
                                                net.minecraft.gametest.framework.GameTestRunner runner) { cleanupVehicle(); }
                @Override public void testAddedForRerun(net.minecraft.gametest.framework.GameTestInfo oldTest,
                        net.minecraft.gametest.framework.GameTestInfo newTest,
                        net.minecraft.gametest.framework.GameTestRunner runner) { cleanupVehicle(); }
            });
            if (!owner.actor().startRiding(vehicle, true)) {
                helper.fail("could not establish the fixture-only terminal passenger mismatch"); return;
            }
            // Invoke the existing production tick handler with the same event type. Its terminal
            // path suspends authority and disconnects immediately; dismount before PlayerList saves
            // this fixture actor so RootVehicle state cannot contaminate the subsequent real login.
            LifecycleCharacterSessionControl.playerTick(new PlayerTickEvent.Post(owner.actor()));
            var suspended = LifecycleStartupRuntime.contextFor(server).flatMap(context ->
                    context.lifecycle().profile(account)).orElse(null);
            if (owner.actor().connection.isAcceptingMessages() || suspended == null || suspended.active()
                    || suspended.state() != PlayerLifecycleRegistry.LifecycleState.RECOVERY_REQUIRED) {
                helper.fail("production terminal handler did not revoke/suspend the committed session"); return;
            }
            owner.actor().stopRiding();
            BoundedGameTestWait.await(helper, 50, () -> "old terminal fault did not suspend/disconnect exact claim",
                    () -> server.getPlayerList().getPlayer(account) != owner.actor()
                            && LifecycleStartupRuntime.contextFor(server).flatMap(context -> context.lifecycle().profile(account))
                            .map(p -> !p.active() && p.state() == PlayerLifecycleRegistry.LifecycleState.RECOVERY_REQUIRED
                                    && p.connectionGeneration() == owner.epoch()).orElse(false),
                    () -> {
                        var context = LifecycleStartupRuntime.contextFor(server).orElseThrow();
                         SavedLifecycleProfile before;
                         try { before = context.currentAccountProfile(account).orElse(null); }
                         catch (java.io.IOException failure) { helper.fail("cannot inspect abandoned durable row: " + failure); return; }
                        var binding = originalBody.playerCharacterBinding();
                        var retainedHands = originalBody.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
                        boolean activeRow = before != null && before.state() == SavedLifecycleProfile.State.ACTIVE
                                && before.connectionGeneration() == owner.epoch();
                        boolean exactSavedIds = before != null && before.bodyId().equals(originalBody.getUUID())
                                && before.mindId().equals(mind.value());
                        boolean liveBoundBody = originalBody.isAlive() && !originalBody.hasInvalidSavedBinding()
                                && binding != null && binding.accountId().equals(account)
                                && binding.mindId().equals(mind.value());
                        boolean sameHands = retainedHands == liveHands && handRoots.equals(retainedHands.handIds());
                        if (!activeRow || !exactSavedIds || !liveBoundBody || !sameHands) {
                            helper.fail("terminal fault retention mismatch activeRow=" + activeRow
                                    + " exactSavedIds=" + exactSavedIds + " liveBoundBody=" + liveBoundBody
                                    + " sameHands=" + sameHands + " humanRemoved=" + originalBody.isRemoved()
                                    + " bodyAlive=" + originalBody.isAlive() + " vehicleRemoved=" + vehicle.isRemoved());
                            return;
                        }
                        BoundedGameTestWait.await(helper, 30, () -> "old player was not removed through normal logout",
                                () -> server.getPlayerList().getPlayer(account) == null,
                                () -> LifecycleHandActorFixture.reconnect(helper, owner, joined -> {
                                     SavedLifecycleProfile current;
                                     try { current = LifecycleStartupRuntime.contextFor(server).orElseThrow()
                                             .currentAccountProfile(account).orElseThrow(); }
                                     catch (java.io.IOException failure) { helper.fail("cannot inspect reclaimed durable row: " + failure); return; }
                                    var next = joined.authority();
                                    if (next.body() != originalBody || !next.mindId().equals(mind)
                                            || !next.handIds().equals(handRoots)
                                            || originalBody.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) != liveHands
                                            || joined.actor().getCamera() != originalBody
                                            || !joined.actor().connection.isAcceptingMessages()
                                            || current.state() != SavedLifecycleProfile.State.ACTIVE
                                            || !current.bodyId().equals(originalBody.getUUID())
                                            || !current.mindId().equals(mind.value())
                                            || current.connectionGeneration() <= owner.epoch()) {
                                        helper.fail("normal login/Ready failed to reclaim exact body, Mind, hand roots/token, camera, and newer ACTIVE generation");
                                        return;
                                    }
                                    helper.succeed();
                                }));
                    });
        });
    }
}
