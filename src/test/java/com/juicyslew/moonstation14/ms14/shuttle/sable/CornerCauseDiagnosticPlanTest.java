package com.juicyslew.moonstation14.ms14.shuttle.sable;

import org.joml.Quaterniond;
import org.joml.AxisAngle4d;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import com.juicyslew.moonstation14.ms14.movement.MovementVector;
import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;

class CornerCauseDiagnosticPlanTest {
    @Test void runtimeYawWrapsToMotorRangeAndMapsForwardWishIntoLocalInsideCorner() {
        for (double yaw : new double[] {0, Math.PI / 2, -Math.PI / 2, Math.toRadians(179), Math.toRadians(-179), Math.PI, -Math.PI}) {
            var frame = new Quaterniond(new AxisAngle4d(yaw, 0, 1, 0));
            float inputYaw = CornerCauseDiagnosticPlan.inputYawForDeck(frame);
            assertTrue(inputYaw >= -180 && inputYaw <= 180, "yaw=" + yaw + " input=" + inputYaw);
            double radians = Math.toRadians(inputYaw);
            var wish = new org.joml.Vector3d(-Math.sin(radians), 0, Math.cos(radians));
            frame.conjugate().transform(wish);
            assertTrue(wish.x > 0 && wish.z > 0, "frame yaw=" + yaw + " local wish=" + wish);
        }
    }

    @Test void jumpReleaseAndAirborneLookLatchSurvivesChangingFrameLook() {
        var approach = CornerCauseDiagnosticPlan.input(true, 7, false);
        assertFalse(approach.jump()); assertEquals(1000, approach.wishZ());
        var launch = CornerCauseDiagnosticPlan.input(true, 8, false);
        assertTrue(launch.jump()); assertEquals(1000, launch.wishZ());
        var released = CornerCauseDiagnosticPlan.input(false, 9, true);
        assertFalse(released.jump()); assertEquals(1000, released.wishZ());
        var latch = new CornerCauseDiagnosticPlan.LookLatch();
        assertEquals(-45f, latch.select(true, -45f));
        latch.latchDeparture(-45f);
        float departureLook = latch.select(false, -45f);
        assertEquals(-45f, departureLook);
        assertEquals(departureLook, latch.select(false, 120f));
        assertEquals(departureLook, latch.select(false, 0f));
        assertEquals(departureLook, latch.frozenBaseInputYaw(), "base input yaw remains latched until landing");
        assertEquals(35f, latch.select(true, 35f));
        assertEquals(80, CornerCauseDiagnosticPlan.MAX_TICKS);
    }

    @Test void compoundNeedsTwoIndependentSameFrameSideRowsNotOneDiagonalOrWrongFrame() {
        UUID frame = UUID.randomUUID();
        var x = row(frame, new BlockPos(19, 3, 11), new MovementVector(1, 0, 0));
        var z = row(frame, new BlockPos(18, 3, 12), new MovementVector(0, 0, 1));
        assertTrue(CornerCauseDiagnosticPlan.classify(List.of(x, z), frame, 0, 0).compound());
        var rotatedX = row(frame, new BlockPos(19, 3, 11), new MovementVector(0, 0, 1));
        var rotatedZ = row(frame, new BlockPos(18, 3, 12), new MovementVector(-1, 0, 0));
        assertTrue(CornerCauseDiagnosticPlan.classify(List.of(rotatedX, rotatedZ), frame, 0, 0).compound());
        var diagonal = row(frame, new BlockPos(19, 3, 11), new MovementVector(1, 0, 1));
        assertFalse(CornerCauseDiagnosticPlan.classify(List.of(diagonal), frame, 0, 0).compound());
        var cornerBlock = row(frame, new BlockPos(19, 3, 12), new MovementVector(1, 0, 1));
        assertFalse(CornerCauseDiagnosticPlan.classify(List.of(cornerBlock), frame, 0, 0).compound());
        assertFalse(CornerCauseDiagnosticPlan.classify(List.of(x, z), UUID.randomUUID(), 0, 0).compound());
        var sameFaceOrthogonal = row(frame, new BlockPos(19, 3, 10), new MovementVector(0, 0, 1));
        assertFalse(CornerCauseDiagnosticPlan.classify(List.of(x, sameFaceOrthogonal), frame, 0, 0).compound(),
                "independent normals on one wall face are not a corner compound");
        assertFalse(CornerCauseDiagnosticPlan.classify(List.of(x, row(frame, new BlockPos(17, 3, 12),
                new MovementVector(0, 1, 0))), frame, 0, 0).compound());
    }

    @Test void supportContextAcceptsWorldAirButRequiresExactGroundedFrameAndRejectsOtherFrame() {
        UUID frame = UUID.randomUUID(), other = UUID.randomUUID();
        assertTrue(CornerCauseDiagnosticPlan.supportContextValid(false, true, frame, null, frame));
        assertTrue(CornerCauseDiagnosticPlan.supportContextValid(false, true, frame, frame, frame));
        assertFalse(CornerCauseDiagnosticPlan.supportContextValid(false, false, frame, null, frame), "missing point is not WORLD");
        assertFalse(CornerCauseDiagnosticPlan.supportContextValid(true, true, frame, null, null));
        assertFalse(CornerCauseDiagnosticPlan.supportContextValid(false, true, frame, other, frame));
        assertFalse(CornerCauseDiagnosticPlan.supportContextValid(false, true, frame, null, other));
    }

