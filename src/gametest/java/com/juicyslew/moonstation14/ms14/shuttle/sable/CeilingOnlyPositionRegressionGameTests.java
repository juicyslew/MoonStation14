package com.juicyslew.moonstation14.ms14.shuttle.sable;

import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery;
import com.juicyslew.moonstation14.ms14.movement.MovementVector;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.minecraft.world.phys.Vec3;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import org.joml.Vector3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Live native owned-body regression for descending ceiling-only position correction. */
@GameTestTags({"shuttle-ceiling-side-regression", "shuttle"})
@PrefixGameTestTemplate(false)
public final class CeilingOnlyPositionRegressionGameTests {
    private static final Logger LOGGER = LoggerFactory.getLogger(CeilingOnlyPositionRegressionGameTests.class);
    private static final MovementVector REQUEST = new MovementVector(0, -.07544406705017148, 0);

    private CeilingOnlyPositionRegressionGameTests() { }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 650)
    public static void descendingCeilingOnlyPositionIsNotOwnAcceleration(GameTestHelper helper) {
        MovingCabinAuthorityFixture.start(helper, new BlockPos(3360, 300, 1860),
                "ceiling-only-position", 2.5, 1, 2.5, (fixture, body) -> {
            var plot = fixture.plot();
            var storage = fixture.cabin().origin();
            for (int x = 1; x <= 4; x++) for (int y = 0; y <= 4; y++) for (int z = 1; z <= 4; z++)
                writeOrVerify(helper, plot, storage.offset(x, y, z), Blocks.AIR.defaultBlockState(),
                        "clear bounded native test volume");
            BlockPos roof = storage.offset(2, 3, 2);
            writeOrVerify(helper, plot, roof, Blocks.STONE.defaultBlockState(), "author native roof cube");

            double feetY = 3 - body.getBbHeight() + .10;
            Vector3d localFeet = new Vector3d(storage.getX() + 2.5, storage.getY() + feetY,
                    storage.getZ() + 2.5);
            Vector3d worldFeet = plot.subLevel().logicalPose().transformPosition(localFeet);
            body.moveTo(worldFeet.x, worldFeet.y, worldFeet.z, body.getYRot(), body.getXRot());
            Vec3 before = body.position();
            var boundsBefore = body.getBoundingBox();
            var actorPoint = LoadedFrameRayQuery.currentActorPoint(fixture.level(), body);
            var move = SableOwnedBodyMotion.move(body, REQUEST, new SableOwnedBodyMotion.MoveOptions(false,
                    actorPoint, SableOwnedBodyMotion.CollisionPosePolicy.STOCK_NATIVE_INTERVAL,
                    SableOwnedBodyMotion.BodyPosePolicy.TRACKED_CHARACTER, true));
            var proof = move.nativeContactProofDiagnostic();
            var info = move.collisionInfo();
            var scope = move.collisionScope();
            var clip = scope == null ? null : scope.worldClipObservation();
            var correction = move.positionCorrection();
            var geometry = correction == null ? null : correction.geometryCorrection();
            var trace = scope == null ? null : scope.nativeCollisionTrace();
            String context = "proof=" + proof + ", native=" + (info == null ? null : info.motion)
                    + ", request=" + REQUEST + ", bounded=" + move.boundedOwnDisplacement()
                    + ", measured=" + move.measuredOwnDisplacement() + ", correction=" + correction
                    + ", trace=" + trace + ", clip=" + clip + ", before=" + before
                    + ", after=" + body.position() + ", bounds=" + boundsBefore + ", roof=" + roof;
            require(actorPoint != null && plot.engineId().equals(actorPoint.frameId()),
                    "owned actor must resolve to fixture frame; " + context);
            require(move.collisionInfoFresh() && info != null && scope != null
                            && scope.nativeCollisionTraceComplete() && scope.geometryTraceComplete()
                            && trace != null && !trace.isEmpty(),
                    "actual owned move must produce fresh complete native contact and geometry evidence; " + context);
            require(trace.stream().allMatch(row -> row.verticalCollision() && row.selectedY() < 0
                            && -row.selectedY() > Math.hypot(row.selectedX(), row.selectedZ())),
                    "all observed native contacts must be vertical ceiling responses, without side contact; " + context);
            require(proof != null && proof.branch() == NativeContactProofDiagnostic.Branch.CEILING_ONLY_POSITION
                            && proof.positionStage() == NativeContactProofDiagnostic.PositionStage.ACCEPTED
                            && proof.groundingDecision() == SableOwnedBodyMotion.GroundingDecision.UNCHANGED
                            && proof.traceComplete() && proof.geometryComplete(),
                    "live caller must accept ceiling-only position proof without airborne override; " + context);
            require(move.boundedOwnDisplacement().y() == REQUEST.y(),
                    "ceiling correction must not accelerate or attenuate bounded own Y; " + context);
            require(move.horizontalContactEvidence() == null && geometry != null && geometry.y() < 0,
                    "position correction must be negative ceiling geometry only, with no horizontal contact; " + context);
            require(Math.abs(move.measuredOwnDisplacement().y()
                            - (move.boundedOwnDisplacement().y() + geometry.y())) <= 1e-6,
                    "actual committed displacement must account for bounded own motion plus geometry correction; " + context);
            require(clip != null && clip.complete() && !clip.downwardFloorClip()
                            && clip.matchesCall(body, new Vec3(info.motion.x, info.motion.y, info.motion.z),
                                    new Vec3(move.measuredOwnDisplacement().x(), move.measuredOwnDisplacement().y(),
                                            move.measuredOwnDisplacement().z()), before, boundsBefore),
                    "world clip observation must be complete, from this call, and not floor clipping; " + context);

            // Ordinary upward roof clipping remains the established nonnegative response path.
            placeAtLocalFeet(fixture, body, 3 - body.getBbHeight() - .001);
            var ordinary = performMove(fixture, body, new MovementVector(0, .42, 0));
            String ordinaryContext = describe(ordinary, body, roof);
            var ordinaryGeo = ordinary.move.positionCorrection() == null ? null
                    : ordinary.move.positionCorrection().geometryCorrection();
            require(ordinary.move.collisionInfoFresh() && ordinary.proof != null
                            && ordinary.proof.branch() != NativeContactProofDiagnostic.Branch.CEILING_ONLY_POSITION
                            && ordinary.move.collisionInfo().motion.y >= 0
                            && ordinary.move.boundedOwnDisplacement().y() >= 0
                            && ordinary.move.boundedOwnDisplacement().y() <= .42
                            && (ordinaryGeo == null || ordinaryGeo.y() >= 0),
                    "positive roof-clipped control must not enter the negative ceiling-only domain; " + ordinaryContext);

            // The frame is translated once as authored fixture geometry, using a transformed point so
            // storage/COM offsets are included; physics and the native move then produce all evidence.
            var frame = plot.subLevel();
            double thirdFeetLocalY = 3 - body.getBbHeight() + .098;
            Vector3d local = new Vector3d(storage.getX() + 2.5, storage.getY() + thirdFeetLocalY,
                    storage.getZ() + 2.5);
            double authoredWorldFeet = frame.logicalPose().transformPosition(local).y;
            double frameShift = 302.002 - authoredWorldFeet;
            Vector3d authoredPosition = new Vector3d(frame.logicalPose().position()).add(0, frameShift, 0);
            var orientation = new org.joml.Quaterniond(frame.logicalPose().orientation());
            frame.logicalPose().position().set(authoredPosition);
            RigidBodyHandle.of(frame).teleport(authoredPosition, orientation);
            frame.updateLastPose();
            frame.updateBoundingBox();
            placeAtLocalFeet(fixture, body, thirdFeetLocalY);
            BlockPos floor = new BlockPos((int) Math.floor(body.getX()), 301, (int) Math.floor(body.getZ()));
            for (int x = floor.getX() - 1; x <= floor.getX() + 1; x++)
                for (int z = floor.getZ() - 1; z <= floor.getZ() + 1; z++)
                    fixture.level().setBlock(new BlockPos(x, 301, z), Blocks.AIR.defaultBlockState(), 3);
            fixture.level().setBlock(floor, Blocks.STONE.defaultBlockState(), 3);
            var beforeFloor = body.getBoundingBox();
            var floorTop = floor.getY() + 1d;
            require(beforeFloor.minY > floorTop && beforeFloor.maxX > floor.getX()
                            && beforeFloor.minX < floor.getX() + 1 && beforeFloor.maxZ > floor.getZ()
                            && beforeFloor.minZ < floor.getZ() + 1,
                    "third case must start clear above and overlap the actual ordinary floor cell: bounds="
                            + beforeFloor + ", floor=" + floor + ", pose=" + frame.logicalPose());
            Vec3 beforeThird = body.position();
            MovementVector floorClipRequest = new MovementVector(.02, .42, .01);
            var clipped = performMove(fixture, body, floorClipRequest);
            var clipContext = describe(clipped, body, floor);
            var clippedScope = clipped.move.collisionScope();
            var worldClip = clippedScope == null ? null : clippedScope.worldClipObservation();
            var nativeMotion = clipped.move.collisionInfo() == null ? null : clipped.move.collisionInfo().motion;
            var measured = clipped.move.measuredOwnDisplacement();
            var clippedGeometry = clipped.move.positionCorrection() == null ? null
                    : clipped.move.positionCorrection().geometryCorrection();
            require(clipped.move.collisionInfoFresh() && clippedScope != null
                            && clippedScope.nativeCollisionTraceComplete() && clippedScope.geometryTraceComplete()
                            && clippedScope.nativeCollisionTrace() != null && !clippedScope.nativeCollisionTrace().isEmpty()
                            && clippedScope.nativeCollisionTrace().stream().allMatch(row -> row.verticalCollision()
                                    && row.selectedY() < 0
                                    && -row.selectedY() > Math.hypot(row.selectedX(), row.selectedZ()))
                            && !clipped.move.collisionInfo().verticalCollisionBelow,
                    "third native evidence must be fresh ceiling-only contact with no native floor support; " + clipContext);
            require(clipped.proof != null
                            && clipped.proof.branch() == NativeContactProofDiagnostic.Branch.CEILING_ONLY_POSITION
                            && clipped.proof.positionStage() == NativeContactProofDiagnostic.PositionStage.ACCEPTED
                            && clipped.proof.groundingDecision() == SableOwnedBodyMotion.GroundingDecision.UNCHANGED
                            && clipped.move.boundedOwnDisplacement().y() == 0
                            && clipped.move.boundedOwnDisplacement().x() == floorClipRequest.x()
                            && clipped.move.boundedOwnDisplacement().z() == floorClipRequest.z()
                            && clipped.move.horizontalContactEvidence() == null
                            && clippedGeometry != null && clippedGeometry.y() < 0,
                    "positive request may accept only the ceiling-position correction, not own motion; " + clipContext);
            require(worldClip != null && worldClip.complete() && worldClip.downwardFloorClip()
                            && worldClip.input().y < worldClip.output().y && worldClip.output().y <= 0
                            && worldClip.input().x == worldClip.output().x
                            && worldClip.input().z == worldClip.output().z
                            && worldClip.matchesCall(body, new Vec3(nativeMotion.x, nativeMotion.y, nativeMotion.z),
                                    new Vec3(measured.x(), measured.y(), measured.z()), beforeThird, beforeFloor),
                    "ordinary world-floor clip must be the exact paired call evidence for this native move; "
                            + "worldClip=" + (worldClip == null ? null : "complete=" + worldClip.complete()
                                    + ", downwardFloorClip=" + worldClip.downwardFloorClip()
                                    + ", input=" + worldClip.input() + ", output=" + worldClip.output())
                            + "; " + clipContext);

            // Safety/diagnostic control: repeat from the same authored third-case pose with zero XZ.
            placeAtLocalFeet(fixture, body, thirdFeetLocalY);
            Vec3 zeroBefore = body.position();
            var zeroBounds = body.getBoundingBox();
            MovementVector zeroRequest = new MovementVector(0, .42, 0);
            var zero = performMove(fixture, body, zeroRequest);
            var zeroMove = zero.move;
            var zeroInfo = zeroMove.collisionInfo();
            var zeroScope = zeroMove.collisionScope();
            var zeroClip = zeroScope == null ? null : zeroScope.worldClipObservation();
            var zeroNative = zeroInfo == null ? null : zeroInfo.motion;
            var zeroMeasured = zeroMove.measuredOwnDisplacement();
            var zeroProof = zero.proof;
            boolean zeroCallBound = zeroClip != null && zeroNative != null
                    && zeroClip.matchesCall(body, new Vec3(zeroNative.x, zeroNative.y, zeroNative.z),
                    new Vec3(zeroMeasured.x(), zeroMeasured.y(), zeroMeasured.z()), zeroBefore, zeroBounds);
            boolean normalizationObserved = zeroClip != null && zeroClip.input() != null && zeroClip.output() != null
                    && (zeroClip.input().x != zeroClip.output().x || zeroClip.input().z != zeroClip.output().z);
            LOGGER.info("[ceiling-zero-axis-witness] rawin={} rawout={} floorClip={} complete={} currentCallBound={} native={} measured={} request={} before={} beforeBounds={} body.onGround={} branch={} reason={} positionStage={} rowcount={} normalizationObserved={}",
                    zeroClip == null ? null : zeroClip.input(), zeroClip == null ? null : zeroClip.output(),
                    zeroClip != null && zeroClip.downwardFloorClip(), zeroClip != null && zeroClip.complete(),
                    zeroCallBound, zeroInfo == null ? null : zeroInfo.motion, zeroMeasured, zeroRequest, zeroBefore,
                    zeroBounds, body.onGround(), zeroProof == null ? null : zeroProof.branch(),
                    zeroProof == null ? null : zeroProof.reason(),
                    zeroProof == null ? null : zeroProof.positionStage(),
                    zeroScope == null || zeroScope.nativeCollisionTrace() == null ? 0 : zeroScope.nativeCollisionTrace().size(),
                    normalizationObserved);
            require(zeroClip != null && zeroClip.complete() && zeroCallBound && zeroScope != null
                            && zeroScope.nativeCollisionTraceComplete() && zeroScope.geometryTraceComplete()
                            && zeroScope.nativeCollisionTrace() != null && !zeroScope.nativeCollisionTrace().isEmpty()
                            && zeroScope.nativeCollisionTrace().stream().allMatch(row -> row.verticalCollision()
                                    && row.selectedY() < 0 && -row.selectedY() > Math.hypot(row.selectedX(), row.selectedZ())),
                    "zero-axis diagnostic requires complete current native/clip evidence; " + describe(zero, body, floor));
            if (normalizationObserved) {
                var in = zeroClip.input(); var out = zeroClip.output();
                require(zeroClip.downwardFloorClipWithUnchangedOrVanillaZeroedXZ() && zeroProof != null
                                && zeroProof.branch() == NativeContactProofDiagnostic.Branch.CEILING_ONLY_POSITION
                                && zeroProof.positionStage() == NativeContactProofDiagnostic.PositionStage.ACCEPTED
                                && zeroMove.positionCorrection() != null
                                && zeroMove.boundedOwnDisplacement().y() == 0
                                && zeroMove.boundedOwnDisplacement().x() == zeroRequest.x()
                                && zeroMove.boundedOwnDisplacement().z() == zeroRequest.z()
                                && zeroMove.horizontalContactEvidence() == null
                                && zeroProof.groundingDecision() == SableOwnedBodyMotion.GroundingDecision.UNCHANGED,
                        "verified vanilla zero-axis normalization must accept ceiling position only; " + describe(zero, body, floor));
            } else {
                require(zeroProof != null && zeroProof.branch() == NativeContactProofDiagnostic.Branch.CEILING_ONLY_POSITION
                                && zeroProof.positionStage() == NativeContactProofDiagnostic.PositionStage.ACCEPTED
                                && zeroMove.positionCorrection() != null,
                        "identity floor-only control must retain accepted ceiling proof; " + describe(zero, body, floor));
            }
            helper.succeed();
        });
    }

    private record Observed(SableOwnedBodyMotion.MoveResult move, NativeContactProofDiagnostic proof) { }

    private static Observed performMove(MovingCabinAuthorityFixture.Ready fixture,
            com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessEntity body,
            MovementVector request) {
        var point = LoadedFrameRayQuery.currentActorPoint(fixture.level(), body);
        var move = SableOwnedBodyMotion.move(body, request, new SableOwnedBodyMotion.MoveOptions(false, point,
                SableOwnedBodyMotion.CollisionPosePolicy.STOCK_NATIVE_INTERVAL,
                SableOwnedBodyMotion.BodyPosePolicy.TRACKED_CHARACTER, true));
        return new Observed(move, move.nativeContactProofDiagnostic());
    }

    private static void placeAtLocalFeet(MovingCabinAuthorityFixture.Ready fixture,
            com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessEntity body,
            double feetY) {
        var storage = fixture.cabin().origin();
        Vector3d local = new Vector3d(storage.getX() + 2.5, storage.getY() + feetY, storage.getZ() + 2.5);
        Vector3d point = fixture.plot().subLevel().logicalPose().transformPosition(local);
        body.moveTo(point.x, point.y, point.z, body.getYRot(), body.getXRot());
    }

    private static String describe(Observed observed,
            com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessEntity body,
            BlockPos block) {
        var move = observed.move;
        return "proof=" + observed.proof + ", request=" + move.requestedOwnDisplacement()
                + ", native=" + (move.collisionInfo() == null ? null : move.collisionInfo().motion)
                + ", bounded=" + move.boundedOwnDisplacement() + ", measured=" + move.measuredOwnDisplacement()
                + ", correction=" + move.positionCorrection() + ", clip="
                + (move.collisionScope() == null ? null : move.collisionScope().worldClipObservation())
                + ", body=" + body.position() + ", bounds=" + body.getBoundingBox() + ", block=" + block;
    }

    private static void writeOrVerify(GameTestHelper helper, SableFixturePlot plot, BlockPos pos,
                                      net.minecraft.world.level.block.state.BlockState desired, String purpose) {
        var level = helper.getLevel();
        var before = level.getBlockState(pos);
        boolean written = plot.setStorageBlock(pos, desired);
        var actual = level.getBlockState(pos);
        require(actual.equals(desired) && (written || before.equals(desired)),
                purpose + " failed at " + pos + ": before=" + before + ", desired=" + desired
                        + ", after=" + actual + ", writeResult=" + written + ", plot=" + plot.engineId());
    }

    private static void require(boolean value, String message) {
        if (!value) throw new GameTestAssertException(message);
    }
}
