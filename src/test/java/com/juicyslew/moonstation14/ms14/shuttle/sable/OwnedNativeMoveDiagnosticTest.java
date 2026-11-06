package com.juicyslew.moonstation14.ms14.shuttle.sable;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.UUID;
import com.juicyslew.moonstation14.ms14.movement.MovementVector;
import com.juicyslew.moonstation14.ms14.movement.ContactPositionCorrectionEvidence;
import com.juicyslew.moonstation14.ms14.movement.HorizontalContactEvidence;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.Test;

class OwnedNativeMoveDiagnosticTest {
    @Test void ambientDebugDoesNotEnableBulkGeometryFormatting() {
        assertFalse(OwnedNativeMoveDiagnostic.shouldDescribeRejection(false));
        assertTrue(OwnedNativeMoveDiagnostic.shouldDescribeRejection(true));
    }

    @Test void rejectionSummaryPrioritizesActualValuesAndFitsBeforeVerboseTrace() {
        var sampleRequest = new MovementVector(.0204376393, .248136, .2805064445);
        var sampleReturned = new MovementVector(.0204375888, .2736293634, .0380481987);
        var request = new MovementVector(0, .1, 0);
        var returned = new MovementVector(0, .1, 0);
        var correction = ContactPositionCorrectionEvidence.validateAscendingFloor(request, returned,
                new MovementVector(.01, .02, .03), null).orElseThrow();
        var contact = HorizontalContactEvidence.validate(new MovementVector(-1, 0, 0),
                new MovementVector(-.5, 0, 0), 1, 0).orElseThrow();
        var proof = new NativeContactProofDiagnostic(NativeContactProofDiagnostic.Branch.ASCENDING_FLOOR_AIRBORNE,
                true,true,true,true,true,true,true,true,true,true,NativeSideContactProof.Failure.NONE,0,
                request,returned,returned,returned,new MovementVector(.01,.02,.03),
                NativeContactProofDiagnostic.PositionStage.REJECTED,SableOwnedBodyMotion.GroundingDecision.ASCENDING_FLOOR_AIRBORNE,
                new NativeContactProofDiagnostic.ClearanceObservation(NativeContactProofDiagnostic.QueryMode.BODY_VOLUME,
                        NativeFinalFootClearance.Result.CLEAR,true,NativeFinalFootClearance.Result.CLEAR,
                        NativeContactProofDiagnostic.Authorization.AUTHORIZED));
        var move = new SableOwnedBodyMotion.MoveResult(request,returned, sampleReturned, MovementVector.ZERO,
                null,false,null,null,null,null,contact,null,correction,returned,false,false,MovementVector.ZERO,proof,null);
        String summary = OwnedNativeMoveDiagnostic.rejectionSummary(request, returned, true,
                "actual motor rejection reason", move);
        String message = summary + " " + "geometry=".repeat(500);
        assertTrue(summary.startsWith("motorRejection reason=\"actual motor rejection reason\""));
        assertTrue(summary.contains("motorRequested=" + request));
        assertTrue(summary.contains("motorReturnedActual=" + returned));
        assertTrue(summary.contains("groundedAtCatch=true"));
        assertTrue(summary.contains("nativeMeasured=" + sampleReturned + " nativeBounded=" + returned));
        assertTrue(summary.contains("positionCorrection=present:MovementVector[x=0.01, y=0.02, z=0.03]"));
        assertTrue(summary.contains("positionCorrectionMatches=false horizontalEvidence=present horizontalEvidenceMatches=false"));
        assertTrue(summary.contains("positionStage=REJECTED groundingDecision=ASCENDING_FLOOR_AIRBORNE"));
        assertTrue(summary.contains("clearance=BODY_VOLUME:CLEAR/same:CLEAR:AUTHORIZED"));
        assertTrue(summary.length() < 2000);
        assertTrue(message.substring(0, Math.min(2000, message.length())).contains("positionStage=REJECTED"));
        assertTrue(OwnedNativeMoveDiagnostic.rejectionSummary(sampleRequest, sampleReturned, true,
                "reported sample", null).contains("motorReturnedActual=" + sampleReturned));
        assertTrue(summary.length() < 2000);
    }

