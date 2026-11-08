package com.juicyslew.moonstation14.ms14.loose_objects.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WorldItemPresentationRendererAlphaTest {
    @Test void jugConsumerUsesTintAndAlphaWrapperIncludingOpaqueTint() {
        var source = org.mockito.Mockito.mock(com.mojang.blaze3d.vertex.VertexConsumer.class);
        var buffers = org.mockito.Mockito.mock(net.minecraft.client.renderer.MultiBufferSource.class);
        org.mockito.Mockito.when(buffers.getBuffer(org.mockito.ArgumentMatchers.any())).thenReturn(source);
        int[] count = {0};
        var opaque = WorldItemPresentationRenderer.jugVertexConsumer(buffers, 255, 0xFF0000, count);
        opaque.setColor(80, 120, 200, 255);
        opaque.addVertex(0, 0, 0);
        org.mockito.Mockito.verify(source).setColor(80, 0, 0, 255);
        assertEquals(1, count[0]);

        org.mockito.Mockito.reset(buffers);
        org.mockito.Mockito.when(buffers.getBuffer(org.mockito.ArgumentMatchers.any())).thenReturn(source);
        assertSame(source, WorldItemPresentationRenderer.jugVertexConsumer(buffers, 255, 0xFFFFFF, null));

        org.mockito.Mockito.reset(source, buffers);
        org.mockito.Mockito.when(buffers.getBuffer(org.mockito.ArgumentMatchers.any())).thenReturn(source);
        var translucent = WorldItemPresentationRenderer.jugVertexConsumer(buffers, 128, 0x808040, null);
        translucent.setColor(200, 100, 50, 128);
        org.mockito.Mockito.verify(source).setColor(100, 50, 13, 64);
    }

    @Test void suppliedJugMaterialIsUsedWithoutLeakingIntoDefaultCallers() {
        var source = org.mockito.Mockito.mock(com.mojang.blaze3d.vertex.VertexConsumer.class);
        var buffers = org.mockito.Mockito.mock(net.minecraft.client.renderer.MultiBufferSource.class);
        var override = org.mockito.Mockito.mock(net.minecraft.client.renderer.RenderType.class);
        org.mockito.Mockito.when(buffers.getBuffer(org.mockito.ArgumentMatchers.any())).thenReturn(source);

        assertSame(source, WorldItemPresentationRenderer.jugVertexConsumer(buffers, 255, 0xFFFFFF, null, override));
        org.mockito.Mockito.verify(buffers).getBuffer(org.mockito.ArgumentMatchers.same(override));

        org.mockito.Mockito.clearInvocations(buffers);
        WorldItemPresentationRenderer.jugVertexConsumer(buffers, 255, 0xFFFFFF, null);
        org.mockito.Mockito.verify(buffers).getBuffer(org.mockito.ArgumentMatchers.same(net.minecraft.client.renderer.RenderType.cutout()));
    }

    @Test void alphaClampsAndRoundsToByte() {
        assertEquals(0, WorldItemPresentationRenderer.alphaByte(Float.NEGATIVE_INFINITY));
        assertEquals(0, WorldItemPresentationRenderer.alphaByte(Float.NaN));
        assertEquals(0, WorldItemPresentationRenderer.alphaByte(-.1f));
        assertEquals(0, WorldItemPresentationRenderer.alphaByte(0f));
        assertEquals(1, WorldItemPresentationRenderer.alphaByte(.5f / 255f));
        assertEquals(128, WorldItemPresentationRenderer.alphaByte(.5f));
        assertEquals(255, WorldItemPresentationRenderer.alphaByte(1f));
        assertEquals(255, WorldItemPresentationRenderer.alphaByte(Float.POSITIVE_INFINITY));
        assertEquals(255, WorldItemPresentationRenderer.alphaByte(2f));
    }

    @Test void redBlendPreservesBaseAtZeroAndReturnsPureRedAtFullStrength() {
        assertEquals(0x123456, WorldItemPresentationRenderer.blendRed(0x123456, 0));
        assertEquals(0xFF0000, WorldItemPresentationRenderer.blendRed(0x123456, 1));
        assertEquals(0xFF0000, WorldItemPresentationRenderer.blendRed(0x123456, 2));
        assertEquals(128, WorldItemPresentationRenderer.alphaByte(.5f));
    }

    @Test void projectionExtentUsesActualTransformedQuadVerticesAndRejectsDegenerateAxis() {
        var model = org.mockito.Mockito.mock(net.minecraft.client.resources.model.BakedModel.class);
        var quad = org.mockito.Mockito.mock(net.minecraft.client.renderer.block.model.BakedQuad.class);
        int[] vertices = new int[32]; int at=0;
        float[][] corners={{.1f,.2f,.3f},{.9f,.15f,.25f},{.8f,.85f,.4f},{.2f,.75f,.35f}};
        for(float[] p:corners){vertices[at]=Float.floatToRawIntBits(p[0]);vertices[at+1]=Float.floatToRawIntBits(p[1]);vertices[at+2]=Float.floatToRawIntBits(p[2]);at+=8;}
        org.mockito.Mockito.when(quad.getVertices()).thenReturn(vertices);
        org.mockito.Mockito.when(model.getQuads(org.mockito.ArgumentMatchers.isNull(),org.mockito.ArgumentMatchers.isNull(),org.mockito.ArgumentMatchers.any())).thenReturn(java.util.List.of(quad));
        org.mockito.Mockito.when(model.getQuads(org.mockito.ArgumentMatchers.isNull(),org.mockito.ArgumentMatchers.notNull(),org.mockito.ArgumentMatchers.any())).thenReturn(java.util.List.of());
        org.mockito.Mockito.when(model.applyTransform(org.mockito.ArgumentMatchers.eq(net.minecraft.world.item.ItemDisplayContext.NONE),org.mockito.ArgumentMatchers.any(com.mojang.blaze3d.vertex.PoseStack.class),org.mockito.ArgumentMatchers.eq(false))).thenReturn(model);
        var profile = new com.juicyslew.moonstation14.ms14.loose_objects.LooseObjectProfile(1,.5f,0,10,1,"flat_sprite","item",false,0,10,1,0,.5f,.1f,
                new com.juicyslew.moonstation14.ms14.loose_objects.LooseObjectProfile.VisualTuning(.6f,.8f,.13f,-.17f,1,0));
        var caller=new org.joml.Matrix4f().rotationXYZ(.4f,.7f,-.2f);
        var axis=new org.joml.Vector3f(.3f,.5f,.8f).normalize();
        var extent = LoosePresentationBounds.flatSpriteProjection(model, profile, 1.25f, caller, axis);
        assertNotNull(extent);
        var pose=new com.mojang.blaze3d.vertex.PoseStack();pose.last().pose().set(caller);LooseItemRenderer.applyCameraFacingFlatPose(pose,profile,1.25f);pose.translate(-.5,-.5,-.5);
        float min=Float.POSITIVE_INFINITY,max=Float.NEGATIVE_INFINITY; org.joml.Vector3f maxPoint=null;
        for(float[] p:corners){var transformed=pose.last().pose().transformPosition(new org.joml.Vector3f(p[0],p[1],p[2]));float dot=transformed.dot(axis);min=Math.min(min,dot);if(dot>max){max=dot;maxPoint=transformed;}}
        assertEquals(min,extent.min(),1e-5);assertEquals(max,extent.max(),1e-5);
        assertEquals(maxPoint.x, (float)extent.maxPoint().x,1e-5);assertEquals(maxPoint.y,(float)extent.maxPoint().y,1e-5);assertEquals(maxPoint.z,(float)extent.maxPoint().z,1e-5);
        assertEquals(max, new org.joml.Vector3f((float)extent.maxPoint().x,(float)extent.maxPoint().y,(float)extent.maxPoint().z).dot(axis),1e-5);
        assertNull(LoosePresentationBounds.flatSpriteProjection(model,
                profile, 1f,
                new org.joml.Matrix4f(), new org.joml.Vector3f()));
    }
}
