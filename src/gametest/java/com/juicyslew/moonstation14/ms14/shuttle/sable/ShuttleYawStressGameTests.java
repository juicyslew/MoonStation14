package com.juicyslew.moonstation14.ms14.shuttle.sable;

import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.ms14.shuttle.fixture.ShuttleCabinFixture;
import com.juicyslew.moonstation14.ms14.shuttle.fixture.ShuttleFixtureMotionService;
import com.juicyslew.moonstation14.ms14.shuttle.fixture.ShuttleMotionRequest;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.joml.Vector3d;

/** Native physics yaw-rate stress: records cross-axis angular motion and uprightness, never imposes attitude lock. */
@GameTestTags({"shuttle-yaw-stress", "shuttle"})
@PrefixGameTestTemplate(false)
public final class ShuttleYawStressGameTests {
    private ShuttleYawStressGameTests() { }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 2000)
    public static void yawPointFiveRadiansPerSecond(GameTestHelper helper) { run(helper, 8000, .5, 280); }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 2000)
    public static void yawOneRadianPerSecond(GameTestHelper helper) { run(helper, 8200, 1, 280); }

    @GameTestTags({"shuttle-yaw-three-stock-diagnostics"})
    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 2000)
    public static void yawThreeRadiansPerSecond(GameTestHelper helper) { run(helper, 8400, 3, 900); }

    @GameTestTags({"shuttle-yaw-target-regression"})
    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 2000)
    public static void yawFiveRadiansPerSecondHasBoundedNetRampAndCoasts(GameTestHelper helper) {
        run(helper, 8600, 5, 600, true);
    }

    private static void run(GameTestHelper helper, int x, double requestedRate, int rampTicks) {
        run(helper, x, requestedRate, rampTicks, false);
    }

    private static void run(GameTestHelper helper, int x, double requestedRate, int rampTicks, boolean coastAfter) {
        var level = helper.getLevel();
        var region = SableFixtureScene.acquire(helper, new BlockPos(x, 300, 6000), 24, 12, 24);
        SableFixtureScene.awaitEmpty(helper, region, 80, () -> {
            SableFixtureScene.authorStoneSupportPad(level, region);
            var authored = SableFixturePlot.allocateAndAuthor(level, new Vector3d(x + 8.5, 300, 6005.5), plot -> {
                var storage = plot.storageOrigin();
                plot.ensureStorageFootprint(storage, ShuttleCabinFixture.WIDTH, ShuttleCabinFixture.HEIGHT, ShuttleCabinFixture.DEPTH);
                return ShuttleCabinFixture.build(level, storage, plot.engineId());
            });
            var plot = authored.plot();
            SableFixtureScene.trackPlot(helper, plot);
            var owner = plot.subLevel();
            ShuttleFixtureMotionService.registerFixture(owner);
            var handle = RigidBodyHandle.of(owner);
            require(handle != null && handle.isValid(), "yaw fixture has a live native body");
            HullImpactSubstepsExpectation.requireNormalSix(level, "yaw-stress-" + requestedRate);
            double padTop = region.supportSurfaceY();
            // Natural gravity/support settling occurs before any controller request; no pose or velocity reset.
            SableFixtureScene.awaitStationaryFrame(helper, plot, 200, () -> {
                var settledPose = dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem.get(level).getPipeline()
                        .readPose(owner, new dev.ryanhcode.sable.companion.math.Pose3d());
                var settledUp = settledPose.orientation().transform(new Vector3d(0, 1, 0));
                var settledOmega = handle.getAngularVelocity(new Vector3d());
                require(settledUp.y > Math.cos(Math.toRadians(5)) && Math.abs(settledUp.x) < .087 && Math.abs(settledUp.z) < .087,
                        "naturally settled frame starts upright; up=" + settledUp + " omega=" + settledOmega);
                require(ShuttleFixtureMotionService.start(level, owner,
                        new ShuttleMotionRequest(0, .75, 0, 0, 400)), "physical 400-tick clearance lift starts");
                awaitClearance(helper, level, owner, handle, requestedRate, rampTicks, padTop, plot, 0, coastAfter);
            });
        });
    }

    /** Samples lift ramp and yaw ramp plus a final 101-tick steady window; native state is checked each game tick. */
    private static final class YawObserver {
        private final GameTestHelper helper;
        private final net.minecraft.server.level.ServerLevel level;
        private final dev.ryanhcode.sable.sublevel.ServerSubLevel owner;
        private final RigidBodyHandle handle;
        private final double target, padTop;
        private final int rampTicks;
        private final SableFixturePlot plot;
        private final boolean coastAfter;
        private double minYaw = Double.POSITIVE_INFINITY, maxYaw = Double.NEGATIVE_INFINITY;
        private double maxRollRate, maxPitchRate, maxTilt;
        private double minBottom = Double.POSITIVE_INFINITY;
        private int samples;
        private double steadyMin = Double.POSITIVE_INFINITY, steadyMax = Double.NEGATIVE_INFINITY, steadySum;
        private long previousApplied = -1;
        private Vector3d previousOmega;

        YawObserver(GameTestHelper helper, net.minecraft.server.level.ServerLevel level,
                    dev.ryanhcode.sable.sublevel.ServerSubLevel owner, RigidBodyHandle handle,
                     double target, int rampTicks, double padTop, SableFixturePlot plot, boolean coastAfter) {
            this.helper = helper; this.level = level; this.owner = owner; this.handle = handle;
            this.target = target; this.rampTicks = rampTicks; this.padTop = padTop; this.plot = plot;
            this.coastAfter = coastAfter;
            this.previousOmega = handle.getAngularVelocity(new Vector3d());
        }

        void sample(int tick) {
            var snapshot = ShuttleFixtureMotionService.snapshot(level, owner);
            require(snapshot != null && snapshot.active(), "yaw controller/request expired during sample " + tick);
            require(snapshot.sdkSubsteps() == 6, "normal six-substep configuration changed during yaw");
            if (previousApplied >= 0) require(snapshot.appliedSubsteps() - previousApplied == 6,
                    "native applied-substep counter did not advance exactly six at sample " + tick);
            previousApplied = snapshot.appliedSubsteps();
            var omega = handle.getAngularVelocity(new Vector3d());
            var pose = dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem.get(level).getPipeline()
                    .readPose(owner, new dev.ryanhcode.sable.companion.math.Pose3d());
            var up = pose.orientation().transform(new Vector3d(0, 1, 0));
            double tilt = Math.acos(Math.max(-1, Math.min(1, up.y)));
            double bottom = worldBottom(level, owner);
            minYaw = Math.min(minYaw, omega.y); maxYaw = Math.max(maxYaw, omega.y);
            maxRollRate = Math.max(maxRollRate, Math.abs(omega.x)); maxPitchRate = Math.max(maxPitchRate, Math.abs(omega.z));
            maxTilt = Math.max(maxTilt, tilt); minBottom = Math.min(minBottom, bottom); samples++;
            if (tick >= rampTicks) {
                steadyMin = Math.min(steadyMin, omega.y); steadyMax = Math.max(steadyMax, omega.y); steadySum += omega.y;
            }
            if (previousOmega != null && previousOmega.x == 0 && (omega.x != 0 || omega.z != 0))
                com.juicyslew.moonstation14.MoonStation14.LOGGER.info("[shuttle-yaw-stress-cross-axis-first] tick={} omega={} tiltRad={} bottom={}", tick, omega, tilt, bottom);
            if (target == 5 && tick <= rampTicks) {
                double netStep = omega.y - previousOmega.y;
                // ShuttleMotionMath's damping feed-forward makes post-damping net delta=a*h;
                // the sampled post-actuation SDK rate is therefore bounded by a*h*(1+h*d).
                // Six native steps make h=.05 s per game tick; 1e-4 covers float/inertia sampling noise.
                double h = .05, drag = snapshot.configuredUniversalDrag();
                double maxNetStep = .25 * h * (1 + h * drag) + 1e-4;
                require(netStep <= maxNetStep,
                        "same-phase net yaw spin-up exceeds .25 rad/s^2 damping-adjusted bound " + maxNetStep
                                + " at tick " + tick + ": " + netStep + " drag=" + drag);
            }
            previousOmega = new Vector3d(omega);
            if (bottom <= padTop + 1.0) {
                com.juicyslew.moonstation14.MoonStation14.LOGGER.warn("[shuttle-yaw-stress-contact] firstBadSample={} bottom={} omega={} tiltRad={} yawRange=[{},{}]", tick, bottom, omega, tilt, minYaw, maxYaw);
                throw new GameTestAssertException("yaw stress lost pad-clearance premise at sample=" + tick + " bottom=" + bottom + " omega=" + omega + " tiltRad=" + tilt);
            }
            if (tilt > Math.toRadians(45)) throw new GameTestAssertException("gross capsize diagnostic >45deg sample=" + tick + " tiltRad=" + tilt + " omega=" + omega);
            if (tick < rampTicks + 100) { helper.runAfterDelay(1, () -> sample(tick + 1)); return; }
            require(samples == rampTicks + 101, "all ramp plus 101 steady-window tick samples observed");
            double tolerance = Math.max(.05, target * .10);
            require(steadyMin >= target - tolerance && steadyMax <= target + tolerance,
                    "native yaw outside explicit steady rate band +/-" + tolerance + " rad/s; target=" + target
                            + " steadyRange=[" + steadyMin + "," + steadyMax + "] mean=" + steadySum / 101
                            + " fullRange=[" + minYaw + "," + maxYaw + "] omegaXYZ=" + omega + " maxRollRate=" + maxRollRate);
            com.juicyslew.moonstation14.MoonStation14.LOGGER.info(
                    "[shuttle-yaw-stress] target={} achievedRange=[{},{}] maxRollRate={} maxPitchRate={} maxTiltRad={} minBottom={} samples={} owner={}",
                    target, steadyMin, steadyMax, maxRollRate, maxPitchRate, maxTilt, minBottom, samples, owner.getUniqueId());
            if (coastAfter) {
                var beforeStop = handle.getAngularVelocity(new Vector3d()).y;
                require(ShuttleFixtureMotionService.stop(level, owner), "high-rate yaw controller stops");
                coast(helper, level, owner, handle, plot, padTop, beforeStop, 0);
            } else {
                require(ShuttleFixtureMotionService.stop(level, owner), "yaw stress controller stops");
                plot.close(); helper.runAfterDelay(1, helper::succeed);
            }
        }
    }

    private static void coast(GameTestHelper helper, net.minecraft.server.level.ServerLevel level,
            dev.ryanhcode.sable.sublevel.ServerSubLevel owner, RigidBodyHandle handle, SableFixturePlot plot,
            double padTop, double initial, int tick) {
        require(tick < 10, "coast observation stays within ten ticks of restored gravity");
        helper.runAfterDelay(1, () -> {
            double current = handle.getAngularVelocity(new Vector3d()).y;
            require(worldBottom(level, owner) > padTop + 1,
                    "physical clearance remains during short gravity-resuming coast sample");
            if (tick == 9) {
                require(Math.abs(current) < Math.abs(initial) && Math.abs(current) > 0,
                        "inactive controller coasts under native damping, not an instantaneous angular reset: initial="
                                + initial + " current=" + current);
                plot.close(); helper.runAfterDelay(1, helper::succeed);
            } else coast(helper, level, owner, handle, plot, padTop, initial, tick + 1);
        });
    }

    private static void awaitClearance(GameTestHelper helper, net.minecraft.server.level.ServerLevel level,
            dev.ryanhcode.sable.sublevel.ServerSubLevel owner, RigidBodyHandle handle, double rate,
            int rampTicks, double padTop, SableFixturePlot plot, int waited, boolean coastAfter) {
        double bottom = worldBottom(level, owner);
        var snapshot = ShuttleFixtureMotionService.snapshot(level, owner);
        require(snapshot != null && snapshot.active(), "lift controller expired before physical clearance; bottom=" + bottom);
        if (bottom > padTop + 2.0) {
            require(ShuttleFixtureMotionService.start(level, owner,
                    new ShuttleMotionRequest(0, 0, 0, rate, 1200)), "bounded 1200-tick yaw retarget starts");
            // The 3-rad/s case uses 900 ticks (45 s), allowing native drag response to approach target;
            // .5 and 1-rad/s controls retain 280 ticks. Each then gets 101 steady samples.
            new YawObserver(helper, level, owner, handle, rate, rampTicks, padTop, plot, coastAfter).sample(0);
            return;
        }
        if (waited >= 200) throw new GameTestAssertException("physical lift failed to clear support pad within 200 ticks; bottom="
                + bottom + " padTop=" + padTop + " omega=" + handle.getAngularVelocity(new Vector3d()));
        helper.runAfterDelay(1, () -> awaitClearance(helper, level, owner, handle, rate, rampTicks, padTop, plot, waited + 1, coastAfter));
    }

    private static double worldBottom(net.minecraft.server.level.ServerLevel level,
            dev.ryanhcode.sable.sublevel.ServerSubLevel owner) {
        var box = owner.boundingBox().toMojang();
        return box.minY;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