    @Test void summarizesCapturedFailureRowWithoutMutatingTrace() {
        var original = new CurrentFrameCollisionScope.NativeCollisionTrace(UUID.randomUUID(), 1, 2, 0,
                BlockPos.ZERO, Blocks.STONE.defaultBlockState(), "stone", "stone", .1, .2, .3,
                0, 0, 0, .4, .5, .6, .7, .8, .9, null, false);
        var trace = List.of(original);
        String summary = OwnedNativeMoveDiagnostic.failureRowSummary(trace, 0);
        assertTrue(summary.contains("selectedPos=" + BlockPos.ZERO));
        assertTrue(summary.contains("selected=0.1,0.2,0.3"));
        assertTrue(summary.contains("applied=0.4,0.5,0.6"));
        assertTrue(summary.contains("response=0.7,0.8,0.9"));
        assertTrue(summary.contains("geometry=absent"));
        assertFalse(summary.contains("frameId="), "compact failure-row format remains unchanged");
        assertSame(original, trace.get(0));
    }

    @Test void invalidAndMissingRowsAreExplicitlyUnavailable() {
        var trace = List.<CurrentFrameCollisionScope.NativeCollisionTrace>of();
        assertTrue(OwnedNativeMoveDiagnostic.failureRowSummary(trace, -1).contains("failureRowUnavailable(index=-1"));
        assertTrue(OwnedNativeMoveDiagnostic.failureRowSummary(trace, 0).contains("failureRowUnavailable(index=0"));
        assertTrue(OwnedNativeMoveDiagnostic.failureRowSummary(null, 0).contains("native trace absent"));
    }

    @Test void fullTraceRetainsFailureRowAndEveryOrderedRow() {
        var rows = new java.util.ArrayList<CurrentFrameCollisionScope.NativeCollisionTrace>();
        for (int i = 0; i < 4; i++) rows.add(new CurrentFrameCollisionScope.NativeCollisionTrace(UUID.randomUUID(),
                1, 1, i, new BlockPos(i, 0, 0), Blocks.STONE.defaultBlockState(), "block" + i, "state" + i,
                i, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, null, false));
        String full = OwnedNativeMoveDiagnostic.fullTraceSummary(List.copyOf(rows), 2);
        assertTrue(full.startsWith("failureRow[index=2,"));
        assertTrue(full.contains("fullTrace[rows=4, truncated=false]"));
        int previous = -1;
        for (int i = 0; i < rows.size(); i++) {
            int current = full.indexOf("row[index=" + i + ",");
            assertTrue(current > previous);
            assertTrue(full.contains("block" + i));
            previous = current;
        }
        assertTrue(OwnedNativeMoveDiagnostic.fullTraceSummary(null, 3).contains("native trace absent"));
    }

