package com.juicyslew.moonstation14.ms14.loose_objects.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EmbeddedSpriteTipProjectionTest {
    @Test void hypoDartLargerLiveVisualScaleKeepsAuthoredTipAtContact() {
        var model=fixture();
        var dart=new com.juicyslew.moonstation14.ms14.loose_objects.LooseObjectProfile(1,.5f,0,10,1,"flat_sprite","item",false,0,16,1.5f,0,.4f,.08f,
                new com.juicyslew.moonstation14.ms14.loose_objects.LooseObjectProfile.VisualTuning(.4f,.4f,0,0,1,0));
        var hypo=new com.juicyslew.moonstation14.ms14.loose_objects.LooseObjectProfile(1,.5f,0,10,1,"flat_sprite","item",false,0,16,1.5f,0,.4f,.08f,
                new com.juicyslew.moonstation14.ms14.loose_objects.LooseObjectProfile.VisualTuning(.8f,.8f,0,0,1,0));
        var contact=new net.minecraft.world.phys.Vec3(.2,.3,-.1);
        var direction=new net.minecraft.world.phys.Vec3(.3,.4,.8660254).normalize();
        var data=new com.juicyslew.moonstation14.ms14.combat.throwing.ThrownItemProfile.EmbeddingData(1.5f,true,false,0,0,java.util.Optional.empty(),315,
                java.util.Optional.of(new com.juicyslew.moonstation14.ms14.combat.throwing.ThrownItemProfile.SpriteTipData(.625f,.65625f)));
        var attachment=new com.juicyslew.moonstation14.entities.ItemAttachment(java.util.UUID.randomUUID(),1,contact,direction,com.juicyslew.moonstation14.entities.ItemAttachment.AttachmentKind.EMBEDDED);
        for (var profile : new com.juicyslew.moonstation14.ms14.loose_objects.LooseObjectProfile[]{dart,hypo}) {
            var embedded=AttachedThrownItemLayer.EmbeddedItemPose.calculate(model,profile,1,attachment,data);
            assertNotNull(embedded);
            var tip=LoosePresentationBounds.flatSpritePointAtUv(model,profile,1,new org.joml.Matrix4f().rotate(embedded.rotation()),.625f,.65625f);
            tip.add(embedded.anchor());
            assertTrue(tip.length()<1e-5f,"sprite scale="+profile.visual().spriteScaleX());
        }
    }

    @Test void liveEmbeddedPosePlacesAuthoredUvTipAtContactAndPreservesKnifeInwardOffset() {
        var model=fixture();
        var profile=new com.juicyslew.moonstation14.ms14.loose_objects.LooseObjectProfile(1,.5f,0,10,1,"flat_sprite","item",false,0,10,1,0,.5f,.1f,
                new com.juicyslew.moonstation14.ms14.loose_objects.LooseObjectProfile.VisualTuning(.6f,.8f,.13f,-.17f,1,0));
        var contact=new net.minecraft.world.phys.Vec3(.2,.3,-.1);
        var direction=new net.minecraft.world.phys.Vec3(.3,.4,.8660254);
        direction=direction.normalize();
        for(float[] spec:new float[][]{{45,.875f,.125f,-.15f},{315,.625f,.65625f,0}}) {
            var data=new com.juicyslew.moonstation14.ms14.combat.throwing.ThrownItemProfile.EmbeddingData(1.5f,true,false,spec[3],0,java.util.Optional.empty(),spec[0],java.util.Optional.of(new com.juicyslew.moonstation14.ms14.combat.throwing.ThrownItemProfile.SpriteTipData(spec[1],spec[2])));
            var attachment=new com.juicyslew.moonstation14.entities.ItemAttachment(java.util.UUID.randomUUID(),1,contact,direction,com.juicyslew.moonstation14.entities.ItemAttachment.AttachmentKind.EMBEDDED);
            var embedded=AttachedThrownItemLayer.EmbeddedItemPose.calculate(model,profile,1,attachment,data);
            assertNotNull(embedded);
            var tip=LoosePresentationBounds.flatSpritePointAtUv(model,profile,1,new org.joml.Matrix4f().rotate(embedded.rotation()),spec[1],spec[2]);
            tip.add(embedded.anchor());
            var expected=new org.joml.Vector3f((float)(-direction.x*spec[3]),(float)(-direction.y*spec[3]),(float)(-direction.z*spec[3]));
            assertTrue(tip.distance(expected)<1e-5f,"axis="+spec[0]);
        }
    }

    @Test void authoredNormalizedUvsMapThroughAtlasRangeAndLiveModelPose() {
        var model=fixture();
        var profile=new com.juicyslew.moonstation14.ms14.loose_objects.LooseObjectProfile(1,.5f,0,10,1,"flat_sprite","item",false,0,10,1,0,.5f,.1f,
                new com.juicyslew.moonstation14.ms14.loose_objects.LooseObjectProfile.VisualTuning(.6f,.8f,.13f,-.17f,1,0));
        for(float[] uv:new float[][]{{.875f,.125f},{.625f,.65625f}}) {
            var pose=new org.joml.Matrix4f().rotateY(.7f).rotateX(.3f);
            var actual=LoosePresentationBounds.flatSpritePointAtUv(model,profile,1,pose,uv[0],uv[1]);
            assertNotNull(actual);
            PoseStack expectedPose=new PoseStack(); expectedPose.last().pose().set(pose);
            LooseItemRenderer.applyCameraFacingFlatPose(expectedPose,profile,1);
            model.applyTransform(net.minecraft.world.item.ItemDisplayContext.NONE,expectedPose,false);
            expectedPose.translate(-.5,-.5,-.5);
            var expected=expectedPose.last().pose().transformPosition(new org.joml.Vector3f(uv[0],1-uv[1],.5f));
            assertTrue(actual.distance(expected)<1e-5f,"UV="+uv[0]+","+uv[1]);
        }
        assertNull(LoosePresentationBounds.flatSpritePointAtUv(model,profile,1,new org.joml.Matrix4f(),1.1f,.5f));
    }

    private static BakedModel fixture() {
        var model=mock(BakedModel.class); var quad=mock(BakedQuad.class); var sprite=mock(TextureAtlasSprite.class);
        when(sprite.getU0()).thenReturn(.2f); when(sprite.getU1()).thenReturn(.4f);
        when(sprite.getV0()).thenReturn(.6f); when(sprite.getV1()).thenReturn(.8f);
        when(quad.getSprite()).thenReturn(sprite);
        float[][] p={{0,1,.5f},{0,0,.5f},{1,0,.5f},{1,1,.5f}};
        float[][] t={{.2f,.6f},{.2f,.8f},{.4f,.8f},{.4f,.6f}}; int[] data=new int[32];
        for(int i=0;i<4;i++){int a=i*8;for(int k=0;k<3;k++)data[a+k]=Float.floatToRawIntBits(p[i][k]);data[a+4]=Float.floatToRawIntBits(t[i][0]);data[a+5]=Float.floatToRawIntBits(t[i][1]);}
        when(quad.getVertices()).thenReturn(data);
        when(model.getQuads(isNull(),isNull(),any())).thenReturn(java.util.List.of(quad));
        when(model.getQuads(isNull(),notNull(),any())).thenReturn(java.util.List.of());
        when(model.applyTransform(eq(net.minecraft.world.item.ItemDisplayContext.NONE),any(PoseStack.class),eq(false))).thenReturn(model);
        return model;
    }
}
