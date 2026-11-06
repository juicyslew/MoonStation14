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
import org.joml.Quaterniond;
import org.joml.Vector3d;

/** Live ascending-floor admission controls using only native body.move collision evidence. */
@PrefixGameTestTemplate(false)
@GameTestTags({"shuttle", "shuttle-transient-floor-side-regression"})
public final class AscendingFloorAdmissionGameTests {
    private AscendingFloorAdmissionGameTests() { }

    @GameTest(templateNamespace="moonstation14", template="empty", timeoutTicks=260)
    public static void sideThenFloorAscendingMoveIsAdmitted(GameTestHelper helper) {
        runAscending(helper, Layout.SIDE_THEN_FLOOR, false);
    }

    @GameTest(templateNamespace="moonstation14", template="empty", timeoutTicks=260)
    public static void floorFirstAscendingMoveIsAdmitted(GameTestHelper helper) {
        runAscending(helper, Layout.FLOOR_FIRST, false);
    }

    @GameTest(templateNamespace="moonstation14", template="empty", timeoutTicks=260)
    public static void supportBandObstructedBodyClearAscendingControlIsAdmitted(GameTestHelper helper) {
        runAscending(helper, Layout.FLOOR_FIRST, true);
    }

    @GameTest(templateNamespace="moonstation14", template="empty", timeoutTicks=260)
    public static void bodyVolumeObstructionDeniesAscendingAuthorization(GameTestHelper helper) {
        runAscending(helper, Layout.FLOOR_FIRST, false,
                SableOwnedBodyMotion.CollisionPosePolicy.CURRENT_FRAME_GEOMETRY,
                new BlockPos(6800, 300, 6000), true);
    }

    @GameTest(templateNamespace="moonstation14", template="empty", timeoutTicks=260)
    public static void stockIntervalSideThenFloorAscendingMoveIsAdmitted(GameTestHelper helper) {
        runAscending(helper, Layout.SIDE_THEN_FLOOR, false,
                SableOwnedBodyMotion.CollisionPosePolicy.STOCK_NATIVE_INTERVAL,
                new BlockPos(6600, 300, 6000));
    }

    private enum Layout { SIDE_THEN_FLOOR, FLOOR_FIRST }

    private static void runAscending(GameTestHelper helper, Layout layout, boolean obstruct) {
        BlockPos worldOrigin = obstruct ? new BlockPos(6400, 300, 6000)
                : layout == Layout.SIDE_THEN_FLOOR ? new BlockPos(6000, 300, 6000)
                : new BlockPos(6200, 300, 6000);
        runAscending(helper, layout, obstruct,
                SableOwnedBodyMotion.CollisionPosePolicy.CURRENT_FRAME_GEOMETRY, worldOrigin);
    }

    private static void runAscending(GameTestHelper helper, Layout layout, boolean obstruct,
                                     SableOwnedBodyMotion.CollisionPosePolicy posePolicy, BlockPos worldOrigin) {
        runAscending(helper, layout, obstruct, posePolicy, worldOrigin, false);
    }

