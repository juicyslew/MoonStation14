package com.juicyslew.moonstation14.ms14.shuttle.sable;

import com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.ms14.character.AngularRecoveryLook;
import com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessEntity;
import com.juicyslew.moonstation14.ms14.player_body_control.character.SupportedMovementYaw;
import com.juicyslew.moonstation14.ms14.player_body_control.network.GhostControlPayloads;
import com.juicyslew.moonstation14.ms14.standing.WorldUpTwist;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.EntityMovementExtension;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.util.Optional;

/** Exercises the support-relative heading through lifecycle input and the live grounded server consumer. */
@PrefixGameTestTemplate(false)
@GameTestTags({"shuttle-supported-movement-reference"})
public final class SupportedMovementReferenceGameTests {
    private SupportedMovementReferenceGameTests() { }

    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 500)
    public static void heldSupportedMovementReferenceTracksRotatingFloorWithoutChangingRawLook(GameTestHelper helper) {
        MovingCabinAuthorityFixture.start(helper, new BlockPos(3600, 300, 1500), "supported-move-ref",
                2.5, 1, 2.5, (fixture, body) -> {
            var owner = fixture.owner();
            var frameId = fixture.plot().engineId();
            var initialPoint = LoadedFrameRayQuery.currentActorPoint(fixture.level(), body);
            var initialQ = initialPoint == null ? null : LoadedFrameRayQuery.frameOrientation(
                    fixture.level(), frameId, initialPoint.storagePoint(), false, 1f);
            if (initialPoint == null || !frameId.equals(initialPoint.frameId()) || initialQ == null) {
                helper.fail("actor did not start grounded on the actual cabin support frame: " + initialPoint);
                return;
            }
            double upAngle = Math.acos(Math.max(-1, Math.min(1,
                    initialQ.transform(new Vector3d(0, 1, 0)).y)));
            if (upAngle > Math.toRadians(5)) {
                helper.fail("test frame is not sufficiently world-up aligned: angle=" + upAngle + " q=" + initialQ);
                return;
            }
            MovingCabinAuthorityFixture.awaitNativeYaw(helper, fixture, body, () -> {
                if (!AngularRecoveryGameTests.removeAuthoredStonePad(fixture)) return;
                Vector3d omega = RigidBodyHandle.of(fixture.plot().subLevel()).getAngularVelocity(new Vector3d());
                if (omega.length() <= .05) {
                    helper.fail("native frame spin is not measurable after support pad removal: " + omega);
                    return;
                }
                var point = LoadedFrameRayQuery.currentActorPoint(fixture.level(), body);
                var q = point == null ? null : LoadedFrameRayQuery.frameOrientation(
                        fixture.level(), frameId, point.storagePoint(), false, 1f);
                if (point == null || !frameId.equals(point.frameId()) || q == null
                        || !nativeSupport(body, frameId)) {
                    helper.fail("body lost the actual native floor support before reference capture: point=" + point);
                    return;
                }
                double captureUpAngle = Math.acos(Math.max(-1, Math.min(1,
                        q.transform(new Vector3d(0, 1, 0)).y)));
                if (captureUpAngle > Math.toRadians(5)) {
                    helper.fail("rotating frame departed the near-world-up test premise: angle=" + captureUpAngle);
                    return;
                }
                float frameYaw = WorldUpTwist.degrees(q);
                float worldMovementYaw = AngularRecoveryLook.worldPlanarYaw(
                        ((PlayerCharacterHarnessEntity) body).authoritativeAngularRecoveryState().orElse(null), body.getYRot());
                var reference = SupportedMovementYaw.capture(frameId, frameYaw, worldMovementYaw);
                owner.requestedView().set(() -> body.getEyePosition().add(0, 0, 4));
                owner.setSupportedMovementReference(Optional.of(reference));
                long firstSequence = owner.intentSequence().get() + 1;
                owner.setWish(0, 1000);
                final class Track implements TrackView {
                    Vector3d baseline;
                    float baselineFrameYaw;
                    int samples;
                    double progress;
                    double crossTrack;
                    @Override public String describe() {
                        return "baseline=" + baseline + ", baselineYaw=" + baselineFrameYaw + ", samples=" + samples
                                + ", progress=" + progress + ", crossTrack=" + crossTrack;
                    }
                }
                Track track = new Track();
                BoundedGameTestWait.observeServerTicks(helper, 60, event -> {
                    if (track.baseline == null) {
                        if (owner.drainAppliedSequence() < firstSequence || !nativeSupport(body, frameId)) return;
                        var baselinePoint = LoadedFrameRayQuery.currentActorPoint(fixture.level(), body);
                        var baselineQ = baselinePoint == null ? null : LoadedFrameRayQuery.frameOrientation(
                                fixture.level(), frameId, baselinePoint.storagePoint(), false, 1f);
                        if (baselinePoint == null || !frameId.equals(baselinePoint.frameId()) || baselineQ == null)
                            throw diagnostic("no grounded same-frame baseline", helper, fixture, body, track, reference, omega);
                        track.baseline = storage(baselinePoint);
                        track.baselineFrameYaw = WorldUpTwist.degrees(baselineQ);
                        return;
                    }
                    var nowPoint = LoadedFrameRayQuery.currentActorPoint(fixture.level(), body);
                    if (!nativeSupport(body, frameId) || nowPoint == null || !frameId.equals(nowPoint.frameId()))
                        throw diagnostic("lost exact grounded support frame", helper, fixture, body, track, reference, omega);
                    Vector3d offset = storage(nowPoint).sub(track.baseline);
                    double radians = Math.toRadians(reference.localMovementYaw());
                    double forwardX = -Math.sin(radians), forwardZ = Math.cos(radians);
                    track.progress = offset.x * forwardX + offset.z * forwardZ;
                    track.crossTrack = Math.abs(offset.x * forwardZ - offset.z * forwardX);
                    track.samples++;
                    if (track.samples < 16) return;
                    var currentQ = LoadedFrameRayQuery.frameOrientation(fixture.level(), frameId,
                            nowPoint.storagePoint(), false, 1f);
                    double yawChange = Math.abs(Math.toRadians(SupportedMovementYaw.wrap(
                            WorldUpTwist.degrees(currentQ) - track.baselineFrameYaw)));
                    if (track.progress <= .5 || track.crossTrack >= .05 || yawChange < .08
                            || omega.length() <= .05 || Math.abs(wrapYaw(body.getYRot())) > .02)
                        throw diagnostic("held reference failed frame-local progress/cross-track/raw-look oracle; yawChange="
                                + yawChange, helper, fixture, body, track, reference, omega);
                    owner.setWish(0, 0);
                    owner.setSupportedMovementReference(Optional.empty());
                    BoundedGameTestWait.succeedAfterObservation(helper);
                }, () -> {
                    if (track.baseline == null || track.samples < 16)
                        throw diagnostic("bounded observation expired before baseline plus 16 samples",
                                helper, fixture, body, track, reference, omega);
                });
            });
        });
    }

    private static boolean nativeSupport(net.minecraft.world.entity.LivingEntity body, java.util.UUID frameId) {
        var info = ((EntityMovementExtension) (Object) body).sable$getCollisionInfo();
        var point = LoadedFrameRayQuery.currentActorPoint(body.level(), body);
        return info != null && info.verticalCollisionBelow && body.onGround() && point != null
                && frameId.equals(point.frameId());
    }

    private static Vector3d storage(LoadedFrameRayQuery.ActorPoint point) {
        return new Vector3d(point.storagePoint().x, point.storagePoint().y, point.storagePoint().z);
    }

    private static float wrapYaw(float yaw) {
        return (yaw + 180f) % 360f - 180f;
    }

    private static GameTestAssertException diagnostic(String reason, GameTestHelper helper,
            MovingCabinAuthorityFixture.Ready fixture, PlayerCharacterHarnessEntity body,
            Object rawTrack, GhostControlPayloads.SupportedMovementReference reference, Vector3d omega) {
        String state = rawTrack instanceof TrackView view ? view.describe() : rawTrack.toString();
        return new GameTestAssertException(reason + "; frame=" + fixture.plot().engineId() + " ref=" + reference
                + " body=" + body.position() + " rawYaw=" + body.getYRot() + " omega=" + omega + " track=" + state);
    }

    private interface TrackView { String describe(); }
}
