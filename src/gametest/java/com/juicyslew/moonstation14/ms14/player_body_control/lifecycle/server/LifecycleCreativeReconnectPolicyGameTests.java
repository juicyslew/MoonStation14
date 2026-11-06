package com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait;
import com.juicyslew.moonstation14.ms14.hands.network.LifecycleHandActorFixture;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessEntity;
import com.juicyslew.moonstation14.ms14.hands.quarantine.CreativeCarrierTransition;
import com.juicyslew.moonstation14.ms14.hands.quarantine.CreativeCarrierSnapshotProbe;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.persistence.SavedLifecycleProfile;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.GameType;
import net.minecraft.network.chat.Component;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import com.mojang.brigadier.CommandDispatcher;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.Map;
import java.util.UUID;

/** Lifecycle reconnect policy and live embedded-connection regression fixtures. */
@GameTestTags({"lifecycle", "character", "lifecycle-reconnect"})
@PrefixGameTestTemplate(false)
public final class LifecycleCreativeReconnectPolicyGameTests {
    private LifecycleCreativeReconnectPolicyGameTests() { }

@GameTest(template = "empty", templateNamespace = "moonstation14")
    public static void creativeUnmarkedParksBeforeCleanGate(GameTestHelper helper) {
        AtomicInteger parks = new AtomicInteger();
        require(LifecycleExistingBodyReconnect.admitCarrier(GameType.CREATIVE, false,
                () -> { parks.incrementAndGet(); return CreativeCarrierTransition.Result.PARKED; },
                () -> parks.get() == 1) == LifecycleExistingBodyReconnect.Admission.NEWLY_PARKED,
                "new Creative carrier must park before its clean gate");
        require(LifecycleExistingBodyReconnect.admitCarrier(GameType.CREATIVE, false,
                () -> CreativeCarrierTransition.Result.DENIED, () -> true)
                == LifecycleExistingBodyReconnect.Admission.DENIED, "failed park cannot admit");
        require(LifecycleExistingBodyReconnect.admitCarrier(GameType.CREATIVE, false,
                () -> CreativeCarrierTransition.Result.PARKED, () -> false)
                == LifecycleExistingBodyReconnect.Admission.RECOVERY, "dirty after park requires recovery");
        helper.succeed();
    }

@GameTest(template = "empty", templateNamespace = "moonstation14")
    public static void existingMarkerAndNonCreativeNeverRepark(GameTestHelper helper) {
        AtomicInteger parks = new AtomicInteger();
        var park = (java.util.function.Supplier<CreativeCarrierTransition.Result>) () -> {
            parks.incrementAndGet(); return CreativeCarrierTransition.Result.PARKED;
        };
        require(LifecycleExistingBodyReconnect.admitCarrier(GameType.CREATIVE, true, park, () -> true)
                == LifecycleExistingBodyReconnect.Admission.READY, "valid existing park accepted unchanged");
        require(LifecycleExistingBodyReconnect.admitCarrier(GameType.SURVIVAL, false, park, () -> false)
                == LifecycleExistingBodyReconnect.Admission.DENIED, "dirty Survival rejected");
        require(LifecycleExistingBodyReconnect.admitCarrier(GameType.ADVENTURE, false, park, () -> true)
                == LifecycleExistingBodyReconnect.Admission.READY, "empty Adventure baseline accepted");
        require(parks.get() == 0, "only unmarked Creative may park");
        helper.succeed();
    }

@GameTest(template = "empty", templateNamespace = "moonstation14")
    public static void canceledModeSwitchRestoresOnlyNewPark(GameTestHelper helper) {
        AtomicInteger restores = new AtomicInteger();
        AtomicInteger syncs = new AtomicInteger();
        require(!LifecycleExistingBodyReconnect.inertPolicy(true, () -> false, () -> GameType.CREATIVE,
                () -> true, () -> { restores.incrementAndGet(); return CreativeCarrierTransition.Result.RESTORED; },
                syncs::incrementAndGet), "canceled switch cannot activate");
        require(restores.get() == 1 && syncs.get() == 1, "new snapshot restored and synced");
        require(!LifecycleExistingBodyReconnect.inertPolicy(false, () -> false, () -> GameType.CREATIVE,
                () -> true, () -> { restores.incrementAndGet(); return CreativeCarrierTransition.Result.RESTORED; },
                syncs::incrementAndGet), "preexisting marker cannot be restored by reconnect");
        require(restores.get() == 1, "preexisting snapshot untouched");
        require(LifecycleExistingBodyReconnect.inertPolicy(true, () -> true, () -> GameType.SPECTATOR,
                () -> true, () -> { restores.incrementAndGet(); return CreativeCarrierTransition.Result.RESTORED; },
                syncs::incrementAndGet), "clean actual Spectator accepted");
        require(restores.get() == 1, "successful handoff retains snapshot");
        require(!LifecycleExistingBodyReconnect.inertPolicy(true, () -> true, () -> GameType.SPECTATOR,
                () -> false, () -> CreativeCarrierTransition.Result.RESTORED, syncs::incrementAndGet),
                "dirty Spectator rejected");
        helper.succeed();
    }

@GameTest(template = "empty", templateNamespace = "moonstation14")
    public static void offlineCreativeRefusalIsReadOnlyPolicyNotConnectedAdmission(GameTestHelper helper) {
        var denied = new CreativeCarrierTransition.Attempt(CreativeCarrierTransition.Result.DENIED,
                true, CreativeCarrierSnapshotProbe.Reason.READY);
        require(LifecycleExistingBodyReconnect.offlineCreativeRefusalEligible(
                LifecycleExistingBodyReconnect.Admission.DENIED, denied, GameType.CREATIVE, false),
                "no-write Creative refusal may request a detached claim without parking or a body");
        require(!LifecycleExistingBodyReconnect.offlineCreativeRefusalEligible(
                LifecycleExistingBodyReconnect.Admission.DENIED, denied, GameType.CREATIVE, true),
                "marker mismatch denied");
        require(!LifecycleExistingBodyReconnect.offlineCreativeRefusalEligible(
                LifecycleExistingBodyReconnect.Admission.DENIED, denied, GameType.SURVIVAL, false),
                "nonCreative denied");
        require(!LifecycleExistingBodyReconnect.offlineCreativeRefusalEligible(
                LifecycleExistingBodyReconnect.Admission.DENIED,
                new CreativeCarrierTransition.Attempt(CreativeCarrierTransition.Result.DENIED, false,
                        CreativeCarrierSnapshotProbe.Reason.READY), GameType.CREATIVE, false),
                "ambiguous writes denied");
        require(LifecycleExistingBodyReconnect.admitCarrier(GameType.CREATIVE, false,
                () -> CreativeCarrierTransition.Result.PARKED, () -> true)
                == LifecycleExistingBodyReconnect.Admission.NEWLY_PARKED,
                "clean retry still takes ordinary body reconnect route");
        helper.succeed();
    }

@GameTest(template = "empty", templateNamespace = "moonstation14")
    public static void pinnedOfflineClaimRejectsChangedRowAndConflictingMemory(GameTestHelper helper) {
        UUID account = UUID.randomUUID(), mind = UUID.randomUUID(), body = UUID.randomUUID();
        var saved = new SavedLifecycleProfile(1, account, "main", mind, body, "minecraft:overworld",
                new SavedLifecycleProfile.Location(0, 64, 0), Map.of(), UUID.randomUUID(), 4,
                SavedLifecycleProfile.State.OFFLINE, 2, 1L);
        require(LifecycleDevelopmentMode.verifiedOfflineCreativeRow(saved, saved, account, null),
                "exact offline row with no in-memory authority may be pinned");
        require(!LifecycleDevelopmentMode.verifiedOfflineCreativeRow(saved, saved, UUID.randomUUID(), null),
                "different account/stale player cannot use the pinned row");
        var changed = new SavedLifecycleProfile(1, account, "main", mind, body, "minecraft:overworld",
                saved.location(), Map.of(), saved.connectionEpoch(), 5,
                SavedLifecycleProfile.State.OFFLINE, 3, 1L);
        require(!LifecycleDevelopmentMode.verifiedOfflineCreativeRow(changed, saved, account, null),
                "generation/revision change invalidates claim");
        require(!LifecycleDevelopmentMode.verifiedOfflineCreativeRow(saved, saved, account,
                new com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.PlayerLifecycleRegistry.Snapshot(
                        1, account, "main", new com.juicyslew.moonstation14.ms14.player_body_control.MindId(mind),
                        new com.juicyslew.moonstation14.ms14.player_body_control.MobHarnessId(body),
                        com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.PlayerLifecycleRegistry.LifecycleState.ACTIVE,
                        4, true, false)), "active memory denies detached claim");
        Object connected = new Object(), staleSameAccount = new Object();
        require(LifecycleDevelopmentMode.offlineClaimOwner(connected, connected),
                "exact owner can return or clear its claim on logout");
        require(!LifecycleDevelopmentMode.offlineClaimOwner(connected, staleSameAccount),
                "stale player cannot return or clear the new player's claim");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "moonstation14")
    public static void reconnectFailureWaitsAsInertSpectatorWithoutInventoryWrites(GameTestHelper helper) {
        AtomicInteger writes = new AtomicInteger();
        final GameType[] mode = {GameType.SURVIVAL};
        require(LifecycleExistingBodyReconnect.safeWaitAdmission(() -> {
            mode[0] = GameType.SPECTATOR;
            return true;
        }, () -> mode[0]), "failed proof must admit inert spectator carrier");
        require(writes.get() == 0, "waiting admission never mutates items");
        require(LifecycleExistingBodyReconnect.pendingDecision(20,
                com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.persistence.LoadedBodyResolver.Outcome.DEFER_KNOWN_BODY,
                true) == LifecycleExistingBodyReconnect.PendingDecision.TIMEOUT,
                "unloaded exact body remains deferred after bounded automatic wait");
        require(LifecycleExistingBodyReconnect.pendingDecision(1,
                com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.persistence.LoadedBodyResolver.Outcome.RECOVERY_REQUIRED,
                true, false) == LifecycleExistingBodyReconnect.PendingDecision.FAIL_CLOSED,
                "visible conflicting body evidence never authorizes reconnect");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 400)
    public static void missingOfflineBodyReconnectStaysSafeAndRetryable(GameTestHelper helper) {
        LifecycleHandActorFixture.start(helper, "missing-offline-body", owner -> {
            var server = helper.getLevel().getServer();
            var context = LifecycleStartupRuntime.contextFor(server).orElseThrow();
            var actorId = owner.actor().getUUID();
            var body = (PlayerCharacterHarnessEntity) owner.authority().body();
            var rowBefore = current(context, actorId, helper);
            var bodyScene = net.minecraft.world.phys.AABB.ofSize(body.position(), 128, 128, 128);
            int bodyCount = helper.getLevel().getEntitiesOfClass(PlayerCharacterHarnessEntity.class,
                    bodyScene, ignored -> true).size();
            owner.actor().connection.disconnect(Component.literal("missing body fixture logout"));
            BoundedGameTestWait.await(helper, 80, () -> "fixture did not persist exact OFFLINE claim",
                    () -> server.getPlayerList().getPlayer(actorId) != owner.actor()
                            && context.lifecycle().profile(actorId).map(profile -> !profile.active()
                            && profile.state() == com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.PlayerLifecycleRegistry.LifecycleState.OFFLINE).orElse(false),
                    () -> {
                        try {
                            var offline = context.currentAccountProfile(actorId).orElseThrow();
                            var mindAtOffline = context.ownership().mind(actorId).orElseThrow();
                            if (!offline.equals(rowBefore) && (offline.state() != SavedLifecycleProfile.State.OFFLINE
                                    || !offline.bodyId().equals(rowBefore.bodyId()) || !offline.mindId().equals(rowBefore.mindId()))) {
                                helper.fail("normal logout did not preserve exact saved identity"); return;
                            }
                            // Put the intentionally unusable saved pose away from vanilla spawn so
                            // the test detects an illicit saved-pose carrier teleport.
                            var displaced = new SavedLifecycleProfile(offline.schemaVersion(), offline.accountId(),
                                    offline.profileKey(), offline.mindId(), offline.bodyId(), offline.dimension(),
                                    new SavedLifecycleProfile.Location(offline.location().x() + 32,
                                            offline.location().y(), offline.location().z() + 32), offline.appearance(),
                                    offline.connectionEpoch(), offline.connectionGeneration(), offline.state(),
                                    offline.revision() + 1, offline.offlineSinceMillis());
                            context.primaryStore().withCurrentPrimary(lease -> {
                                var rows = new java.util.ArrayList<>(lease.envelope().profiles());
                                rows.set(rows.indexOf(offline), displaced);
                                lease.compareAndSwap(lease.envelope().generationCounter(), rows);
                                return true;
                            });
                            final SavedLifecycleProfile waitingRow = displaced;
                            body.discard();
                            LifecycleHandActorFixture.reconnectCarrier(helper, owner, (joined, channel) -> {
                                var beforeRetry = joined.position();
                                if (!LifecycleExistingBodyReconnect.isWaiting(server, joined)
                                        || joined.gameMode.getGameModeForPlayer() != GameType.SPECTATOR
                                        || joined.connection == null || !joined.connection.isAcceptingMessages()) {
                                    helper.fail("missing-body login was kicked or not admitted as safe spectator"); return;
                                }
                                if (joined.position().distanceToSqr(new net.minecraft.world.phys.Vec3(
                                        waitingRow.location().x(), waitingRow.location().y(), waitingRow.location().z())) < 256) {
                                    helper.fail("missing-body admission teleported carrier to the saved pose"); return;
                                }
                                if (!assertNonOperatorRetryCommand(helper, server, joined)) return;
                                try {
                                    int result = server.getCommands().getDispatcher().execute("ms14_return", joined.createCommandSourceStack());
                                    if (result <= 0) { helper.fail("permission-free retry command was not usable"); return; }
                                } catch (com.mojang.brigadier.exceptions.CommandSyntaxException failure) {
                                    helper.fail("permission-free retry command failed: " + failure.getMessage()); return;
                                }
                                if (joined.gameMode.getGameModeForPlayer() != GameType.SPECTATOR
                                        || !joined.connection.isAcceptingMessages()
                                        || !LifecycleExistingBodyReconnect.isWaiting(server, joined)
                                        || joined.position().distanceToSqr(beforeRetry) > 1.0e-8) {
                                    helper.fail("failed retry did not remain connected/inert without teleport"); return;
                                }
                                try {
                                    var after = context.currentAccountProfile(actorId).orElseThrow();
                                    var mindAfter = context.ownership().mind(actorId).orElseThrow();
                                    int afterCount = helper.getLevel().getEntitiesOfClass(PlayerCharacterHarnessEntity.class,
                                            bodyScene, ignored -> true).size();
                                    boolean claimSame = after.equals(waitingRow);
                                    boolean mindSame = mindAfter.equals(mindAtOffline);
                                    boolean bodyGone = body.isRemoved() && helper.getLevel().getEntity(waitingRow.bodyId()) == null;
                                    boolean countExpected = afterCount == bodyCount - 1;
                                    if (!claimSame || !mindSame || !bodyGone || !countExpected) {
                                        helper.fail("wait invariant failure claimSame=" + claimSame + " mindSame=" + mindSame
                                                + " bodyGone=" + bodyGone + " sceneCount=" + bodyCount + "->" + afterCount
                                                + " currentRow=" + after + " mind=" + mindAfter + " bodyRemoved=" + body.isRemoved()); return;
                                    }
                                } catch (java.io.IOException failure) {
                                    helper.fail("cannot verify retained offline claim: " + failure.getMessage()); return;
                                }
                                helper.succeed();
                            });
                        } catch (java.io.IOException failure) { helper.fail("cannot inspect offline claim: " + failure.getMessage()); }
                    });
        });
    }

    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 450)
    public static void waitingOfflineReconnectRetryReusesExactBodyAndMind(GameTestHelper helper) {
        LifecycleHandActorFixture.start(helper, "offline-retry-same-body", owner -> {
            var server = helper.getLevel().getServer();
            var context = LifecycleStartupRuntime.contextFor(server).orElseThrow();
            var actorId = owner.actor().getUUID();
            var body = (PlayerCharacterHarnessEntity) owner.authority().body();
            var mindId = owner.authority().mindId();
            var handIds = owner.authority().handIds();
            owner.actor().connection.disconnect(Component.literal("retry fixture logout"));
            BoundedGameTestWait.await(helper, 80, () -> "same-body fixture did not become OFFLINE",
                    () -> server.getPlayerList().getPlayer(actorId) != owner.actor()
                            && context.lifecycle().profile(actorId).map(profile -> !profile.active()
                            && profile.state() == com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.PlayerLifecycleRegistry.LifecycleState.OFFLINE).orElse(false),
                    () -> {
                        try {
                            var offline = context.currentAccountProfile(actorId).orElseThrow();
                            if (offline.bodyId().equals(body.getUUID()) && offline.mindId().equals(mindId.value())) {
                                body.setNoAi(false); // A live identity which temporarily fails reconnect proof.
                                LifecycleHandActorFixture.reconnectCarrier(helper, owner, (joined, channel) -> {
                                    if (!LifecycleExistingBodyReconnect.isWaiting(server, joined)
                                            || joined.gameMode.getGameModeForPlayer() != GameType.SPECTATOR
                                            || !joined.connection.isAcceptingMessages()) {
                                        helper.fail("unusable body did not enter safe waiting state"); return;
                                    }
                                    if (!assertNonOperatorRetryCommand(helper, server, joined)) return;
                                    body.setNoAi(true);
                                    try {
                                        int result = server.getCommands().getDispatcher().execute("ms14_return", joined.createCommandSourceStack());
                                        if (result <= 0) { helper.fail("waiting body retry command did not run"); return; }
                                    } catch (com.mojang.brigadier.exceptions.CommandSyntaxException failure) {
                                        helper.fail("waiting body retry command failed: " + failure.getMessage()); return;
                                    }
                                    LifecycleHandActorFixture.completeReconnect(helper, joined, channel,
                                            body.getUUID(), mindId, handIds,
                                            () -> {
                                                var memory = context.lifecycle().profile(actorId).orElse(null);
                                                SavedLifecycleProfile current = null;
                                                try { current = context.currentAccountProfile(actorId).orElse(null); }
                                                catch (java.io.IOException ignored) { }
                                                return "retry Begin timeout account=" + actorId + " row=" + current
                                                        + " memory=" + memory + " waiting="
                                                        + LifecycleExistingBodyReconnect.isWaiting(server, joined)
                                                        + " bodyNoAi=" + body.isNoAi() + " removed=" + body.isRemoved();
                                            }, rejoined -> {
                                                try {
                                                    var authority = rejoined.authority();
                                                    var active = context.currentAccountProfile(actorId).orElseThrow();
                                                    if (authority.body() != body || !authority.handIds().equals(handIds)
                                                            || !active.bodyId().equals(body.getUUID())
                                                            || !active.mindId().equals(mindId.value())
                                                            || active.state() != SavedLifecycleProfile.State.ACTIVE
                                                            || active.connectionGeneration() <= offline.connectionGeneration()
                                                            || LifecycleExistingBodyReconnect.isWaiting(server, joined)
                                                            || !joined.connection.isAcceptingMessages()) {
                                                        helper.fail("retry changed body/Mind, failed generation CAS, or retained waiting owner"); return;
                                                    }
                                                    helper.succeed();
                                                } catch (java.io.IOException failure) { helper.fail("cannot verify ACTIVE retry row: " + failure.getMessage()); }
                                            });
                                });
                            } else helper.fail("OFFLINE row did not retain original body and Mind");
                        } catch (java.io.IOException failure) { helper.fail("cannot inspect OFFLINE retry claim: " + failure.getMessage()); }
                    });
        });
    }

    private static SavedLifecycleProfile current(LifecycleServerContext context, UUID account, GameTestHelper helper) {
        try { return context.currentAccountProfile(account).orElseThrow(); }
        catch (java.io.IOException failure) { helper.fail("cannot read current lifecycle primary: " + failure.getMessage()); return null; }
    }

    private static boolean assertNonOperatorRetryCommand(GameTestHelper helper,
            net.minecraft.server.MinecraftServer server, net.minecraft.server.level.ServerPlayer player) {
        var source = player.createCommandSourceStack();
        if (player.hasPermissions(2)) { helper.fail("retry fixture unexpectedly has operator permission"); return false; }
        CommandDispatcher<CommandSourceStack> collision = new CommandDispatcher<>();
        collision.register(Commands.literal("ms14").requires(command -> command.hasPermission(2))
                .then(Commands.literal("fixture").executes(command -> 1)));
        collision.register(Commands.literal("ms14_return").executes(command -> 1));
        int collisionResult;
        try { collisionResult = collision.execute("ms14_return", source); }
        catch (com.mojang.brigadier.exceptions.CommandSyntaxException failure) {
            helper.fail("canonical collision route failed to parse: " + failure.getMessage()); return false;
        }
        if (collision.getRoot().getChild("ms14").canUse(source)
                || !collision.getRoot().getChild("ms14_return").canUse(source)
                || collisionResult != 1) {
            helper.fail("canonical retry root failed permission-zero collision/parse/execute check"); return false;
        }
        var canonical = server.getCommands().getDispatcher().getRoot().getChild("ms14_return");
        if (canonical == null || !canonical.canUse(source)) {
            helper.fail("registered canonical retry command is not available to permission-zero player"); return false;
        }
        return true;
    }

    private static void require(boolean valid, String message) {
        if (!valid) throw new AssertionError(message);
    }
}