    private static void runAscending(GameTestHelper helper, Layout layout, boolean obstruct,
                                     SableOwnedBodyMotion.CollisionPosePolicy posePolicy, BlockPos worldOrigin,
                                     boolean bodyObstructionBoundary) {
        // The three existing fixtures author an identity logical pose for current-geometry
        // construction. STOCK_NATIVE_INTERVAL deliberately leaves the live SDK pose untouched.
        MovingCabinAuthorityFixture.start(helper, worldOrigin,
                "ascending-floor-" + layout + (obstruct ? "-blocked" : ""), 2.5, 1, 2.5,
                (fixture, body) -> {
            var plot = fixture.plot();
            var frame = plot.subLevel();
            if (posePolicy == SableOwnedBodyMotion.CollisionPosePolicy.CURRENT_FRAME_GEOMETRY) {
                frame.logicalPose().orientation().identity();
                var identity = new Quaterniond().identity();
                dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle.of(frame)
                        .teleport(frame.logicalPose().position(), identity);
                frame.updateLastPose();
                frame.updateBoundingBox();
            }

            BlockPos origin = fixture.cabin().origin();
            for (int x=1;x<=5;x++) for (int y=0;y<=4;y++) for (int z=1;z<=5;z++)
                write(helper, plot, origin.offset(x,y,z), Blocks.AIR.defaultBlockState(), "clear movement volume");

            // The SDK advances request/6 before SAT and selects the greatest MTV magnitude.
            // FLOOR_FIRST therefore starts at feet=.96 (first-step feet=.98, floor MTV=.02).
            // SIDE_THEN_FLOOR starts at centerX=2.78, feet=.96: the adjacent wall MTV=.06
            // wins over floor MTV=.02, then the same microstep still resolves the floor.
            BlockPos floor = origin.offset(2, 0, 2);
            write(helper, plot, floor, Blocks.STONE.defaultBlockState(), "author floor SAT cube");
            if (layout == Layout.SIDE_THEN_FLOOR)
                write(helper, plot, origin.offset(3, 1, 2), Blocks.STONE.defaultBlockState(), "author adjacent side SAT cube");

            double lx = layout == Layout.SIDE_THEN_FLOOR ? 2.78 : 2.5;
            double ly = obstruct ? .99 : .96;
            double lz = 2.5;
            Vector3d start = frame.logicalPose().transformPosition(new Vector3d(origin.getX()+lx,
                    origin.getY()+ly, origin.getZ()+lz));
            body.moveTo(start.x, start.y, start.z, body.getYRot(), body.getXRot());
            body.setOnGround(false);
            var before = LoadedFrameRayQuery.currentActorPoint(fixture.level(), body);
            var beforePosition = body.position();
            var beforeBounds = body.getBoundingBox();
            var localRequest = new Vector3d(layout == Layout.SIDE_THEN_FLOOR ? -.12 : 0,
                    obstruct ? .005 : .12, 0);
            if (posePolicy == SableOwnedBodyMotion.CollisionPosePolicy.STOCK_NATIVE_INTERVAL)
                new Quaterniond(frame.logicalPose().orientation()).transform(localRequest);
            var request = new MovementVector(localRequest.x, localRequest.y, localRequest.z);
            var result = SableOwnedBodyMotion.move(body, request,
                    new SableOwnedBodyMotion.MoveOptions(false, before,
                            posePolicy,
                            SableOwnedBodyMotion.BodyPosePolicy.TRACKED_CHARACTER, false, false));
            var proof = result.nativeContactProofDiagnostic();
            var scope = result.collisionScope();
            var trace = scope == null ? null : scope.nativeCollisionTrace();
            var info = result.collisionInfo();
            var clip = scope == null ? null : scope.worldClipObservation();
            var yaw = scope == null ? null : NativeActorYaw.capture(body, scope).orElse(null);
            var clearance = yaw == null ? NativeFinalFootClearance.Result.UNKNOWN
                    : NativeFinalFootClearance.classify(fixture.level(), body, yaw);
            var bodyClearance = yaw == null ? NativeFinalFootClearance.Result.UNKNOWN
                    : SableOwnedBodyMotion.queryTransientClearance(fixture.level(), body, yaw,
                            SableOwnedBodyMotion.TransientPolicy.ASCENDING);
            String context = "layout="+layout+", obstruct="+obstruct+", result="+result+", proof="+proof
                    +", trace="+trace+", native="+(info == null ? null : info.motion)+", measured="
                    +result.measuredOwnDisplacement()+", clip="+clip+", supportBand="+clearance
                    +", bodyVolume="+bodyClearance
                    +", policy="+posePolicy+", lastPose="+frame.lastPose()+", logicalPose="+frame.logicalPose()
                    +", frameOrientation="+frame.logicalPose().orientation()+", start="+start+", end="+body.position();
            require(result.collisionInfoFresh() && info != null && info.motion != null && scope != null && scope.nativeCollisionTraceComplete()
                            && scope.geometryTraceComplete() && trace != null && !trace.isEmpty(),
                    "must retain fresh complete native and geometry capture; "+context);
            require(result.collisionPosePolicy() == posePolicy && !scope.geometryObserved(),
                    "must retain explicitly selected pose policy with default-off observation; "+context);
            require(trace.stream().allMatch(row -> row.geometry()!=null && row.selectedPos()!=null
                            && row.selectedState()!=null && row.frameId().equals(plot.engineId())),
                    "all selected rows need real frame geometry/state; "+context);
            require(clip != null && clip.complete() && clip.input() != null && clip.output() != null
                            && clip.input().equals(clip.output()) && clip.matchesCall(body,
                            new net.minecraft.world.phys.Vec3(info.motion.x, info.motion.y, info.motion.z),
                            new net.minecraft.world.phys.Vec3(result.measuredOwnDisplacement().x(),
                                    result.measuredOwnDisplacement().y(), result.measuredOwnDisplacement().z()),
                            beforePosition, beforeBounds),
                    "exact-call identity world clip required; "+context);
            require(trace.stream().anyMatch(row -> row.verticalCollision() && row.selectedY()>0),
                    "native movement must have positive floor response; "+context);
            var first = trace.getFirst();
            require(info != null && info.firstCollisions != null
                            && info.firstCollisions.get(frame) != null,
                    "actual first-contact attestation missing; "+context);
            var firstContact = info.firstCollisions.get(frame);
            require(firstContact.horizontal() == !first.verticalCollision(),
                    "first selected row classification must agree with SDK first contact; "+context);
            if (layout == Layout.SIDE_THEN_FLOOR) {
                require(!first.verticalCollision() && firstContact.horizontal()
                                && trace.stream().skip(1).anyMatch(row -> row.verticalCollision() && row.selectedY()>0),
                        "authored SIDE-first layout must actually select side then positive floor; "+context);
            } else {
                require(first.verticalCollision() && first.selectedY()>0 && !firstContact.horizontal(),
                        "authored floor-first layout must actually select positive floor first; "+context);
            }
            var normal = firstContact.globalDirection();
            double length = Math.sqrt(first.selectedX()*first.selectedX()+first.selectedY()*first.selectedY()
                    +first.selectedZ()*first.selectedZ());
            require(length > 0 && Math.abs(normal.x()-first.selectedX()/length) <= com.juicyslew.moonstation14.ms14.movement.NativeCollisionSlop.MAX_RESIDUAL
                            && Math.abs(normal.y()-first.selectedY()/length) <= com.juicyslew.moonstation14.ms14.movement.NativeCollisionSlop.MAX_RESIDUAL
                            && Math.abs(normal.z()-first.selectedZ()/length) <= com.juicyslew.moonstation14.ms14.movement.NativeCollisionSlop.MAX_RESIDUAL,
                    "SDK first-contact normal must match normalized first selected MTV; "+context);
            var known = (java.util.function.Predicate<CurrentFrameCollisionScope.NativeCollisionTrace>) row ->
                    row.selectedPos() != null && row.selectedState() != null
                            && fixture.level().hasChunkAt(row.selectedPos())
                            && fixture.level().getBlockState(row.selectedPos()).equals(row.selectedState());
             var math = NativeSideContactProof.validateAscendingFloorDetailed(request,
                    new MovementVector(info.motion.x, info.motion.y, info.motion.z),
                     result.measuredOwnDisplacement(), plot.engineId(), trace, known, clip);
             if (bodyObstructionBoundary) {
                 require(math.result().isPresent() && proof != null
                                 && proof.branch()==NativeContactProofDiagnostic.Branch.ASCENDING_FLOOR_AIRBORNE
                                 && proof.clearanceObservation().authorization()==NativeContactProofDiagnostic.Authorization.AUTHORIZED
                                 && info == result.collisionInfo() && yaw != null
                                 && bodyClearance==NativeFinalFootClearance.Result.CLEAR,
                         "boundary test requires the real clear ascending native proof and current collision identity; "+context);
                 var candidate = new SableOwnedBodyMotion.TransientContactCandidate(math.result().orElseThrow(),
                         math, info, body.position(), body.getBoundingBox(), yaw, bodyClearance,
                         SableOwnedBodyMotion.TransientPolicy.ASCENDING);
                 require(SableOwnedBodyMotion.transientEndpointUnchanged(candidate, info,
                                 body.position(), body.getBoundingBox()),
                         "candidate endpoint must match the real body position/bounds before obstacle edit");
                 var obstacle = origin.offset(2, 2, 2);
                 write(helper, plot, obstacle, Blocks.STONE.defaultBlockState(), "author upper-body storage obstacle");
                 require(FrameBoxIntersection.intersects(body.getBoundingBox(),
                                 new net.minecraft.world.phys.AABB(obstacle), frame.logicalPose()),
                         "authored upper-body storage cube must geometrically overlap the unchanged body bounds");
                 var blocked = SableOwnedBodyMotion.queryTransientClearance(fixture.level(), body, yaw,
                         SableOwnedBodyMotion.TransientPolicy.ASCENDING);
                 var captureDenied = SableOwnedBodyMotion.transientCandidateAuthorization(
                         new SableOwnedBodyMotion.TransientContactCandidate(math.result().orElseThrow(), math,
                                 info, body.position(), body.getBoundingBox(), yaw, blocked,
                                 SableOwnedBodyMotion.TransientPolicy.ASCENDING), info, blocked);
                 var finalDenied = SableOwnedBodyMotion.transientCandidateAuthorization(candidate, info, blocked);
                 System.out.println("ascending-floor-case=body-volume-obstruction-authorization-boundary, mode=BODY_VOLUME"
                         +", overlap=true, captured="+blocked+", capturedAuthorization="+captureDenied
                         +", clearCaptureFinalAuthorization="+finalDenied);
                 require(blocked==NativeFinalFootClearance.Result.OBSTRUCTED
                                 && captureDenied==NativeContactProofDiagnostic.Authorization.DENIED_CAPTURE
                                 && finalDenied==NativeContactProofDiagnostic.Authorization.DENIED_FINAL,
                         "actual obstructed query must deny both captured and revalidated final clearance; capture="
                                 +captureDenied+", final="+finalDenied+", query="+blocked);
                 write(helper, plot, obstacle, Blocks.AIR.defaultBlockState(), "remove upper-body storage obstacle");
                 var cleared = SableOwnedBodyMotion.queryTransientClearance(fixture.level(), body, yaw,
                         SableOwnedBodyMotion.TransientPolicy.ASCENDING);
                 require(cleared==NativeFinalFootClearance.Result.CLEAR
                                 && SableOwnedBodyMotion.transientCandidateAuthorization(candidate, info, cleared)
                                 ==NativeContactProofDiagnostic.Authorization.AUTHORIZED,
                         "removing obstacle must restore clear final authorization");
                 helper.succeed();
                 return;
             }
            System.out.println("ascending-floor-case="+(obstruct ? "support-band-obstructed-body-clear-control" : "clear-control")
                    +", supportBand="+clearance+", bodyVolume="+bodyClearance+", proof="+proof
                    +", authorization="+(proof == null ? null : proof.clearanceObservation().authorization()));
            if (!obstruct) {
                require(math.result().isPresent() && clearance == NativeFinalFootClearance.Result.CLEAR
                                && proof != null && proof.proofGeometryCorrection() != null
                                && proof.proofGeometryCorrection().y()>0,
                        "clear path requires pure proof, clear final feet and accepted position evidence; "+context+", math="+math);
                // acceptedOwnDisplacement includes bounded geometric correction; the proof's
                // bounded own component must preserve the requested positive Y exactly.
                require(proof != null && proof.branch()==NativeContactProofDiagnostic.Branch.ASCENDING_FLOOR_AIRBORNE
                                && proof.positionStage()==NativeContactProofDiagnostic.PositionStage.ACCEPTED
                                && proof.groundingDecision()==SableOwnedBodyMotion.GroundingDecision.ASCENDING_FLOOR_AIRBORNE
                                && result.boundedOwnDisplacement().y()==request.y()
                                && proof.proofBoundedOwn().y()==request.y() && !body.onGround(),
                        "clear ascending floor contact should retain exact own-Y and admit airborne response; "+context);
            } else {
                require(math.result().isPresent(),
                        "ascending pure geometry proof must validate independently of final obstruction; "+context+", math="+math);
                require(math.result().isPresent() && yaw != null
                                && clearance == NativeFinalFootClearance.Result.OBSTRUCTED
                                && bodyClearance == NativeFinalFootClearance.Result.CLEAR,
                        "support band is obstructed while production ascending BODY_VOLUME query is clear; this is an admission control, not body-obstruction denial; "+context);
                require(proof != null && proof.branch()==NativeContactProofDiagnostic.Branch.ASCENDING_FLOOR_AIRBORNE
                                && proof.positionStage()==NativeContactProofDiagnostic.PositionStage.ACCEPTED
                                && proof.clearanceObservation().mode()==NativeContactProofDiagnostic.QueryMode.BODY_VOLUME
                                && proof.clearanceObservation().captured()==NativeFinalFootClearance.Result.CLEAR
                                && proof.clearanceObservation().finalResult()==NativeFinalFootClearance.Result.CLEAR
                                && proof.clearanceObservation().authorization()==NativeContactProofDiagnostic.Authorization.AUTHORIZED
                                && proof.groundingDecision()==SableOwnedBodyMotion.GroundingDecision.ASCENDING_FLOOR_AIRBORNE,
                        "support-band-only obstruction must not deny a clear BODY_VOLUME ascending candidate; "+context);
            }
            helper.succeed();
        });
    }

    private static void write(GameTestHelper helper, SableFixturePlot plot, BlockPos pos,
                              net.minecraft.world.level.block.state.BlockState state, String purpose) {
        var before=helper.getLevel().getBlockState(pos);
        boolean changed=plot.setStorageBlock(pos,state);
        var actual=helper.getLevel().getBlockState(pos);
        require(actual.equals(state) && (changed || before.equals(state)), purpose+" failed at "+pos
                +", before="+before+", after="+actual+", write="+changed);
    }

    private static void require(boolean ok, String message) {
        if (!ok) throw new GameTestAssertException(message);
    }
}
