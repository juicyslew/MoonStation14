package com.juicyslew.moonstation14.ms14.combat;

import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import com.juicyslew.moonstation14.ms14.standing.BodyInteractionRay;

class MeleeArcGeometryTest {
    @Test void targetDirectedCaptureStopsAtTargetBoundsButEmptyPointCaptureKeepsNominalRange() {
        var source = new AABB(-.3,0,-.3,.3,1.8,.3);
        var eye = new Vec3(0,1.6,0);
        var target = new AABB(-.3,1.2,1.1,.3,1.8,1.5);
        var hit = MeleeArcGeometry.captureStrike(Vec3.ZERO, source, eye, new Vec3(0,0,1), 0,
                2, 60, MeleeAttackRequest.Mode.LIGHT, .6, target, null, ray -> ray.length());
        var miss = MeleeArcGeometry.captureStrike(Vec3.ZERO, source, eye, new Vec3(0,0,1), 0,
                2, 60, MeleeAttackRequest.Mode.LIGHT, .6, null, null, ray -> ray.length());
        assertNotNull(hit);
        assertNotNull(miss);
        assertTrue(hit.centerEndpoint().z <= target.maxZ + 1e-5);
        assertTrue(miss.centerEndpoint().z > target.maxZ);
        assertEquals(source, hit.sourceBounds());
        assertEquals(source, miss.sourceBounds());
    }
    @Test void npcTargetCaptureUsesTargetDirectionAndRetainsSourceBounds() {
        Vec3 eye = new Vec3(0,1.6,0), direction = new Vec3(0,0,1);
        AABB body = new AABB(-.3,0,-.3,.3,1.8,.3), target = new AABB(-.3,0,1.2,.3,1.8,1.8);
        var capture = MeleeArcGeometry.captureStrike(Vec3.ZERO, body, eye, direction, 0, 1.5, 60,
                MeleeAttackRequest.Mode.LIGHT, .6, target, null, ray -> ray.length());
        assertNotNull(capture);
        assertEquals(1, capture.fullDirection().z, 1e-9);
        assertEquals(body, capture.sourceBounds());
        assertTrue(capture.centerEndpoint().z > eye.z);
        assertTrue(capture.emissionOrigin().z < capture.centerEndpoint().z);
        var coincident = MeleeArcGeometry.captureStrike(Vec3.ZERO, body, eye, new Vec3(0,0,1), 0, 1.5, 60,
                MeleeAttackRequest.Mode.LIGHT, .6, new AABB(-.2,1.4,-.2,.2,1.8,.2), null, ray -> ray.length());
        assertNotNull(coincident);
        assertTrue(Double.isFinite(coincident.fullDirection().z));
    }
    private static MeleeArcGeometry.StrikeCapture strike(Vec3 look, double angle, MeleeAttackRequest.Mode mode,
            java.util.function.ToDoubleFunction<MeleeArcGeometry.Ray> clip) {
        return MeleeArcGeometry.captureStrike(Vec3.ZERO,new AABB(-.3,0,-.3,.3,1.8,.3),
                new Vec3(0,1.6,0),look,0,2,angle,mode,.6,null,null,clip);
    }
    @Test void wideOriginUsesResolvedRatioAndNominalFanRange() {
        AABB bounds = new AABB(-.3,0,-.3,.3,1.8,.3);
        var c = MeleeArcGeometry.captureStrike(Vec3.ZERO,bounds,new Vec3(0,1.6,0),new Vec3(0,0,1),0,
                1.5,120,MeleeAttackRequest.Mode.WIDE,.6,null,null,r->r.length());
        Vec3 chest = BodyInteractionRay.bodyHeightOrigin(bounds,.6);
        assertEquals(1.08,c.emissionOrigin().y,1e-9);
        assertEquals(chest,c.centerEndpoint().subtract(0,0,1.5));
        assertEquals(2*1.5*Math.sin(Math.toRadians(60)),c.boundaryEndpoints().get(0).distanceTo(c.boundaryEndpoints().get(12)),1e-6);
    }
    @Test void pitchIsPreservedAndYawBasisStaysFiniteAtVerticalLook() {
        for(double pitch : new double[]{45,-45,90,-90}) {
            var c=strike(new Vec3(0,Math.sin(Math.toRadians(pitch)),Math.cos(Math.toRadians(pitch))),60,
                    MeleeAttackRequest.Mode.WIDE,r->r.length());
            assertNotNull(c); assertEquals(Math.sin(Math.toRadians(pitch)),c.fullDirection().y,1e-6);
            assertEquals(13,c.boundaryEndpoints().size());
            assertTrue(c.boundaryEndpoints().stream().allMatch(v->Double.isFinite(v.y)));
        }
    }
    @Test void endpointsStopAtPhysicalReachAndContactsAndClipIndependently() {
        var c=MeleeArcGeometry.captureStrike(Vec3.ZERO,new AABB(-.3,0,-.3,.3,1.8,.3),
                new Vec3(0,1.6,0),new Vec3(0,0,1),0,1,30,MeleeAttackRequest.Mode.LIGHT,
                null,null,r->r.length());
        double max=1 + Math.sqrt(.6*.6+1.8*1.8+.6*.6)+.5;
        assertTrue(c.centerEndpoint().distanceTo(new Vec3(0,1.6,0)) <= max);
        var clipped=strike(new Vec3(0,0,1),60,MeleeAttackRequest.Mode.WIDE,r->r.length()*.5);
        assertEquals(13,clipped.boundaryEndpoints().size());
        assertTrue(clipped.boundaryEndpoints().stream().allMatch(p->p.distanceTo(new Vec3(0,1.6,0))<2));
    }
    @Test void fanAnglesAreYawRotatedWithoutChangingVerticalPitch() {
        for(double angle:new double[]{30,60}) {
            var c=strike(new Vec3(0,.5,Math.sqrt(.75)),angle,MeleeAttackRequest.Mode.WIDE,r->r.length());
            assertEquals(13,c.boundaryEndpoints().size());
            Vec3 start=c.boundaryEndpoints().get(0).subtract(c.emissionOrigin()).normalize();
            Vec3 end=c.boundaryEndpoints().get(12).subtract(c.emissionOrigin()).normalize();
            var basis = BodyInteractionRay.headLocalBasis(new Vec3(0,.5,Math.sqrt(.75)), 0);
            var expectedStart = basis.forward().scale(Math.cos(Math.toRadians(angle/2)))
                    .add(basis.right().scale(-Math.sin(Math.toRadians(angle/2)))).normalize();
            var expectedEnd = basis.forward().scale(Math.cos(Math.toRadians(angle/2)))
                    .add(basis.right().scale(Math.sin(Math.toRadians(angle/2)))).normalize();
            assertEquals(expectedStart.y,start.y,1e-6); assertEquals(expectedEnd.y,end.y,1e-6);
            assertEquals(expectedStart.x,start.x,1e-5); assertEquals(expectedEnd.x,end.x,1e-5);
            assertEquals(expectedStart.z,start.z,1e-5); assertEquals(expectedEnd.z,end.z,1e-5);
            assertEquals(1,start.length(),1e-6); assertEquals(1,end.length(),1e-6);
        }
    }
    @Test void wideNominalRangeAndPureVerticalDirectionAreStable() {
        var c=MeleeArcGeometry.captureStrike(Vec3.ZERO,new AABB(-.3,0,-.3,.3,1.8,.3),new Vec3(0,1.6,0),
                new Vec3(0,0,1),0,1.5,60,MeleeAttackRequest.Mode.WIDE,null,null,r->r.length());
        assertEquals(1.5,c.range(),1e-9);
        assertEquals(1.5,c.centerEndpoint().distanceTo(c.emissionOrigin()),1e-9);
        assertEquals(0,BodyInteractionRay.pitchedYawDirection(new Vec3(0,1,0),1).distanceTo(new Vec3(0,1,0)),1e-9);
    }
    @Test void wideCenterAndEdgesUseNominalRangeAndClipIndependently() {
        for(double pitch:new double[]{0,60,-60,90,-90}) for(double yaw:new double[]{0,90,180}) {
            Vec3 look=new Vec3(Math.sin(Math.toRadians(yaw))*Math.cos(Math.toRadians(pitch)),Math.sin(Math.toRadians(pitch)),Math.cos(Math.toRadians(yaw))*Math.cos(Math.toRadians(pitch)));
            var c=MeleeArcGeometry.captureStrike(Vec3.ZERO,new AABB(-.3,0,-.3,.3,1.8,.3),new Vec3(0,1.6,0),look,(float)yaw,1.5,90,MeleeAttackRequest.Mode.WIDE,null,null,r->r.length());
            assertEquals(1.5,c.centerEndpoint().distanceTo(c.emissionOrigin()),1e-8);
            for(Vec3 edge:c.boundaryEndpoints()) assertEquals(1.5,edge.distanceTo(c.emissionOrigin()),1e-8);
            assertEquals(2 * 1.5 * Math.sin(Math.toRadians(90 / 2.0)),
                    c.boundaryEndpoints().get(0).distanceTo(c.boundaryEndpoints().get(12)),1e-6);
        }
        var c=strike(new Vec3(0,0,1),90,MeleeAttackRequest.Mode.WIDE,r->r.direction().z>.99?.4:r.length());
         assertEquals(.4,c.centerEndpoint().distanceTo(c.emissionOrigin()),1e-9);
         assertTrue(c.boundaryEndpoints().stream().anyMatch(p->p.distanceTo(c.emissionOrigin())>1.49));
    }
    @Test void upperBoundIsTranslationInvariant() {
        AABB box=new AABB(-.3,0,-.3,.3,1.8,.3); Vec3 eye=new Vec3(0,1.6,0);
        var origin=MeleeArcGeometry.captureStrike(Vec3.ZERO,box,eye,new Vec3(0,0,1),0,1.5,0,MeleeAttackRequest.Mode.LIGHT,null,null,r->r.length());
        Vec3 shift=new Vec3(10000,-20000,30000);
        var moved=MeleeArcGeometry.captureStrike(shift,box.move(shift),eye.add(shift),new Vec3(0,0,1),0,1.5,0,MeleeAttackRequest.Mode.LIGHT,null,null,r->r.length());
        assertTrue(origin.centerEndpoint().distanceTo(moved.centerEndpoint().subtract(shift))<1e-4);
        assertTrue(origin.emissionOrigin().distanceTo(moved.emissionOrigin().subtract(shift))<1e-4);
    }
    @Test void emissionOriginClearsCapturedBoundsAlongFullPitchButNeverPassesCenterClip() {
        for (AABB bounds : new AABB[]{new AABB(-.3,0,-.3,.3,1.8,.3), new AABB(-.25,0,-.25,.25,1,.25),
                new AABB(-.4,-.8,-.3,.4,.6,.3)}) {
            for(Vec3 look : new Vec3[]{new Vec3(0,0,1),new Vec3(0,1,1),new Vec3(0,-1,1),new Vec3(0,1,0)}) {
                Vec3 eye=bounds.getCenter();
                var c=MeleeArcGeometry.captureStrike(Vec3.ZERO,bounds,eye,look,0,2,40,MeleeAttackRequest.Mode.LIGHT,null,null,r->r.length());
                Vec3 d=look.normalize();
                assertTrue(CombatMeleeTargetProbe.BodyBounds.distance(bounds,new AABB(c.emissionOrigin(),c.emissionOrigin()))>0);
                assertTrue(c.emissionOrigin().subtract(eye).dot(d)>0);
                assertTrue(c.emissionOrigin().distanceTo(eye)<=c.centerEndpoint().distanceTo(eye));
            }
        }
        AABB bounds=new AABB(-.3,0,-.3,.3,1.8,.3); Vec3 eye=new Vec3(0,1.6,0);
        var wall=MeleeArcGeometry.captureStrike(Vec3.ZERO,bounds,eye,new Vec3(0,0,1),0,2,0,MeleeAttackRequest.Mode.LIGHT,null,null,r->.1);
        assertTrue(wall.emissionOrigin().distanceTo(eye)<=wall.centerEndpoint().distanceTo(eye));
        assertTrue(wall.centerEndpoint().distanceTo(wall.emissionOrigin()) > 0);
        var halfBlock=MeleeArcGeometry.captureStrike(Vec3.ZERO,bounds,eye,new Vec3(0,0,1),0,2,0,MeleeAttackRequest.Mode.LIGHT,null,null,r->.5);
        assertEquals(.3, halfBlock.emissionOrigin().distanceTo(eye), 1e-3);
        assertTrue(halfBlock.centerEndpoint().distanceTo(halfBlock.emissionOrigin()) >= .19);
    }
    @Test void defaultSourceClearsMaximumGenericLungeEnvelope() {
        AABB bounds=new AABB(-.3,0,-.3,.3,1.8,.3); Vec3 eye=new Vec3(0,1.6,0);
        var capture=MeleeArcGeometry.captureStrike(Vec3.ZERO,bounds,eye,new Vec3(0,0,1),0,2,0,
                MeleeAttackRequest.Mode.LIGHT,null,null,r->r.length());
        assertTrue(capture.emissionOrigin().z >= .3 + MeleeArcTimeline.MAX_LUNGE_DISTANCE);
    }
    @Test void rejectsNonfiniteGeometry() {
        assertNull(strike(new Vec3(Double.NaN,0,1),30,MeleeAttackRequest.Mode.LIGHT,r->r.length()));
    }
    @Test void closeTargetUsesTargetBearingAndDistance() {
        var aim = MeleeArcGeometry.capture(Vec3.ZERO, 2, new Vec3(0,0,1), new Vec3(1,20,0), 4);
        assertEquals(new Vec3(1,0,0), aim.direction());
        assertEquals(new Vec3(1,1,0), aim.attackPoint());
    }
    @Test void missingTargetUsesMaximumPointAndRangeClampsAtZero() {
        assertEquals(new Vec3(2.8,1,0), MeleeArcGeometry.capture(Vec3.ZERO,2,new Vec3(1,0,0),null,3).attackPoint());
        assertEquals(Vec3.ZERO, MeleeArcGeometry.capture(Vec3.ZERO,0,new Vec3(1,0,0),null,.1).attackPoint());
    }
    @Test void nonfiniteTargetDistanceFallsBackToMaximumAndCapturedVectorsAreFrozen() {
        var source = new Vec3(1,2,3);
        var aim = MeleeArcGeometry.capture(source,2,new Vec3(1,0,0),new Vec3(Double.NaN,0,0),3);
        source.add(100,100,100);
        assertEquals(new Vec3(1,2,3), aim.bodyPosition());
        assertEquals(new Vec3(3.8,3,3), aim.attackPoint());
    }
    @Test void legacyAimFallsBackForVerticalLookWithoutChangingStrikeCapture() {
        for (double pitch : new double[]{90, -90}) {
            Vec3 look = new Vec3(0, Math.sin(Math.toRadians(pitch)), 0);
            var legacy = MeleeArcGeometry.capture(Vec3.ZERO, 1.8, look, null, 2);
            assertNull(legacy);
            var strike = strike(look, 0, MeleeAttackRequest.Mode.LIGHT, r -> r.length());
            var fallback = MeleeArcGeometry.legacyAimWithStrikeFallback(legacy, strike);
            assertNotNull(fallback);
            assertEquals(strike.frozenBodyPosition(), fallback.bodyPosition());
            assertEquals(0, fallback.direction().y, 1e-9);
            assertTrue(Double.isFinite(fallback.direction().x) && Double.isFinite(fallback.direction().z));
            assertEquals(strike.centerEndpoint(), fallback.attackPoint());
            assertEquals(look.normalize(), strike.fullDirection());
        }
        var original = MeleeArcGeometry.capture(Vec3.ZERO, 1.8, new Vec3(0, 0, 1), null, 2);
        var strike = strike(new Vec3(0, 0, 1), 0, MeleeAttackRequest.Mode.LIGHT, r -> r.length());
        assertSame(original, MeleeArcGeometry.legacyAimWithStrikeFallback(original, strike));
        assertNull(MeleeArcGeometry.legacyAimWithStrikeFallback(null, null));
    }
    @Test void validatedBlockContactUsesFrozenBodyBearingAndNullKeepsPriorAim() {
        var frozen = MeleeArcGeometry.capture(new Vec3(2, 3, 4), 2, new Vec3(1, 0, 0), null, 5);
        var contact = new Vec3(2, 3, 7);
        var contactAim = MeleeArcGeometry.capture(frozen.bodyPosition(), 0, frozen.direction(), contact, 5);
        assertEquals(new Vec3(0, 0, 1), contactAim.direction());
        assertEquals(contact, contact);
        assertEquals(new Vec3(1, 0, 0), frozen.direction()); // null contact fallback
    }
}
