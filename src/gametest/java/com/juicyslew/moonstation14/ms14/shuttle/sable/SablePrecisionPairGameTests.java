package com.juicyslew.moonstation14.ms14.shuttle.sable;

import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.ms14.doors.AirlockBlock;
import com.juicyslew.moonstation14.ms14.doors.AirlockBlockEntity;
import com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery;
import com.juicyslew.moonstation14.ms14.hands.network.LifecycleHandActorFixture;
import com.juicyslew.moonstation14.ms14.shuttle.sable.NativeFrameInterval;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.joml.Vector3d;
import org.joml.Quaterniond;
import dev.ryanhcode.sable.companion.math.Pose3d;

import java.util.Locale;
import java.util.UUID;
import java.util.function.Consumer;

/** Controlled precision diagnostic: identical small authored bodies near and far in one disposable dimension. */
@GameTestTags({"sable-precision", "shuttle-frame-transition", "shuttle-authority", "shuttle-client-frame"})
@PrefixGameTestTemplate(false)
public final class SablePrecisionPairGameTests {
    private static final int WIDTH = 2, HEIGHT = 3, DEPTH = 2;

    private SablePrecisionPairGameTests() { }

    @GameTest(templateNamespace = "moonstation14", template = "atmos_large_empty", batch = "sable_precision", timeoutTicks = 100)
    public static void pairedOriginAndMillionBlockPoseDiagnostics(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        int sampleY = level.getMaxBuildHeight() - 20;
        BlockPos nearOrigin = new BlockPos(0, sampleY, 0);
        BlockPos fixtureAnchor = helper.absolutePos(new BlockPos(2, 0, 2));
        BlockPos farOrigin = new BlockPos(fixtureAnchor.getX(), sampleY, fixtureAnchor.getZ());
        SableFixtureScene.Region nearScene = SableFixtureScene.acquire(helper, nearOrigin, WIDTH, HEIGHT, DEPTH);
        SableFixtureScene.Region farScene = SableFixtureScene.acquire(helper, farOrigin, WIDTH, HEIGHT, DEPTH);
        SableFixtureScene.awaitEmpty(helper, nearScene, 40,
                () -> SableFixtureScene.awaitEmpty(helper, farScene, 40,
                        () -> runPair(helper, level, nearOrigin, farOrigin)));
    }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 10)
    public static void sdkPoseCopiesAndPerBodyIntervals(GameTestHelper helper) {
        Pose3d base = new Pose3d(new Vector3d(1, 2, 3), new Quaterniond(), new Vector3d(.5, .25, -.75), new Vector3d(1));
        var captured = NativeFrameInterval.PosePair.capture(base, base);
        var equalCopy = NativeFrameInterval.PosePair.capture(new Pose3d(base), new Pose3d(base));
        require(captured.equals(equalCopy), "independent SDK pose copies bake to equal immutable values");
        java.util.List<Consumer<Pose3d>> mutations = java.util.List.of(p -> p.position().x += 1,
                p -> p.orientation().y += .1, p -> p.rotationPoint().z += 1, p -> p.scale().x += .25);
        String[] labels = {"translation", "rotation", "rotation point", "scale"};
        for (int i = 0; i < mutations.size(); i++) {
            Pose3d changed = new Pose3d(base); mutations.get(i).accept(changed);
            require(!NativeFrameInterval.PosePair.capture(changed, base).equals(captured), labels[i] + " changes baked fingerprint");
            require(captured.equals(equalCopy), "SDK source mutation cannot change already-captured interval value");
        }
        Pose3d lastChanged = new Pose3d(base); lastChanged.position().z += 1;
        require(!NativeFrameInterval.PosePair.capture(lastChanged, base).equals(captured),
                "lastPose mutation changes interval even while logical pose is fixed");
        NativeFrameInterval oneBody = new NativeFrameInterval(), anotherBody = new NativeFrameInterval();
        UUID frame = UUID.randomUUID();
        require(oneBody.consumeIfChanged(1, frame, captured), "first interval is consumed");
        require(!oneBody.consumeIfChanged(1, frame, equalCopy), "replay intents in an interval do not consume twice");
        require(anotherBody.consumeIfChanged(1, frame, equalCopy), "other body has independent policy state");
        require(oneBody.consumeIfChanged(1, UUID.randomUUID(), captured), "frame identity resets interval");
        require(oneBody.consumeIfChanged(2, frame, captured), "body epoch resets interval");
        oneBody.reset(); require(oneBody.consumeIfChanged(2, frame, captured), "reset permits new interval");
        helper.succeed();
    }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 120)
    public static void realWorldHumanSnapshotHasNoFrameMetadata(GameTestHelper helper) {
        LifecycleHandActorFixture.start(helper, "world-frame-snapshot", owner -> {
            var body = owner.authority().body();
            BoundedGameTestWait.await(helper, 10, () -> "no authoritative world snapshot observed",
                    () -> { owner.drainAppliedSequence(); return owner.latestSnapshot().get() != null; }, () -> {
                        var point = LoadedFrameRayQuery.currentActorPoint(helper.getLevel(), body);
                        var snapshot = owner.latestSnapshot().get();
                        require(point != null && point.frameId() == null && snapshot.frameState().isEmpty(),
                                "ordinary world Human remains frame-less; point=" + point + ", snapshot=" + snapshot);
                        helper.succeed();
                    });
        });
    }

    private static void runPair(GameTestHelper helper, ServerLevel level, BlockPos nearBlockOrigin, BlockPos farBlockOrigin) {
        Pair near = null, far = null;
        try {
            near = author(level, "near-zero", new Vector3d(nearBlockOrigin.getX() + .5,
                    nearBlockOrigin.getY(), nearBlockOrigin.getZ() + .5));
            far = author(level, "far-world", new Vector3d(farBlockOrigin.getX() + .5,
                    farBlockOrigin.getY(), farBlockOrigin.getZ() + .5));
            sample(near, "after-allocation", null);
            sample(far, "after-allocation", null);

            authorFloor(near);
            authorFloor(far);
            sample(near, "after-one-floor-block", null);
            sample(far, "after-one-floor-block", null);

            finishPrimitive(near);
            finishPrimitive(far);
            sample(near, "complete-geometry-before-next-native-tick", null);
            sample(far, "complete-geometry-before-next-native-tick", null);

            Pair finalNear = near, finalFar = far;
            RigidBodyHandle nearHandle = RigidBodyHandle.of(near.plot.subLevel());
            RigidBodyHandle farHandle = RigidBodyHandle.of(far.plot.subLevel());
            Vector3d nearBeforeMotion = near.plot.worldOrigin();
            Vector3d farBeforeMotion = far.plot.worldOrigin();
            BoundedGameTestWait.await(helper, 30,
                    () -> "paired native bodies failed to register: near=" + nearHandle.isValid()
                            + ", far=" + farHandle.isValid(),
                    () -> nearHandle.isValid() && farHandle.isValid(), () -> {
                        nearHandle.addLinearAndAngularVelocity(new Vector3d(.5, 0, .5), new Vector3d());
                        farHandle.addLinearAndAngularVelocity(new Vector3d(.5, 0, .5), new Vector3d());
                        require(nearHandle.getLinearVelocity(new Vector3d()).x > .1
                                        && nearHandle.getLinearVelocity(new Vector3d()).z > .1
                                        && farHandle.getLinearVelocity(new Vector3d()).x > .1
                                        && farHandle.getLinearVelocity(new Vector3d()).z > .1,
                                "both native bodies accepted the same one-shot horizontal diagnostic velocity");
                        BoundedGameTestWait.safeDelay(helper, 4, () -> {
                            try {
                                sample(finalNear, "after-four-native-ticks-and-horizontal-velocity", nearHandle);
                                sample(finalFar, "after-four-native-ticks-and-horizontal-velocity", farHandle);
                                Vector3d nearDelta = new Vector3d(finalNear.plot.worldOrigin()).sub(nearBeforeMotion);
                                Vector3d farDelta = new Vector3d(finalFar.plot.worldOrigin()).sub(farBeforeMotion);
                                require(nearDelta.x > .02 && nearDelta.z > .02,
                                        "near-zero native body must show positive world-projected horizontal X/Z motion; nearDelta="
                                                + nearDelta + "; far-world is intentionally measurement-only outside the supported envelope: farDelta="
                                                + farDelta);
                                verifyStorageIdentity(finalNear);
                                verifyStorageIdentity(finalFar);
                                helper.succeed();
                            } finally {
                                finalNear.plot.close();
                                finalFar.plot.close();
                            }
                        });
                    });
        } catch (RuntimeException | Error failure) {
            if (near != null) near.plot.close();
            if (far != null) far.plot.close();
            throw failure;
        }
    }

    private static Pair author(ServerLevel level, String name, Vector3d requested) {
        SableFixturePlot plot = SableFixturePlot.allocate(level, requested);
        return new Pair(name, plot, plot.storageOrigin(), requested);
    }

    private static void authorFloor(Pair pair) {
        require(pair.plot.setStorageBlock(pair.storageOrigin, ModBlocks.STEEL_WALL_BLOCK.get().defaultBlockState()),
                pair.name + " first floor block placement");
    }

    private static void finishPrimitive(Pair pair) {
        BlockPos origin = pair.storageOrigin;
        require(pair.plot.setStorageBlock(origin.east(), ModBlocks.STEEL_WALL_BLOCK.get().defaultBlockState()),
                pair.name + " connected second floor block placement");
        BlockPos door = origin.above();
        require(pair.plot.setStorageBlock(door, ModBlocks.AIRLOCK.get().defaultBlockState()),
                pair.name + " lower real airlock placement");
        require(pair.plot.setStorageBlock(door.above(), ModBlocks.AIRLOCK.get().defaultBlockState()
                        .setValue(AirlockBlock.HALF, DoubleBlockHalf.UPPER)),
                pair.name + " upper real airlock placement");
        pair.airlock = (AirlockBlockEntity) pair.plot.subLevel().getLevel().getBlockEntity(door);
        require(pair.airlock != null && pair.airlock.prototype() != null,
                pair.name + " real airlock BE/prototype resolves before the first native tick");
        pair.airlockId = pair.airlock.identity();
    }

    private static void sample(Pair pair, String phase, RigidBodyHandle handle) {
        var body = pair.plot.subLevel();
        var pose = body.logicalPose();
        Vector3d projected = pair.plot.worldOrigin();
        Vector3d delta = new Vector3d(projected).sub(pair.requestedWorld);
        Vector3d linear = handle == null ? new Vector3d() : handle.getLinearVelocity(new Vector3d());
        Vector3d angular = handle == null ? new Vector3d() : handle.getAngularVelocity(new Vector3d());
        var mass = body.getMassTracker();
        var centerData = mass == null ? null : mass.getCenterOfMass();
        var center = centerData == null ? new Vector3d() : new Vector3d(centerData);
        String centerJson = centerData == null ? "null"
                : String.format(Locale.ROOT, "[%.17g,%.17g,%.17g]", center.x, center.y, center.z);
        double totalMass = mass == null ? 0.0 : mass.getMass();
        String json = String.format(Locale.ROOT,
                "{\"case\":\"sable-precision-pair\",\"fixture\":\"%s\",\"phase\":\"%s\","
                        + "\"engineUUID\":\"%s\",\"storageOrigin\":[%d,%d,%d],\"requestedWorld\":[%.17g,%.17g,%.17g],"
                        + "\"projectedStorageOriginWorld\":[%.17g,%.17g,%.17g],\"projectedDelta\":[%.17g,%.17g,%.17g],"
                        + "\"logicalPosePosition\":[%.17g,%.17g,%.17g],\"rotationPoint\":[%.17g,%.17g,%.17g],"
                        + "\"mass\":%.17g,\"centerOfMass\":%s,"
                        + "\"linearVelocity\":[%.17g,%.17g,%.17g],\"angularVelocity\":[%.17g,%.17g,%.17g]}",
                pair.name, phase, pair.plot.engineId(), pair.storageOrigin.getX(), pair.storageOrigin.getY(), pair.storageOrigin.getZ(),
                pair.requestedWorld.x, pair.requestedWorld.y, pair.requestedWorld.z,
                projected.x, projected.y, projected.z, delta.x, delta.y, delta.z,
                pose.position().x, pose.position().y, pose.position().z,
                pose.rotationPoint().x(), pose.rotationPoint().y(), pose.rotationPoint().z(),
                totalMass, centerJson,
                linear.x, linear.y, linear.z, angular.x, angular.y, angular.z);
        System.out.println("MS14_SABLE_PRECISION " + json);
        require(Double.isFinite(projected.x) && Double.isFinite(projected.y) && Double.isFinite(projected.z)
                        && Double.isFinite(linear.x) && Double.isFinite(linear.y) && Double.isFinite(linear.z),
                pair.name + " emitted finite pose and velocity measurements at " + phase);
    }

    private static void verifyStorageIdentity(Pair pair) {
        BlockPos floor = pair.storageOrigin;
        BlockPos doorPos = floor.above();
        require(pair.plot.subLevel().getUniqueId().equals(pair.plot.engineId())
                        && pair.plot.subLevel().getLevel().getBlockState(floor).is(ModBlocks.STEEL_WALL_BLOCK.get())
                        && pair.plot.subLevel().getLevel().getBlockEntity(doorPos) == pair.airlock
                        && pair.airlock.identity().equals(pair.airlockId),
                pair.name + " native steps keep the same engine UUID and authored block/airlock storage records");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }

    private static final class Pair {
        final String name;
        final SableFixturePlot plot;
        final BlockPos storageOrigin;
        final Vector3d requestedWorld;
        AirlockBlockEntity airlock;
        UUID airlockId;

        Pair(String name, SableFixturePlot plot, BlockPos storageOrigin, Vector3d requestedWorld) {
            this.name = name;
            this.plot = plot;
            this.storageOrigin = storageOrigin;
            this.requestedWorld = new Vector3d(requestedWorld);
        }
    }
}
