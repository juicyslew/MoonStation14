package com.juicyslew.moonstation14.ms14.shuttle.sable;

import com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.ms14.character.AngularRecoveryModel;
import com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery;
import com.juicyslew.moonstation14.ms14.hands.network.LifecycleHandActorFixture;
import com.juicyslew.moonstation14.ms14.player_body_control.network.GhostControlPayloads;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessEntity;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import com.juicyslew.moonstation14.block.ModBlocks;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** End-to-end checks for native support sampling and authoritative airborne recovery state. */
@PrefixGameTestTemplate(false)
@GameTestTags({"angular-recovery"})
public final class AngularRecoveryGameTests {
    private AngularRecoveryGameTests() { }

    @GameTestTags({"angular-recovery-jump"})
    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 700)
    public static void rotatingNativeSupportSpinSurvivesJumpGraceAndIsPublished(GameTestHelper helper) {
        MovingCabinAuthorityFixture.start(helper, new BlockPos(3800, 300, 1800), "angular-jump",
             2.5, 1, 2.5, (fixture, body) -> {
            var actor = fixture.owner();
            var character = (PlayerCharacterHarnessEntity) body;
            if (!clearJumpHeadroom(fixture)) return;
            MovingCabinAuthorityFixture.awaitNativeYaw(helper, fixture, body, () -> {
                if (!removeAuthoredStonePad(fixture)) return;
                var preJumpOmega = RigidBodyHandle.of(fixture.plot().subLevel())
                        .getAngularVelocity(new org.joml.Vector3d());
                if (preJumpOmega.length() < .05) {
                    helper.fail("rotating support has no measurable native world angular velocity: " + preJumpOmega);
                    return;
                }
                actor.drainSnapshots();
                actor.buttons().updateAndGet(value -> value | GhostControlPayloads.BUTTON_JUMP);
                final class Departure {
                    AngularRecoveryModel.State state;
                    org.joml.Vector3d omega;
                    long tick;
                    int graceSamples;
                    org.joml.Quaterniond expectedOrientation;
                    org.joml.Quaterniond initialOrientation;
                    double previousElapsed;
                    double initialElapsed;
                    double eighthElapsed;
                    org.joml.Quaterniond eighthOrientation;
                }
                var departure = new Departure();
                BoundedGameTestWait.observeServerTicks(helper, 12, ignored -> {
                    if (departure.state != null && departure.graceSamples >= 8) return;
                    long tick = helper.getLevel().getServer().getTickCount();
                    if (departure.state == null) {
                        if (body.onGround()) return;
                        var state = character.authoritativeAngularRecoveryState().orElse(null);
                        var nativeOmega = RigidBodyHandle.of(fixture.plot().subLevel())
                                .getAngularVelocity(new org.joml.Vector3d());
                        double tolerance = 4.0 * Math.max(Math.ulp((float) nativeOmega.x),
                                Math.max(Math.ulp((float) nativeOmega.y), Math.ulp((float) nativeOmega.z)));
                        if (state == null || nativeOmega.length() <= .05
                                || vector(state.worldAngularVelocity()).distance(nativeOmega) > tolerance
                                || state.airborneSeconds() >= .65)
                            throw new GameTestAssertException("bad first-airborne departure: "
                                    + angularDiagnostic(tick, body, state, nativeOmega, fixture));
                        departure.state = state;
                        departure.omega = nativeOmega;
                        departure.tick = tick;
                        departure.expectedOrientation = quaternion(state.orientation());
                        departure.initialOrientation = new org.joml.Quaterniond(departure.expectedOrientation);
                        departure.previousElapsed = state.airborneSeconds();
                        departure.initialElapsed = state.airborneSeconds();
                        actor.buttons().updateAndGet(value -> value & ~GhostControlPayloads.BUTTON_JUMP);
                        return;
                    }
                    if (body.onGround()) throw new GameTestAssertException("landed before eight airborne grace samples: "
                            + angularDiagnostic(tick, body, character.authoritativeAngularRecoveryState().orElse(null),
                                    departure.omega, fixture));
                    var now = character.authoritativeAngularRecoveryState().orElse(null);
                    double dt = now == null ? 0 : now.airborneSeconds() - departure.previousElapsed;
                    if (now != null && dt > 0) {
                        var increment = new org.joml.Quaterniond().fromAxisAngleRad(
                                new org.joml.Vector3d(departure.omega).normalize(), departure.omega.length() * dt);
                        departure.expectedOrientation = increment.mul(departure.expectedOrientation).normalize();
                        departure.previousElapsed = now.airborneSeconds();
                    }
                    if (now == null || now.airborneSeconds() >= .65
                            || quaternionDotError(departure.expectedOrientation, now.orientation()) > 2e-8
                            || !lookMatches(body, departure.expectedOrientation)
                            || vector(now.worldAngularVelocity()).distance(vector(departure.state.worldAngularVelocity())) > 1e-10
                            || now.airborneSeconds() <= departure.state.airborneSeconds())
                        throw new GameTestAssertException("support spin changed during grace: "
                                    + angularDiagnostic(tick, body, now, departure.omega, fixture)
                                    + " expectedQ=" + quaternionDiagnostic(departure.expectedOrientation) + " actualQ="
                                    + (now == null ? "null" : now.orientation()) + " look=" + body.getLookAngle() + " dt=" + dt);
                    departure.graceSamples++;
                    if (departure.graceSamples == 8) {
                        departure.eighthElapsed = now.airborneSeconds();
                        departure.eighthOrientation = new org.joml.Quaterniond(departure.expectedOrientation);
                        if (1.0 - Math.abs(departure.initialOrientation.dot(departure.eighthOrientation)) < 1e-5)
                            throw new GameTestAssertException("native spin produced no measurable eighth-sample attitude change: initialQ="
                                    + quaternionDiagnostic(departure.initialOrientation) + " expectedQ="
                                    + quaternionDiagnostic(departure.eighthOrientation) + " "
                                    + angularDiagnostic(tick, body, now, departure.omega, fixture));
                    }
                }, () -> {
                    if (departure.state == null || departure.graceSamples < 8)
                        throw new GameTestAssertException("bounded observer missed departure/eight grace samples: "
                                + angularDiagnostic(helper.getLevel().getServer().getTickCount(), body,
                                        character.authoritativeAngularRecoveryState().orElse(null), departure.omega, fixture));
                    var published = actor.drainSnapshots().stream()
                            .filter(snapshot -> snapshot.epoch() == actor.epoch()
                                    && snapshot.harnessEntityId() == body.getId() && !snapshot.onGround()
                                    && snapshot.serverGameTick() >= departure.tick)
                            .filter(snapshot -> snapshot.angularRecoveryState().isPresent())
                            .filter(snapshot -> vector(snapshot.angularRecoveryState().orElseThrow().worldAngularVelocity())
                                    .distance(vector(departure.state.worldAngularVelocity())) <= 1e-10)
                            .filter(snapshot -> snapshot.angularRecoveryState().orElseThrow().airborneSeconds()
                                    > departure.initialElapsed
                                    && snapshot.angularRecoveryState().orElseThrow().airborneSeconds() <= departure.eighthElapsed)
                            .filter(snapshot -> quaternionDotError(orientationAt(departure.initialOrientation,
                                            departure.omega, snapshot.angularRecoveryState().orElseThrow().airborneSeconds()
                                                    - departure.initialElapsed),
                                    snapshot.angularRecoveryState().orElseThrow().orientation()) <= 2e-8)
                            .findFirst().orElse(null);
                    if (published == null) throw new GameTestAssertException(
                            "authoritative airborne owner snapshot omitted exact departure angular state; "
                                    + angularDiagnostic(departure.tick, body, departure.state, departure.omega, fixture)
                                    + " expectedQ=" + quaternionDiagnostic(departure.eighthOrientation) + " published=" + published);
                    var publishedState = published.angularRecoveryState().orElseThrow();
                    var publishedExpected = orientationAt(departure.initialOrientation, departure.omega,
                            publishedState.airborneSeconds() - departure.initialElapsed);
                    if (quaternionDotError(publishedExpected, publishedState.orientation()) > 2e-8)
                        throw new GameTestAssertException("published grace attitude mismatch; expectedQ="
                                + quaternionDiagnostic(publishedExpected) + " actualQ="
                                + quaternionDiagnostic(quaternion(publishedState.orientation())) + " eighthQ="
                                + quaternionDiagnostic(departure.eighthOrientation)
                                + " snapshotTick=" + published.serverGameTick() + " snapshotElapsed=" + publishedState.airborneSeconds()
                                + " logicalYawPitch=" + body.getYRot() + "/" + body.getXRot());
                    helper.succeed();
                });
            });
        });
    }

    @GameTestTags({"angular-recovery-fall"})
    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 260)
    public static void removedRealSteelSupportRecoversSpinDuringLongUnsupportedFall(GameTestHelper helper) {
        MovingCabinAuthorityFixture.start(helper, new BlockPos(4200, 300, 1800), "angular-fall",
                2.5, 1, 2.5, (fixture, body) -> {
            MovingCabinAuthorityFixture.awaitNativeYaw(helper, fixture, body, () -> {
                if (!removeAuthoredStonePad(fixture)) return;
                var point = LoadedFrameRayQuery.currentActorPoint(fixture.level(), body);
                var movement = (dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.EntityMovementExtension) (Object) body;
                var nativeInfo = movement.sable$getCollisionInfo();
                if (point == null || !fixture.plot().engineId().equals(point.frameId()) || !body.onGround()
                        || nativeInfo == null || !nativeInfo.verticalCollisionBelow
                        || nativeInfo.trackingSubLevel != fixture.plot().subLevel()) {
                    helper.fail("support-loss baseline lacks exact native steel-floor witness: " + point + " " + nativeInfo);
                    return;
                }
                var local = new org.joml.Vector3d(point.storagePoint().x, point.storagePoint().y, point.storagePoint().z);
                int floorY = (int) Math.floor(local.y - .01);
                int centerX = net.minecraft.core.BlockPos.containing(local.x, floorY + 1e-4, local.z).getX();
                int centerZ = net.minecraft.core.BlockPos.containing(local.x, floorY + 1e-4, local.z).getZ();
                var center = new BlockPos(centerX, floorY, centerZ);
                var floor = LoadedFrameRayQuery.knownServerBlock(fixture.level(), fixture.plot().subLevel(), center);
                if (floor == null || !floor.is(ModBlocks.STEEL_WALL_BLOCK.get())
                        || floor.getCollisionShape(fixture.plot().subLevel().getLevel(), center).isEmpty()) {
                    helper.fail("selected native support is not authored colliding steel: " + center + "=" + floor);
                    return;
                }
                int removed = 0;
                for (int x = centerX - 1; x <= centerX + 1; x++) for (int z = centerZ - 1; z <= centerZ + 1; z++)
                    for (int y = floorY; y >= fixture.cabin().origin().getY(); y--) {
                        BlockPos cell = new BlockPos(x, y, z);
                        var state = LoadedFrameRayQuery.knownServerBlock(fixture.level(), fixture.plot().subLevel(), cell);
                        if (state == null) { helper.fail("unknown loaded support block " + cell); return; }
                        if (!state.isAir()) {
                            if (!fixture.plot().setStorageBlock(cell, Blocks.AIR.defaultBlockState())) {
                                helper.fail("actual floor removal rejected at " + cell); return;
                            }
                            removed++;
                        }
                    }
                if (removed == 0) { helper.fail("actual witnessed steel support removal selected no cells"); return; }
                BoundedGameTestWait.await(helper, 60, () -> "actor did not leave removed steel support",
                        () -> !body.onGround() && LoadedFrameRayQuery.currentActorPoint(fixture.level(), body) != null
                                && LoadedFrameRayQuery.currentActorPoint(fixture.level(), body).frameId() == null,
                        () -> BoundedGameTestWait.await(helper, 60, () -> "angular recovery did not finish during unsupported fall",
                                () -> character(body).authoritativeAngularRecoveryState().map(s -> s.airborneSeconds() >= 1.65).orElse(false),
                                () -> {
                                    var state = character(body).authoritativeAngularRecoveryState().orElse(null);
                                    if (state == null || body.onGround() || state.worldAngularVelocity().lengthSquared() != 0
                                            || state.orientation().rotate(new AngularRecoveryModel.Vec3(0, 1, 0))
                                                .normalized().y() < .999999) {
                                        helper.fail("unsupported recovery did not reach zero-spin upright state: " + state
                                                + " grounded=" + body.onGround()); return;
                                    }
                                    helper.succeed();
                                }));
            });
        });
    }

    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 260)
    public static void tiltedOffAxisShortJumpLandingAppliesKnockdownAndStun(GameTestHelper helper) {
        MovingCabinAuthorityFixture.start(helper, new BlockPos(3800, 300, 2200), "angular-tilted-landing",
                2.5, 1, 2.5, (fixture, body) -> shortJumpWithSeededInertialState(helper, fixture.owner(), body,
                        AngularRecoveryModel.Quat.fromAxisAngle(new AngularRecoveryModel.Vec3(0, 0, 1), Math.toRadians(60)),
                        new AngularRecoveryModel.Vec3(2, 0, 0), true));
    }

    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 260)
    public static void uprightYawOnlyShortJumpLandingDoesNotKnockDownOrStun(GameTestHelper helper) {
        MovingCabinAuthorityFixture.start(helper, new BlockPos(4200, 300, 2200), "angular-yaw-landing",
                2.5, 1, 2.5, (fixture, body) -> shortJumpWithSeededInertialState(helper, fixture.owner(), body,
                        AngularRecoveryModel.Quat.fromAxisAngle(new AngularRecoveryModel.Vec3(0, 1, 0), Math.PI / 2),
                        new AngularRecoveryModel.Vec3(0, 3, 0), false));
    }

    @GameTestTags({"angular-recovery-supported-stability"})
    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 500)
    public static void rotatingSupportRemainsStableAfterShortCeilingJump(GameTestHelper helper) {
        MovingCabinAuthorityFixture.start(helper, new BlockPos(4400, 300, 1600), "angular-supported-stability",
                2.5, 1, 2.5, (fixture, body) -> {
            var actor = fixture.owner();
            var character = character(body);
            MovingCabinAuthorityFixture.awaitNativeYaw(helper, fixture, body, () -> {
                if (!removeAuthoredStonePad(fixture)) return;
                var sub = fixture.plot().subLevel();
                var nativeBody = RigidBodyHandle.of(sub);
                var initialOmega = nativeBody.getAngularVelocity(new org.joml.Vector3d());
                if (initialOmega.length() <= .05) {
                    helper.fail("native rotating support lacks measurable angular velocity: " + initialOmega);
                    return;
                }
                var initialQ = character.authoritativeAngularRecoveryState().orElse(null);
                actor.buttons().updateAndGet(value -> value | GhostControlPayloads.BUTTON_JUMP);
                final class Landing {
                    boolean leftGround;
                    org.joml.Quaterniond departureQ;
                    long tick;
                    int settledTicks;
                    boolean stabilityStarted;
                }
                var landing = new Landing();
                BoundedGameTestWait.observeServerTicks(helper, 100, ignored -> {
                    if (landing.stabilityStarted) return;
                    if (!landing.leftGround) {
                        if (!body.onGround()) {
                            landing.leftGround = true;
                            landing.tick = helper.getLevel().getServer().getTickCount();
                            landing.departureQ = character.authoritativeAngularRecoveryState()
                                    .map(state -> quaternion(state.orientation())).orElse(null);
                            actor.buttons().updateAndGet(value -> value & ~GhostControlPayloads.BUTTON_JUMP);
                        }
                    } else if (body.onGround()) {
                        var point = LoadedFrameRayQuery.currentActorPoint(fixture.level(), body);
                        var movement = (dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.EntityMovementExtension) (Object) body;
                        var info = movement.sable$getCollisionInfo();
                        if (!body.onGround() || point == null || !fixture.plot().engineId().equals(point.frameId())
                                || info == null || !info.verticalCollision || !info.verticalCollisionBelow
                                || info.trackingSubLevel != sub) {
                            landing.settledTicks = 0;
                            return;
                        }
                        if (++landing.settledTicks < 4) return;
                        landing.stabilityStarted = true;
                        var state = character.authoritativeAngularRecoveryState().orElse(null);
                        var landedQ = state == null ? null : quaternion(state.orientation());
                        if (landing.departureQ == null || landedQ == null
                                || 1.0 - Math.abs(landing.departureQ.dot(landedQ)) < 1e-5)
                            throw new GameTestAssertException("natural ceiling-jump landing did not retain measurable attitude change: tick="
                                    + helper.getLevel().getServer().getTickCount() + " state=" + state + " initial=" + initialQ);
                        var baseline = new org.joml.Vector3d(point.storagePoint().x, point.storagePoint().y, point.storagePoint().z);
                        final class Samples { int count; double maxDrift; double maxTurn; double orientationChange; }
                        var samples = new Samples();
                        var poseBaseline = new org.joml.Quaterniond(sub.logicalPose().orientation());
                        BoundedGameTestWait.observeServerTicks(helper, 16, tickIgnored -> {
                            var current = LoadedFrameRayQuery.currentActorPoint(fixture.level(), body);
                            var collision = movement.sable$getCollisionInfo();
                            var angular = nativeBody.getAngularVelocity(new org.joml.Vector3d());
                            var qState = character.authoritativeAngularRecoveryState().orElse(null);
                            double drift = current == null ? Double.POSITIVE_INFINITY : baseline.distance(
                                    new org.joml.Vector3d(current.storagePoint().x, current.storagePoint().y, current.storagePoint().z));
                            samples.maxDrift = Math.max(samples.maxDrift, drift);
                            samples.maxTurn = Math.max(samples.maxTurn, angular.length());
                            var pose = new org.joml.Quaterniond(sub.logicalPose().orientation());
                            samples.orientationChange = Math.max(samples.orientationChange, 1.0 - Math.abs(poseBaseline.dot(pose)));
                            if (!body.isAlive() || !body.onGround() || current == null
                                    || !fixture.plot().engineId().equals(current.frameId()) || collision == null
                                    || !collision.verticalCollisionBelow || collision.trackingSubLevel != sub
                                    || qState == null || qState.worldAngularVelocity().lengthSquared() != 0
                                    || angular.length() <= .05 || drift >= .05)
                                throw new GameTestAssertException(String.format(java.util.Locale.ROOT,
                                        "supported stability failure tick=%d samples=%d drift=%.17g maxDrift=%.17g omega=(%.17g,%.17g,%.17g) q=%s grounded=%s frame=%s collision=%s",
                                        helper.getLevel().getServer().getTickCount(), samples.count, drift, samples.maxDrift,
                                        angular.x, angular.y, angular.z, qState, body.onGround(),
                                        current == null ? "null" : current.frameId(), collision));
                            samples.count++;
                            if (samples.count == 16) {
                                if (samples.orientationChange < 1e-5)
                                    throw new GameTestAssertException("rotating support pose did not change: maxChange="
                                            + samples.orientationChange + " maxOmega=" + samples.maxTurn);
                                helper.succeed();
                            }
                        }, () -> {
                            if (samples.count < 16) helper.fail("supported stability observer ended early: " + samples.count);
                        });
                    }
                }, () -> {
                    if (!landing.leftGround || !landing.stabilityStarted)
                        helper.fail("bounded jump/landing observer missed departure or stable native landing: leftGround="
                                + landing.leftGround + " stabilityStarted=" + landing.stabilityStarted + " initialQ="
                                + initialQ + " omega=" + initialOmega);
                });
            });
        });
    }

    private static void shortJumpWithSeededInertialState(GameTestHelper helper, LifecycleHandActorFixture.Ready owner,
            net.minecraft.world.entity.LivingEntity body, AngularRecoveryModel.Quat orientation,
            AngularRecoveryModel.Vec3 omega, boolean expectBadLanding) {
        var character = character(body);
        owner.buttons().updateAndGet(value -> value | GhostControlPayloads.BUTTON_JUMP);
        BoundedGameTestWait.await(helper, 60, () -> "ordinary lifecycle jump did not become airborne",
                () -> !body.onGround(), () -> {
                    owner.buttons().updateAndGet(value -> value & ~GhostControlPayloads.BUTTON_JUMP);
                    character.setAngularRecoveryState(java.util.Optional.of(
                            AngularRecoveryModel.departure(orientation, omega)));
                    BoundedGameTestWait.await(helper, 40, () -> "short jump failed to land on the real cabin floor",
                            body::onGround, () -> {
                                boolean stunned = com.juicyslew.moonstation14.ms14.character.CharacterControlSystem.isStunned(body);
                                boolean knockedDown = com.juicyslew.moonstation14.ms14.character.CharacterControlSystem.isKnockedDown(body);
                                boolean down = com.juicyslew.moonstation14.ms14.standing.PostureSystem.isDownPosture(body);
                                if (expectBadLanding ? !(stunned && knockedDown && down) : stunned || knockedDown || down) {
                                    helper.fail("landing status mismatch expectedBad=" + expectBadLanding + " stunned=" + stunned
                                            + " knockedDown=" + knockedDown + " down=" + down); return;
                                }
                                helper.succeed();
                            });
                });
    }

    private static PlayerCharacterHarnessEntity character(net.minecraft.world.entity.LivingEntity body) {
        return (PlayerCharacterHarnessEntity) body;
    }

    private static org.joml.Vector3d vector(AngularRecoveryModel.Vec3 value) {
        return new org.joml.Vector3d(value.x(), value.y(), value.z());
    }

    private static org.joml.Quaterniond quaternion(AngularRecoveryModel.Quat value) {
        return new org.joml.Quaterniond(value.x(), value.y(), value.z(), value.w()).normalize();
    }

    private static double quaternionDotError(org.joml.Quaterniond expected, AngularRecoveryModel.Quat actual) {
        return 1.0 - Math.abs(expected.dot(quaternion(actual)));
    }

    private static String quaternionDiagnostic(org.joml.Quaterniond q) {
        return String.format(java.util.Locale.ROOT, "(%.17g,%.17g,%.17g,%.17g)", q.w, q.x, q.y, q.z);
    }

    private static org.joml.Quaterniond orientationAt(org.joml.Quaterniond initial, org.joml.Vector3d omega, double elapsed) {
        return new org.joml.Quaterniond().fromAxisAngleRad(new org.joml.Vector3d(omega).normalize(),
                omega.length() * elapsed).mul(initial).normalize();
    }

    private static boolean lookMatches(net.minecraft.world.entity.LivingEntity body, org.joml.Quaterniond orientation) {
        double yaw = Math.toRadians(body.getYRot()), pitch = Math.toRadians(body.getXRot());
        var raw = new org.joml.Vector3d(-Math.sin(yaw) * Math.cos(pitch), -Math.sin(pitch), Math.cos(yaw) * Math.cos(pitch));
        orientation.transform(raw);
        var actual = body.getLookAngle();
        return actual.distanceToSqr(raw.x, raw.y, raw.z) <= 4e-12;
    }

    private static String angularDiagnostic(long tick, net.minecraft.world.entity.LivingEntity body,
            AngularRecoveryModel.State state, org.joml.Vector3d nativeOmega,
            MovingCabinAuthorityFixture.Ready fixture) {
        var point = LoadedFrameRayQuery.currentActorPoint(fixture.level(), body);
        return String.format(java.util.Locale.ROOT,
                "tick=%d grounded=%s position=(%.17g,%.17g,%.17g) frame=%s state=%s stateOmega=%s nativeOmega=(%.17g,%.17g,%.17g)",
                tick, body.onGround(), body.getX(), body.getY(), body.getZ(), point == null ? "unknown" : point.frameId(),
                state, state == null ? "null" : state.worldAngularVelocity(), nativeOmega.x, nativeOmega.y, nativeOmega.z);
    }

    /** Remove only the explicitly authored world support of this test scene after native support is proven. */
    static boolean removeAuthoredStonePad(MovingCabinAuthorityFixture.Ready fixture) {
        return MovingCabinAuthorityFixture.removeAuthoredStonePad(fixture);
    }

    /** Remove the authored internal ceiling above only the jump actor's 3x3 column. */
    private static boolean clearJumpHeadroom(MovingCabinAuthorityFixture.Ready fixture) {
        var subLevel = fixture.plot().subLevel().getLevel();
        var origin = fixture.cabin().origin();
        for (int x = 1; x <= 3; x++) for (int z = 1; z <= 3; z++) {
            var ceiling = origin.offset(x, 3, z);
            var state = LoadedFrameRayQuery.knownServerBlock(fixture.level(), fixture.plot().subLevel(), ceiling);
            if (state == null || !state.is(ModBlocks.STEEL_WALL_BLOCK.get())
                    || state.getCollisionShape(subLevel, ceiling).isEmpty()) {
                throw new GameTestAssertException("expected loaded colliding authored steel ceiling at " + ceiling + " got " + state);
            }
            for (int y = 4; y <= 5; y++) {
                var overhead = origin.offset(x, y, z);
                var overheadState = LoadedFrameRayQuery.knownServerBlock(fixture.level(), fixture.plot().subLevel(), overhead);
                if (overheadState == null || !overheadState.getCollisionShape(subLevel, overhead).isEmpty()) {
                    throw new GameTestAssertException("jump headroom obstructed at " + overhead + " by " + overheadState);
                }
            }
        }
        for (int x = 1; x <= 3; x++) for (int z = 1; z <= 3; z++) {
            var ceiling = origin.offset(x, 3, z);
            if (!fixture.plot().setStorageBlock(ceiling, Blocks.AIR.defaultBlockState())) {
                throw new GameTestAssertException("internal steel ceiling removal rejected at " + ceiling);
            }
        }
        return true;
    }

}
