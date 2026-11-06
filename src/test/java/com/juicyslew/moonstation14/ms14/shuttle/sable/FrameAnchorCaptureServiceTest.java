package com.juicyslew.moonstation14.ms14.shuttle.sable;

import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class FrameAnchorCaptureServiceTest {
    private static final UUID ID = UUID.randomUUID();
    private static final FrameAnchorCaptureService.Allocation AREA = new FrameAnchorCaptureService.Allocation(10_000, 10_000, 7);
    private static FrameAnchorCaptureService.OwnershipProofs proofs() { return new FrameAnchorCaptureService.OwnershipProofs(); }
    private static dev.ryanhcode.sable.companion.math.Pose3d pose(double x, double z, double yaw) {
        var p = new dev.ryanhcode.sable.companion.math.Pose3d();
        p.position().set(x, 4, z); p.orientation().rotateY(yaw); return p;
    }
    private static FrameAnchorCaptureService.Capture capture(FrameAnchorCaptureService.OwnershipProofs p,
            Object owner,Object registered,UUID id,boolean sameLevel,boolean removed,Vec3 world,
            dev.ryanhcode.sable.companion.math.Pose3dc live,FrameAnchorCaptureService.Allocation allocation) {
        return p.capture(owner,registered,id,sameLevel,removed,world,live,allocation);
    }

    @Test void liveRotatedPoseRecoversLargeStorageCoordinates() {
        var p=proofs(); Object owner=new Object(); var pose=pose(10,10,Math.PI/2);
        Vec3 local=new Vec3(20_481_032.375,128,20_481_032.625);
        Vec3 world=pose.transformPosition(local);
        var result=capture(p,owner,owner,ID,true,false,world,pose,AREA);
        assertEquals(FrameAnchorState.Status.FRAME,result.status()); assertEquals("live",result.reason());
        assertEquals(local.x,result.anchor().storagePoint().x,1e-7); assertEquals(local.y,result.anchor().storagePoint().y,1e-7);
        assertEquals(local.z,result.anchor().storagePoint().z,1e-7);
    }

    @Test void unloadedProofCopiesLogicalPoseAndRequiresExactOwnerInstance() {
        var p=proofs(); Object owner=new Object(); var logical=pose(10,10,.4);
        Vec3 local=new Vec3(100,20,200); Vec3 world=logical.transformPosition(local);
        p.removed(owner,ID,SubLevelRemovalReason.UNLOADED,logical,new FrameAnchorCaptureService.Allocation(0,0,7));
        logical.position().set(500,500,500);
        var result=capture(p,owner,null,ID,true,true,world,null,null);
        assertEquals(FrameAnchorState.Status.FRAME,result.status()); assertEquals("unloaded",result.reason());
        assertEquals(local.x,result.anchor().storagePoint().x,1e-8); assertEquals(local.y,result.anchor().storagePoint().y,1e-8);
        assertEquals(local.z,result.anchor().storagePoint().z,1e-8);
        assertEquals(FrameAnchorState.Status.UNRESOLVED,capture(p,new Object(),null,ID,true,true,world,null,null).status());
        assertEquals(FrameAnchorState.Status.UNRESOLVED,capture(p,owner,new Object(),ID,true,true,world,null,null).status());
    }

    @Test void unloadProofBaselineMakesIdentityAndLevelFailuresMeaningful() {
        var p=proofs(); Object owner=new Object(); var pose=pose(10,10,.2); Vec3 local=new Vec3(100,20,200);
        Vec3 world=pose.transformPosition(local); var allocation=new FrameAnchorCaptureService.Allocation(0,0,7);
        p.removed(owner,ID,SubLevelRemovalReason.UNLOADED,pose,allocation);
        assertEquals(FrameAnchorState.Status.FRAME,capture(p,owner,null,ID,true,true,world,null,null).status());
        assertEquals(FrameAnchorState.Status.UNRESOLVED,capture(p,owner,null,UUID.randomUUID(),true,true,world,null,null).status());
        assertEquals(FrameAnchorState.Status.UNRESOLVED,capture(p,owner,null,ID,false,true,world,null,null).status());
        assertEquals(FrameAnchorState.Status.UNRESOLVED,capture(p,owner,null,null,true,true,world,null,null).status());
    }

    @Test void invalidationAndMissingProofAreCheckedAgainstValidControl() {
        var p=proofs(); Object owner=new Object(); var pose=pose(10,10,.2); Vec3 world=pose.transformPosition(new Vec3(100,20,200));
        var allocation=new FrameAnchorCaptureService.Allocation(0,0,7);
        assertEquals(FrameAnchorState.Status.UNRESOLVED,capture(p,owner,null,ID,true,true,world,null,null).status());
        p.removed(owner,ID,SubLevelRemovalReason.UNLOADED,pose,allocation);
        assertEquals(FrameAnchorState.Status.FRAME,capture(p,owner,null,ID,true,true,world,null,null).status());
        p.removed(owner,ID,SubLevelRemovalReason.REMOVED,pose,allocation);
        assertEquals(FrameAnchorState.Status.UNRESOLVED,capture(p,owner,null,ID,true,true,world,null,null).status());
        p.removed(owner,ID,SubLevelRemovalReason.UNLOADED,pose,allocation);
        assertEquals(FrameAnchorState.Status.FRAME,capture(p,owner,null,ID,true,true,world,null,null).status());
        p.removed(owner,ID,SubLevelRemovalReason.UNLOADED,null,allocation);
        assertEquals(FrameAnchorState.Status.UNRESOLVED,capture(p,owner,null,ID,true,true,world,null,null).status());
        p.removed(owner,ID,SubLevelRemovalReason.UNLOADED,pose,allocation);
        assertEquals(FrameAnchorState.Status.FRAME,capture(p,owner,null,ID,true,true,world,null,null).status());
        p.removed(owner,null,SubLevelRemovalReason.UNLOADED,pose,allocation);
        assertEquals(FrameAnchorState.Status.UNRESOLVED,capture(p,owner,null,ID,true,true,world,null,null).status());
    }

    @Test void closePreventsLateUnloadAndOwnerlessWorldIsOnlyActiveWorld() {
        var p=proofs(); Object owner=new Object(); var pose=pose(10,10,.2); Vec3 world=pose.transformPosition(new Vec3(100,20,200));
        var allocation=new FrameAnchorCaptureService.Allocation(0,0,7);
        p.removed(owner,ID,SubLevelRemovalReason.UNLOADED,pose,allocation);
        assertEquals(FrameAnchorState.Status.FRAME,capture(p,owner,null,ID,true,true,world,null,null).status());
        assertEquals(FrameAnchorState.Status.WORLD,capture(p,null,null,null,true,false,world,null,null).status());
        p.close(); p.removed(owner,ID,SubLevelRemovalReason.UNLOADED,pose,allocation);
        assertEquals(FrameAnchorState.Status.UNRESOLVED,capture(p,owner,null,ID,true,true,world,null,null).status());
        assertEquals(FrameAnchorState.Status.UNRESOLVED,capture(p,null,null,null,true,false,world,null,null).status());
    }

    @Test void liveAndUnloadedCoreRejectNonfiniteOrOutOfAllocationPoints() {
        var p=proofs(); Object owner=new Object(); var pose=pose(0,0,.1);
        var allocation=new FrameAnchorCaptureService.Allocation(0,0,7); Vec3 valid=pose.transformPosition(new Vec3(100,20,200));
        assertEquals(FrameAnchorState.Status.FRAME,capture(p,owner,owner,ID,true,false,valid,pose,allocation).status());
        assertEquals(FrameAnchorState.Status.UNRESOLVED,capture(p,owner,owner,ID,true,false,new Vec3(Double.NaN,0,0),pose,allocation).status());
        var badPose=pose(Double.NaN,0,0);
        assertEquals(FrameAnchorState.Status.UNRESOLVED,capture(p,owner,owner,ID,true,false,valid,badPose,allocation).status());
        assertEquals(FrameAnchorState.Status.UNRESOLVED,capture(p,owner,owner,ID,true,false,pose.transformPosition(new Vec3(2_100_000,0,0)),pose,allocation).status());
        p.removed(owner,ID,SubLevelRemovalReason.UNLOADED,pose,allocation);
        assertEquals(FrameAnchorState.Status.FRAME,capture(p,owner,null,ID,true,true,valid,null,null).status());
        assertEquals(FrameAnchorState.Status.UNRESOLVED,capture(p,owner,null,ID,true,true,new Vec3(Double.NaN,0,0),null,null).status());
        assertEquals(FrameAnchorState.Status.UNRESOLVED,capture(p,owner,null,ID,true,true,pose.transformPosition(new Vec3(2_100_000,0,0)),null,null).status());
    }

    @Test void allocationIsFiniteHalfOpenAndDoesNotConstrainYOrHullSize() {
        var a=new FrameAnchorCaptureService.Allocation(2,3,7); double size=16.0*(1L<<7);
        assertTrue(a.contains(new Vec3(2*size,Double.MAX_VALUE,3*size)));
        assertFalse(a.contains(new Vec3(3*size,0,3*size))); assertFalse(a.contains(new Vec3(2*size,0,4*size)));
        assertFalse(a.contains(new Vec3(Double.NaN,0,3*size))); assertFalse(a.contains(new Vec3(2*size,0,Double.POSITIVE_INFINITY)));
        assertFalse(new FrameAnchorCaptureService.Allocation(0,0,-1).contains(Vec3.ZERO));
        assertFalse(new FrameAnchorCaptureService.Allocation(0,0,31).contains(Vec3.ZERO));
        var negative=new FrameAnchorCaptureService.Allocation(-2,-3,7);
        assertTrue(negative.contains(new Vec3(-2*size,0,-3*size)));
        assertFalse(negative.contains(new Vec3(-3*size,0,-3*size)));
    }

    @Test void committedCaptureKeepsAcceptedPointAcrossPoseChangeWhileCargoRemainsInverseBased() {
        var p=proofs(); Object owner=new Object(); var initial=pose(0,0,.2);
        Vec3 local=new Vec3(100,129,200), world=initial.transformPosition(local);
        var allocation=new FrameAnchorCaptureService.Allocation(0,0,7);
        var candidate=new FrameAnchor(ID,local);
        var moved=pose(0,0,.7); moved.position().set(0,4.952,0);
        assertEquals(FrameAnchorState.Status.FRAME,p.captureCommitted(owner,owner,ID,true,false,allocation,candidate).status());
        assertEquals(local,p.captureCommitted(owner,owner,ID,true,false,allocation,candidate).anchor().storagePoint());
        var inverse=capture(p,owner,owner,ID,true,false,world,moved,allocation);
        assertEquals(FrameAnchorState.Status.UNRESOLVED,inverse.status());
        assertEquals(FrameAnchorState.Status.UNRESOLVED,p.captureCommitted(owner,owner,ID,true,false,allocation,
                new FrameAnchor(UUID.randomUUID(),local)).status());
        assertEquals(FrameAnchorState.Status.UNRESOLVED,p.captureCommitted(owner,new Object(),ID,true,false,allocation,candidate).status());
        assertEquals(FrameAnchorState.Status.UNRESOLVED,p.captureCommitted(owner,owner,ID,true,false,allocation,
                new FrameAnchor(ID,new Vec3(2_100_000,129,200))).status());
    }

    @Test void committedUnloadedUsesIdentityProofAllocationAndOriginalPoint() {
        var p=proofs(); Object owner=new Object(); var initial=pose(0,0,.2);
        Vec3 local=new Vec3(100,129,200), world=initial.transformPosition(local);
        var allocation=new FrameAnchorCaptureService.Allocation(0,0,7);
        p.removed(owner,ID,SubLevelRemovalReason.UNLOADED,initial,allocation);
        var candidate=new FrameAnchor(ID,local);
        assertEquals(FrameAnchorState.Status.FRAME,p.captureCommitted(owner,null,ID,true,true,null,candidate).status());
        assertEquals(local,p.captureCommitted(owner,null,ID,true,true,null,candidate).anchor().storagePoint());
        assertNotEquals(local,capture(p,owner,null,ID,true,true,world,null,null).anchor().storagePoint());
        p.removed(owner,ID,SubLevelRemovalReason.REMOVED,initial,allocation);
        assertEquals(FrameAnchorState.Status.UNRESOLVED,p.captureCommitted(owner,null,ID,true,true,null,candidate).status());
    }

    @Test void stateWriteAndRuntimeCaptureKeepSharedDelegation() throws Exception {
        assertCalls(FrameAnchorState.class,"write","writeCapture","FrameAnchorState");
        assertCalls(FrameAnchorCaptureService.class,"capture","capture","OwnershipProofs");
        ClassNode node=read(FrameAnchorCaptureService.class);
        var removal=node.methods.stream().filter(m->m.name.equals("recordRemoval")).findFirst().orElseThrow();
        assertTrue(calls(removal,"removed","OwnershipProofs"));
    }
    private static void assertCalls(Class<?> type,String method,String target,String targetClass) throws Exception {
        ClassNode node=read(type); var m=node.methods.stream().filter(x->x.name.equals(method)).findFirst().orElseThrow();
        assertTrue(calls(m,target,targetClass),method+" delegates to "+target);
    }
    private static boolean calls(org.objectweb.asm.tree.MethodNode method,String name) {
        return calls(method,name,name.equals("writeCapture")?"FrameAnchorState":"FrameAnchorCaptureService");
    }
    private static boolean calls(org.objectweb.asm.tree.MethodNode method,String name,String targetClass) {
        for(var i:method.instructions) if(i instanceof MethodInsnNode call && call.name.equals(name)
                && (call.owner.endsWith("/"+targetClass) || call.owner.endsWith("$"+targetClass))) return true;
        return false;
    }
    private static ClassNode read(Class<?> type) throws Exception {
        ClassNode node=new ClassNode(Opcodes.ASM9);
        try(var in=type.getResourceAsStream("/"+type.getName().replace('.','/')+".class")) { assertNotNull(in); new ClassReader(in).accept(node,ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES); }
        return node;
    }
}
