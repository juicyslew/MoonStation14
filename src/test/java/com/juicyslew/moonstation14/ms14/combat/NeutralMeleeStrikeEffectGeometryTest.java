package com.juicyslew.moonstation14.ms14.combat;

import com.juicyslew.moonstation14.ms14.combat.client.NeutralMeleeStrikeEffectGeometry;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class NeutralMeleeStrikeEffectGeometryTest {

    @Test void wideEndpointChoiceFollowsSwingDirectionNotHand() {
        var capture = new MeleeArcGeometry.StrikeCapture(Vec3.ZERO, Vec3.ZERO, Vec3.ZERO, new Vec3(0, 0, 1), 0, 1,
                MeleeAttackRequest.Mode.WIDE, 90, Vec3.ZERO,
                List.of(new Vec3(-1, 0, 1), Vec3.ZERO, new Vec3(1, 0, 1)));
        var left = fanExpected(capture, 0, true);
        var right = fanExpected(capture, 0, false);
        assertEquals(1, left.x, 1e-9); assertEquals(-1, right.x, 1e-9);
        assertEquals(0, fanExpected(capture, .5, true).x, 1e-9);
        assertEquals(0, fanExpected(capture, .5, false).x, 1e-9);
        assertEquals(-1, fanExpected(capture, 1, true).x, 1e-9);
        assertEquals(1, fanExpected(capture, 1, false).x, 1e-9);
    }

    private static Vec3 fanExpected(MeleeArcGeometry.StrikeCapture capture, double fraction, boolean left) {
        var points = capture.boundaryEndpoints();
        return points.get(0).lerp(points.get(points.size() - 1), left ? 1 - fraction : fraction);
    }

    @Test void strikeViewUsesTimelineSweepForBothDirectionsAndKeepsLightCenter() {
        var boundaries = List.of(new Vec3(-1, 0, 2), new Vec3(0, 0, 2), new Vec3(1, 0, 2));
        var wide = new MeleeArcGeometry.StrikeCapture(Vec3.ZERO, Vec3.ZERO, Vec3.ZERO,
                new Vec3(0, 0, 1), 0, 2, MeleeAttackRequest.Mode.WIDE, 0, Vec3.ZERO, boundaries);
        var profile = new MeleeStrikeProfile(1, .45, .8, .1, 1);
        for (double age : new double[]{.06, .09, .12, .36}) {
            double progress = age / profile.durationSeconds();
            double fraction = MeleeStrikeTimeline.wideSweepFraction(profile, progress);
            for (boolean left : new boolean[]{false, true}) {
                var view = new com.juicyslew.moonstation14.ms14.combat.client.MeleeArcClient.StrikeView(
                        java.util.UUID.randomUUID(), 1, false, wide, profile,
                        MeleeStrikeTimeline.sample(profile, 1, age), left);
                assertEquals(fanExpected(wide, fraction, left), NeutralMeleeStrikeEffectGeometry.selectedEndpoint(view));
            }
        }
        var light = new MeleeArcGeometry.StrikeCapture(Vec3.ZERO, Vec3.ZERO, Vec3.ZERO,
                new Vec3(0, 0, 1), 0, 2, MeleeAttackRequest.Mode.LIGHT, 0, new Vec3(0, 0, 2), List.of());
        for (double age : new double[]{0, .09, .3, .5, .9}) {
            var sample = MeleeStrikeTimeline.sample(profile, 1, age);
            var lightView = new com.juicyslew.moonstation14.ms14.combat.client.MeleeArcClient.StrikeView(
                    java.util.UUID.randomUUID(), 1, false, light, profile, sample, true);
            assertEquals(light.centerEndpoint(), NeutralMeleeStrikeEffectGeometry.selectedEndpoint(lightView));
            assertEquals(light.emissionOrigin().lerp(light.centerEndpoint(), sample.extension()),
                    NeutralMeleeStrikeEffectGeometry.weaponTipPoint(light.emissionOrigin(), lightView));
        }
    }

    @Test void capturedAimAndWristCapIgnoreMovedShoulderAndCurrentYaw() {
        var capture = new MeleeArcGeometry.StrikeCapture(Vec3.ZERO, new Vec3(0, 1, 0), new Vec3(0, 1, 0),
                new Vec3(0, 0, 1), 0, 2, MeleeAttackRequest.Mode.LIGHT, 0,
                new Vec3(0, 1, 2), List.of());
        Vec3 endpoint = new Vec3(0, 1, 2);
        Vec3 aim = NeutralMeleeStrikeEffectGeometry.capturedDirection(capture, endpoint);
        assertEquals(new Vec3(0, 0, 1), aim);
        Vec3 movedShoulder = new Vec3(0, 1, 3);
        assertEquals(new Vec3(0, 0, 1), aim);
    }

    @Test void legacyBoundslessCaptureUsesFiniteSourcePlaneClearance() {
        var capture=new MeleeArcGeometry.StrikeCapture(new Vec3(3,0,0),Vec3.ZERO,new Vec3(3,1,0),new Vec3(1,0,0),0,
                1,MeleeAttackRequest.Mode.LIGHT,0,new Vec3(4,1,0),List.of());
        assertEquals(3,NeutralMeleeStrikeEffectGeometry.rearClearance(capture,new Vec3(1,0,0)));
    }

    @Test void observerClearanceUsesBodySupportAndCurrentLungeNotPaddedEmissionOrFutureEnvelope() {
        var box=new net.minecraft.world.phys.AABB(-.3,0,-.3,.3,1.8,.3);
        var capture=new MeleeArcGeometry.StrikeCapture(Vec3.ZERO,Vec3.ZERO,new Vec3(0,0,2),new Vec3(0,0,1),0,2,
                MeleeAttackRequest.Mode.LIGHT,0,new Vec3(0,0,3),List.of(),box);
        double zero=NeutralMeleeStrikeEffectGeometry.observerClearance(capture,new Vec3(0,0,1),Vec3.ZERO);
        double current=NeutralMeleeStrikeEffectGeometry.observerClearance(capture,new Vec3(0,0,1),new Vec3(0,0,.03));
        assertTrue(zero<2); // emission is padded beyond the body; it is not the clip plane.
        assertEquals(.03,current-zero,1e-9);
        assertTrue(current < NeutralMeleeStrikeEffectGeometry.rearClearance(capture,new Vec3(0,0,1)));
        Vec3 angled=new Vec3(Math.sin(Math.toRadians(60)),0,Math.cos(Math.toRadians(60)));
        double angledBase=NeutralMeleeStrikeEffectGeometry.observerClearance(capture,angled,Vec3.ZERO);
        double angledLunge=NeutralMeleeStrikeEffectGeometry.observerClearance(capture,angled,new Vec3(0,0,.22));
        assertEquals(.11,angledLunge-angledBase,1e-9);
        assertEquals(0,NeutralMeleeStrikeEffectGeometry.observerClearance(capture,new Vec3(1,0,0),new Vec3(0,0,.22))
                -NeutralMeleeStrikeEffectGeometry.observerClearance(capture,new Vec3(1,0,0),Vec3.ZERO),1e-9);
    }

    @Test void wideFanClearanceUsesCapturedBodyExitAndFrozenEndpoints() {
        var bounds=new net.minecraft.world.phys.AABB(-.3,0,-.3,.3,1.8,.3);
        var points=List.of(new Vec3(-1,1,1),new Vec3(0,1,1),new Vec3(1,1,1));
        var capture=new MeleeArcGeometry.StrikeCapture(Vec3.ZERO,new Vec3(0,1.6,0),new Vec3(0,1.08,0),
                new Vec3(0,0,1),0,1.5,MeleeAttackRequest.Mode.WIDE,120,new Vec3(0,1,1.5),points,bounds);
        // Head-up ray exits the top of the captured body at y=1.8; rear support must
        // begin beyond that exit plus the captured lunge envelope.
        double clearance=NeutralMeleeStrikeEffectGeometry.rearClearance(capture,new Vec3(0,1,0));
        assertTrue(clearance>=1.8+MeleeArcTimeline.MAX_LUNGE_DISTANCE);
        var profile=new MeleeStrikeProfile(1,.45,.8,.1,1);
        Vec3[] sampled=new Vec3[2];
        for(int side=0;side<2;side++) {
            boolean left=side==1;
            var view=new com.juicyslew.moonstation14.ms14.combat.client.MeleeArcClient.StrikeView(java.util.UUID.randomUUID(),1,false,capture,
                    profile,MeleeStrikeTimeline.sample(profile,1,.2),left);
            var frozen=NeutralMeleeStrikeEffectGeometry.selectedEndpoint(view);
            double fraction=MeleeStrikeTimeline.wideSweepFraction(profile,view.sample().progress());
            sampled[side]=points.get(0).lerp(points.get(2),left?1-fraction:fraction);
            assertEquals(sampled[side],frozen);
            assertEquals(frozen,NeutralMeleeStrikeEffectGeometry.selectedEndpoint(view));
            assertEquals(capture.emissionOrigin(),NeutralMeleeStrikeEffectGeometry.source(view));
            assertTrue(frozen.distanceTo(capture.emissionOrigin())<=capture.range()+1e-9);
        }
        assertNotEquals(sampled[0],sampled[1]);
    }

    @Test void obliqueClearanceUsesWholeAabbSupportNotCenterRayExit() {
        var box=new net.minecraft.world.phys.AABB(-.3,0,-.3,.3,1.8,.3);
        Vec3 source=new Vec3(0,1.08,0), direction=new Vec3(1,1,0).normalize();
        var capture=new MeleeArcGeometry.StrikeCapture(Vec3.ZERO,new Vec3(0,1.6,0),source,new Vec3(0,0,1),0,2,
                MeleeAttackRequest.Mode.WIDE,120,new Vec3(0,1,2),List.of(),box);
        double support=box.maxX*direction.x+box.maxY*direction.y;
        double oldRayExit=new Vec3(.3,1.38,0).dot(direction);
        double clearance=NeutralMeleeStrikeEffectGeometry.rearClearance(capture,direction);
        assertTrue(oldRayExit<support);
        assertTrue(clearance>=support+MeleeArcTimeline.MAX_LUNGE_DISTANCE);
        for(double pitch:new double[]{-60,0,60}) for(double bearing:new double[]{-60,0,60}) {
            Vec3 d=new Vec3(Math.sin(Math.toRadians(bearing))*Math.cos(Math.toRadians(pitch)),Math.sin(Math.toRadians(pitch)),
                    Math.cos(Math.toRadians(bearing))*Math.cos(Math.toRadians(pitch))).normalize();
            double corner=Double.NEGATIVE_INFINITY;
            for(double x:new double[]{box.minX,box.maxX}) for(double y:new double[]{box.minY,box.maxY}) for(double z:new double[]{box.minZ,box.maxZ})
                corner=Math.max(corner,new Vec3(x,y,z).dot(d));
            assertTrue(NeutralMeleeStrikeEffectGeometry.rearClearance(capture,d)>=corner+MeleeArcTimeline.MAX_LUNGE_DISTANCE);
        }
    }

    @Test void wideAndLightSourcesUseCapturedEmissionOrigin() {
        var profile = new MeleeStrikeProfile(1, .45, .8, .1, 1);
        var wide = new MeleeArcGeometry.StrikeCapture(Vec3.ZERO,new Vec3(0,1,0),new Vec3(0,1,.2),new Vec3(0,0,1),0,1.5,MeleeAttackRequest.Mode.WIDE,90,new Vec3(0,1,1.5),List.of());
        var light = new MeleeArcGeometry.StrikeCapture(Vec3.ZERO,new Vec3(0,1,0),new Vec3(0,1,.2),new Vec3(0,0,1),0,1.5,MeleeAttackRequest.Mode.LIGHT,0,new Vec3(0,1,1.5),List.of());
        var wv = new com.juicyslew.moonstation14.ms14.combat.client.MeleeArcClient.StrikeView(java.util.UUID.randomUUID(),1,false,wide,profile,MeleeStrikeTimeline.sample(profile,1,.1),false);
        var lv = new com.juicyslew.moonstation14.ms14.combat.client.MeleeArcClient.StrikeView(java.util.UUID.randomUUID(),1,false,light,profile,MeleeStrikeTimeline.sample(profile,1,.1),false);
        assertEquals(wide.emissionOrigin(),NeutralMeleeStrikeEffectGeometry.source(wv));
        assertEquals(light.emissionOrigin(),NeutralMeleeStrikeEffectGeometry.source(lv));
    }
}
