package com.juicyslew.moonstation14.ms14.shuttle.sable;

import dev.ryanhcode.sable.companion.math.Pose3d;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import java.util.Map;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import static org.junit.jupiter.api.Assertions.*;

class OwnedFrameSimulationScopeTest {
    @Test void compiledSublevelEndpointRequiresIntervalBeforeCachingRequestedId() throws Exception {
        var node=new ClassNode(Opcodes.ASM9);
        try(var in=OwnedFrameSimulationScope.class.getResourceAsStream("OwnedFrameSimulationScope.class")) {
            assertNotNull(in); new ClassReader(in).accept(node,0);
        }
        var method=node.methods.stream().filter(m->m.name.equals("to")
                && m.desc.equals("(Ldev/ryanhcode/sable/sublevel/SubLevel;)Ldev/ryanhcode/sable/companion/math/Pose3dc;"))
                .findFirst().orElseThrow();
        var calls=java.util.Arrays.stream(method.instructions.toArray()).filter(MethodInsnNode.class::isInstance)
                .map(MethodInsnNode.class::cast).toList();
        int require=-1,cached=-1,id=-1;
        for(int i=0;i<calls.size();i++) {
            var call=calls.get(i);
            if(call.name.equals("requireInterval")) require=i;
            if(call.name.equals("getUniqueId")) id=i;
            if(call.name.equals("cachedTo")) cached=i;
        }
        assertTrue(require>=0 && require<cached,"requested frame interval must be required before cache lookup");
        assertTrue(id>=0 && id<cached,"cache key must come from requested SubLevel UUID");
    }
    private static OwnedFrameSimulationScope scope(OwnedFrameSimulationScope.Failure failure) throws Exception {
        return scope(failure, null, null);
    }
    private static OwnedFrameSimulationScope scope(OwnedFrameSimulationScope.Failure failure,
                                                   dev.ryanhcode.sable.companion.math.Pose3dc from,
                                                   dev.ryanhcode.sable.companion.math.Pose3dc to) throws Exception {
        var ctor=OwnedFrameSimulationScope.class.getDeclaredConstructor(net.minecraft.world.entity.Entity.class,
                dev.ryanhcode.sable.sublevel.SubLevel.class,dev.ryanhcode.sable.companion.math.Pose3dc.class,
                dev.ryanhcode.sable.companion.math.Pose3dc.class,AuthoritativeFrameTimeline.Result.class,
                AuthoritativeFrameTimeline.Result.class,OwnedFrameSimulationScope.Failure.class,long.class,long.class);
        ctor.setAccessible(true);
        return ctor.newInstance(null,null,from,to,null,null,failure,10L,11L);
    }
    private static Pose3d pose(double x,double y,double z){var p=new Pose3d();p.position().set(x,y,z);return p;}
    private static void interval(OwnedFrameSimulationScope scope,UUID id,Pose3d from,Pose3d to) throws Exception {
        var field=OwnedFrameSimulationScope.class.getDeclaredField("intervals");field.setAccessible(true);
        @SuppressWarnings("unchecked") var intervals=(Map<UUID,OwnedFrameSimulationScope.Interval>)field.get(scope);
        intervals.put(id,new OwnedFrameSimulationScope.Interval(from,to,null,null));
    }
    @Test void cachedEndpointSelectsFrameAndReturnsDefensiveCopy() throws Exception {
        var scope=scope(OwnedFrameSimulationScope.Failure.NONE);
        try {
            UUID a=UUID.randomUUID();var end=pose(2048,7,-13);interval(scope,a,pose(2040,0,0),end);
            assertNull(scope.to(),"WORLD zero-argument endpoint remains the initial anchor");
            var selected=(Pose3d)scope.cachedTo(a);
            assertEquals(2048,selected.position().x);assertEquals(7,selected.position().y);assertEquals(-13,selected.position().z);
            selected.position().x=999;
            assertEquals(2048,((Pose3d)scope.cachedTo(a)).position().x);
        } finally {scope.close();}
    }
    @Test void cachedEndpointRoundTripsFarTranslatedRotatedPivotPrecisely() throws Exception {
        UUID id=UUID.randomUUID(); double base=20_481_032;
        var end=pose(base+12.25, 31.5, -9.75);
        end.rotationPoint().set(base+3.125, 2.5, -4.25);
        end.orientation().rotationY(0.713);
        var scope=scope(OwnedFrameSimulationScope.Failure.NONE);
        try {
            interval(scope,id,pose(base,30,-10),end);
            Vec3 storage=new Vec3(base+3.625, 34.75, -8.125);
            Vec3 roundTrip=((Pose3d)scope.cachedTo(id)).transformPositionInverse(end.transformPosition(storage));
            assertTrue(Double.isFinite(roundTrip.x) && Double.isFinite(roundTrip.y) && Double.isFinite(roundTrip.z));
            assertEquals(storage.x,roundTrip.x,1e-8); assertEquals(storage.y,roundTrip.y,1e-8); assertEquals(storage.z,roundTrip.z,1e-8);
        } finally {scope.close();}
    }
    @Test void cachedEndpointUsesRequestedFrameNotInitialAnchor() throws Exception {
        var scope=scope(OwnedFrameSimulationScope.Failure.NONE,pose(100,0,0),pose(110,0,0));
        try {
            UUID a=UUID.randomUUID(),b=UUID.randomUUID();var anchor=pose(100,0,0);var endA=pose(110,0,0);var endB=pose(-40,5,2);
            interval(scope,a,anchor,endA);interval(scope,b,pose(-50,0,0),endB);
            assertEquals(110,((Pose3d)scope.to()).position().x,"scope anchor remains frame A");
            assertEquals(-40,((Pose3d)scope.cachedTo(b)).position().x);
            assertNotEquals(((Pose3d)scope.to()).position().x,((Pose3d)scope.cachedTo(b)).position().x);
            assertEquals(110,((Pose3d)scope.cachedTo(a)).position().x);
        } finally {scope.close();}
    }
    @Test void missingOrFailedIntervalsNeverFallbackToAnotherEndpoint() throws Exception {
        var scope=scope(OwnedFrameSimulationScope.Failure.NONE);UUID a=UUID.randomUUID();
        try {
            var missing=assertThrows(OwnedFrameSimulationScope.TemporalUnavailableException.class,()->scope.cachedTo(a));
            assertEquals(OwnedFrameSimulationScope.Failure.HISTORY_UNAVAILABLE,missing.reason());
            assertEquals(OwnedFrameSimulationScope.Failure.HISTORY_UNAVAILABLE,scope.failure());
        } finally {scope.close();}
        var failed=scope(OwnedFrameSimulationScope.Failure.HISTORY_UNAVAILABLE);
        try {
            interval(failed,a,pose(0,0,0),pose(10,0,0));
            var error=assertThrows(OwnedFrameSimulationScope.TemporalUnavailableException.class,()->failed.cachedTo(a));
            assertEquals(OwnedFrameSimulationScope.Failure.HISTORY_UNAVAILABLE,error.reason());
        } finally {failed.close();}
    }
    @Test void unrelatedMissingTimelineDoesNotPoisonStepButSdkCandidateDoes() {
        assertFalse(OwnedFrameSimulationScope.missingHistoryIsFatal(false));
        assertTrue(OwnedFrameSimulationScope.missingHistoryIsFatal(true));
    }

