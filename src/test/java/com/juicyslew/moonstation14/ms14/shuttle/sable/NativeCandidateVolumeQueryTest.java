package com.juicyslew.moonstation14.ms14.shuttle.sable;

import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NativeCandidateVolumeQueryTest {
    @Test void flatFloorSupportBandOverlapsButBodyVolumeDoesNotAndProductionModesStayDistinct() {
        var body = new AABB(-.3, 1, -.3, .3, 2.8, .3);
        var floor = new AABB(-1, 0, -1, 1, 1, 1);
        var support = NativeFinalFootClearance.footBand(body);
        assertTrue(support.intersects(floor));
        assertFalse(body.intersects(floor));
        assertEquals(NativeContactProofDiagnostic.QueryMode.BODY_VOLUME,
                SableOwnedBodyMotion.transientClearanceMode(SableOwnedBodyMotion.TransientPolicy.ASCENDING));
        assertEquals(NativeContactProofDiagnostic.QueryMode.SUPPORT_BAND,
                SableOwnedBodyMotion.transientClearanceMode(SableOwnedBodyMotion.TransientPolicy.NEGATIVE));
    }
    @Test void candidateQueryRejectsMissingOrInvalidContextBeforeWorldAccess() {
        assertEquals(NativeFinalFootClearance.Result.UNKNOWN,
                NativeFinalFootClearance.classifyCandidateBox(null, null, null, null));
        assertEquals(NativeFinalFootClearance.Result.UNKNOWN,
                NativeFinalFootClearance.classifyCandidateBox(null, null,
                        new AABB(0, 0, 0, 1, 1, 1), null));
    }

    @Test void optInInvalidQueryReportsBoundedUnknownWithoutChangingResultOrLettingObserverThrowEscape() {
        var seen = new java.util.concurrent.atomic.AtomicReference<NativeFinalFootClearance.QueryObservation>();
        var off = NativeFinalFootClearance.classifyCandidateBox(null, null, null, null);
        var on = NativeFinalFootClearance.classifyCandidateBox(null, null, null, null, 16, 1, seen::set);
        assertEquals(off, on);
        assertEquals(NativeFinalFootClearance.Result.UNKNOWN, seen.get().result());
        assertEquals("invalid-input", seen.get().reason());
        assertEquals(NativeFinalFootClearance.Result.UNKNOWN,
                NativeFinalFootClearance.classifyCandidateBox(null, null, null, null, 16, 1,
                        ignored -> { throw new IllegalStateException("observer failure"); }));
    }

    @Test void queryWitnessKeepsDistinctBoxesNumericYawAndDefensivePoseValue() {
        var worldQuery = new AABB(-1, 0, -1, 1, 2, 1);
        var envelope = new AABB(-1.5, 0, -1.5, 1.5, 2, 1.5);
        var frameBox = new AABB(-.4, 0, -.4, .4, 2, .4);
        var bodyBounds = new AABB(-.4, .001, -.4, .4, 2, .4);
        double[] matrix = new double[16];
        matrix[0] = matrix[5] = matrix[10] = matrix[15] = 1;
        var pose = new NativeFrameInterval.PoseValue(matrix);
        var observation = new NativeFinalFootClearance.QueryObservation("BODY_VOLUME", worldQuery, envelope,
                frameBox, bodyBounds, .7853981633974483, 17L, NativeFinalFootClearance.Result.OBSTRUCTED,
                "frame-shape-overlap", "FRAME_SHAPE", "frame-id", net.minecraft.core.BlockPos.ZERO,
                "stone", new AABB(0, 0, 0, .2, .2, .2), pose);
        matrix[0] = 9;
        var accessorCopy = observation.pose().matrix();
        accessorCopy[0] = 7;
        assertNotEquals(worldQuery, observation.frameBox());
        assertNotEquals(frameBox, observation.nativeShapeContextBounds());
        assertEquals(.7853981633974483, observation.nativeYawRadians());
        assertEquals(1, observation.pose().matrix()[0]);
    }

    @Test void diagnosticMetadataExtractionFailureIsContainedAndNotInvokedWhenOff() {
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        NativeFinalFootClearance.emitObservation(null, () -> {
            calls.incrementAndGet();
            throw new IllegalStateException("metadata extraction failed");
        });
        assertEquals(0, calls.get());
        NativeFinalFootClearance.emitObservation(ignored -> { }, () -> {
            calls.incrementAndGet();
            throw new IllegalStateException("metadata extraction failed");
        });
        assertEquals(1, calls.get(), "guard catches extraction failure without propagating into classification");
    }

    @Test void rotatedCandidateEnvelopeContainsEveryRotatedCorner() {
        var box = new AABB(-.4, 2, -.2, .4, 3.8, .2);
        var orientation = new org.joml.Quaterniond().rotationY(Math.PI / 4);
        var envelope = FrameBoxIntersection.orientedWorldBounds(box, orientation);
        assertNotNull(envelope);
        for (int mask = 0; mask < 8; mask++) {
            var corner = new org.joml.Vector3d((mask & 1) == 0 ? box.minX : box.maxX,
                    (mask & 2) == 0 ? box.minY : box.maxY, (mask & 4) == 0 ? box.minZ : box.maxZ);
            double cx = (box.minX + box.maxX) * .5, cy = (box.minY + box.maxY) * .5,
                    cz = (box.minZ + box.maxZ) * .5;
            corner.sub(cx, cy, cz);
            orientation.transform(corner);
            corner.add(cx, cy, cz);
            double epsilon = 1e-9;
            assertTrue(corner.x >= envelope.minX - epsilon && corner.x <= envelope.maxX + epsilon
                    && corner.y >= envelope.minY - epsilon && corner.y <= envelope.maxY + epsilon
                    && corner.z >= envelope.minZ - epsilon && corner.z <= envelope.maxZ + epsilon,
                    "rotated corner remains inside the envelope within floating-point tolerance: " + corner);
        }
    }
}
