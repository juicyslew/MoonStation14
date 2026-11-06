package com.juicyslew.moonstation14.ms14.shuttle.sable;

import com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server.LifecycleSingleWorldStepObserver;
import com.juicyslew.moonstation14.ms14.player_body_control.network.GhostControlPayloads;
import com.juicyslew.moonstation14.ms14.shuttle.fixture.ShuttleFixtureMotionService;
import com.juicyslew.moonstation14.ms14.shuttle.fixture.ShuttleMotionRequest;
import com.juicyslew.moonstation14.ms14.movement.NativeCollisionSlop;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/** Live native regression: jump from, then remain supported by, a descending deck. */
@PrefixGameTestTemplate(false)
@GameTestTags("shuttle-descending-landing")
public final class DescendingFrameLandingGameTests {
    private static final int MAX_ATTEMPTS = 256;
    private DescendingFrameLandingGameTests() { }

    @GameTestTags("shuttle-descending-landing")
    @GameTest(template="empty", templateNamespace="moonstation14", timeoutTicks=500)
    public static void zeroAngularDescendJumpLandsAndStaysSupported(GameTestHelper helper) { run(helper, false); }

    @GameTestTags("shuttle-descending-landing")
    @GameTest(template="empty", templateNamespace="moonstation14", timeoutTicks=500)
    public static void yawingDescendJumpLandsAndStaysSupported(GameTestHelper helper) { run(helper, true); }

    private static void run(GameTestHelper helper, boolean yaw) {
        MovingCabinAuthorityFixture.start(helper, new BlockPos(yaw ? 4800 : 4400, 300, 1800),
                yaw ? "descending-yaw" : "descending-straight", 2.5, 1, 2.5, (fixture, body) -> {
            var frame = fixture.plot().subLevel();
            if (!MovingCabinAuthorityFixture.removeAuthoredStonePad(fixture)) return;
            ShuttleFixtureMotionService.registerFixture(frame);
            if (!ShuttleFixtureMotionService.start(fixture.level(), frame,
                    new ShuttleMotionRequest(0, -2, 0, yaw ? .5 : 0, 400))) {
                helper.fail("native descending-frame controller did not start"); return;
            }
            var attempts = new AtomicReference<List<LifecycleSingleWorldStepObserver.Attempt>>(List.of());
            var overflow = new AtomicBoolean();
            final AutoCloseable observer;
            try { observer = LifecycleSingleWorldStepObserver.install(fixture.owner().actor(), attempts,
                    MAX_ATTEMPTS, overflow); }
            catch (Exception e) { helper.fail("observer installation failed: " + e); return; }
            helper.testInfo.addListener(new net.minecraft.gametest.framework.GameTestListener() {
                private boolean closed;
                private void close() {
                    if (closed) return;
                    closed = true;
                    try { observer.close(); } catch (Exception ignored) { }
                    ShuttleFixtureMotionService.stop(fixture.level(), frame);
                }
                @Override public void testStructureLoaded(net.minecraft.gametest.framework.GameTestInfo test) { }
                @Override public void testPassed(net.minecraft.gametest.framework.GameTestInfo test,
                        net.minecraft.gametest.framework.GameTestRunner runner) { close(); }
                @Override public void testFailed(net.minecraft.gametest.framework.GameTestInfo test,
                        net.minecraft.gametest.framework.GameTestRunner runner) { close(); }
                @Override public void testAddedForRerun(net.minecraft.gametest.framework.GameTestInfo oldTest,
                        net.minecraft.gametest.framework.GameTestInfo newTest,
                        net.minecraft.gametest.framework.GameTestRunner runner) { close(); }
            });
            awaitStableDescent(helper, fixture, body, yaw, attempts, overflow);
        });
    }

