package com.juicyslew.moonstation14.ms14.shuttle.sable;

import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.ms14.shuttle.fixture.ShuttleCabinFixture;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.joml.Quaterniond;
import org.joml.Vector3d;

/** Diagnostic-only instantaneous native inertia/frame contract probe. */
@GameTestTags({"shuttle-angular-response", "shuttle"})
@PrefixGameTestTemplate(false)
public final class NativeAngularResponseGameTests {
    private NativeAngularResponseGameTests() { }

    @GameTest(templateNamespace="moonstation14", template="empty", timeoutTicks=900)
    public static void uprightNativeAngularResponse(GameTestHelper helper) { run(helper, 8600, false); }

    @GameTest(templateNamespace="moonstation14", template="empty", timeoutTicks=900)
    public static void tiltedNativeAngularResponse(GameTestHelper helper) { run(helper, 8800, true); }

    private static void run(GameTestHelper helper, int x, boolean tilted) {
        var level = helper.getLevel();
        var region = SableFixtureScene.acquire(helper, new BlockPos(x, 300, 6000), 24, 12, 24);
        SableFixtureScene.awaitEmpty(helper, region, 80, () -> {
            SableFixtureScene.authorStoneSupportPad(level, region);
            var authored = SableFixturePlot.allocateAndAuthor(level,
                    new Vector3d(x + 8.5, 300, 6005.5), plot -> {
                        var storage = plot.storageOrigin();
                        plot.ensureStorageFootprint(storage, ShuttleCabinFixture.WIDTH,
                                ShuttleCabinFixture.HEIGHT, ShuttleCabinFixture.DEPTH);
                        return ShuttleCabinFixture.build(level, storage, plot.engineId());
                    });
            var plot = authored.plot();
            SableFixtureScene.trackPlot(helper, plot);
            var owner = plot.subLevel();
            var handle = RigidBodyHandle.of(owner);
            require(handle != null && handle.isValid(), "probe uses a live authored native body");
            SableFixtureScene.awaitStationaryFrame(helper, plot, 200, () -> {
                var physics = dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem.get(level);
                if (tilted) {
                    var authoredRotation = new Quaterniond().rotationXYZ(Math.toRadians(37), 0, Math.toRadians(23));
                    owner.logicalPose().orientation().set(authoredRotation);
                    handle.teleport(owner.logicalPose().position(), authoredRotation);
                    owner.updateLastPose();
                    owner.updateBoundingBox();
                    // Pinned Sable ad8 teleportObject -> Rapier set_position(pose, true); pinned Rapier
                    // set_position immediately refreshes world mass properties when pose changes. Probe now:
                    // an intervening settle could alter this authored initial-condition orientation.
                    probeCurrentBody(helper, physics, owner, handle, true, authoredRotation);
                } else {
                    probeCurrentBody(helper, physics, owner, handle, false, null);
                }
            });
        });
    }

    private static void probeCurrentBody(GameTestHelper helper,
            dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem physics,
            dev.ryanhcode.sable.sublevel.ServerSubLevel owner, RigidBodyHandle handle,
            boolean tilted, Quaterniond authoredRotation) {
        var mass = owner.getMassTracker();
        require(mass != null && Double.isFinite(mass.getMass()) && mass.getMass() > 0,
                "actual native body has installed positive merged mass");
        var inertia = mass.getInertiaTensor();
        require(inertia != null, "actual merged local inertia tensor installed");
        var pose = physics.getPipeline().readPose(owner, new dev.ryanhcode.sable.companion.math.Pose3d());
        if (authoredRotation != null) {
            require(Math.abs(pose.orientation().dot(authoredRotation)) > .999999,
                    "native readPose reflects authored compound tilt; q=" + pose.orientation());
            var up = pose.orientation().transform(new Vector3d(0, 1, 0));
            require(up.angle(new Vector3d(0, 1, 0)) > .4,
                    "native initial condition remains meaningfully tilted; up=" + up);
        }
        probe(helper, handle, pose.orientation(), inertia, mass.getMass(), tilted);
    }

    private static void probe(GameTestHelper helper, RigidBodyHandle handle, Quaterniond orientation,
                              org.joml.Matrix3dc inertia, double mass, boolean tilted) {
        // Pinned default JNI impulse wakes the body; getters and calls are synchronous with no integration between.
        var result=NativeAngularImpulseProbe.run(handle,inertia,orientation);
        if (!result.passed()) throw new GameTestAssertException("native angular impulse calibration failed tilted=" + tilted
                + " orientation=" + orientation + " mass=" + mass + " mergedLocalInertia=" + inertia + " axes="+result.details());
        helper.succeed();
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