    @Test void temporalBoundsUnionIndependentFromAndToTransformsAndAdmitsFastFrame() {
        var plot=new AABB(0,0,0,1,1,1);
        var from=new Pose3d(); from.position().set(10,0,0);
        var to=new Pose3d(); to.position().set(20,0,0);
        AABB union=OwnedFrameSimulationScope.unionBounds(plot,from,to);
        assertEquals(10,union.minX,1e-9);
        assertEquals(21,union.maxX,1e-9);
        assertFalse(new AABB(0,0,0,1,1,1).intersects(new AABB(10,0,0,21,1,1)));
        assertTrue(union.intersects(new AABB(10,0,0,11,1,1)));
    }

    @Test void rotationalSweepAndNonzeroPivotAreConservativelyBounded() {
        AABB longPlot=new AABB(-5,-.5,-.5,5,.5,.5);
        var from=new Pose3d(); from.position().set(10,2,0); from.rotationPoint().set(1,0,0);
        var to=new Pose3d(); to.position().set(20,3,1); to.rotationPoint().set(1,0,0);
        to.orientation().rotationY(Math.PI);
        AABB swept=OwnedFrameSimulationScope.unionBounds(longPlot,from,to);
        var middle=new Pose3d(from).lerp(to,.5);
        for(double x:new double[]{-5,5})for(double y:new double[]{-.5,.5})for(double z:new double[]{-.5,.5}) {
            var world=middle.transformPosition(new net.minecraft.world.phys.Vec3(x,y,z));
            assertTrue(swept.contains(world.x,world.y,world.z),"mid-rotation corner must be broadphase-admitted");
        }
        double storageOffset=10_000_000;
        AABB farPlot=new AABB(storageOffset-5,-.5,-.5,storageOffset+5,.5,.5);
        var farFrom=new Pose3d(from);farFrom.rotationPoint().x+=storageOffset;
        var farTo=new Pose3d(to);farTo.rotationPoint().x+=storageOffset;
        AABB farSwept=OwnedFrameSimulationScope.unionBounds(farPlot,farFrom,farTo);
        assertEquals(swept.minX,farSwept.minX,1e-8);assertEquals(swept.maxX,farSwept.maxX,1e-8);
        assertEquals(swept.minY,farSwept.minY,1e-8);assertEquals(swept.maxY,farSwept.maxY,1e-8);
        assertEquals(swept.minZ,farSwept.minZ,1e-8);assertEquals(swept.maxZ,farSwept.maxZ,1e-8);
    }

