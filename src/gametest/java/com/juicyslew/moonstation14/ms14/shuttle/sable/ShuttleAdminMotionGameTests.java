package com.juicyslew.moonstation14.ms14.shuttle.sable;

import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.ms14.shuttle.fixture.ShuttleFixtureMotionService;
import com.juicyslew.moonstation14.ms14.shuttle.fixture.ShuttleMotionRequest;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Native-body regression checks for bounded fixture motion; no client or fake physics backend. */
@GameTestTags({"shuttle-admin-motion", "shuttle"})
@PrefixGameTestTemplate(false)
public final class ShuttleAdminMotionGameTests {
    private ShuttleAdminMotionGameTests() { }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 260)
    public static void nativeControllerLifecycleAndGravityResume(GameTestHelper helper) {
        var level = helper.getLevel();
        var region = SableFixtureScene.acquire(helper, new BlockPos(1880, 300, 1660), 24, 12, 24);
        // Fixture startup can take longer while chunks are asynchronously promoted; physics checks remain unchanged.
        SableFixtureScene.awaitEmpty(helper, region, 80, () -> {
            SableFixtureScene.authorStoneSupportPad(level, region);
            var authored = SableFixturePlot.allocateAndAuthor(level, new Vector3d(1888.5, 300, 1665.5), plot -> {
                var storage = plot.storageOrigin();
                plot.ensureStorageFootprint(storage,
                        com.juicyslew.moonstation14.ms14.shuttle.fixture.ShuttleCabinFixture.WIDTH,
                        com.juicyslew.moonstation14.ms14.shuttle.fixture.ShuttleCabinFixture.HEIGHT,
                        com.juicyslew.moonstation14.ms14.shuttle.fixture.ShuttleCabinFixture.DEPTH);
                return com.juicyslew.moonstation14.ms14.shuttle.fixture.ShuttleCabinFixture.build(level, storage, plot.engineId());
            });
            var plot = authored.plot();
            SableFixtureScene.trackPlot(helper, plot);
            var owner = plot.subLevel();
            ShuttleFixtureMotionService.registerFixture(owner);
            var handle = RigidBodyHandle.of(owner);
            require(handle != null && handle.isValid(), "fixture has a live native body");
            var service = ShuttleFixtureMotionService.snapshot(level, owner);
            HullImpactSubstepsExpectation.requireNormalSix(level, "admin-motion-native-lifecycle");
            require(service != null && service.sdkSubsteps() == 6, "native SDK uses six substeps");
            require(service.configuredGravity().y() < -1, "configured gravity remains enabled");
            double initialY = owner.logicalPose().position().y();
            require(ShuttleFixtureMotionService.start(level, owner, new ShuttleMotionRequest(0, .75, 0, 0, 150)), "lift target starts");
            sampleConsecutiveSubsteps(helper, level, owner, 80, () -> {
                helper.runAfterDelay(70, () -> {
                double airborneY = owner.logicalPose().position().y();
                require(airborneY > initialY + 1, "physical lift moves the cabin more than one meter above its support");
                require(handle.getLinearVelocity(new Vector3d()).y > 0, "native body is still moving upward before stop");
                var before = handle.getLinearVelocity(new Vector3d());
                require(ShuttleFixtureMotionService.stop(level, owner), "stop succeeds");
                var stopped = handle.getLinearVelocity(new Vector3d());
                require(before.distance(stopped) < 1e-8, "stop preserves exact native velocity synchronously");
                require(ShuttleFixtureMotionService.stop(level, owner), "repeated stop is idempotent");
                helper.runAfterDelay(4, () -> {
                    require(handle.getLinearVelocity(new Vector3d()).y < stopped.y - .1,
                            "gravity resumes after forces are disabled while cabin remains airborne");
                    require(owner.logicalPose().position().y() > initialY + .5, "gravity observation precedes support contact");
                    require(ShuttleFixtureMotionService.start(level, owner, new ShuttleMotionRequest(0, 0, 0, 0, 20)), "short restart succeeds");
                    helper.runAfterDelay(24, () -> {
                        var expired = ShuttleFixtureMotionService.snapshot(level, owner);
                        require(expired != null && !expired.active() && expired.remainingTicks() == 0,
                                "deadline expires without another active request");
                        plot.close();
                        helper.runAfterDelay(1, () -> {
                            require(ShuttleFixtureMotionService.snapshot(level, owner) == null,
                                    "removed exact owner has no snapshot or future callbacks");
                            helper.succeed();
                        });
                    });
                });
                });
            });
        });
    }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 650)
    @GameTestTags({"shuttle-horizontal-lift-cause"})
    public static void horizontalTargetHasNoVerticalDriftGroundedOrAirborne(GameTestHelper helper) {
        var level = helper.getLevel();
        var region = SableFixtureScene.acquire(helper, new BlockPos(1930, 300, 1660), 24, 12, 24);
        SableFixtureScene.awaitEmpty(helper, region, 40, () -> {
            SableFixtureScene.authorStoneSupportPad(level, region);
            var authored = SableFixturePlot.allocateAndAuthor(level, new Vector3d(1938.5, 300, 1665.5), plot -> {
                var storage = plot.storageOrigin();
                plot.ensureStorageFootprint(storage,
                        com.juicyslew.moonstation14.ms14.shuttle.fixture.ShuttleCabinFixture.WIDTH,
                        com.juicyslew.moonstation14.ms14.shuttle.fixture.ShuttleCabinFixture.HEIGHT,
                        com.juicyslew.moonstation14.ms14.shuttle.fixture.ShuttleCabinFixture.DEPTH);
                return com.juicyslew.moonstation14.ms14.shuttle.fixture.ShuttleCabinFixture.build(level, storage, plot.engineId());
            });
            var plot = authored.plot();
            SableFixtureScene.trackPlot(helper, plot);
            var owner = plot.subLevel();
            ShuttleFixtureMotionService.registerFixture(owner);
            var handle = RigidBodyHandle.of(owner);
            var initial = ShuttleFixtureMotionService.snapshot(level, owner);
            HullImpactSubstepsExpectation.requireNormalSix(level, "admin-motion-vertical-drift");
            require(handle != null && handle.isValid(), "drift fixture has live native body");
            require(initial != null && initial.sdkSubsteps() == 6, "drift fixture uses normal six SDK substeps");
            require(initial.configuredGravity().y() < -1 && Double.isFinite(initial.configuredUniversalDrag()),
                    "fixture retains configured gravity and universal drag");
            helper.runAfterDelay(30, () -> {
                // Capture physical COM/anchor coordinates as primitives, never mutable SDK pose references.
                double groundedStartY = nativeComY(level, owner);
                logDriftSample("startup", level, owner, handle, initial, groundedStartY, Double.NaN);
                var firstCommandObserver = new FirstCommandDriftObserver(helper, level, owner, handle, 18);
                firstCommandObserver.start();
                require(ShuttleFixtureMotionService.start(level, owner, new ShuttleMotionRequest(5, 0, 0, 0, 240)),
                        "horizontal target accepted");
                firstCommandObserver.whenComplete(() -> {
                    require(ShuttleFixtureMotionService.start(level, owner, new ShuttleMotionRequest(.5, 0, 0, 0, 240)),
                            "bounded horizontal target retarget accepted after first 18 paired substeps");
                    helper.runAfterDelay(10, () -> {
                    double settledStartY = nativeComY(level, owner);
                    logDriftSample("grounded-settled-start", level, owner, handle, initial, settledStartY, Double.NaN);
                    helper.runAfterDelay(60, () -> {
                        double settledEndY = nativeComY(level, owner);
                        double groundedVy = handle.getLinearVelocity(new Vector3d()).y;
                        logDriftSample("grounded-settled-end-60t", level, owner, handle, initial, settledEndY,
                                (settledEndY - settledStartY) / 3.0);
                        require(Math.abs(settledEndY - settledStartY) < .20 && Math.abs(groundedVy) < .15,
                                "grounded post-startup vertical drift bounded: startY=" + settledStartY
                                        + " endY=" + settledEndY + " vy=" + groundedVy + " initialY=" + groundedStartY);
                        require(ShuttleFixtureMotionService.start(level, owner, new ShuttleMotionRequest(0, .75, 0, 0, 120)),
                                "bounded physical lift target accepted to clear pad");
                        helper.runAfterDelay(90, () -> {
                            double airborneY = nativeComY(level, owner);
                            require(airborneY > settledEndY + 1, "fixture is physically clear of support pad");
                            require(ShuttleFixtureMotionService.start(level, owner, new ShuttleMotionRequest(.5, 0, 0, 0, 240)),
                                    "airborne horizontal zero-vertical target accepted");
                            helper.runAfterDelay(80, () -> {
                                double airStartY = nativeComY(level, owner);
                                logDriftSample("airborne-zeroY-settled-start", level, owner, handle, initial, airStartY, Double.NaN);
                                double[] windowRates = new double[3];
                                sampleAirDriftWindow(helper, level, owner, handle, initial, airStartY, windowRates, 1, () -> {
                                    double airEndY = nativeComY(level, owner);
                                    double airVy = handle.getLinearVelocity(new Vector3d()).y;
                                    logDriftSample("airborne-zeroY-settled-end-60t", level, owner, handle, initial, airEndY,
                                            (airEndY - airStartY) / 3.0);
                                    require(Math.abs(airEndY - airStartY) < .25 && Math.abs(airVy) < .15,
                                            "airborne zero-Y target does not climb/fall persistently: startY=" + airStartY
                                                    + " endY=" + airEndY + " vy=" + airVy + " airborneOriginY=" + airborneY
                                                    + " gravity=" + initial.configuredGravity()
                                                    + " drag=" + initial.configuredUniversalDrag());
                                     // At Y≈306 one pose-coordinate ULP is about 0.0000305 m; 0.0005 m/s
                                     // allows >16 ULPs of quantization per one-second window but rejects the
                                     // previously observed ~0.002 m/s native upward drift.
                                     require(java.util.Arrays.stream(windowRates).allMatch(rate -> Math.abs(rate) <= .0005),
                                             "settled zero-Y target bounds each measured native COM rate to .0005 m/s (quantization-tolerant): "
                                                     + java.util.Arrays.toString(windowRates));
                                    var observer = new SubstepDriftObserver(helper, level, owner, 18, () -> {
                                        require(ShuttleFixtureMotionService.stop(level, owner), "drift controller stops");
                                        plot.close();
                                        helper.runAfterDelay(1, helper::succeed);
                                    });
                                    observer.start();
                                });
                            });
                        });
                    });
                    });
                });
            });
        });
    }

    /** Captures the initial command's real native PRE/POST pairs; HIGHEST POST is before the NORMAL controller. */
    private static final class FirstCommandDriftObserver {
        private record State(double comY, double vy, double dt, double gravityY, double mass,
                             int substeps, double targetX, double targetY, long tick) { }
        private final GameTestHelper helper;
        private final net.minecraft.server.level.ServerLevel level;
        private final dev.ryanhcode.sable.sublevel.ServerSubLevel owner;
        private final RigidBodyHandle handle;
        private final int limit;
        private final List<State> pre = new ArrayList<>(), post = new ArrayList<>();
        private final double startY, startVy;
        private Consumer<dev.ryanhcode.sable.neoforge.event.ForgeSablePrePhysicsTickEvent> preListener;
        private Consumer<dev.ryanhcode.sable.neoforge.event.ForgeSablePostPhysicsTickEvent> postListener;
        private Runnable complete;
        private boolean closed;

        FirstCommandDriftObserver(GameTestHelper helper, net.minecraft.server.level.ServerLevel level,
                dev.ryanhcode.sable.sublevel.ServerSubLevel owner, RigidBodyHandle handle, int limit) {
            this.helper = helper; this.level = level; this.owner = owner; this.handle = handle; this.limit = limit;
            this.startY = nativeComY(level, owner);
            this.startVy = handle.getLinearVelocity(new Vector3d()).y;
            helper.testInfo.addListener(new net.minecraft.gametest.framework.GameTestListener() {
                private void cleanup() { close(); }
                @Override public void testStructureLoaded(net.minecraft.gametest.framework.GameTestInfo ignored) { }
                @Override public void testPassed(net.minecraft.gametest.framework.GameTestInfo ignored,
                        net.minecraft.gametest.framework.GameTestRunner runner) { cleanup(); }
                @Override public void testFailed(net.minecraft.gametest.framework.GameTestInfo ignored,
                        net.minecraft.gametest.framework.GameTestRunner runner) { cleanup(); }
                @Override public void testAddedForRerun(net.minecraft.gametest.framework.GameTestInfo oldTest,
                        net.minecraft.gametest.framework.GameTestInfo next,
                        net.minecraft.gametest.framework.GameTestRunner runner) { cleanup(); }
            });
        }

        void start() {
            preListener = this::onPre; postListener = this::onPost;
            NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, preListener);
            NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, postListener);
        }

        void whenComplete(Runnable callback) { complete = callback; }

        private State capture(double dt) {
            var snapshot = ShuttleFixtureMotionService.snapshot(level, owner);
            var mass = owner.getMassTracker().getMass();
            double gravityY = dev.ryanhcode.sable.physics.config.dimension_physics.DimensionPhysicsData.getGravity(level).y();
            double y = nativeComY(level, owner), vy = handle.getLinearVelocity(new Vector3d()).y;
            if (snapshot == null || !snapshot.active() || snapshot.sdkSubsteps() != 6
                    || !Double.isFinite(dt) || dt <= 0 || !Double.isFinite(mass) || mass <= 0
                    || !Double.isFinite(gravityY) || !Double.isFinite(y) || !Double.isFinite(vy))
                throw new IllegalStateException("invalid first-command paired native sample");
            return new State(y, vy, dt, gravityY, mass, snapshot.sdkSubsteps(), snapshot.request().targetX(),
                    snapshot.request().targetY(), level.getGameTime());
        }

        private boolean accepts(dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem physics) {
            if (closed || physics.getLevel() != level) return false;
            if (owner.isRemoved() || owner.getLevel() != level
                    || dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(level)
                    .getSubLevel(owner.getUniqueId()) != owner) { close(); return false; }
            return true;
        }

        private void onPre(dev.ryanhcode.sable.neoforge.event.ForgeSablePrePhysicsTickEvent event) {
            if (!accepts(event.getPhysicsSystem())) return;
            try {
                if (pre.size() != post.size()) throw new IllegalStateException("unpaired PRE event before next native step");
                var sample = capture(event.getTimeStep());
                if (sample.targetX() != 5 || sample.targetY() != 0)
                    throw new IllegalStateException("first horizontal command target was not exactly (5, 0)");
                pre.add(sample);
            } catch (RuntimeException ex) { fail(ex); }
        }

        private void onPost(dev.ryanhcode.sable.neoforge.event.ForgeSablePostPhysicsTickEvent event) {
            if (!accepts(event.getPhysicsSystem()) || pre.size() == post.size()) return;
            try {
                var sample = capture(event.getTimeStep());
                var before = pre.get(pre.size() - 1);
                if (sample.targetX() != 5 || sample.targetY() != 0 || Math.abs(sample.dt() - before.dt()) > 1e-9
                        || sample.substeps() != 6 || sample.tick() != before.tick())
                    throw new IllegalStateException("PRE/POST native pair identity/cadence mismatch");
                post.add(sample);
                com.juicyslew.moonstation14.MoonStation14.LOGGER.info(
                         "[shuttle-admin-first-command-pair] pair={}/{} owner={} tick={} comY={} preVy={} postVy={} dt={} gravityY={} mass={} substeps={} targetX={} targetY={} startY={} startVy={}",
                         post.size(), limit, owner.getUniqueId(), sample.tick(), sample.comY(), before.vy(), sample.vy(),
                         sample.dt(), sample.gravityY(), sample.mass(), sample.substeps(), sample.targetX(), sample.targetY(), startY, startVy);
                if (post.size() == limit) finish();
            } catch (RuntimeException ex) { fail(ex); }
        }

        private void finish() {
            close();
            double rise = post.get(post.size() - 1).comY() - startY;
            double maxPostVy = post.stream().mapToDouble(State::vy).max().orElse(Double.NaN);
            require(pre.size() == limit && post.size() == limit, "all first-command native substeps have paired PRE/POST samples");
            require(Math.abs(startVy) <= .02, "cold grounded first-command SDK Vy near zero: " + startVy);
            // 0.02 m bounds early COM rise to contact-scale tolerance, far below the 0.08 m/s one-gravity-step impulse.
            require(rise <= .02, "first 18 horizontal-target substeps have no material upward COM travel: rise=" + rise);
            // HIGHEST POST runs before the NORMAL controller's actuation, so this permits one gravity-step velocity.
            require(maxPostVy <= .1, "pre-controller POST vertical rate excludes an upward actuation pulse: maxVy=" + maxPostVy);
            require(pre.stream().allMatch(s -> s.substeps() == 6 && s.targetX() == 5 && s.targetY() == 0)
                            && post.stream().allMatch(s -> s.substeps() == 6 && s.targetX() == 5 && s.targetY() == 0),
                    "all paired first-command samples retain six substeps and exact target (5, 0)");
            if (complete != null) complete.run();
        }

        private void fail(RuntimeException ex) {
            close(); helper.testInfo.fail(new GameTestAssertException("first-command drift observer failed: " + ex));
        }

        private void close() {
            if (closed) return; closed = true;
            if (preListener != null) { NeoForge.EVENT_BUS.unregister(preListener); preListener = null; }
            if (postListener != null) { NeoForge.EVENT_BUS.unregister(postListener); postListener = null; }
        }
    }

    /** Test-scoped pre-controller observer; bounded to one exact fixture and 18 native substeps. */
    private static final class SubstepDriftObserver {
        private final net.minecraft.server.level.ServerLevel level;
        private final dev.ryanhcode.sable.sublevel.ServerSubLevel owner;
        private final GameTestHelper helper;
        private final int limit;
        private final Runnable complete;
        private final List<DriftSample> samples = new ArrayList<>();
        private Consumer<dev.ryanhcode.sable.neoforge.event.ForgeSablePrePhysicsTickEvent> listener;
        private boolean closed;

        private record DriftSample(double poseX, double poseY, double poseZ, double velocityY, double dt,
                                   double gravityY, double mass, double drag, int substeps, double targetY,
                                   double impulseY, double logicalY, double transportX, double transportY,
                                   double transportZ, double previousProductionTransportY) { }
        private Vector3d previousNativeCom;
        private double previousStepSeconds;
        private int callbacksSeen;

        SubstepDriftObserver(GameTestHelper helper, net.minecraft.server.level.ServerLevel level,
                             dev.ryanhcode.sable.sublevel.ServerSubLevel owner, int limit, Runnable complete) {
            this.level = level;
            this.owner = owner;
            this.helper = helper;
            this.limit = limit;
            this.complete = complete;
            helper.testInfo.addListener(new net.minecraft.gametest.framework.GameTestListener() {
                private void cleanup() { close(); }
                @Override public void testStructureLoaded(net.minecraft.gametest.framework.GameTestInfo ignored) { }
                @Override public void testPassed(net.minecraft.gametest.framework.GameTestInfo ignored,
                        net.minecraft.gametest.framework.GameTestRunner runner) { cleanup(); }
                @Override public void testFailed(net.minecraft.gametest.framework.GameTestInfo ignored,
                        net.minecraft.gametest.framework.GameTestRunner runner) { cleanup(); }
                @Override public void testAddedForRerun(net.minecraft.gametest.framework.GameTestInfo oldTest,
                        net.minecraft.gametest.framework.GameTestInfo next,
                        net.minecraft.gametest.framework.GameTestRunner runner) { cleanup(); }
            });
        }

        void start() {
            listener = this::onPre;
            NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, listener);
        }

        private void onPre(dev.ryanhcode.sable.neoforge.event.ForgeSablePrePhysicsTickEvent event) {
            if (closed || event.getPhysicsSystem().getLevel() != level) return;
            if (owner.isRemoved() || owner.getLevel() != level
                    || dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(level)
                    .getSubLevel(owner.getUniqueId()) != owner) {
                close();
                return;
            }
            try {
                var physics = event.getPhysicsSystem();
                var handle = RigidBodyHandle.of(owner);
                if (handle == null || !handle.isValid())
                    throw new IllegalStateException("native handle unavailable during bounded pre-physics sampling");
                var snapshot = ShuttleFixtureMotionService.snapshot(level, owner);
                var massTracker = owner.getMassTracker();
                double dt = event.getTimeStep();
                double mass = massTracker.getMass();
                var gravity = new Vector3d(dev.ryanhcode.sable.physics.config.dimension_physics.DimensionPhysicsData.getGravity(level));
                var pose = new dev.ryanhcode.sable.companion.math.Pose3d();
                physics.getPipeline().readPose(owner, pose);
                Vector3d nativeCom = pose.position().get(new Vector3d());
                double nativeY = nativeCom.y;
                double logicalY = owner.logicalPose().position().y();
                var velocity = handle.getLinearVelocity(new Vector3d());
                var omega = handle.getAngularVelocity(new Vector3d());
                if (snapshot == null || !snapshot.active()
                        || !Double.isFinite(dt) || dt <= 0 || !Double.isFinite(mass) || mass <= 0
                        || !finite(nativeCom) || !Double.isFinite(nativeY) || !Double.isFinite(logicalY)
                        || !Double.isFinite(velocity.y) || !Double.isFinite(gravity.y)
                        || snapshot.transportVelocity() == null
                        || !Double.isFinite(snapshot.transportVelocity().y()))
                    throw new IllegalStateException("invalid native state during bounded pre-physics sampling");
                callbacksSeen++;
                if (previousNativeCom == null) {
                    previousNativeCom = new Vector3d(nativeCom);
                    previousStepSeconds = dt;
                    com.juicyslew.moonstation14.MoonStation14.LOGGER.info(
                            "[shuttle-admin-substep-drift] phase=seed tick={} dt={} nativeCom=({}, {}, {}) sdkVy={} previousProductionTransportY={} owner={}",
                            level.getGameTime(), dt, nativeCom.x, nativeCom.y, nativeCom.z, velocity.y,
                            snapshot.transportVelocity().y(), owner.getUniqueId());
                    return;
                }
                var measuredTransport = com.juicyslew.moonstation14.ms14.shuttle.fixture.ShuttleMotionMath.measureTransportVelocity(
                        previousNativeCom, previousStepSeconds, nativeCom, velocity);
                if (!finite(measuredTransport))
                    throw new IllegalStateException("invalid observer COM-delta transport measurement");
                var impulses = com.juicyslew.moonstation14.ms14.shuttle.fixture.ShuttleMotionMath.compute(
                        snapshot.request(), measuredTransport, omega, pose.orientation(), mass, massTracker.getInertiaTensor(), gravity, dt);
                if (impulses == null) throw new IllegalStateException("existing motion math rejected native sample");
                var localImpulse = new Vector3d(impulses.linearLocal().x(), impulses.linearLocal().y(), impulses.linearLocal().z());
                pose.orientation().transform(localImpulse);
                double impulseY = localImpulse.y;
                DriftSample current = new DriftSample(nativeCom.x, nativeY, nativeCom.z, velocity.y, dt, gravity.y, mass,
                        snapshot.configuredUniversalDrag(), snapshot.sdkSubsteps(), snapshot.request().targetY(), impulseY,
                        logicalY, measuredTransport.x, measuredTransport.y, measuredTransport.z,
                        snapshot.transportVelocity().y());
                double poseRate = Double.NaN, measuredDeltaV = Double.NaN, predictedDeltaV = Double.NaN;
                if (!samples.isEmpty()) {
                    DriftSample previous = samples.get(samples.size() - 1);
                    poseRate = (current.poseY() - previous.poseY()) / previous.dt();
                    measuredDeltaV = current.velocityY() - previous.velocityY();
                    predictedDeltaV = previous.impulseY() / previous.mass() + previous.gravityY() * previous.dt();
                }
                samples.add(current);
                previousNativeCom = new Vector3d(nativeCom);
                previousStepSeconds = dt;
                com.juicyslew.moonstation14.MoonStation14.LOGGER.info(
                        "[shuttle-admin-substep-drift] index={}/{} callback={}/{} tick={} dt={} nativeCom=({}, {}, {}) logicalPoseY={} nativeVy={} currentComDeltaTransport=({}, {}, {}) previousProductionTransportY={} targetY={} gravityY={} universalDrag={} substeps={} mass={} expectedImpulseWorldY={} previousStepPoseRate={} measuredDeltaVy={} priorImpulseGravityDeltaVy={} owner={}",
                        samples.size(), limit, callbacksSeen, limit + 1, level.getGameTime(), dt,
                        current.poseX(), current.poseY(), current.poseZ(), logicalY, velocity.y,
                        current.transportX(), current.transportY(), current.transportZ(), current.previousProductionTransportY(),
                        current.targetY(), current.gravityY(), current.drag(), current.substeps(), mass, impulseY,
                        poseRate, measuredDeltaV, predictedDeltaV,
                        owner.getUniqueId());
                if (samples.size() == limit) {
                    close();
                    if (callbacksSeen != limit + 1)
                        throw new IllegalStateException("observer did not capture one seed plus exact bounded sample count");
                    if (samples.stream().anyMatch(s -> !finite(s)))
                        throw new IllegalStateException("non-finite bounded pre-physics sample");
                    for (int i = 1; i < samples.size(); i++)
                        if (Math.abs(samples.get(i).dt() - samples.get(0).dt()) > 1e-9)
                            throw new IllegalStateException("pre-physics substep dt changed within sample window");
                    if (samples.stream().anyMatch(s -> s.substeps() != 6))
                        throw new IllegalStateException("pre-physics cadence ceased to be normal six-substep configuration");
                    if (samples.get(0).dt() < .001 || samples.get(0).dt() > .02)
                        throw new IllegalStateException("unexpected pre-physics timestep " + samples.get(0).dt());
                    complete.run();
                }
            } catch (RuntimeException failure) {
                close();
                helper.testInfo.fail(new GameTestAssertException("bounded substep drift observer failed: " + failure));
            }
        }

        private static boolean finite(DriftSample s) {
            return Double.isFinite(s.poseX()) && Double.isFinite(s.poseY()) && Double.isFinite(s.poseZ())
                    && Double.isFinite(s.velocityY()) && Double.isFinite(s.dt())
                    && Double.isFinite(s.gravityY()) && Double.isFinite(s.mass())
                    && Double.isFinite(s.drag()) && Double.isFinite(s.targetY())
                    && Double.isFinite(s.impulseY()) && Double.isFinite(s.logicalY())
                    && Double.isFinite(s.transportX()) && Double.isFinite(s.transportY())
                    && Double.isFinite(s.transportZ()) && Double.isFinite(s.previousProductionTransportY());
        }

        private static boolean finite(Vector3dc vector) {
            return vector != null && Double.isFinite(vector.x()) && Double.isFinite(vector.y()) && Double.isFinite(vector.z());
        }

        private void close() {
            if (closed) return;
            closed = true;
            if (listener != null) {
                NeoForge.EVENT_BUS.unregister(listener);
                listener = null;
            }
        }
    }

    private static void sampleAirDriftWindow(GameTestHelper helper, net.minecraft.server.level.ServerLevel level,
            dev.ryanhcode.sable.sublevel.ServerSubLevel owner, RigidBodyHandle handle,
            ShuttleFixtureMotionService.Snapshot configured, double previousY, double[] rates, int window, Runnable done) {
        helper.runAfterDelay(20, () -> {
            double y = nativeComY(level, owner);
            double rate = (y - previousY); // A 20-tick window is one second.
            rates[window - 1] = rate;
            logDriftSample("airborne-zeroY-window-" + window, level, owner, handle, configured, y, rate);
            if (window == 3) {
                done.run();
            } else {
                sampleAirDriftWindow(helper, level, owner, handle, configured, y, rates, window + 1, done);
            }
        });
    }

    private static double nativeComY(net.minecraft.server.level.ServerLevel level,
            dev.ryanhcode.sable.sublevel.ServerSubLevel owner) {
        var physics = dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem.get(level);
        if (physics == null) throw new GameTestAssertException("native sublevel physics unavailable for COM sample");
        var pose = physics.getPipeline().readPose(owner, new dev.ryanhcode.sable.companion.math.Pose3d());
        return pose.position().get(new Vector3d()).y;
    }

    private static void logDriftSample(String phase, net.minecraft.server.level.ServerLevel level,
            dev.ryanhcode.sable.sublevel.ServerSubLevel owner, RigidBodyHandle handle,
            ShuttleFixtureMotionService.Snapshot configured, double physicalAnchorY, double deltaYPerWindow) {
        double nativeVy = handle.getLinearVelocity(new Vector3d()).y;
        com.juicyslew.moonstation14.MoonStation14.LOGGER.info(
                "[shuttle-admin-motion-drift-receipt] phase={} gameTick={} owner={} physicalAnchorY={} nativeVy={} deltaYPerSecond={} gravityY={} universalDrag={} sdkSubsteps={}",
                phase, level.getGameTime(), owner.getUniqueId(), physicalAnchorY, nativeVy, deltaYPerWindow,
                configured.configuredGravity().y(), configured.configuredUniversalDrag(), configured.sdkSubsteps());
    }

    private static void sampleConsecutiveSubsteps(GameTestHelper helper, net.minecraft.server.level.ServerLevel level,
                                                   dev.ryanhcode.sable.sublevel.ServerSubLevel owner,
                                                   int ticksLeft, Runnable done) {
        var first = ShuttleFixtureMotionService.snapshot(level, owner);
        helper.runAfterDelay(1, () -> {
            var second = ShuttleFixtureMotionService.snapshot(level, owner);
            if (first != null && second != null && second.appliedSubsteps() - first.appliedSubsteps() == 6) {
                done.run();
                return;
            }
            if (ticksLeft <= 0) throw new GameTestAssertException("consecutive game ticks did not apply exactly six native substeps");
            sampleConsecutiveSubsteps(helper, level, owner, ticksLeft - 1, done);
        });
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
