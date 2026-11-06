package com.juicyslew.moonstation14.ms14.shuttle.sable;

import org.junit.jupiter.api.Test;
import dev.ryanhcode.sable.companion.math.Pose3d;
import net.minecraft.world.phys.AABB;
import org.joml.Vector3d;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class NativeActorYawTest {
    private static CurrentFrameCollisionScope.YawSample sample(int i,int n,double yaw) {
        var pose=new NativeFrameInterval.PoseValue(new double[16]);
        return new CurrentFrameCollisionScope.YawSample(i,n,null,pose,0,pose,yaw,false);
    }
    @Test void usesCompleteOrderedFinalSubstepAndReturnsIndependentOrientation() {
        var yaw=NativeActorYaw.fromSamples(List.of(sample(1,2,.1),sample(2,2,.7)),true).orElseThrow();
        assertEquals(.7,yaw.yaw());
        var q=new org.joml.Quaterniond(yaw.orientation()); q.identity();
        assertEquals(Math.sin(.35),yaw.orientation().y(),1e-12);
    }
    @Test void rejectsUntrustworthySamples() {
        assertTrue(NativeActorYaw.fromSamples(List.of(),true).isEmpty());
        assertTrue(NativeActorYaw.fromSamples(List.of(sample(1,2,0)),true).isEmpty());
        assertTrue(NativeActorYaw.fromSamples(List.of(sample(2,2,0),sample(1,2,0)),true).isEmpty());
        assertTrue(NativeActorYaw.fromSamples(List.of(sample(1,1,Double.NaN)),true).isEmpty());
        assertTrue(NativeActorYaw.fromSamples(List.of(sample(1,1,0)),false).isEmpty());
    }

    @Test void nativeYawDetectsFloorCornerThatIdentitySatMissesAndEnvelopeContainsVertices() {
        var pose=new Pose3d();
        pose.position().set(100,300,100);
        pose.orientation().rotateY(Math.PI/4);
        AABB storage=new AABB(3,0,3,4,1,4);
        Vector3d feet=new Vector3d(2.71,.9995,2.71);
        pose.transformPosition(feet);
        AABB band=new AABB(feet.x-.3,feet.y-.001,feet.z-.3,feet.x+.3,feet.y,feet.z+.3);
        double yaw=dev.ryanhcode.sable.sublevel.entity_collision.SubLevelEntityCollision.getHitBoxYaw(pose);
        var orientation=new org.joml.Quaterniond().rotationY(yaw);
        assertFalse(FrameBoxIntersection.intersects(band,storage,pose));
        assertTrue(FrameBoxIntersection.intersects(band,orientation,storage,pose));

        AABB envelope=FrameBoxIntersection.orientedWorldBounds(band,orientation);
        for(int i=0;i<8;i++) {
            var corner=new Vector3d((i&1)==0?band.minX:band.maxX,
                    (i&2)==0?band.minY:band.maxY,(i&4)==0?band.minZ:band.maxZ);
            corner.sub(feet).rotateY(yaw).add(feet);
            assertTrue(corner.x>=envelope.minX&&corner.x<=envelope.maxX);
            assertTrue(corner.y>=envelope.minY&&corner.y<=envelope.maxY);
            assertTrue(corner.z>=envelope.minZ&&corner.z<=envelope.maxZ);
        }
    }
}
