package com.juicyslew.moonstation14.ms14.shuttle.sable;

import com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.gametest.support.PowerFixtureReadiness;
import com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime;
import com.juicyslew.moonstation14.ms14.shuttle.fixture.ShuttleCabinFixture;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.joml.Vector3d;

import java.util.function.Consumer;

/** Controlled, test-only whole-server tick envelope comparison; not a Sable profiler or throughput test. */
@PrefixGameTestTemplate(false)
public final class ShuttleServerCostGameTests {
    private static final int PAD = 64, SURFACE_Y = -47, SUPPORT_LAYERS = 4;
    private ShuttleServerCostGameTests() { }

    @GameTestTags("shuttle-cost-diagnostic")
    @GameTest(templateNamespace="moonstation14", template="atmos_large_empty", timeoutTicks=2000)
    public static void singleCabinCostEnvelope(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        int x = 8000;
        BlockPos origin = new BlockPos(x, SURFACE_Y, 8000);
        var scene = SableFixtureScene.acquire(helper, origin, PAD, 8, PAD, SUPPORT_LAYERS);
        SableFixtureScene.awaitEmpty(helper, scene, 60, () -> {
            SableFixtureScene.authorStoneSupportPad(level, scene);
            var authored = SableFixturePlot.allocateAndAuthor(level,
                    new Vector3d(x + PAD / 2d, scene.supportSurfaceY(), 8000 + PAD / 2d), plot -> {
                        BlockPos storage = plot.storageOrigin();
                        plot.ensureStorageFootprint(storage, ShuttleCabinFixture.WIDTH,
                                ShuttleCabinFixture.HEIGHT, ShuttleCabinFixture.DEPTH);
                        return ShuttleCabinFixture.build(level, storage);
                    });
            SableFixturePlot plot = authored.plot();
            SableFixtureScene.trackPlot(helper, plot);
            var cabin = authored.result();
            var handle = RigidBodyHandle.of(plot.subLevel());
            BoundedGameTestWait.await(helper, 80, () -> "native cabin body registration", handle::isValid,
                    () -> PowerFixtureReadiness.await(helper, level, 500,
                            () -> {
                                var meter = PowerRuntime.airAlarmMeter(level,
                                        (com.juicyslew.moonstation14.ms14.atmos.piping.alarm.AirAlarmBlockEntity)
                                                level.getBlockEntity(cabin.at(ShuttleCabinFixture.MACHINE)));
                                return meter.known() && meter.deliveredWatts() == 100;
                             }, () -> "cabin air alarm expected known 100W; meter=" + PowerRuntime.airAlarmMeter(level,
                                     (com.juicyslew.moonstation14.ms14.atmos.piping.alarm.AirAlarmBlockEntity)
                                             level.getBlockEntity(cabin.at(ShuttleCabinFixture.MACHINE))),
                             () -> SableFixtureScene.awaitStationaryFrame(helper, plot, 100, () -> {
                                 try {
                                     SableFixtureScene.requireStoneSupportUnderProjectedFootprint(level, scene, plot,
                                             plot.storageOrigin(), ShuttleCabinFixture.WIDTH,
                                             ShuttleCabinFixture.DEPTH);
                                     requireNaturalRest(level, scene, plot);
                                    int actual = SubLevelPhysicsSystem.require(level).getConfig().substepsPerTick;
                                    String expected = System.getProperty("moonstation14.hullImpactExpectedSubsteps");
                                     if (!"2".equals(expected) && !"6".equals(expected) && !"10".equals(expected))
                                         throw new GameTestAssertException("cost diagnostic requires explicit expected SDK substeps 2, 6, or 10; got " + expected);
                                    HullImpactSubstepsExpectation.requireExpected(actual, expected);
                                    var probe = new HullTerrainImpactProbe(level, scene, plot, plot.storageOrigin());
                                    requireProbe(probe, scene);
                                    com.juicyslew.moonstation14.MoonStation14.LOGGER.info(
                                            "[shuttle-cost] stage=setup frame={} dimension={} pad={}x{}x{} hullPieces={} effectiveSubsteps={} expectedSubsteps={} warmupTicks=200 players={}",
                                            plot.engineId(), level.dimension().location(), PAD, SUPPORT_LAYERS, PAD,
                                            probe.hullPartCount(), actual, expected == null ? "none" : expected,
                                            level.players().size());
                                    helper.runAfterDelay(200, () -> {
                                        try {
                                             requireNaturalRest(level, scene, plot);
                                            var warmed = probe.sample();
                                            if (!warmed.sceneComplete() || !warmed.hullUnchanged())
                                                throw new GameTestAssertException("warmup probe invariant failed");
                                            new Measurement(helper, level, scene, plot, probe, handle, actual).parked();
                                        } catch (RuntimeException | AssertionError failure) {
                                            helper.testInfo.fail(failure);
                                        }
                                    });
                                } catch (RuntimeException | AssertionError failure) { helper.testInfo.fail(failure); }
                            })));
        });
    }

