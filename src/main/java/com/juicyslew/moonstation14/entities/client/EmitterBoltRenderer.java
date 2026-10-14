package com.juicyslew.moonstation14.entities.client;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.entities.EmitterBoltEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

/** Crossed textured planes retain the SS14 bolt sprite's recognizable silhouette. */
public final class EmitterBoltRenderer extends EntityRenderer<EmitterBoltEntity> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(MoonStation14.MOD_ID,
            "textures/entity/emitter_bolt.png");
    public EmitterBoltRenderer(EntityRendererProvider.Context context) { super(context); }
    @Override public void render(EmitterBoltEntity entity, float yaw, float partialTick, PoseStack pose,
                                 MultiBufferSource buffers, int light) {
        pose.pushPose();
        try {
            pose.mulPose(ProjectileBillboard.flightRotation(entity.getDeltaMovement()));
            float scale = com.juicyslew.moonstation14.entities.EmitterBoltImpactPolicy.spriteScale(32);
            pose.scale(scale,scale,scale);
            // SS14 emitter projectiles are unshaded sprites, not a muzzle-flash/beam effect.
            ProjectileBillboard.renderCrossed(pose,buffers,RenderType.entityCutoutNoCull(TEXTURE),15728880);
        } finally { pose.popPose(); }
        super.render(entity,yaw,partialTick,pose,buffers,light);
    }
    @Override public ResourceLocation getTextureLocation(EmitterBoltEntity entity) { return TEXTURE; }
}