    @Test void fullTracePrintsEveryTopLevelRecordFieldIndependentOfNestedGeometry() {
        UUID firstFrame = UUID.randomUUID(), secondFrame = UUID.randomUUID();
        var firstGeometry = geometry(new MovementVector(-8, -9, -10), 90, 99);
        var secondGeometry = geometry(new MovementVector(-18, -19, -20), 91, 100);
        var first = completeRow(firstFrame, 1, 6, 3, new MovementVector(.11, .12, .13), true, firstGeometry);
        var second = completeRow(secondFrame, 4, 9, 8, new MovementVector(.41, .42, .43), false, secondGeometry);
        String full = OwnedNativeMoveDiagnostic.fullTraceSummary(List.of(first, second), 1);
        assertTrue(full.contains("failureRow[index=1,"));
        assertTrue(full.contains("rows=2, truncated=false"));
        assertTrue(full.contains("frameId=" + firstFrame + ",substep=1,substeps=6,iteration=3,preCollisionMotion=0.11,0.12,0.13,verticalCollision=true"));
        assertTrue(full.contains("frameId=" + secondFrame + ",substep=4,substeps=9,iteration=8,preCollisionMotion=0.41,0.42,0.43,verticalCollision=false"));
        assertTrue(full.indexOf("frameId=" + firstFrame) < full.indexOf("frameId=" + secondFrame));
        assertTrue(full.contains("geometry=NativeCollisionGeometry"));
        assertTrue(full.contains("iteration=90, substeps=99"));
        assertTrue(full.contains("iteration=91, substeps=100"));
        var missingGeometry = completeRow(firstFrame, 5, 6, 11, new MovementVector(3, 4, 5), false, null);
        String absent = OwnedNativeMoveDiagnostic.fullTraceSummary(List.of(missingGeometry), 0);
        assertTrue(absent.contains("frameId=" + firstFrame + ",substep=5,substeps=6,iteration=11,preCollisionMotion=3.0,4.0,5.0,verticalCollision=false"));
        assertTrue(absent.contains("geometry=absent"));
        assertTrue(OwnedNativeMoveDiagnostic.fullTraceSummary(List.of(first), -1, true).contains("truncated=true"));
        assertTrue(OwnedNativeMoveDiagnostic.fullTraceSummary(List.of(), -1).contains("rows=0, truncated=false"));
    }

    private static CurrentFrameCollisionScope.NativeCollisionTrace completeRow(UUID frame, int substep, int substeps,
            int iteration, MovementVector before, boolean vertical, NativeCollisionGeometry geometry) {
        return new CurrentFrameCollisionScope.NativeCollisionTrace(frame, substep, substeps, iteration,
                new BlockPos(substep, 2, iteration), Blocks.STONE.defaultBlockState(), "stone", "state",
                .1, .2, .3, before.x(), before.y(), before.z(), .4, .5, .6, .7, .8, .9, geometry, vertical);
    }

    private static NativeCollisionGeometry geometry(MovementVector motion, int iteration, int substeps) {
        return new NativeCollisionGeometry(new MovementVector(1, 2, 3), new MovementVector(1, 2, 3),
                new NativeCollisionGeometry.QuaternionValue(0, 0, 0, 1),
                new NativeFrameInterval.PoseValue(new double[] {1,0,0,0,0,1,0,0,0,0,1,0,0,0,0,1}),
                new net.minecraft.world.phys.AABB(0, 0, 0, 1, 1, 1), new MovementVector(1, 2, 3), motion,
                iteration, substeps);
    }

    @Test void fallbackIsBoundedAndIncludesVerticalWitnessWithoutDuplicatingPrefix() {
        var rows = new java.util.ArrayList<CurrentFrameCollisionScope.NativeCollisionTrace>();
        for (int i = 0; i < 12; i++) rows.add(new CurrentFrameCollisionScope.NativeCollisionTrace(UUID.randomUUID(),
                1, 1, i, BlockPos.ZERO, Blocks.STONE.defaultBlockState(), "block" + i, "state" + i,
                i, 0, 0, 0, 0, 0, i == 4 ? -.2 : 0, 0, 0, i == 4 ? -.2 : 0, 0, 0,
                null, i == 4));
        var snapshot = List.copyOf(rows);
        String summary = OwnedNativeMoveDiagnostic.failureRowSummary(rows, -1);
        assertTrue(summary.contains("failureRowUnavailable(index=-1"));
        assertTrue(summary.contains("verticalWitness[index=4"));
        assertTrue(summary.contains("block4"));
        assertEquals(4, occurrences(summary, "selectedPos="));
        assertEquals(snapshot, rows);
        assertTrue(OwnedNativeMoveDiagnostic.failureRowSummary(rows, 4).startsWith("failureRow[index=4,"));
    }

    private static int occurrences(String text, String needle) {
        int count = 0, offset = 0;
        while ((offset = text.indexOf(needle, offset)) >= 0) { count++; offset += needle.length(); }
        return count;
    }
}