    private static void requireNaturalRest(ServerLevel level, SableFixtureScene.Region scene, SableFixturePlot plot) {
        var pose = plot.subLevel().logicalPose();
        var handle = RigidBodyHandle.of(plot.subLevel());
        var probe = new HullTerrainImpactProbe(level, scene, plot, plot.storageOrigin());
        var row = probe.sampleNativeOnly();
        if (!handle.isValid() || row.hullBounds() == null
                || Math.abs(row.hullBounds().minY - scene.supportSurfaceY()) > .10
                || handle.getLinearVelocity(new Vector3d()).lengthSquared() > .0025
                || handle.getAngularVelocity(new Vector3d()).lengthSquared() > .0001
                || pose.position().y < scene.supportSurfaceY() - .10)
            throw new GameTestAssertException("cabin not naturally settled on original support; frame="
                    + plot.engineId() + " pose=" + pose + " linear=" + handle.getLinearVelocity(new Vector3d())
                    + " angular=" + handle.getAngularVelocity(new Vector3d()));
    }

    private static void requireProbe(HullTerrainImpactProbe probe, SableFixtureScene.Region scene) {
        var row = probe.sample();
        if (!row.sceneComplete() || !row.hullUnchanged() || row.hullBounds() == null
                || !HullTerrainGeometry.nearSupportSurface(row.hullBounds(), scene.supportSurfaceY(), .10, .10)
                || row.linearVelocity().lengthSquared() > .0025 || row.angularVelocity().lengthSquared() > .0001) {
            var mismatch = probe.firstMismatch();
            throw new GameTestAssertException("cost workload strict support/authored-state probe failed; frame="
                    + probe.frameId() + " observation=" + row + " firstMismatch=" + mismatch);
        }
    }

    private static final class Measurement {
        private final GameTestHelper helper;
        private final ServerLevel level;
        private final SableFixtureScene.Region scene;
        private final SableFixturePlot plot;
        private final HullTerrainImpactProbe probe;
        private final RigidBodyHandle handle;
        private final int actual;
        private ServerTickCostRecorder recorder;
        private long phaseStart;
        private int launches;
        private boolean closed;
        private long nextPairId;
        private long openPairId = -1;
        private long openActualPreTick = -1;
        private long lastActualPreTick = -1;
        private long firstActualPreTick = -1, lastActualPostTick = -1;
        private Consumer<ServerTickEvent.Pre> pre;
        private Consumer<ServerTickEvent.Post> post;

        Measurement(GameTestHelper helper, ServerLevel level, SableFixtureScene.Region scene,
                    SableFixturePlot plot, HullTerrainImpactProbe probe, RigidBodyHandle handle, int actual) {
            this.helper=helper; this.level=level; this.scene=scene; this.plot=plot;
            this.probe=probe; this.handle=handle; this.actual=actual;
            BoundedGameTestWait.safeAtTickTime(helper, 1800, () -> {
                if (!closed) {
                    closed = true;
                    removeObservers();
                    helper.testInfo.fail(new GameTestAssertException("cost diagnostic exceeded explicit 1800-tick deadline"));
                }
            });
        }

        void parked() { begin("parked", 300, false); }
        void flight() { begin("flight", 600, true); }

