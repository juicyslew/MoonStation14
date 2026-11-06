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

/** Real native-frame floor edge/corner regression; no synthetic collision evidence is injected. */
@GameTestTags({"shuttle-transient-floor-side-regression"})
@PrefixGameTestTemplate(false)
public final class TransientFloorSideRegressionGameTests {
    private static final MovementVector REQUEST = new MovementVector(-.035250923117812784, -.001, -.279032139581343);
    private TransientFloorSideRegressionGameTests() { }

    @GameTest(templateNamespace="moonstation14", template="empty", timeoutTicks=650)
    public static void nativeOwnedFloorEdgeCornerClearsToAirborne(GameTestHelper helper) {
        runEdge(helper, false);
    }

    @GameTest(templateNamespace="moonstation14", template="empty", timeoutTicks=650)
    public static void finalNativeFloorSupportRejectsAirborneOverride(GameTestHelper helper) {
        runEdge(helper, true);
    }

    private static void runEdge(GameTestHelper helper, boolean finalSupport) {
        MovingCabinAuthorityFixture.start(helper, new BlockPos(3000, 300, 1660),
                finalSupport ? "transient-supported-control" : "transient-edge", 3.300000336,
                1.00000010724304, 1.867736224, 3, (fixture, human) -> {
            var plot = fixture.plot();
            var cabin = fixture.cabin();
            var storage = cabin.origin();
            // Remove authored cabin geometry in a bounded interior volume. The only native shapes left
            // in the movement corridor are the intended floor and side-corner cubes below.
            for (int x = 1; x <= 5; x++) for (int y = 0; y <= 4; y++) for (int z = 1; z <= 5; z++)
                writeOrVerify(helper, plot, storage.offset(x, y, z), Blocks.AIR.defaultBlockState(), "clear bounded native test volume");
            BlockPos floor = storage.offset(3, 0, 2);
            BlockPos side = storage.offset(2, 2, 2);
            writeOrVerify(helper, plot, floor, Blocks.STONE.defaultBlockState(), "author native floor cube");
            writeOrVerify(helper, plot, side, Blocks.STONE.defaultBlockState(), "author native side/corner cube");
            if (finalSupport) writeOrVerify(helper, plot, storage.offset(3, 0, 1),
                    Blocks.STONE.defaultBlockState(), "author final-foot support control");

            // Reposition only in fixture setup, into the recorded edge pose; subsequent displacement
            // and all collision evidence come from the actual Sable-owned body.move call.
            Vector3d start = plot.subLevel().logicalPose().transformPosition(
                    new Vector3d(storage.getX() + 3.300000336, storage.getY() + 1.00000010724304,
                            storage.getZ() + 1.867736224));
            human.moveTo(start.x, start.y, start.z, human.getYRot(), human.getXRot());
            var point = LoadedFrameRayQuery.currentActorPoint(fixture.level(), human);
            var result = SableOwnedBodyMotion.move(human, REQUEST,
                    new SableOwnedBodyMotion.MoveOptions(false, point,
                            SableOwnedBodyMotion.CollisionPosePolicy.CURRENT_FRAME_GEOMETRY,
                            SableOwnedBodyMotion.BodyPosePolicy.TRACKED_CHARACTER, false, false));
            var proof = result.nativeContactProofDiagnostic();
            String context = "finalSupport=" + finalSupport + ", proof=" + proof + ", result=" + result
                    + ", pose=" + plot.subLevel().logicalPose() + ", storage=" + storage
                    + ", floor=" + floor + ", side=" + side + ", start=" + start
                    + ", end=" + human.position() + ", box=" + human.getBoundingBox();
            if (!finalSupport) {
                require(proof != null && proof.branch() == NativeContactProofDiagnostic.Branch.TRANSIENT_FLOOR_SIDE_AIRBORNE
                                && proof.positionStage() == NativeContactProofDiagnostic.PositionStage.ACCEPTED
                                && proof.groundingDecision() == SableOwnedBodyMotion.GroundingDecision.TRANSIENT_FLOOR_SIDE_AIRBORNE
                                && proof.traceComplete() && proof.geometryComplete() && !human.onGround(),
                        "native owned-body floor-edge/corner proof must accept and become airborne; " + context);
            } else {
                require(proof == null || proof.branch() != NativeContactProofDiagnostic.Branch.TRANSIENT_FLOOR_SIDE_AIRBORNE
                                || proof.positionStage() != NativeContactProofDiagnostic.PositionStage.ACCEPTED
                                || proof.groundingDecision() != SableOwnedBodyMotion.GroundingDecision.TRANSIENT_FLOOR_SIDE_AIRBORNE,
                        "final native support must not admit transient airborne override; " + context);
                require(human.onGround(), "native final-support control must not force airborne; " + context);
            }
            helper.succeed();
        });
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
