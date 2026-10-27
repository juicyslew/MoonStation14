package com.juicyslew.moonstation14.ms14.tesla;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TeslaMovementPolicyTest {
    private record OrbitRun(int nearTick,int farTick,double minimum,double maximum,double planeChange) {}
    private static Vec3 bouncingCenter(int tick) {
        double phase=Math.floorMod(tick,80)*.05;
        return new Vec3(phase<=2?phase*2:4-(phase-2)*2,0,0);
    }
    private static OrbitRun simulateOrbit(java.util.UUID id,TeslaBallProfile.Orbit tune,int startTick,int ticks,
                                           Vec3 initialPosition,java.util.function.IntFunction<Vec3> centerAt,boolean floor) {
        Vec3 velocity=Vec3.ZERO,pos=initialPosition;int near=-1,far=-1;
        double min=Double.POSITIVE_INFINITY,max=0;
        Vec3 previousNormal=null;double planeChange=0;
        for(int tick=startTick;tick<startTick+ticks;tick++) {
            double age=tick*.05;
            Vec3 target=TeslaMovementPolicy.orbitPoint(centerAt.apply(tick),id,age,tune);
            if(floor) target=new Vec3(target.x,Math.max(target.y,-3.5+.65),target.z);
            Vec3 normal=target.subtract(TeslaMovementPolicy.orbitPoint(centerAt.apply(tick),id,age-.01,tune)).normalize();
            if(previousNormal!=null) planeChange=Math.max(planeChange,normal.cross(previousNormal).length());previousNormal=normal;
            Vec3 next=TeslaMovementPolicy.orbitVelocity(velocity,pos,target,Vec3.ZERO,tune,7,.05);
            assertTrue(Double.isFinite(next.length()),"finite velocity id="+id+" tick="+tick);
            assertTrue(next.length()<=7+1e-9);assertTrue(next.subtract(velocity).length()<=.4000001);
            pos=pos.add(next.scale(.05));velocity=next;
            double radius=pos.distanceTo(centerAt.apply(tick));min=Math.min(min,radius);max=Math.max(max,radius);
            if(near<0&&radius<=3.5)near=tick;
            if(far<0&&radius>=5.5)far=tick;
        }
        return new OrbitRun(near,far,min,max,planeChange);
    }
    @Test void nativeLikeOrbitObservationAcrossUuidPhases() {
        var tune=new TeslaBallProfile.Orbit(2,8,14,10,8,1.5,8);
        var ids=new java.util.ArrayList<java.util.UUID>();
        ids.add(java.util.UUID.fromString("e40481a4-7c17-4721-93b0-4989f571b001"));
        for(int i=0;i<64;i++) ids.add(java.util.UUID.nameUUIDFromBytes(("tesla-orbit-phase-"+i).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        for(boolean moving: new boolean[]{false,true}) for(boolean floor:new boolean[]{false,true}) {
            int worstNear=0,worstFar=0,failedNear=0,failedFar=0;
            for(var id:ids) {
                var run=simulateOrbit(id,tune,1,1800,new Vec3(4,0,0),tick -> moving ? bouncingCenter(tick) : Vec3.ZERO,floor);
                if(run.nearTick<0)failedNear++;else worstNear=Math.max(worstNear,run.nearTick);
                if(run.farTick<0)failedFar++;else worstFar=Math.max(worstFar,run.farTick);
                if(id.equals(ids.get(0))) System.out.println("TESLA_ORBIT_PHASE mode="+(moving?"bounce":"static")+(floor?"-floor":"")+" failingUuid="+id+" nearTick="+run.nearTick+" farTick="+run.farTick+" min="+run.minimum+" max="+run.maximum);
            }
            System.out.println("TESLA_ORBIT_PHASE mode="+(moving?"bounce":"static")+(floor?"-floor":"")+" phases="+ids.size()+" worstNearTick="+worstNear+" worstFarTick="+worstFar+" missedNear="+failedNear+" missedFar="+failedFar+" horizonTicks=1800");
            assertEquals(0,failedNear,"all phases reach near before 90s");
            assertEquals(0,failedFar,"all phases reach far before 90s");
        }
    }
    @Test void orbitIsDeterministicThreeDimensionalAndVelocityBounded() {
        var tune=new TeslaBallProfile.Orbit(2,8,14,10,8,1.5,8);
        var id=java.util.UUID.fromString("12345678-1234-5678-9abc-def012345678");
        double min=99,max=0,minY=99,maxY=-99;
        Vec3 center=Vec3.ZERO;
        for(int tick=0;tick<560;tick++) {
            Vec3 point=TeslaMovementPolicy.orbitPoint(center,id,tick*.05,tune);
            double radius=point.length();min=Math.min(min,radius);max=Math.max(max,radius);minY=Math.min(minY,point.y);maxY=Math.max(maxY,point.y);
        }
        assertTrue(min<=3.5);assertTrue(max>=5.5);assertTrue(maxY-minY>1);
        var actual=simulateOrbit(id,tune,0,6000,TeslaMovementPolicy.orbitPoint(Vec3.ZERO,id,0,tune),tick->Vec3.ZERO,false);
        assertTrue(actual.minimum<=3.5,"actual controlled path reaches near band: "+actual.minimum);
        assertTrue(actual.maximum>=5.5,"actual controlled path reaches far band: "+actual.maximum);
        assertTrue(actual.planeChange>.01,"orbit sample direction changes in 3D");
    }
    @Test void orbitPursuitTrailsReversingCenterAndCatchesLargeJump() {
        var tune=new TeslaBallProfile.Orbit(2,8,14,10,8,1.5,8);
        var id=java.util.UUID.fromString("12345678-1234-5678-9abc-def012345678");
        Vec3 pos=TeslaMovementPolicy.orbitPoint(Vec3.ZERO,id,0,tune), velocity=Vec3.ZERO;
        double maxError=0;
        Vec3 atBounceVelocity=Vec3.ZERO; double postBounceLag=0;
        for(int i=1;i<=80;i++) {
            double t=i*.05;
            Vec3 center=bouncingCenter(i);
            Vec3 target=TeslaMovementPolicy.orbitPoint(center,id,t,tune);
            Vec3 oldVelocity=velocity;
            velocity=TeslaMovementPolicy.orbitVelocity(velocity,pos,target,Vec3.ZERO,tune,7,.05);
            pos=pos.add(velocity.scale(.05));
            assertTrue(velocity.length()<=7+1e-9);
            assertTrue(velocity.subtract(oldVelocity).length()<=.4000001,"bounded acceleration across continuous center reversal");
            if(i==40) atBounceVelocity=velocity;
            if(i>=41&&i<=46) postBounceLag=Math.max(postBounceLag,pos.distanceTo(target));
            maxError=Math.max(maxError,pos.distanceTo(target));
        }
        assertTrue(atBounceVelocity.x>0,"mini retains outward momentum at the center bounce");
        assertTrue(postBounceLag>0.2,"mini visibly trails the target after center reversal: "+postBounceLag);
        assertTrue(maxError>0.2,"orbit remains a moving path");
        // Keep the same owner while its center travels ten blocks, then measure
        // whether pursuit catches up to the moving (age-dependent) orbit target.
        double initialGap=0,finalGap=0,maxJumpSpeed=0,maxJumpAcceleration=0;
        for(int i=0;i<1000;i++) {
            double t=(80+i)*.05;
            Vec3 center=new Vec3(10*Math.min(1,(i+1)/20.0),0,0);
            Vec3 target=TeslaMovementPolicy.orbitPoint(center,id,t,tune);
            Vec3 oldVelocity=velocity;
            velocity=TeslaMovementPolicy.orbitVelocity(velocity,pos,target,Vec3.ZERO,tune,7,.05);
            pos=pos.add(velocity.scale(.05));
            double gap=pos.distanceTo(target);
            if(i==0) initialGap=gap;
            if(i==999) finalGap=gap;
            maxJumpSpeed=Math.max(maxJumpSpeed,velocity.length());
            maxJumpAcceleration=Math.max(maxJumpAcceleration,velocity.subtract(oldVelocity).length());
        }
        assertTrue(finalGap<initialGap-.25,"mini closes a substantial part of a retained owner's moving-center gap: "+initialGap+" -> "+finalGap);
        assertTrue(maxJumpSpeed<=7+1e-9,"speed remains bounded during owner-center motion");
        assertTrue(maxJumpAcceleration<=.4000001,"acceleration remains bounded during owner-center motion");
    }
    @Test void orbitBasisIsContinuousAcrossInclinationsAndUuidPhases() {
        var tune=new TeslaBallProfile.Orbit(2,8,14,10,8,1.5,8);
        Vec3 center=Vec3.ZERO;
        for(String text: java.util.List.of("12345678-1234-5678-9abc-def012345678", "aaaaaaaa-1111-2222-3333-444444444444", "ffffffff-ffff-ffff-ffff-ffffffffffff")) {
            var id=java.util.UUID.fromString(text);
            Vec3 previous=TeslaMovementPolicy.orbitPoint(center,id,0,tune);
            for(int i=1;i<=12000;i++) {
                double t=i*.05;
                Vec3 point=TeslaMovementPolicy.orbitPoint(center,id,t,tune);
                assertTrue(point.subtract(previous).length()<1.0,"discontinuous orbit at t="+t+" id="+id);
                previous=point;
            }
        }
    }
    @Test void targetlessDampingHasAuthoredHalfLifeAndPreservesHorizontalMotion() {
        Vec3 velocity = new Vec3(1, 2, -3);
        Vec3 oneStep = TeslaMovementPolicy.dampTargetlessVertical(velocity, .05, 1);
        assertEquals(1, oneStep.x);
        assertEquals(-3, oneStep.z);
        assertEquals(2 * Math.pow(2, -.05), oneStep.y, 1e-12);
        Vec3 twentySteps = velocity;
        for (int i = 0; i < 20; i++) twentySteps = TeslaMovementPolicy.dampTargetlessVertical(twentySteps, .05, 1);
        assertEquals(1, twentySteps.x);
        assertEquals(-3, twentySteps.z);
        assertEquals(1, twentySteps.y, 1e-12);
        assertEquals(velocity, TeslaMovementPolicy.dampTargetlessVertical(velocity, .05, 0));
    }
}
