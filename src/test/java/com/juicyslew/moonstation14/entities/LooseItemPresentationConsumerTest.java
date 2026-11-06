package com.juicyslew.moonstation14.entities;

import com.juicyslew.moonstation14.ms14.loose_objects.LooseItemQueryGeometry;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** Focused source-contract and numeric regression for custom cargo presentation consumers. */
class LooseItemPresentationConsumerTest {
    @Test void capturedPresentationOffsetRemainsExactForRendererPivot() {
        Vec3 dispatcherBase=new Vec3(-12.859865596903767,-4.224373044806688,26.556209124329083);
        Vec3 renderedPivot=new Vec3(-11.650496199047216,-4.112371813850608,26.130421118814887);
        Vec3 capturedOffset=new Vec3(1.20936939785655,0.11200123095608028,-0.42578800551419604);
        Vec3 actual=LooseItemEntity.finitePresentationOffset(renderedPivot,dispatcherBase);
        assertNotNull(actual);
        assertEquals(capturedOffset.x,actual.x,1e-12);
        assertEquals(capturedOffset.y,actual.y,1e-12);
        assertEquals(capturedOffset.z,actual.z,1e-12);

        assertNull(LooseItemEntity.finitePresentationOffset(new Vec3(Double.NaN,0,0),Vec3.ZERO));
    }

    @Test void probeOffsetIsProjectedAsLocalPointThroughCombinedRenderPose() {
        Vec3 anchor=new Vec3(20.25,10.25,30.25);
        Vec3 probeOffset=new Vec3(.2,.65,-.15);
        for (var rotations : new double[][] {{.41,0,0},{0,0,Math.PI/2},{.7,-.35,.8},{1.1,.6,-.4}}) {
            var pose=new dev.ryanhcode.sable.companion.math.Pose3d();
            pose.position().set(100,300,100);
            pose.orientation().rotateY(rotations[0]).rotateX(rotations[1]).rotateZ(rotations[2]);
            Vec3 projectedProbe=pose.transformPosition(anchor.add(probeOffset));
            Vec3 recovered=pose.transformPositionInverse(projectedProbe);
            assertEquals(anchor.x+probeOffset.x,recovered.x,1e-9);
            assertEquals(anchor.y+probeOffset.y,recovered.y,1e-9);
            assertEquals(anchor.z+probeOffset.z,recovered.z,1e-9);
        }
        var yawOnly=new dev.ryanhcode.sable.companion.math.Pose3d();
        yawOnly.position().set(100,300,100);
        yawOnly.orientation().rotateY(.9);
        Vec3 yawPivot=yawOnly.transformPosition(anchor);
        Vec3 eyeOffset=new Vec3(0,probeOffset.y,0);
        Vec3 oldYawProbe=yawOnly.transformPositionInverse(yawPivot.add(eyeOffset));
        assertTrue(anchor.add(eyeOffset).distanceTo(oldYawProbe)<1e-9,
                "yaw preserves world up, so the inherited world-space probe already matches local up");
        var roll=new dev.ryanhcode.sable.companion.math.Pose3d();
        roll.position().set(100,300,100);
        roll.orientation().rotateX(Math.PI/2);
        Vec3 legacyWorldUpProbe=roll.transformPositionInverse(roll.transformPosition(anchor).add(0,probeOffset.y,0));
        assertEquals(anchor.x,legacyWorldUpProbe.x,1e-9);
        assertEquals(30,net.minecraft.core.BlockPos.containing(anchor).getZ());
        assertEquals(29,net.minecraft.core.BlockPos.containing(legacyWorldUpProbe).getZ());
        assertNotEquals(anchor.z,legacyWorldUpProbe.z,1e-6,
                "old pivot-plus-world-up drifts into another storage cell under roll");
    }