    @Test void onlySameMoveAirbornePureSideCompoundCountsAsTargetCoverage() {
        var pureSide = NativeContactProofDiagnostic.Branch.PURE_SIDE;
        var otherBranch = NativeContactProofDiagnostic.Branch.ASCENDING_FLOOR_AIRBORNE;
        assertTrue(CornerCauseDiagnosticPlan.pureAirCompound(true, false, pureSide));
        assertFalse(CornerCauseDiagnosticPlan.pureAirCompound(true, true, pureSide), "ground compound cannot satisfy target");
        assertFalse(CornerCauseDiagnosticPlan.pureAirCompound(false, false, pureSide), "different air-side row is not a compound");
        assertFalse(CornerCauseDiagnosticPlan.pureAirCompound(true, false, otherBranch), "other proof branch is not PURE_SIDE");
    }

    @Test void taggedGameTestConsumesThePlanForForwardLookAndNativeStep() throws Exception {
        Path root = Path.of(System.getProperty("user.dir"));
        while (root != null && !Files.exists(root.resolve("src/gametest/java/com/juicyslew/moonstation14/ms14/shuttle/sable/ShuttleCornerCauseDiagnosticGameTests.java"))) root = root.getParent();
        assertNotNull(root);
        String entry = Files.readString(root.resolve("src/gametest/java/com/juicyslew/moonstation14/ms14/shuttle/sable/ShuttleCornerCauseDiagnosticGameTests.java"));
        assertTrue(entry.contains("@GameTestTags(\"shuttle-corner-cause-diagnostic\")"));
        assertTrue(entry.contains("SableOwnedBodyCarryGameTests.cornerNeighborhoodNativeProofDiagnostic(helper)"));
        String source = Files.readString(root.resolve("src/gametest/java/com/juicyslew/moonstation14/ms14/shuttle/sable/SableOwnedBodyCarryGameTests.java"));
        int tag = source.indexOf("static void cornerNeighborhoodNativeProofDiagnostic");
        int method = source.indexOf("cornerNeighborhoodNativeProofDiagnostic", tag);
        int driver = source.indexOf("runCornerNeighborhoodSteps", method);
        int step = source.indexOf("GroundedHarnessWorldStep.observingNativeGeometry().step", driver);
        assertTrue(tag >= 0 && method > tag && driver > method && step > driver);
        String driverSource = source.substring(driver, source.indexOf("private static boolean finitePose", driver));
        assertTrue(driverSource.contains("CornerCauseDiagnosticPlan.input(grounded, groundedApproach, jumpAttempted)"));
        assertTrue(driverSource.contains("input.wishX(), input.wishZ()"));
        assertTrue(driverSource.contains("CornerCauseDiagnosticPlan.inputYawForDeck(frame.logicalPose().orientation())"));
        assertTrue(driverSource.contains("lookLatch.select(grounded, baseInputYaw)"));
        assertTrue(driverSource.contains("CornerCauseDiagnosticPlan.supportContextValid(grounded"));
        assertTrue(driverSource.contains("CornerCauseDiagnosticPlan.pureAirCompound(contacts.compound(), human.onGround()"));
        assertTrue(driverSource.contains("sameMovePureAirCompounds"));
        assertTrue(driverSource.contains("lookLatch.latchDeparture(baseInputYaw)"));
        assertTrue(driverSource.contains("CornerCauseDiagnosticPlan.classify"));
        assertTrue(driverSource.contains("worldTime != previousWorldTime + 1"));
        int floorWait = source.indexOf("awaitManualDoorwayFloorContact(helper, fixture.level(), human", method);
        int floorCapture = source.indexOf("long floorReadyWorldTime = fixture.level().getGameTime()", floorWait);
        int scheduled = source.indexOf("helper.runAfterDelay(1, () -> runCornerNeighborhoodSteps", floorCapture);
        assertTrue(floorWait >= 0 && floorCapture > floorWait && scheduled > floorCapture,
                "the floor-ready clock must be captured before scheduling the next-tick driver");
        String scheduledCallback = source.substring(scheduled, source.indexOf("});", scheduled) + 3);
        assertTrue(scheduledCallback.contains("floorReadyWorldTime"));
        assertFalse(scheduledCallback.contains("fixture.level().getGameTime()"),
                "callback-time clock capture would compare the current tick to itself");
    }

    private static CurrentFrameCollisionScope.NativeCollisionTrace row(UUID frame, BlockPos pos, MovementVector mtv) {
        var geometry = new NativeCollisionGeometry(new MovementVector(0, 0, 0), new MovementVector(1, 2, 1),
                new NativeCollisionGeometry.QuaternionValue(0, 0, 0, 1), new NativeFrameInterval.PoseValue(
                        new double[] {1,0,0,0,0,1,0,0,0,0,1,0,0,0,0,1}),
                new AABB(pos.getX(), pos.getY(), pos.getZ(), pos.getX()+1, pos.getY()+1, pos.getZ()+1),
                mtv, MovementVector.ZERO, 0, 6);
        return new CurrentFrameCollisionScope.NativeCollisionTrace(frame, 0, 6, 0, pos, Blocks.STONE.defaultBlockState(),
                "stone", "stone", 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, geometry, false);
    }
}
