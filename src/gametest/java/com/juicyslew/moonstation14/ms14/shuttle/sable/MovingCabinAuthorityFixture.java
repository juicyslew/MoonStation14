package com.juicyslew.moonstation14.ms14.shuttle.sable;

import com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.gametest.support.PowerFixtureReadiness;
import com.juicyslew.moonstation14.ms14.atmos.piping.alarm.AirAlarmBlockEntity;
import com.juicyslew.moonstation14.ms14.hands.network.LifecycleHandActorFixture;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessEntity;
import com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime;
import com.juicyslew.moonstation14.ms14.shuttle.fixture.ShuttleCabinFixture;
import com.juicyslew.moonstation14.ms14.shuttle.fixture.ShuttleManualFixture;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.EntityMovementExtension;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;

import java.util.function.BiConsumer;
import java.util.concurrent.atomic.AtomicBoolean;

/** A single resolved, powered cabin plus a normally enrolled, hand-issued human actor. */
public final class MovingCabinAuthorityFixture {
    private MovingCabinAuthorityFixture() { }

    /** Remove only this fixture's authored world pad after validating every support cell. */
    public static boolean removeAuthoredStonePad(Ready fixture) {
        var scene = fixture.scene();
        var level = fixture.level();
        for (int x = 0; x < scene.width; x++) for (int z = 0; z < scene.depth; z++)
            for (int layer = 1; layer <= scene.supportThickness; layer++) {
                var pos = scene.supportCell(x, layer, z);
                if (!scene.tickets.entityTicking(pos) || level.getChunkSource().getChunk(
                        pos.getX() >> 4, pos.getZ() >> 4, net.minecraft.world.level.chunk.status.ChunkStatus.FULL, false) == null)
                    throw new GameTestAssertException("authored support-pad bounds are not loaded: " + pos);
                if (!level.getBlockState(pos).is(net.minecraft.world.level.block.Blocks.STONE))
                    throw new GameTestAssertException("expected authored stone support pad at " + pos + " got "
                            + level.getBlockState(pos));
            }
        for (int x = 0; x < scene.width; x++) for (int z = 0; z < scene.depth; z++)
            for (int layer = 1; layer <= scene.supportThickness; layer++)
                level.setBlock(scene.supportCell(x, layer, z), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
        return true;
    }

    public record Ready(ServerLevel level, SableFixtureScene.Region scene, SableFixturePlot plot,
                  ShuttleCabinFixture.Built cabin, AirAlarmBlockEntity alarm,
                  LifecycleHandActorFixture.Ready owner, AtomicBoolean intentPumpEnabled) {
        /**
         * Enables/disables only this fixture's ordinary neutral-intent pump. Disabled ticks send
         * no lifecycle payload (and therefore produce no fresh acknowledgement); body physics,
         * native frame motion, and the normal Ready/session flow are untouched. Defaults enabled.
         */
        public void setIntentPumpEnabled(boolean enabled) { intentPumpEnabled.set(enabled); }

        /** Releases only this test scene's chunk lease; operational-frame residency remains production-owned. */
        public void closeSceneLease() { scene.tickets.close(); }
        public boolean sceneLeaseClosed() { return scene.tickets.isClosed(); }
    }

    public static void start(GameTestHelper helper, BlockPos world, String playerName, double localX, double localFeetY, double localZ,
                      BiConsumer<Ready, PlayerCharacterHarnessEntity> action) {
        start(helper, world, playerName, localX, localFeetY, localZ, 0, false, action);
    }

    /** Lifecycle-owner setup using the previously validated open manual boarding-bay cabin. */
    public static void startManual(GameTestHelper helper, BlockPos world, String playerName,
                                   double localX, double localFeetY, double localZ,
                                   BiConsumer<Ready, PlayerCharacterHarnessEntity> action) {
        start(helper, world, playerName, localX, localFeetY, localZ, 0, true, action);
    }

    /** Optional positive-X/Z ordinary ground corridor for long native-carry fixtures. */
    public static void start(GameTestHelper helper, BlockPos world, String playerName, double localX, double localFeetY, double localZ,
                       int supportCorridor, BiConsumer<Ready, PlayerCharacterHarnessEntity> action) {
        start(helper, world, playerName, localX, localFeetY, localZ, supportCorridor, false, action);
    }

    private static void start(GameTestHelper helper, BlockPos world, String playerName, double localX,
                       double localFeetY, double localZ, int supportCorridor, boolean manual,
                       BiConsumer<Ready, PlayerCharacterHarnessEntity> action) {
        ServerLevel level = helper.getLevel();
        // The pad is world-space: cover the whole hull after its 45-degree test rotation,
        // the front approach, and the optional positive-axis support corridor.
        int cabinWidth=manual?ShuttleManualFixture.WIDTH:ShuttleCabinFixture.WIDTH;
        int cabinHeight=manual?ShuttleManualFixture.HEIGHT:ShuttleCabinFixture.HEIGHT;
        int cabinDepth=manual?ShuttleManualFixture.DEPTH:ShuttleCabinFixture.DEPTH;
        if (manual && Double.compare(localFeetY, 1.05) != 0)
            throw new IllegalArgumentException("manual bay lifecycle placement must use the validated 1.05 local feet height");
        int padRadius = Math.max(cabinWidth, cabinDepth) + supportCorridor + 2;
        BlockPos padOrigin = world.offset(-padRadius, 0, -padRadius);
        int padSize = padRadius * 2 + 1;
        SableFixtureScene.Region scene = SableFixtureScene.acquire(helper, padOrigin,
                padSize, cabinHeight, padSize);
        SableFixtureScene.awaitEmpty(helper, scene, SableFixtureScene.READINESS_TICKS, () -> {
            SableFixtureScene.authorStoneSupportPad(level, scene);
            var authored = SableFixturePlot.allocateAndAuthor(level,
                    new Vector3d(world.getX() + .5, world.getY(), world.getZ() + .5), plot -> {
                        BlockPos storage = plot.storageOrigin();
                        plot.ensureStorageFootprint(storage, cabinWidth, cabinHeight, cabinDepth);
                        return manual
                                ? ShuttleManualFixture.build(level, storage, plot.engineId(), plot::setStorageBlock)
                                : ShuttleCabinFixture.build(level, storage, plot.engineId());
                    });
            SableFixturePlot plot = authored.plot();
            SableFixtureScene.trackPlot(helper, plot);
            var cabin = authored.result();
            // Raised machine-bay tread clears the cabin's y=3 partition below the high-mounted APC.
            // It remains ordinary solid floor with the authored roof/headroom; the HUMAN retains gravity and physics.
            if (!manual && localFeetY > 1)
                plot.setStorageBlock(cabin.origin().offset((int) Math.floor(localX),
                        (int) Math.floor(localFeetY) - 1, (int) Math.floor(localZ)),
                        net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
            BlockPos alarmPos = cabin.at(ShuttleCabinFixture.MACHINE);
            if (!(level.getBlockEntity(alarmPos) instanceof AirAlarmBlockEntity alarm)
                    || alarm.prototype() == null)
                throw new IllegalStateException("authored paid air alarm lacks resolved prototype owner");
            RigidBodyHandle handle = RigidBodyHandle.of(plot.subLevel());
            BoundedGameTestWait.await(helper, 80, () -> "native cabin missing: " + plot.engineId(),
                     handle::isValid, () -> SableFixtureScene.awaitStationaryFrame(helper, plot, 120, () ->
                     PowerFixtureReadiness.await(helper, level, 500,
                            () -> PowerRuntime.airAlarmMeter(level, alarm).known()
                                    && PowerRuntime.airAlarmMeter(level, alarm).deliveredWatts() == 100,
                             () -> "100W alarm power did not settle: " + PowerRuntime.airAlarmMeter(level, alarm), () -> {
                                     if (manual) StraightTakeoffIntervalDiagnosticGameTests.requireLaunchClearance(
                                             level, plot.subLevel(), cabin.origin());
                                     LifecycleHandActorFixture.start(helper, playerName, owner -> {
                                        var body = (PlayerCharacterHarnessEntity) owner.authority().body();
                                        // One initial fixture placement only; all later world movement is native Sable/body carry.
                                        Vector3d localFeet = new Vector3d(cabin.origin().getX() + localX,
                                                cabin.origin().getY() + localFeetY, cabin.origin().getZ() + localZ);
                                        Vector3d worldPoint = plot.subLevel().logicalPose().transformPosition(localFeet);
                                          Vec3 worldFeet = new Vec3(worldPoint.x, worldPoint.y, worldPoint.z);
                                           Object preRelocationInfo = ((EntityMovementExtension) (Object) body).sable$getCollisionInfo();
                                           body.moveTo(worldFeet.x, worldFeet.y, worldFeet.z, 0, 0);
                                          AtomicBoolean intentPumpEnabled = new AtomicBoolean(true);
                                          BoundedGameTestWait.safeOnEachTick(helper, () -> {
                                              if (intentPumpEnabled.get() && !body.isRemoved()) owner.neutralIntent(body);
                                          });
                                         BoundedGameTestWait.await(helper, 40,
                                                  () -> "enrolled HUMAN did not settle on the authored "
                                                          + (manual ? "manual bay floor" : "machinery-bay deck") + ": "
                                                         + body.position() + ", onGround=" + body.onGround()
                                                         + ", appliedControl=" + owner.drainAppliedSequence()
                                                         + ", intentSequence=" + owner.intentSequence().get(),
                                                  () -> {
                                                      var nativeInfo = ((EntityMovementExtension) (Object) body).sable$getCollisionInfo();
                                                      var actorPoint = com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery
                                                              .currentActorPoint(level, body);
                                                      return nativeInfo != null && nativeInfo != preRelocationInfo
                                                              && nativeInfo.trackingSubLevel == plot.subLevel()
                                                              && nativeInfo.verticalCollisionBelow && body.onGround()
                                                              && actorPoint != null && actorPoint.frameId().equals(plot.subLevel().getUniqueId())
                                                              && body.position().distanceToSqr(worldFeet) < .5
                                                              && body.isAlive() && !body.isNoGravity() && !body.noPhysics;
                                                  },
                                                () -> action.accept(
                                                         new Ready(level, scene, plot, cabin, alarm, owner,
                                                                 intentPumpEnabled), body));
                                      });
                             })));
        });
    }

    /** Applies one ordinary native yaw input and waits for a committed, observable frame turn. */
    public static void awaitNativeYaw(GameTestHelper helper, Ready fixture,
                                      net.minecraft.world.entity.LivingEntity body, Runnable ready) {
        var initialPoint = com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery
                .currentActorPoint(fixture.level(), body);
        if (initialPoint == null || !fixture.plot().engineId().equals(initialPoint.frameId())) {
            helper.fail("native yaw fixture actor lacks admitted frame: " + initialPoint);
            return;
        }
        var initialBasis = com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery.frameOrientation(
                fixture.level(), initialPoint.frameId(), initialPoint.storagePoint(), false, 1f);
        if (initialBasis == null) {
            helper.fail("native yaw initial frame basis unavailable: point=" + initialPoint);
            return;
        }
        RigidBodyHandle.of(fixture.plot().subLevel()).addLinearAndAngularVelocity(
                new Vector3d(), new Vector3d(0, .4, 0));
        final com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery.ActorPoint[] observedPoint = {initialPoint};
        final org.joml.Quaterniond[] observedBasis = {initialBasis};
        final double[] observedYaw = {0};
        BoundedGameTestWait.await(helper, 60, () -> {
            var point = com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery.currentActorPoint(fixture.level(), body);
            observedPoint[0] = point;
            var current = point == null || !fixture.plot().engineId().equals(point.frameId()) ? null
                    : com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery.frameOrientation(
                            fixture.level(), fixture.plot().engineId(), point.storagePoint(), false, 1f);
            observedBasis[0] = current;
            if (current != null) {
                var relative = new org.joml.Quaterniond(initialBasis).invert().mul(current).normalize();
                observedYaw[0] = Math.atan2(2 * (relative.w * relative.y + relative.x * relative.z),
                        1 - 2 * (relative.y * relative.y + relative.x * relative.x));
            }
            return "native yaw not observed within 60 ticks: initial=" + initialBasis + " current=" + current
                    + " relativeYaw=" + observedYaw[0] + " point=" + point;
        }, () -> {
            var point = com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery
                    .currentActorPoint(fixture.level(), body);
            if (point == null || !fixture.plot().engineId().equals(point.frameId())) return false;
            var current = com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery.frameOrientation(
                    fixture.level(), fixture.plot().engineId(), point.storagePoint(), false, 1f);
            if (current == null) return false;
            observedPoint[0] = point;
            observedBasis[0] = current;
            var delta = new org.joml.Quaterniond(initialBasis).invert().mul(current).normalize();
            double yaw = Math.atan2(2 * (delta.w * delta.y + delta.x * delta.z),
                    1 - 2 * (delta.y * delta.y + delta.x * delta.x));
            observedYaw[0] = yaw;
            return Math.abs(yaw) >= 1.0e-3 && !current.equals(new org.joml.Quaterniond(), 1.0e-4);
        }, () -> {
            if (observedBasis[0] == null || Math.abs(observedYaw[0]) < 1.0e-3)
                helper.fail("native yaw readiness callback lacks observed pose: initial=" + initialBasis
                        + " current=" + observedBasis[0] + " yaw=" + observedYaw[0]
                        + " point=" + observedPoint[0]);
            else {
                int effectiveSubsteps = dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem
                        .require(fixture.level()).getConfig().substepsPerTick;
                String expectedSubsteps = System.getProperty("moonstation14.hullImpactExpectedSubsteps");
                try {
                    HullImpactSubstepsExpectation.requireExpected(effectiveSubsteps, expectedSubsteps);
                } catch (IllegalArgumentException | IllegalStateException invalidConfig) {
                    helper.testInfo.fail(new net.minecraft.gametest.framework.GameTestAssertException(
                            "native yaw readiness has invalid SDK substeps: " + invalidConfig.getMessage()));
                    return;
                }
                com.juicyslew.moonstation14.MoonStation14.LOGGER.info(
                        "[cargo-drop-native-yaw-readiness] effectiveSubsteps={} expectedSubsteps={} frameId={} actorPoint={} relativeYaw={} initialBasis={} currentBasis={}",
                        effectiveSubsteps, expectedSubsteps == null ? "none" : expectedSubsteps,
                        fixture.plot().engineId(), observedPoint[0], observedYaw[0], initialBasis, observedBasis[0]);
                ready.run();
            }
        });
    }
}