    @Test void capturedUntrackedJugVertexOutsideOldCullBoxIsInsideTranslatedProfileEnvelopeUnion() {
        AABB oldPhysicalCull=new AABB(-12.5,-4.4,25.8,-11.97395,-3.8,26.5);
        Vec3 capturedModelVertex=new Vec3(-11.91605,-4.0,26.2);
        assertFalse(oldPhysicalCull.contains(capturedModelVertex),"captured mesh vertex was beyond physical maxX");

        Vec3 capturedPivot=new Vec3(-11.650496199047216,-4.112371813850608,26.130421118814887);
        var presentedProfile=new LooseItemQueryGeometry(.5,.75,capturedPivot,new Quaterniond().rotationY(.85));
        AABB union=LooseItemQueryGeometry.union(oldPhysicalCull,presentedProfile.worldEnvelope());
        assertTrue(union.contains(capturedModelVertex),"oriented rendered-profile envelope covers the observed mesh point");
        assertTrue(union.minX<=oldPhysicalCull.minX&&union.maxX>=oldPhysicalCull.maxX,
                "union is never narrower than vanilla translated culling bounds");
    }

    @Test void liveLightProbeAndRendererShareFiniteCustomOffsetGateAndConservativeCullGeometry() throws Exception {
        String entity=read("src/main/java/com/juicyslew/moonstation14/entities/LooseItemEntity.java");
        String uses=section(entity,"public boolean usesCustomFramePresentation(","/** Exact renderer offset");
        assertTrue(uses.contains("level().isClientSide"));
        assertTrue(uses.contains("Float.isFinite(partialTick)"));
        assertTrue(uses.contains("!isNativeFrameTracked()"));
        assertTrue(uses.contains("committedRenderFrameId(partialTick)!=null"));
        String probe=section(entity,"@Override public Vec3 getLightProbePosition(","private static boolean finitePoint");
        assertTrue(probe.contains("super.getLightProbePosition(partialTick)"));
        assertTrue(probe.contains("finitePresentationOffset(inherited,ordinary)"));
        assertTrue(probe.contains("anchor.add(probeOffset)"));
        assertTrue(probe.contains("projectCommittedRenderPoint(anchor.add(probeOffset),partialTick)"));
        String projector=section(entity,"private Vec3 projectCommittedRenderPoint(","/** The ordinary renderer pivot");
        assertTrue(projector.contains("committedRenderFrameId(partialTick)"));
        assertTrue(projector.contains("projectActorPoint("));
        String renderPosition=section(entity,"public Vec3 renderPosition(","private Vec3 projectCommittedRenderPoint(");
        assertTrue(renderPosition.contains("projectCommittedRenderPoint(anchor, partialTick)"),
                "pivot and probe must share the production committed-pose projection helper");
        assertTrue(probe.contains("return inherited"),"invalid, paused, world, native and server paths retain vanilla probe");

        String renderer=read("src/main/java/com/juicyslew/moonstation14/ms14/loose_objects/client/LooseItemRenderer.java");
        String offset=section(renderer,"@Override public net.minecraft.world.phys.Vec3 getRenderOffset(","@Override public boolean shouldRender");
        assertTrue(offset.contains("entity.presentationOffset(partialTick)"));
        String culling=section(renderer,"@Override public boolean shouldRender(","/** Applies the production");
        assertTrue(culling.contains("entity.usesCustomFramePresentation(partial)"));
        assertTrue(culling.contains("entity.getBoundingBoxForCulling()"));
        assertTrue(culling.contains("union(translatedPhysical,geometry.worldEnvelope())"));
        assertTrue(culling.contains("frustum.isVisible(conservative)"));
        assertTrue(culling.contains("return super.shouldRender(entity,frustum,cameraX,cameraY,cameraZ)"));
        String hands=read("src/main/java/com/juicyslew/moonstation14/ms14/hands/client/BodyHandClient.java");
        assertTrue(hands.contains("LooseItemQueryGeometry.forPresentedEntity(loose, ray.partialTick())"));
    }

    private static String section(String source,String start,String end) {
        int from=source.indexOf(start),to=source.indexOf(end,from);
        assertTrue(from>=0&&to>from,"source section exists: "+start);
        return source.substring(from,to);
    }

    private static String read(String path) throws Exception {
        Path root=Path.of(System.getProperty("user.dir"));
        while(root!=null&&!Files.exists(root.resolve(path)))root=root.getParent();
        assertNotNull(root,path);
        return Files.readString(root.resolve(path));
    }
}
