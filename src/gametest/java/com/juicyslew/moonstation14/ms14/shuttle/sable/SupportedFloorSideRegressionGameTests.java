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
import org.joml.Vector3d;

/** Native grounded admission: retain the native grounding authority while accepting a later floor row. */
@PrefixGameTestTemplate(false)
@GameTestTags({"shuttle", "shuttle-supported-floor-side"})
public final class SupportedFloorSideRegressionGameTests {
    private SupportedFloorSideRegressionGameTests() { }

    @GameTest(templateNamespace="moonstation14", template="empty", timeoutTicks=260)
    public static void groundedSideFirstThenFloorIsAdmitted(GameTestHelper helper) {
        BlockPos origin = new BlockPos(6800, 300, 6000);
        MovingCabinAuthorityFixture.start(helper, origin, "supported-floor-side", 2.78, 1, 2.5, (fixture, body) -> {
            var plot = fixture.plot();
            var frame = plot.subLevel();
            frame.logicalPose().orientation().identity();
            var identity = new org.joml.Quaterniond().identity();
            dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle.of(frame).teleport(frame.logicalPose().position(), identity);
            frame.updateLastPose(); frame.updateBoundingBox();
            BlockPos block = fixture.cabin().origin();
            for (int x=1;x<=5;x++) for (int y=0;y<=3;y++) for (int z=1;z<=5;z++)
                write(helper, plot, block.offset(x,y,z), Blocks.AIR.defaultBlockState());
            write(helper, plot, block.offset(2,0,2), Blocks.STONE.defaultBlockState());
            write(helper, plot, block.offset(3,1,2), Blocks.STONE.defaultBlockState());
            Vector3d localFeet = frame.logicalPose().transformPosition(new Vector3d(block.getX()+2.78,
                    block.getY()+1, block.getZ()+2.5));
            body.moveTo(localFeet.x, localFeet.y, localFeet.z, body.getYRot(), body.getXRot());
            var before = LoadedFrameRayQuery.currentActorPoint(fixture.level(), body);
            require(body.onGround(), "precondition must be genuinely natively grounded before request");
            var beforePosition = body.position();
            var requested = new MovementVector(.001, -.001, 0);
            var result = SableOwnedBodyMotion.move(body, requested,
                    new SableOwnedBodyMotion.MoveOptions(false, before,
                            SableOwnedBodyMotion.CollisionPosePolicy.CURRENT_FRAME_GEOMETRY,
                            SableOwnedBodyMotion.BodyPosePolicy.TRACKED_CHARACTER, false, false));
            var trace = result.collisionScope() == null ? null : result.collisionScope().nativeCollisionTrace();
            var info = result.collisionInfo();
            var diagnostic = result.nativeContactProofDiagnostic();
            require(result.collisionInfoFresh() && result.collisionScope().nativeCollisionTraceComplete()
                            && result.collisionScope().geometryTraceComplete(), "complete native proof capture required: "+diagnostic);
            require(trace.getFirst().frameId().equals(frame.getUniqueId()) && !trace.getFirst().verticalCollision()
                            && trace.stream().skip(1).anyMatch(row -> row.verticalCollision() && row.selectedY()>0
                                    && row.geometry()!=null && row.selectedPos()!=null),
                    "actual selected rows must be SIDE first then geometric positive floor: "+trace);
            require(diagnostic.branch()==NativeContactProofDiagnostic.Branch.SUPPORTED_FLOOR_SIDE
                            && diagnostic.positionStage()==NativeContactProofDiagnostic.PositionStage.ACCEPTED,
                    "supported branch position must admit with full proof: "+diagnostic);
            require(body.onGround() && diagnostic.groundingDecision()==SableOwnedBodyMotion.GroundingDecision.UNCHANGED,
                    "native grounded status/decision must remain untouched");
            require(Math.abs(body.getY()-beforePosition.y) <= .001001,
                    "bounded downward own movement only; no minted Y support or impulse");
            require(info != null && info.verticalCollisionBelow && info.trackingSubLevel==frame,
                    "native grounded authority remains present");
            helper.succeed();
        });
    }

    private static void write(GameTestHelper helper, SableFixturePlot plot, BlockPos pos,
            net.minecraft.world.level.block.state.BlockState state) {
        plot.setStorageBlock(pos, state);
        if (!helper.getLevel().getBlockState(pos).equals(state)) throw new GameTestAssertException("fixture write failed: "+pos);
    }
    private static void require(boolean ok, String message) { if (!ok) throw new GameTestAssertException(message); }
}
