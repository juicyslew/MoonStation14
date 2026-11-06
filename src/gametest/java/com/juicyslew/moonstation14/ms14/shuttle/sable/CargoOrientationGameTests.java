package com.juicyslew.moonstation14.ms14.shuttle.sable;

import com.juicyslew.moonstation14.entities.LooseFrameOrientation;
import com.juicyslew.moonstation14.entities.LooseItemEntity;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;

/** Live cargo orientation authority/persistence seam; native transition is observed, never assigned by the fixture. */
@PrefixGameTestTemplate(false)
public final class CargoOrientationGameTests {
    private CargoOrientationGameTests() { }

    @GameTestTags({"shuttle-cargo-orientation", "shuttle", "loose-objects"})
    @GameTest(templateNamespace = "moonstation14", template = "atmos_large_empty", timeoutTicks = 320)
    public static void nativeOwnedCargoSnapshotPersistsAndCanBeDerivedByLateTracker(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var region = SableFixtureScene.acquire(helper, new BlockPos(1500, 300, 700), 32, 5, 32);
        SableFixtureScene.awaitEmpty(helper, region, 40, () -> {
            SableFixtureScene.authorStoneSupportPad(level, region);
            var authored = SableFixturePlot.allocateAndAuthor(level,
                    new Vector3d(1516.5, 301, 716.5), plot -> {
                        BlockPos storage = plot.storageOrigin().offset(-2, 0, -2);
                        plot.ensureStorageFootprint(storage, 4, 1, 4);
                        for (int x = 0; x < 4; x++) for (int z = 0; z < 4; z++)
                            plot.setStorageBlock(storage.offset(x, 0, z), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
                        return storage;
                    });
            SableFixtureScene.trackPlot(helper, authored.plot());
            // This helper is specifically an initial-overlap/ownership guard. Check before natural fall/contact,
            // not after the expected tiny native floor-contact residual appears.
            SableFixtureScene.requireStoneSupportUnderProjectedFootprint(level, region, authored.plot(),
                    authored.result(), 4, 4);
            SableFixtureScene.awaitStationaryFrame(helper, authored.plot(), 160,
                    () -> spawnAndVerify(helper, level, authored.plot(), authored.result()));
        });
    }

    private static void spawnAndVerify(GameTestHelper helper, ServerLevel level, SableFixturePlot plot,
                                       BlockPos storage) {
        var pose = plot.subLevel().logicalPose();
        var frameAtSpawn = new org.joml.Quaterniond(pose.orientation());
        Vector3d projected = pose.transformPosition(new Vector3d(storage.getX() + 1.5,
                storage.getY() + 1, storage.getZ() + 1.5));
        Vec3 world = new Vec3(projected.x, projected.y, projected.z);
        LooseItemEntity cargo = LooseItemEntity.create(level, world,
                new net.minecraft.world.item.ItemStack(com.juicyslew.moonstation14.item.ModItems.JUG.get()));
        cargo.setDeltaMovement(Vec3.ZERO);
        cargo.setRotationAngle(.37f);
        if (!level.addFreshEntity(cargo)) throw new IllegalStateException("cargo fixture spawn rejected");
        awaitFirstNativeAnchor(helper, level, plot, cargo, frameAtSpawn, 0);
    }

    private static void awaitFirstNativeAnchor(GameTestHelper helper, ServerLevel level, SableFixturePlot plot,
            LooseItemEntity cargo, org.joml.Quaterniond frameAtSpawn, int elapsed) {
        var point = com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery.currentActorPoint(level, cargo);
        var snapshot = cargo.orientationSnapshot();
        if (point != null && plot.engineId().equals(point.frameId()) && snapshot != null
                && plot.engineId().equals(snapshot.frameId())) {
            var actualFrame = com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery.frameOrientation(
                    level, plot.engineId(), point.storagePoint(), false, 1f);
            if (actualFrame == null) {
                helper.fail("native owner pose was unavailable at initial anchor");
                return;
            }
            var initialWorld = LooseFrameOrientation.worldBasis(actualFrame, snapshot.localBasis());
            if (!initialWorld.equals(new org.joml.Quaterniond(), 1.0e-5)) {
                helper.fail("initial native anchor world basis mismatch: expected=identity actual=" + initialWorld
                        + " spawnFrame=" + frameAtSpawn + " observedFrame=" + actualFrame
                        + " local=" + snapshot.localBasis() + " tick=" + level.getGameTime());
                return;
            }
            var localBeforeTurn = snapshot.localBasis();
            var handle = dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle.of(plot.subLevel());
            handle.addLinearAndAngularVelocity(new Vector3d(), new Vector3d(0, .4, 0));
            awaitNativeYawTurn(helper, level, plot, cargo, localBeforeTurn, actualFrame, 0);
            return;
        }
        if (elapsed >= 60) {
            helper.fail("cargo did not acquire native frame through ordinary contact; point=" + point
                    + " orientation=" + snapshot + " tick=" + level.getGameTime());
            return;
        }
        helper.runAfterDelay(1, () -> awaitFirstNativeAnchor(helper, level, plot, cargo, frameAtSpawn, elapsed + 1));
    }

    private static void awaitNativeYawTurn(GameTestHelper helper, ServerLevel level, SableFixturePlot plot,
            LooseItemEntity cargo, org.joml.Quaterniond localBeforeTurn,
            org.joml.Quaterniond frameBeforeTurn, int elapsed) {
        var point = com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery.currentActorPoint(level, cargo);
        var currentFrame = point == null || !plot.engineId().equals(point.frameId()) ? null
                : com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery.frameOrientation(
                        level, plot.engineId(), point.storagePoint(), false, 1f);
        if (currentFrame != null) {
            var delta = new org.joml.Quaterniond(frameBeforeTurn).invert().mul(currentFrame).normalize();
            double yaw = Math.atan2(2 * (delta.w * delta.y + delta.x * delta.z),
                    1 - 2 * (delta.y * delta.y + delta.x * delta.x));
            if (Math.abs(yaw) >= 1.0e-3) {
                var angularVelocity = dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle.of(plot.subLevel())
                        .getAngularVelocity(new Vector3d());
                dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle.of(plot.subLevel())
                        .addLinearAndAngularVelocity(new Vector3d(), new Vector3d(angularVelocity).negate());
                helper.runAfterDelay(1, () -> assertAfterTurn(helper, level, cargo, plot, localBeforeTurn,
                        "observed native yaw=" + yaw + "rad angular=" + angularVelocity));
                return;
            }
        }
        if (elapsed >= 40) {
            helper.fail("native yaw turn remained below 1e-3rad; actualFrame=" + currentFrame
                    + " point=" + point + " tick=" + level.getGameTime());
            return;
        }
        helper.runAfterDelay(1, () -> awaitNativeYawTurn(helper, level, plot, cargo,
                localBeforeTurn, frameBeforeTurn, elapsed + 1));
    }

    private static void assertAfterTurn(GameTestHelper helper, ServerLevel level, LooseItemEntity cargo,
            SableFixturePlot plot, org.joml.Quaterniond localBeforeTurn, String turnEvidence) {
        var snapshot = cargo.orientationSnapshot();
        if (snapshot == null || !plot.engineId().equals(snapshot.frameId())
                || !localBeforeTurn.equals(snapshot.localBasis(), 1.0e-8)) {
            helper.fail("cargo local basis changed on an in-frame pose update");
            return;
        }
        var point = com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery.currentActorPoint(level, cargo);
        var newFrameBasis = point == null ? null
                : com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery.frameOrientation(
                        level, plot.engineId(), point.storagePoint(), false, 1f);
        if (newFrameBasis == null) {
            helper.fail("turned frame pose unavailable: " + turnEvidence);
            return;
        }
        var expectedWorldBasis = LooseFrameOrientation.worldBasis(newFrameBasis, snapshot.localBasis());
        var restored = new LooseItemEntity(com.juicyslew.moonstation14.entities.ModEntities.LOOSE_ITEM.get(), level);
        var saved = cargo.saveWithoutId(new net.minecraft.nbt.CompoundTag());
        restored.load(saved);
        var restoredSnapshot = restored.orientationSnapshot();
        if (restoredSnapshot == null || !plot.engineId().equals(restoredSnapshot.frameId())
                || !snapshot.localBasis().equals(restoredSnapshot.localBasis(), 1.0e-12)) {
            helper.fail("reconstructed entity did not restore authoritative frame quaternion");
            return;
        }
        var derivedLateClientPose = LooseFrameOrientation.worldBasis(newFrameBasis, restoredSnapshot.localBasis());
        if (!expectedWorldBasis.equals(derivedLateClientPose, 1.0e-10)
                || expectedWorldBasis.equals(new org.joml.Quaterniond(), 1.0e-4)) {
            helper.fail("late-client reconstruction did not match the turned frame's nonidentity world basis; "
                    + turnEvidence + " actual=" + derivedLateClientPose + " expected=" + expectedWorldBasis);
            return;
        }
        var scalarSpin = new org.joml.Quaterniond().rotationY(cargo.getRotationAngle());
        var expectedPresentation = new org.joml.Quaterniond(expectedWorldBasis).mul(scalarSpin).normalize();
        var restoredPresentation = new org.joml.Quaterniond(derivedLateClientPose)
                .mul(new org.joml.Quaterniond().rotationY(cargo.getRotationAngle())).normalize();
        if (!expectedPresentation.equals(restoredPresentation, 1.0e-10)) {
            helper.fail("late-client orientation composition lost local scalar spin");
            return;
        }
        helper.succeed();
    }
}