    @Test void renderPoseCacheSeparatesFramesAndInvalidatesByRevisionWithoutLeakingMutableState() {
        var cache=new PresentationPoseCache(); UUID a=UUID.randomUUID(),b=UUID.randomUUID();
        var poseA=new Pose3d();poseA.position().x=10;var poseB=new Pose3d();poseB.position().x=20;
        cache.put(a,.5f,1,poseA);cache.put(b,.5f,1,poseB);
        var selectedA=(Pose3d)cache.get(a,.5f,1);var selectedB=(Pose3d)cache.get(b,.5f,1);
        assertEquals(10,selectedA.position().x);assertEquals(20,selectedB.position().x);
        selectedA.position().x=999;
        assertEquals(10,((Pose3d)cache.get(a,.5f,1)).position().x);
        assertNull(cache.get(a,.5f,2));
        cache.remove(a);assertNull(cache.get(a,.5f,1));
    }

    @Test void pointVelocityRequiresKnownMotionButPoseOnlySeedRemainsUsable() {
        UUID level=UUID.randomUUID(),stationaryId=UUID.randomUUID(),movingId=UUID.randomUUID();
        var stationary=new AuthoritativeFrameTimeline();var rest=new Pose3d();
        assertTrue(stationary.seed(level,stationaryId,8,rest,new Pose3d(rest)));
        var stationaryResult=stationary.sample(stationaryId,8);
        assertEquals(AuthoritativeFrameTimeline.Kind.EXACT,stationaryResult.kind());
        assertEquals(Vec3.ZERO,OwnedFrameSimulationScope.requireKnownMotion(stationaryResult).linearVelocity());

        var moving=new AuthoritativeFrameTimeline();var before=new Pose3d();var after=new Pose3d();after.position().x=3;
        assertTrue(moving.seed(level,movingId,8,before,after));
        var seeded=moving.sample(movingId,8);
        assertEquals(AuthoritativeFrameTimeline.Kind.EXACT,seeded.kind());
        assertNotNull(seeded.pose(),"pose-only seed remains available to collision and rendering");
        assertFalse(seeded.motionKnown());
        assertThrows(OwnedFrameSimulationScope.TemporalUnavailableException.class,
                ()->OwnedFrameSimulationScope.requireKnownMotion(seeded),"frame departure must not invent zero velocity");
        assertFalse(moving.receive(level,movingId,8,after,new Vec3(4,0,0),Vec3.ZERO),
                "conflicting same-key packet cannot overwrite seed authority");
        assertTrue(moving.receive(level,movingId,9,after,new Vec3(4,0,0),Vec3.ZERO));
        var authoritative=moving.sample(movingId,9);
        assertTrue(authoritative.motionKnown());
        assertEquals(new Vec3(4,0,0),OwnedFrameSimulationScope.requireKnownMotion(authoritative).linearVelocity());
    }
}
