package com.juicyslew.moonstation14.ms14.loose_objects.client;

import com.juicyslew.moonstation14.entities.ItemAttachment;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AttachedThrownItemPoseTest {
    private static net.minecraft.client.resources.model.BakedModel model() {
        var model = mock(net.minecraft.client.resources.model.BakedModel.class);
        var quad = mock(net.minecraft.client.renderer.block.model.BakedQuad.class);
        int[] vertices = new int[32];
        float[][] corners = {{.1f,.1f,.1f},{.9f,.1f,.1f},{.9f,.9f,.1f},{.1f,.9f,.1f}};
        for (int i=0;i<4;i++) { int at=i*8; for(int j=0;j<3;j++) vertices[at+j]=Float.floatToRawIntBits(corners[i][j]); }
        when(quad.getVertices()).thenReturn(vertices);
        when(model.getQuads(isNull(), isNull(), any())).thenReturn(java.util.List.of(quad));
        when(model.getQuads(isNull(), notNull(), any())).thenReturn(java.util.List.of());
        when(model.applyTransform(eq(net.minecraft.world.item.ItemDisplayContext.NONE), any(PoseStack.class), eq(false))).thenReturn(model);
        return model;
    }

    @Test void actualSpriteTipTracksNativeContactForBothAuthoredSpriteAxesAndRootPoses() {
        var profile = new com.juicyslew.moonstation14.ms14.loose_objects.LooseObjectProfile(1,.5f,0,10,1,"flat_sprite","item",false,0,10,1,0,.5f,.1f,
                new com.juicyslew.moonstation14.ms14.loose_objects.LooseObjectProfile.VisualTuning(.6f,.8f,.13f,-.17f,1,0));
        var baked=model();
        var contact = new Vec3(.2,.3,-.1); var incoming = new Vec3(.6,.8,0);
        var attachment = new ItemAttachment(UUID.randomUUID(),1,contact,incoming,ItemAttachment.AttachmentKind.EMBEDDED);
        for(float angle:new float[]{45,315,225}) for(boolean prone:new boolean[]{false,true}) for(float offset:new float[]{-.15f,0}) {
            var data = new com.juicyslew.moonstation14.ms14.combat.throwing.ThrownItemProfile.EmbeddingData(1.5f,true,false,offset,0,java.util.Optional.empty(),angle);
            PoseStack root=new PoseStack();
            if(prone) root.mulPose(com.juicyslew.moonstation14.ms14.standing.PronePoseGeometry.relativeRootMatrix(1,1,90,true));
            else { root.mulPose(new org.joml.Quaternionf().rotationY((float)Math.PI/2)); root.scale(2,2,2); root.translate(0,-1.501,0); }
            var originalBasisDirection=new org.joml.Vector3f(.6f,.8f,0);
            root.last().pose().transformDirection(originalBasisDirection).normalize();
            var embedded=AttachedThrownItemLayer.EmbeddedItemPose.calculate(baked,profile,1,attachment,data);
            assertNotNull(embedded);
            var source=new org.joml.Vector3f((float)Math.cos(Math.toRadians(angle)),(float)Math.sin(Math.toRadians(angle)),0);
            assertTrue(embedded.rotation().transform(source).distance(new org.joml.Vector3f(.6f,.8f,0))<1e-5);
            var expected=root.last().pose().transformPosition(new org.joml.Vector3f((float)contact.x,(float)contact.y,(float)contact.z));
            AttachedThrownItemLayer.AttachedItemPose.apply(root,attachment,1);
            root.translate(embedded.anchor().x,embedded.anchor().y,embedded.anchor().z); root.mulPose(embedded.rotation());
            var extent=LoosePresentationBounds.flatSpriteProjection(baked,profile,1,root.last().pose(),new org.joml.Vector3f(.6f,.8f,0));
            assertNotNull(extent);
            var tip=new org.joml.Vector3f((float)extent.maxPoint().x, (float)extent.maxPoint().y, (float)extent.maxPoint().z);
            var worldDir=originalBasisDirection;
            var desired=new org.joml.Vector3f(expected).add(new org.joml.Vector3f(worldDir).mul(-offset));
            assertTrue(tip.distance(desired)<1e-4, "axis="+angle+" prone="+prone+" offset="+offset+" actual="+tip+" expected="+desired);
        }
    }
    @Test void contactIsTranslatedInRootBeforeScaleIsRemovedAndAimIsNotAppliedTwice() {
        var attachment = new ItemAttachment(UUID.randomUUID(), 1, new Vec3(.4, .2, -.1),
                new Vec3(0, 0, 1), ItemAttachment.AttachmentKind.EMBEDDED);
        for (float scale : new float[]{.5f, 2f}) {
            PoseStack pose = new PoseStack();
            pose.scale(scale, scale, scale);
            var expected = pose.last().pose().transformPosition(new org.joml.Vector3f(.4f, .2f, -.1f));
            AttachedThrownItemLayer.AttachedItemPose.apply(pose, attachment, 1f);
            var root = pose.last().pose();
            assertEquals(expected.x, root.m30(), 1e-5);
            assertEquals(expected.y, root.m31(), 1e-5);
            assertEquals(expected.z, root.m32(), 1e-5);
            assertEquals(1, root.transformPosition(new org.joml.Vector3f(1, 0, 0))
                    .sub(root.transformPosition(new org.joml.Vector3f())).length(), 1e-5);
        }
    }

    @Test void extraRendererScalePreservesCanonicalContactForUprightAndProneRoots() {
        var contact = new Vec3(.31, .24, -.17);
        var attachment = new ItemAttachment(UUID.randomUUID(), 1, contact, new Vec3(.6, .8, 0),
                ItemAttachment.AttachmentKind.EMBEDDED);
        for (boolean prone : new boolean[]{false, true}) for (float lunge : new float[]{0, .22f}) {
            PoseStack canonical = new PoseStack();
            canonical.translate(lunge, 0, 0);
            if (prone) canonical.mulPose(com.juicyslew.moonstation14.ms14.standing.PronePoseGeometry.relativeRootMatrix(1, 1, 90, true));
            else {
                canonical.mulPose(new org.joml.Quaternionf().rotationY((float)Math.PI / 2));
                canonical.scale(-1, -1, 1);
                canonical.translate(0, -1.501, 0);
            }

            PoseStack extra = new PoseStack();
            extra.last().pose().set(canonical.last().pose());
            extra.translate(0, 1.501, 0);
            extra.scale(.82f, .82f, .82f);
            extra.translate(0, -1.501, 0);
            var expected = canonical.last().pose().transformPosition(new org.joml.Vector3f((float)contact.x, (float)contact.y, (float)contact.z));

            AttachedThrownItemLayer.AttachedItemPose.apply(extra, attachment, .82f);
            var actual = extra.last().pose();
            assertEquals(expected.x, actual.m30(), 1e-5, "x prone=" + prone + " lunge=" + lunge);
            assertEquals(expected.y, actual.m31(), 1e-5, "y prone=" + prone + " lunge=" + lunge);
            assertEquals(expected.z, actual.m32(), 1e-5, "z prone=" + prone + " lunge=" + lunge);
            for (int column = 0; column < 3; column++) {
                float norm = (float)Math.sqrt(actual.get(0, column) * actual.get(0, column)
                        + actual.get(1, column) * actual.get(1, column) + actual.get(2, column) * actual.get(2, column));
                assertEquals(1, norm, 1e-5, "column=" + column + " prone=" + prone + " lunge=" + lunge);
            }
        }
    }
}
