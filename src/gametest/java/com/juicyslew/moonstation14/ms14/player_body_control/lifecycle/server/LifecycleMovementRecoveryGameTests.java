package com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server;

import com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.ms14.hands.network.LifecycleHandActorFixture;
import com.juicyslew.moonstation14.ms14.player_body_control.character.GroundedHarnessWorldStep;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessEntity;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;

@GameTestTags({"shuttle-movement-recovery", "shuttle-owner-regression", "lifecycle-ready-timeout", "lifecycle"})
@PrefixGameTestTemplate(false)
public final class LifecycleMovementRecoveryGameTests {
    private LifecycleMovementRecoveryGameTests() { }

    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 600)
    public static void postMoveRejectionAndErrorRetainThenCleanlyRejoinSameAuthority(GameTestHelper helper) {
        LifecycleHandActorFixture.start(helper, "movement-recovery", owner -> {
            var body = (PlayerCharacterHarnessEntity) owner.authority().body();
            var originalBodyId = body.getUUID();
            var originalMind = owner.authority().mindId();
            var originalHands = owner.authority().handIds();
            runPartialFault(helper, owner, false, () -> runPartialFault(helper, owner, true, () -> {
                sendAndAwaitValidMove(helper, owner, body, () -> {
                    assertExactAuthority(helper, owner, body, originalBodyId, originalMind, originalHands);
                    owner.actor().connection.disconnect(Component.literal("movement recovery fixture logout"));
                    var server = helper.getLevel().getServer();
                    BoundedGameTestWait.await(helper, 80, () -> "normal logout did not park exact lifecycle identity",
                            () -> server.getPlayerList().getPlayer(owner.actor().getUUID()) != owner.actor()
                                    && LifecycleStartupRuntime.contextFor(server).flatMap(context ->
                                    context.lifecycle().profile(owner.actor().getUUID())).map(profile ->
                                    profile.state() == com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.PlayerLifecycleRegistry.LifecycleState.OFFLINE
                                            && !profile.active()).orElse(false),
                            () -> LifecycleHandActorFixture.reconnect(helper, owner, rejoined -> {
                                if (!rejoined.authority().body().getUUID().equals(originalBodyId)
                                        || !rejoined.authority().mindId().equals(originalMind)
                                        || !rejoined.authority().handIds().equals(originalHands)
                                        || rejoined.actor().getCamera() != body) {
                                    helper.fail("normal login/Ready failed to reclaim the same body, Mind, hand roots, and camera");
                                    return;
                                }
                                helper.succeed();
                            }));
                });
            }));
        });
    }

    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 300)
    public static void healthyReadyAfterPreviousTimeoutWindowKeepsSameConnectionAndBody(GameTestHelper helper) {
        LifecycleHandActorFixture.start(helper, "late-ready", 125, owner -> {
            if (!owner.actor().connection.isAcceptingMessages()
                    || helper.getLevel().getServer().getPlayerList().getPlayer(owner.actor().getUUID()) != owner.actor()
                    || owner.actor().getCamera() != owner.authority().body()) {
                helper.fail("healthy late Ready did not commit on the original connection and body");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 250)
    public static void unknownPoseHoldsOwnershipSuppressesSnapshotAndResumesAtKnownPoint(GameTestHelper helper) {
        LifecycleHandActorFixture.start(helper, "pose-hold", owner -> {
            var body = (PlayerCharacterHarnessEntity) owner.authority().body();
            // Give the already-spawned body a broad landing area. It may have acquired
            // downward velocity during empty-world startup, so do not rely on one cell.
            var support = body.blockPosition().below();
            for (int x = -4; x <= 4; x++) for (int z = -4; z <= 4; z++)
                helper.getLevel().setBlock(support.offset(x, 0, z), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), 3);
            owner.drainSnapshots();
            long oldRevision = owner.latestSnapshot().get() == null ? 0
                    : owner.latestSnapshot().get().movementRebaseRevision();
            var known = new java.util.concurrent.atomic.AtomicBoolean(false);
            AutoCloseable scope = LifecycleCharacterSessionControl.withSafePoseObserverForTesting(owner.actor(),
                    (mob, level) -> known.get()
                            && com.juicyslew.moonstation14.ms14.player_body_control.movement.CharacterMovementSafePose
                            .finiteBounded(mob.position())
                            && com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery
                            .currentActorPoint(level, mob) != null);
            AtomicBoolean restored = new AtomicBoolean();
            Runnable restore = () -> { if (restored.compareAndSet(false, true)) close(scope); };
            helper.testInfo.addListener(new net.minecraft.gametest.framework.GameTestListener() {
                @Override public void testStructureLoaded(net.minecraft.gametest.framework.GameTestInfo test) { }
                @Override public void testPassed(net.minecraft.gametest.framework.GameTestInfo test,
                                                 net.minecraft.gametest.framework.GameTestRunner runner) { restore.run(); }
                @Override public void testFailed(net.minecraft.gametest.framework.GameTestInfo test,
                                                 net.minecraft.gametest.framework.GameTestRunner runner) { restore.run(); }
                @Override public void testAddedForRerun(net.minecraft.gametest.framework.GameTestInfo oldTest,
                                                        net.minecraft.gametest.framework.GameTestInfo newTest,
                                                        net.minecraft.gametest.framework.GameTestRunner runner) { restore.run(); }
            });
            owner.setWish(0, 0);
            owner.neutralIntent(body);
            long holdStarted = helper.getTick();
            AtomicInteger packetsWhileUnknown = new AtomicInteger();
            BoundedGameTestWait.await(helper, 20, () -> "unknown-pose hold failed to remain stable",
                    () -> {
                        packetsWhileUnknown.addAndGet(owner.drainSnapshots().size());
                        return helper.getTick() - holdStarted >= 3;
                    }, () -> {
                        if (packetsWhileUnknown.get() != 0 || !owner.actor().connection.isAcceptingMessages()
                                || owner.actor().getCamera() != body
                                || com.juicyslew.moonstation14.ms14.hands.HandActorAuthority.resolve(owner.actor()).isEmpty()) {
                            helper.fail("unknown pose emitted a snapshot or retired exact body ownership"); return;
                        }
                        var profile = LifecycleStartupRuntime.contextFor(helper.getLevel().getServer()).orElseThrow()
                                .lifecycle().profile(owner.actor().getUUID()).orElseThrow();
                        if (!profile.active() || profile.state()
                                != com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.PlayerLifecycleRegistry.LifecycleState.ACTIVE) {
                            helper.fail("unknown pose hold revoked lifecycle authority"); return;
                        }
                        known.set(true);
                        owner.setWish(0, 0);
                        final com.juicyslew.moonstation14.ms14.player_body_control.network.GhostControlPayloads.Snapshot[] resumed = {null};
                        final net.minecraft.world.phys.Vec3[] resumedPose = {null};
                        BoundedGameTestWait.observeServerTicks(helper, 30, event -> {
                            // Observe after physical simulation and inspect the real outbound producer.
                            for (var snapshot : owner.drainSnapshots()) {
                                if (snapshot.movementRebaseRevision() > oldRevision) {
                                    resumed[0] = snapshot;
                                    resumedPose[0] = body.position();
                                }
                            }
                        }, () -> {
                            restore.run();
                            var snapshot = resumed[0];
                            var currentPoint = com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery
                                    .currentActorPoint(helper.getLevel(), body);
                            if (snapshot == null || resumedPose[0] == null || currentPoint == null
                                    || !com.juicyslew.moonstation14.ms14.player_body_control.movement.CharacterMovementSafePose
                                    .valid(helper.getLevel(), resumedPose[0])
                                    || !Double.isFinite(snapshot.x()) || !Double.isFinite(snapshot.y())
                                    || !Double.isFinite(snapshot.z()) || owner.actor().getCamera() != body) {
                                helper.fail("known-point recovery failed: resumed=" + (snapshot != null)
                                        + " resumedRevision=" + (snapshot == null ? "none" : snapshot.movementRebaseRevision())
                                        + " oldRevision=" + oldRevision + " currentPoint=" + (currentPoint != null)
                                        + " resumedPose=" + resumedPose[0] + " currentPose=" + body.position()
                                        + " currentPoseValid=" + com.juicyslew.moonstation14.ms14.player_body_control.movement.CharacterMovementSafePose.valid(helper.getLevel(), body.position())
                                        + " cameraBound=" + (owner.actor().getCamera() == body)); return;
                            }
                            BoundedGameTestWait.succeedAfterObservation(helper);
                        });
                    });
        });
    }

    private static void runPartialFault(GameTestHelper helper, LifecycleHandActorFixture.Ready owner,
                                        boolean throwAfterMove, Runnable next) {
        var body = (PlayerCharacterHarnessEntity) owner.authority().body();
        var positionBefore = body.position();
        long previousRevision = owner.latestSnapshot().get() == null ? 0
                : owner.latestSnapshot().get().movementRebaseRevision();
        AtomicInteger calls = new AtomicInteger();
        GroundedHarnessWorldStep production = new GroundedHarnessWorldStep();
        AutoCloseable scope = LifecycleCharacterSessionControl.withWorldStepForTesting(owner.actor(),
                (mob, x, z, jump, sprint, yaw, reference) -> {
                    calls.incrementAndGet();
                    Optional<com.juicyslew.moonstation14.ms14.movement.CharacterMovementState> moved =
                            production.step(mob, x, z, jump, sprint, yaw, null, reference);
                    if (moved.isEmpty()) throw new AssertionError("production native move failed before injected result");
                    if (throwAfterMove) throw new IllegalStateException("fixture post-move resolver failure");
                    return Optional.empty();
                });
        AtomicBoolean restored = new AtomicBoolean();
        Runnable restore = () -> {
            if (!restored.compareAndSet(false, true)) return;
            close(scope);
        };
        helper.testInfo.addListener(new net.minecraft.gametest.framework.GameTestListener() {
            @Override public void testStructureLoaded(net.minecraft.gametest.framework.GameTestInfo test) { }
            @Override public void testPassed(net.minecraft.gametest.framework.GameTestInfo test,
                                             net.minecraft.gametest.framework.GameTestRunner runner) { restore.run(); }
            @Override public void testFailed(net.minecraft.gametest.framework.GameTestInfo test,
                                             net.minecraft.gametest.framework.GameTestRunner runner) { restore.run(); }
            @Override public void testAddedForRerun(net.minecraft.gametest.framework.GameTestInfo oldTest,
                                                    net.minecraft.gametest.framework.GameTestInfo newTest,
                                                    net.minecraft.gametest.framework.GameTestRunner runner) { restore.run(); }
        });
        owner.setWish(1000, 0);
        long sequence = owner.neutralIntent(body);
        BoundedGameTestWait.await(helper, 40,
                () -> "post-move fault was not authoritatively corrected body=" + body.getUUID() + " seq=" + sequence,
                () -> owner.drainAppliedSequence() >= sequence
                        && owner.latestSnapshot().get() != null
                        && owner.latestSnapshot().get().movementRebaseRevision() > previousRevision,
                () -> {
                    restore.run();
                    if (calls.get() != 1) { helper.fail("faulting world step was retried in the same server tick"); return; }
                    if (body.position().distanceToSqr(positionBefore) <= 1.0e-12) {
                        helper.fail("fault-injected native step did not leave the intended partial physical movement"); return;
                    }
                    assertExactAuthority(helper, owner, body, body.getUUID(), owner.authority().mindId(), owner.authority().handIds());
                    if (body.getDeltaMovement().lengthSqr() != 0d) {
                        helper.fail("rejected partial world step did not clear unsafe voluntary velocity"); return;
                    }
                    var profile = LifecycleStartupRuntime.contextFor(helper.getLevel().getServer()).orElseThrow()
                            .lifecycle().profile(owner.actor().getUUID()).orElseThrow();
                    if (!profile.active() || profile.state()
                            != com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.PlayerLifecycleRegistry.LifecycleState.ACTIVE) {
                        helper.fail("movement fault changed durable/in-memory ACTIVE ownership"); return;
                    }
                    com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.persistence.SavedLifecycleProfile saved;
                    try { saved = LifecycleStartupRuntime.contextFor(helper.getLevel().getServer()).orElseThrow()
                            .currentAccountProfile(owner.actor().getUUID()).orElseThrow(); }
                    catch (java.io.IOException failure) { helper.fail("cannot inspect durable ACTIVE row: " + failure); return; }
                    if (saved.state() != com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.persistence.SavedLifecycleProfile.State.ACTIVE
                            || saved.connectionGeneration() != owner.epoch()) {
                        helper.fail("movement fault changed durable ACTIVE row or generation"); return;
                    }
                    next.run();
                });
    }

    private static void sendAndAwaitValidMove(GameTestHelper helper, LifecycleHandActorFixture.Ready owner,
                                              PlayerCharacterHarnessEntity body, Runnable complete) {
        owner.setWish(0, 1000);
        long sequence = owner.neutralIntent(body);
        BoundedGameTestWait.await(helper, 40, () -> "next valid production intent did not work after recovery seq=" + sequence,
                () -> owner.drainAppliedSequence() >= sequence,
                () -> complete.run());
    }

    private static void assertExactAuthority(GameTestHelper helper, LifecycleHandActorFixture.Ready owner,
                                             PlayerCharacterHarnessEntity body, java.util.UUID bodyId,
                                             com.juicyslew.moonstation14.ms14.player_body_control.MindId mind,
                                             java.util.List<String> hands) {
        var authority = com.juicyslew.moonstation14.ms14.hands.HandActorAuthority.resolve(owner.actor()).orElse(null);
        if (authority == null || authority.actor() != owner.actor() || authority.body() != body
                || !body.getUUID().equals(bodyId) || !authority.mindId().equals(mind)
                || !authority.handIds().equals(hands) || owner.actor().getCamera() != body
                || !owner.actor().connection.isAcceptingMessages())
            helper.fail("movement recovery lost exact committed connection/body/Mind/hand root/camera");
    }

    private static void close(AutoCloseable scope) {
        try { scope.close(); }
        catch (Exception failure) { throw new GameTestAssertException("failed restoring scoped world-step resolver: " + failure); }
    }
}