    private static void awaitStableDescent(GameTestHelper h, MovingCabinAuthorityFixture.Ready f,
            net.minecraft.world.entity.LivingEntity body, boolean yaw,
            AtomicReference<List<LifecycleSingleWorldStepObserver.Attempt>> rows, AtomicBoolean overflow) {
        final class Stable {
            long lastWorldTick = Long.MIN_VALUE;
            int consecutive;
            double lastVy = Double.NaN;
        }
        var state = new Stable();
        BoundedGameTestWait.await(h, 80, () -> "deck did not reach steady measured descent",
                () -> {
                    checkOverflow(overflow, rows);
                    var all = rows.get();
                    var a = all.isEmpty() ? null : all.getLast();
                    if (a == null || a.worldGameTick() == state.lastWorldTick) return false;
                    state.lastWorldTick = a.worldGameTick();
                    if (a.failure() != null || a.callbackCount() != 1 || a.move() == null || a.result() == null
                            || a.result().isEmpty()) throw failure("incomplete actual world-step before jump", all);
                    var v = RigidBodyHandle.of(f.plot().subLevel()).getLinearVelocity(new org.joml.Vector3d());
                    var w = RigidBodyHandle.of(f.plot().subLevel()).getAngularVelocity(new org.joml.Vector3d());
                    boolean steady = v.y <= -1.8 && v.y >= -2.2
                            && (Double.isNaN(state.lastVy) || Math.abs(v.y - state.lastVy) <= .02)
                            && (!yaw ? Math.abs(w.y) <= .02 : w.y >= .05)
                            && a.groundedAfter() && a.move().afterPoint() != null
                            && f.plot().engineId().equals(a.move().afterPoint().frameId());
                    state.consecutive = steady ? state.consecutive + 1 : 0;
                    state.lastVy = v.y;
                    return state.consecutive >= 3;
                }, () -> {
                    var point = LoadedFrameRayQuery.currentActorPoint(f.level(), body);
                    if (point == null || !f.plot().engineId().equals(point.frameId()) || !body.onGround())
                        throw failure("stable descent lacks actual same-frame grounded support", rows.get());
                    dispatchJump(h, f, body, rows, overflow);
                });
    }

    private static void dispatchJump(GameTestHelper h, MovingCabinAuthorityFixture.Ready f,
            net.minecraft.world.entity.LivingEntity body,
            AtomicReference<List<LifecycleSingleWorldStepObserver.Attempt>> rows, AtomicBoolean overflow) {
        f.setIntentPumpEnabled(false);
        var owner = f.owner();
        owner.buttons().updateAndGet(v -> v | GhostControlPayloads.BUTTON_JUMP);
        long jumpSequence = owner.neutralIntent(body);
        long dispatchTick = h.getLevel().getServer().getTickCount();
        final class Launch { LifecycleSingleWorldStepObserver.Attempt row; }
        var launch = new Launch();
        BoundedGameTestWait.await(h, 40, () -> "jump was not acknowledged with actual airborne launch",
                () -> {
                    checkOverflow(overflow, rows);
                    launch.row = rows.get().stream().filter(a -> a.serverPhysicalTick() >= dispatchTick
                            && a.jump() && a.groundedBefore() && !a.groundedAfter() && a.move() != null
                            && a.move().acceptedOwnDisplacement().y() > 0).findFirst().orElse(null);
                    return owner.drainAppliedSequence() >= jumpSequence && launch.row != null;
                }, () -> {
                    owner.buttons().updateAndGet(v -> v & ~GhostControlPayloads.BUTTON_JUMP);
                    long neutralSequence = owner.neutralIntent(body);
                    f.setIntentPumpEnabled(true);
                    awaitLanding(h, f, rows, overflow, launch.row, jumpSequence, neutralSequence);
                });
    }

    private static void awaitLanding(GameTestHelper h, MovingCabinAuthorityFixture.Ready f,
            AtomicReference<List<LifecycleSingleWorldStepObserver.Attempt>> rows, AtomicBoolean overflow,
            LifecycleSingleWorldStepObserver.Attempt launch, long jumpSequence, long neutralSequence) {
        final class Landed { LifecycleSingleWorldStepObserver.Attempt row; }
        var landing = new Landed();
        BoundedGameTestWait.await(h, 160, () -> "no real same-frame re-landing after jump seq=" + jumpSequence
                        + " neutral=" + neutralSequence,
                () -> {
                    checkOverflow(overflow, rows);
                    landing.row = rows.get().stream().filter(a -> a.worldGameTick() > launch.worldGameTick()
                            && !a.groundedBefore() && a.groundedAfter() && a.callbackCount() == 1
                            && a.result() != null && a.result().isPresent() && a.move() != null
                            && a.move().afterPoint() != null
                            && f.plot().engineId().equals(a.move().afterPoint().frameId()))
                            .findFirst().orElse(null);
                    return landing.row != null;
                }, () -> awaitHold(h, f, rows, overflow, landing.row));
    }