        private void begin(String phase, int count, boolean moving) {
            if (closed) return;
            requireProbe(probe, scene);
            recorder = new ServerTickCostRecorder(count, System::nanoTime);
            recorder.arm();
            launches = 0;
            long registeredAt = level.getServer().getTickCount();
            pre = event -> {
                if (closed || event.getServer() != level.getServer()) return;
                long tick = level.getServer().getTickCount();
                if (tick <= registeredAt) return;
                try {
                    if (!handle.isValid()) throw new GameTestAssertException("native cabin resource became invalid during cost phase");
                    if (tick <= lastActualPreTick)
                        throw new GameTestAssertException("non-monotonic actual ServerTickEvent.Pre tick count: previous="
                                + lastActualPreTick + " current=" + tick);
                    lastActualPreTick = tick;
                    if (firstActualPreTick < 0) firstActualPreTick = tick;
                    if (phaseStart == 0) {
                        phaseStart = tick;
                    }
                    if (moving) {
                        int relative = Math.toIntExact(tick - phaseStart);
                        if (ServerTickCostRecorder.isLaunchTick(relative)) {
                             requireNaturalRest(level, scene, plot);
                            handle.addLinearAndAngularVelocity(new Vector3d(0,20,0), new Vector3d());
                            if (handle.getLinearVelocity(new Vector3d()).y < 18)
                                throw new GameTestAssertException("native +20 m/s additive nudge not admitted");
                            launches++;
                        }
                    }
                    if (openPairId >= 0) throw new GameTestAssertException("duplicate PRE before paired POST");
                    openPairId = ++nextPairId;
                    openActualPreTick = tick;
                    recorder.pre(openPairId);
                } catch (RuntimeException | AssertionError failure) { observerFailure(failure); }
            };
            post = event -> {
                if (closed || event.getServer() != level.getServer()) return;
                long tick = level.getServer().getTickCount();
                if (tick <= registeredAt) return;
                try {
                    if (openPairId < 0) {
                        if (observed == 0 && phaseStart == 0) return;
                        throw new GameTestAssertException("POST without an open PRE pair");
                    }
                    long pairId = openPairId;
                    long preTick = openActualPreTick;
                    if (tick != preTick)
                        throw new GameTestAssertException("actual ServerTickEvent PRE/POST tick counts differ: pre="
                                + preTick + " post=" + tick);
                    openPairId = -1;
                    openActualPreTick = -1;
                    lastActualPostTick = tick;
                    recorder.post(pairId);
                    observed++;
                    if (recorderCount() == count) {
                        removeObservers();
                        BoundedGameTestWait.safeDelay(helper, 1, () -> finishPhase(phase, moving));
                    }
                } catch (RuntimeException | AssertionError failure) { observerFailure(failure); }
            };
            attachCleanup();
            NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, pre);
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, post);
        }

        // Recorder intentionally exposes no mutable sample count; count advances via paired full ticks.
        private int recorderCount() { return observed; }
        private int observed;
        private void finishPhase(String phase, boolean moving) {
            try {
                if (moving && launches != 6) throw new GameTestAssertException("flight did not issue exactly six nudges: " + launches);
                requireProbe(probe, scene);
                var summary = recorder.finish(moving ? 600 : 300);
                com.juicyslew.moonstation14.MoonStation14.LOGGER.info(
                        "[shuttle-cost-result] phase={} metric=whole-server-tick-envelope count={} minMs={} meanMs={} p50Ms={} p95Ms={} p99Ms={} maxMs={} substeps={} launches={} firstActualPreTick={} lastActualPreTick={} lastActualPostTick={} frame={} dimension={}",
                        phase, summary.count(), summary.min()/1_000_000d, summary.mean()/1_000_000d,
                        summary.p50()/1_000_000d, summary.p95()/1_000_000d,
                        summary.p99()/1_000_000d, summary.max()/1_000_000d, actual,
                        launches, firstActualPreTick, lastActualPreTick, lastActualPostTick,
                        plot.engineId(), level.dimension().location());
                if (moving) { closed = true; helper.succeed(); }
                else {
                    observed = 0; phaseStart = 0; firstActualPreTick = -1;
                    lastActualPreTick = -1; lastActualPostTick = -1;
                    helper.runAfterDelay(1, this::flight);
                }
            } catch (RuntimeException | AssertionError failure) { failOnTestTick(failure); }
        }

        private void attachCleanup() {
            helper.testInfo.addListener(new GameTestListener() {
                private void cleanup() { removeObservers(); }
                @Override public void testStructureLoaded(GameTestInfo ignored) { }
                @Override public void testPassed(GameTestInfo ignored, GameTestRunner runner) { cleanup(); }
                @Override public void testFailed(GameTestInfo ignored, GameTestRunner runner) { cleanup(); }
                @Override public void testAddedForRerun(GameTestInfo oldTest, GameTestInfo next, GameTestRunner runner) { cleanup(); }
            });
        }

        private void observerFailure(Throwable failure) {
            closed = true;
            removeObservers();
            BoundedGameTestWait.safeDelay(helper, 1, () -> helper.testInfo.fail(failure));
        }
        private void failOnTestTick(Throwable failure) { closed = true; removeObservers(); helper.testInfo.fail(failure); }
        private void removeObservers() {
            if (pre != null) { NeoForge.EVENT_BUS.unregister(pre); pre = null; }
            if (post != null) { NeoForge.EVENT_BUS.unregister(post); post = null; }
        }
    }
}
