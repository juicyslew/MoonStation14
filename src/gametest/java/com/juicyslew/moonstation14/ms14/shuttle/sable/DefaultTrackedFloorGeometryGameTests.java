package com.juicyslew.moonstation14.ms14.shuttle.sable;

import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery;
import com.juicyslew.moonstation14.ms14.movement.MovementVector;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.joml.Quaterniond;
import org.joml.Vector3d;

/** Actual SDK callback payload capture for a tracked floor without opt-in observation. */
@PrefixGameTestTemplate(false)
@GameTestTags({"shuttle-transient-floor-side-regression", "shuttle"})
public final class DefaultTrackedFloorGeometryGameTests {
    private DefaultTrackedFloorGeometryGameTests() { }

    @GameTest(templateNamespace="moonstation14", template="empty", timeoutTicks=650)
    public static void axisAlignedFloorGeometryCapturedWithoutObservation(GameTestHelper helper) {
        MovingCabinAuthorityFixture.start(helper, new BlockPos(3520, 300, 1860), "floor-geometry",
                2.5, 1, 2.5, (fixture, body) -> {
            var frame = fixture.plot().subLevel();
            var identity = new Quaterniond().identity();
            frame.logicalPose().orientation().identity();
            RigidBodyHandle.of(frame).teleport(frame.logicalPose().position(), identity);
            frame.updateLastPose();
            frame.updateBoundingBox();
            var localFeet = new Vector3d(fixture.cabin().origin().getX() + 2.5,
                    fixture.cabin().origin().getY() + 1, fixture.cabin().origin().getZ() + 2.5);
            var worldFeet = frame.logicalPose().transformPosition(localFeet);
            body.moveTo(worldFeet.x, worldFeet.y, worldFeet.z, body.getYRot(), body.getXRot());
            var point = LoadedFrameRayQuery.currentActorPoint(fixture.level(), body);
            var move = SableOwnedBodyMotion.move(body, new MovementVector(0, -.001, 0),
                    new SableOwnedBodyMotion.MoveOptions(false, point,
                            SableOwnedBodyMotion.CollisionPosePolicy.CURRENT_FRAME_GEOMETRY,
                            SableOwnedBodyMotion.BodyPosePolicy.TRACKED_CHARACTER, false));
            var scope = move.collisionScope();
            var trace = scope == null ? null : scope.nativeCollisionTrace();
            String context = "point=" + point + ", scope=" + scope + ", trace=" + trace
                    + ", framePose=" + frame.logicalPose() + ", start=" + worldFeet
                    + ", end=" + body.position() + ", onGround=" + body.onGround();
            require(move.collisionInfoFresh() && move.collisionInfo() != null && scope != null
                            && point != null && fixture.plot().engineId().equals(point.frameId()),
                    "actual owned move must retain fresh frame-owned collision info; " + context);
            require(!scope.geometryObserved() && scope.nativeCollisionTraceComplete()
                            && scope.geometryTraceComplete() && trace != null && !trace.isEmpty(),
                    "capture must be complete and non-observing; " + context);
            require(trace.stream().allMatch(row -> row.geometry() != null),
                    "every actual native trace row must carry geometry; " + context);
            require(trace.stream().anyMatch(row -> row.verticalCollision() && row.selectedY() > 0
                            && row.selectedX() == 0 && row.selectedZ() == 0 && row.geometry() != null
                            && row.geometry().maxMtv().equals(new MovementVector(row.selectedX(), row.selectedY(), row.selectedZ()))),
                    "axis-aligned positive-Y floor MTV must retain matching geometry payload; " + context);
            helper.succeed();
        });
    }

    private static void require(boolean value, String message) {
        if (!value) throw new GameTestAssertException(message);
    }
}