    private static void awaitHold(GameTestHelper h, MovingCabinAuthorityFixture.Ready f,
            AtomicReference<List<LifecycleSingleWorldStepObserver.Attempt>> rows, AtomicBoolean overflow,
            LifecycleSingleWorldStepObserver.Attempt landing) {
        final class Hold { long lastWorldTick; int count; double feetY; }
        var hold = new Hold();
        hold.lastWorldTick = landing.worldGameTick();
        hold.feetY = landing.move().afterPoint().storagePoint().y;
        BoundedGameTestWait.await(h, 100, () -> "fewer than 80 distinct grounded postlanding world steps",
                () -> {
                    checkOverflow(overflow, rows);
                    var next = rows.get().stream().filter(a -> a.worldGameTick() > hold.lastWorldTick)
                            .findFirst().orElse(null);
                    if (next == null) return false;
                    if (next.worldGameTick() != hold.lastWorldTick + 1)
                        throw failure("gap in postlanding actual world steps", rows.get());
                    hold.lastWorldTick = next.worldGameTick();
                    if (next.failure() != null || next.callbackCount() != 1 || next.result() == null
                            || next.result().isEmpty() || next.jump() || !next.groundedBefore() || !next.groundedAfter()
                            || next.move() == null || next.move().afterPoint() == null
                            || !f.plot().engineId().equals(next.move().afterPoint().frameId()))
                        throw failure("native grounded same-frame support lost during hold", rows.get());
                    double feetY = next.move().afterPoint().storagePoint().y;
                    if (Math.abs(feetY - hold.feetY) > NativeCollisionSlop.MAX_RESIDUAL)
                        throw failure("frame-relative feet height exceeded native contact slop: "
                                + hold.feetY + " -> " + feetY, rows.get());
                    double normalVelocityTolerance = NativeCollisionSlop.MAX_RESIDUAL
                            / com.juicyslew.moonstation14.ms14.player_body_control.movement.GroundedHarnessPhysics.TICK_SECONDS;
                    if (next.afterVelocity().y > normalVelocityTolerance)
                        throw failure("postlanding upward normal velocity=" + next.afterVelocity(), rows.get());
                    return ++hold.count >= 80;
                }, h::succeed);
    }

    private static void checkOverflow(AtomicBoolean overflow,
            AtomicReference<List<LifecycleSingleWorldStepObserver.Attempt>> rows) {
        if (overflow.get()) throw failure("bounded actual-step observer overflow", rows.get());
    }

    private static GameTestAssertException failure(String reason, List<LifecycleSingleWorldStepObserver.Attempt> rows) {
        String tail = rows.stream().skip(Math.max(0, rows.size() - 4)).map(a -> "tick=" + a.worldGameTick()
                + "/" + a.serverPhysicalTick() + " jump=" + a.jump() + " ground=" + a.groundedBefore() + ">"
                + a.groundedAfter() + " callbacks=" + a.callbackCount() + " ownY="
                + (a.move() == null ? "-" : a.move().acceptedOwnDisplacement().y()) + " vy=" + a.afterVelocity().y()
                + " frame=" + (a.move() == null || a.move().afterPoint() == null ? "?" : a.move().afterPoint().frameId())
                + " storageY=" + (a.move() == null || a.move().afterPoint() == null ? "?" : a.move().afterPoint().storagePoint().y)
                + " fail=" + (a.failure() == null ? "-" : a.failure().substring(0, Math.min(128, a.failure().length()))))
                .toList().toString();
        return new GameTestAssertException(reason + "; attempts=" + rows.size() + " tail=" + tail);
    }
}
