package com.juicyslew.moonstation14.ms14.loose_objects.client;

import com.juicyslew.moonstation14.entities.LooseItemEntity;
import com.juicyslew.moonstation14.item.ModItems;
import com.juicyslew.moonstation14.ms14.loose_objects.LooseObjectProfile;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;

/** Stable, floor-pivoted world geometry for persistent loose objects. */
public final class LooseItemRenderer extends EntityRenderer<LooseItemEntity> {
    public LooseItemRenderer(EntityRendererProvider.Context context) { super(context); }

    /** Applies the production NONE-context flat-sprite model pose. */
    public static void applyFlatPose(PoseStack pose, LooseObjectProfile profile) {
        applyFlatPose(pose,profile,1f);
    }
    public static void applyFlatPose(PoseStack pose, LooseObjectProfile profile,float appearanceScale) {
        applyFlatPose(pose, profile, appearanceScale, false);
    }
    /** Flat sprite pose for a camera-facing caller; offsets/scales stay in the caller's XY plane. */
    public static void applyCameraFacingFlatPose(PoseStack pose, LooseObjectProfile profile, float appearanceScale) {
        applyFlatPose(pose, profile, appearanceScale, true);
    }
    private static void applyFlatPose(PoseStack pose, LooseObjectProfile profile, float appearanceScale, boolean cameraFacing) {
        float sx = profile.visual().spriteScaleX()*appearanceScale, sy = profile.visual().spriteScaleY()*appearanceScale;
        if (!cameraFacing) {
            pose.translate(0, .002 + .03125 * Math.max(sx, sy), 0);
            pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(90));
        }
        pose.translate(profile.visual().spriteOffsetX(), profile.visual().spriteOffsetY(), 0);
        pose.scale(sx, sy, Math.max(sx, sy));
    }
    static void applyFlightOrientation(PoseStack pose, org.joml.Quaternionf rotation, float entityHeight) {
        pose.translate(0, entityHeight * .5f, 0);
        pose.mulPose(rotation);
    }

    @Override
    public void render(LooseItemEntity entity, float entityYaw, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int light) {
        var stack = entity.getItem();
        if (stack.isEmpty()) return;
        // Attached entities are drawn only by their target's body render layer.
        if (entity.attachment().isPresent()) return;
        final LooseObjectProfile profile;
        try {
            profile = LooseObjectProfile.resolveForStack(stack, true);
        } catch (IllegalArgumentException missingClientProfile) {
            // Entity data can arrive before the client prototype catalog. Wait for
            // it rather than failing the render tick or substituting a guessed profile.
            return;
        }
        var thrown = com.juicyslew.moonstation14.ms14.combat.throwing.ThrownItemProfile.resolveForStack(stack, true).orElse(null);
        var embedding = thrown == null ? null : thrown.embedding().orElse(null);
        boolean align = entity.thrownFlightPresentation() && thrown != null && thrown.alignToVelocity()
                && entity.attachment().isEmpty() && embedding != null;
        var aim = align ? ThrownSpritePose.rotation(embedding.spriteForwardDegrees(),
                entity.getDeltaMovement().toVector3f()) : null;
        pose.pushPose();
        try {
            // Render-only local depth separation; never alters the entity's native pose/physics.
            pose.translate(0, LooseItemRenderLayers.offset(entity), 0);
            if (aim != null) {
                applyFlightOrientation(pose, aim, entity.getBbHeight());
            } else pose.mulPose(com.mojang.math.Axis.YP.rotation(entity.getRotationAngle(partialTick)));
            if ("jug".equals(profile.renderKind())) pose.translate(-.5, 0, -.5);
            float flash = com.juicyslew.moonstation14.ms14.damage.client.DamageFeedbackClientState.redFlashProgress(entity);
            int tint = WorldItemPresentationRenderer.blendRed(0xFFFFFF, flash);
            if (aim != null) WorldItemPresentationRenderer.renderCameraFacing(stack, profile, pose, buffers,
                    light, entity.level(), entity.getId(), 1f, tint);
            else WorldItemPresentationRenderer.render(stack, profile, pose, buffers, light, entity.level(), entity.getId(), 1f, tint);
        } finally { pose.popPose(); }
        super.render(entity, entityYaw, partialTick, pose, buffers, light);
    }

    @Override public ResourceLocation getTextureLocation(LooseItemEntity entity) {
        return net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS;
    }
}
