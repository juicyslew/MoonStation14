package com.juicyslew.moonstation14.entities;

import com.juicyslew.moonstation14.ms14.shuttle.sable.NativeNormalContactPolicy;
import com.juicyslew.moonstation14.ms14.shuttle.sable.NativeHullContactEligibility;
import com.juicyslew.moonstation14.ms14.shuttle.sable.NativeSelectedContactAccess;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NativeNormalContactPolicyTest {
    private static final Vec3 X = new Vec3(1, 0, 0);

    @Test void nativeHullEvidenceRequiresExteriorWorldAndOneFreshTickIdentity() {
        Object info = new Object(), old = new Object();
        var frame = java.util.UUID.randomUUID();
        var entry = new NativeSelectedContactAccess.Entry(frame, net.minecraft.core.BlockPos.ZERO, X,
                false, Vec3.ZERO, Vec3.ZERO, 7, 70, false);
        assertTrue(NativeHullContactEligibility.worldPoint(new Object(), null));
        assertFalse(NativeHullContactEligibility.worldPoint(null, null));
        assertFalse(NativeHullContactEligibility.worldPoint(new Object(), new Object()));
        assertTrue(NativeHullContactEligibility.fresh(info, old, null, 7, 7, java.util.List.of(entry), false));
        assertFalse(NativeHullContactEligibility.fresh(info, old, null, 7, 8, java.util.List.of(entry), false));
        assertFalse(NativeHullContactEligibility.fresh(info, old, null, 7, 7,
                java.util.List.of(new NativeSelectedContactAccess.Entry(frame, net.minecraft.core.BlockPos.ZERO,
                        X, false, Vec3.ZERO, Vec3.ZERO, 6, 70, false)), false));
        assertFalse(NativeHullContactEligibility.fresh(info, old, null, 7, 7,
                java.util.List.of(entry, entry), false));
        assertFalse(NativeHullContactEligibility.fresh(info, old, null, Long.MAX_VALUE, Long.MIN_VALUE,
                java.util.List.of(entry), true));
        assertFalse(NativeHullContactEligibility.fresh(info, old, info, 7, 7, java.util.List.of(entry), false));
    }

    @Test void stationaryWallReplacesBaselineNormalOnce() {
        Vec3 result = apply(new Vec3(-2, 0, 0), new Vec3(0, 0, 0), Vec3.ZERO, X, .3);
        assertEquals(.6, result.x, 1e-9);
    }

    @Test void capturedTargetIsIdempotentAndCorrectsAnyPostMotorBaseline() {
        var target = NativeNormalContactPolicy.prescribe(new Vec3(-2, 0, 0), new Vec3(2, 0, 0), X, .3);
        Vec3 baseline = new Vec3(0, 3, 4); // native clipping removed normal velocity
        Vec3 corrected = NativeNormalContactPolicy.replaceNormal(baseline, target);
        assertEquals(3.2, corrected.x, 1e-9);
        assertEquals(3, corrected.y, 1e-9);
        assertEquals(4, corrected.z, 1e-9);
        assertEquals(corrected, NativeNormalContactPolicy.replaceNormal(corrected, target));
        assertEquals(baseline, NativeNormalContactPolicy.replaceNormal(baseline,
                NativeNormalContactPolicy.prescribe(new Vec3(Double.NaN, 0, 0), Vec3.ZERO, X, .3)));
    }

    @Test void translatingAndRotatingSurfacePointVelocityDrivesRestingItem() {
        assertEquals(2.6, apply(Vec3.ZERO, Vec3.ZERO, new Vec3(2, 0, 0), X, .3).x, 1e-9);
        // Rotational omega-cross-r is supplied by the shared point-velocity service as surface velocity.
        assertEquals(1.3, apply(Vec3.ZERO, Vec3.ZERO, new Vec3(1, 0, 0), X, .3).x, 1e-9);
    }

    @Test void separatingContactDoesNotRekickAndTangentialBaselineSurvives() {
        Vec3 baseline = new Vec3(2, 3, 4);
        assertEquals(baseline, NativeNormalContactPolicy.replaceNormal(new Vec3(2, 0, 0), baseline,
                Vec3.ZERO, X, .3));
        Vec3 result = apply(new Vec3(-2, 0, 0), new Vec3(-1, 3, 4), Vec3.ZERO, X, .3);
        assertEquals(.6, result.x, 1e-9);
        assertEquals(3, result.y, 1e-9);
        assertEquals(4, result.z, 1e-9);
        assertEquals(new Vec3(2, 3, 4), NativeNormalContactPolicy.replaceNormal(
                new Vec3(2, 0, 0), new Vec3(-2, 3, 4), Vec3.ZERO, X, .3));
    }

    @Test void twoPlaneCornerAndDuplicateSubstepsDoNotAmplifyResponse() {
        Vec3 incoming = new Vec3(-2, -2, 0);
        Vec3 baseline = Vec3.ZERO;
        Vec3 first = NativeNormalContactPolicy.replaceNormal(incoming, baseline, Vec3.ZERO, X, .3);
        Vec3 duplicate = NativeNormalContactPolicy.replaceNormal(first, first, Vec3.ZERO, X, .3);
        Vec3 original = new Vec3(-2, -2, 0);
        Vec3 secondPlaneIncoming = original.add(X.scale(duplicate.dot(X) - original.dot(X)));
        Vec3 corner = NativeNormalContactPolicy.replaceNormal(secondPlaneIncoming, secondPlaneIncoming, Vec3.ZERO,
                new Vec3(0, 1, 0), .3);
        assertEquals(.6, corner.x, 1e-9);
        assertEquals(.6, corner.y, 1e-9);
    }

    @Test void liveLoopKeepsOrdinaryBaselineTangentAcrossOrthogonalPlanes() {
        Vec3 incoming = new Vec3(-2, -2, 0);
        Vec3 baseline = new Vec3(0, .4, 5);
        Vec3 xPlane = NativeNormalContactPolicy.replaceNormal(incoming, baseline, Vec3.ZERO, X, .3);
        incoming = incoming.add(X.scale(xPlane.dot(X) - incoming.dot(X)));
        baseline = xPlane;
        Vec3 y = new Vec3(0, 1, 0);
        Vec3 yPlane = NativeNormalContactPolicy.replaceNormal(incoming, baseline, Vec3.ZERO, y, .3);
        assertEquals(.6, yPlane.x, 1e-9); // first native normal response survives
        assertEquals(.6, yPlane.y, 1e-9); // ordinary reflected/damped baseline axis is replaced
        assertEquals(5, yPlane.z, 1e-9); // ordinary baseline tangent survives
    }

    @Test void liveNoContactGuardReturnsBaselineRatherThanZero() {
        Vec3 baseline = new Vec3(.2, -.1, .3);
        assertEquals(baseline, LooseItemEntity.noNativeNormalEvidence(null, -1)
                ? baseline : Vec3.ZERO);
    }

    @Test void responseCaptureDefersSettlingButOrdinaryAndStaleMotionCanSettle() {
        Vec3 smallResponse = new Vec3(.01, 0, 0);
        assertEquals(false, LooseItemEntity.shouldSettle(smallResponse, true, false, 4, 4));
        assertEquals(true, LooseItemEntity.shouldSettle(Vec3.ZERO, true, false, 5, 4));
        assertEquals(true, LooseItemEntity.shouldSettle(smallResponse, true, false, 5, 4));
    }

    @Test void duplicateSubstepPlaneAndWorldOwnerZeroCarrierAreStable() {
        Vec3 worldCarrier = Vec3.ZERO;
        Vec3 once = apply(new Vec3(-2, 0, 0).add(worldCarrier), Vec3.ZERO, Vec3.ZERO, X, .3);
        Vec3 duplicate = apply(new Vec3(-2, 0, 0).add(worldCarrier), Vec3.ZERO, Vec3.ZERO, X, .3);
        assertEquals(once, duplicate);
    }

    @Test void sourceAndDestinationCarrierContributionsConvertExactlyOnceAndUnknownFailsClosed() {
        Vec3 incoming = new Vec3(-2, 0, 0).add(new Vec3(1, 0, 0));
        Vec3 baselineAbsolute = new Vec3(0, 0, 0).add(new Vec3(3, 0, 0));
        Vec3 absolute = NativeNormalContactPolicy.replaceNormal(incoming, baselineAbsolute,
                Vec3.ZERO, X, .3);
        Vec3 destinationRelative = absolute.subtract(new Vec3(3, 0, 0));
        assertEquals(-2.7, destinationRelative.x, 1e-9);
        assertEquals(baselineAbsolute, NativeNormalContactPolicy.replaceNormal(incoming,
                baselineAbsolute, null, X, .3));
    }

    private static Vec3 apply(Vec3 incoming, Vec3 baseline, Vec3 surface, Vec3 normal, double e) {
        return NativeNormalContactPolicy.replaceNormal(incoming, baseline, surface, normal, e);
    }
}
