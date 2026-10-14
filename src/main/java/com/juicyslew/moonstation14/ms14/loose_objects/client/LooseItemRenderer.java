package com.juicyslew.moonstation14.ms14.loose_objects.client;

import com.juicyslew.moonstation14.component.ModDataComponents;
import com.juicyslew.moonstation14.entities.LooseItemEntity;
import com.juicyslew.moonstation14.item.ModItems;
import com.juicyslew.moonstation14.ms14.loose_objects.LooseObjectProfile;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;

/** Stable, floor-pivoted world geometry for persistent loose objects. */
public final class LooseItemRenderer extends EntityRenderer<LooseItemEntity> {
    public LooseItemRenderer(EntityRendererProvider.Context context) { super(context); }

    /** Applies the production NONE-context flat-sprite model pose. */
    public static void applyFlatPose(PoseStack pose, LooseObjectProfile profile) {
        float sx = profile.visual().spriteScaleX(), sy = profile.visual().spriteScaleY();
        pose.translate(0, .002 + .03125 * Math.max(sx, sy), 0);
        pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(90));
        pose.translate(profile.visual().spriteOffsetX(), profile.visual().spriteOffsetY(), 0);
        pose.scale(sx, sy, Math.max(sx, sy));
    }

    @Override
    public void render(LooseItemEntity entity, float entityYaw, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int light) {
        var stack = entity.getItem();
        if (stack.isEmpty()) return;
        final LooseObjectProfile profile;
        try {
            profile = LooseObjectProfile.resolveForStack(stack, true);
        } catch (IllegalArgumentException missingClientProfile) {
            // Entity data can arrive before the client prototype catalog. Wait for
            // it rather than failing the render tick or substituting a guessed profile.
            return;
        }
        if ("jug".equals(profile.renderKind()) && stack.is(ModItems.JUG.get())) {
            var reagent = stack.get(com.juicyslew.moonstation14.component.ModDataComponents.REAGENT.get());
            var minecraft = net.minecraft.client.Minecraft.getInstance();
            int fill = com.juicyslew.moonstation14.ms14.loose_objects.appearance.JugItemAppearance.fillTier(reagent,
                    com.juicyslew.moonstation14.ms14.loose_objects.appearance.JugItemAppearance.capacityCents(minecraft.level));
            pose.pushPose();
            try {
                pose.mulPose(com.mojang.math.Axis.YP.rotation(entity.getRotationAngle(partialTick)));
                pose.translate(-.5, 0, -.5);
                BakedModel model = minecraft.getModelManager().getModel(
                        net.minecraft.client.resources.model.ModelResourceLocation.standalone(
                                ResourceLocation.fromNamespaceAndPath(
                                        com.juicyslew.moonstation14.MoonStation14.MOD_ID,
                                        "block/jug_" + fill)));
                int color = com.juicyslew.moonstation14.ms14.loose_objects.appearance.JugItemAppearance.color(reagent, minecraft.level);
                float red = ((color >> 16) & 255) / 255f;
                float green = ((color >> 8) & 255) / 255f;
                float blue = (color & 255) / 255f;
                var consumer = buffers.getBuffer(RenderType.cutout());
                minecraft.getBlockRenderer().getModelRenderer().renderModel(pose.last(), consumer,
                        null, model, red, green, blue, light, OverlayTexture.NO_OVERLAY);
            } finally { pose.popPose(); }
        } else {
            pose.pushPose();
            try {
                // Render-only local depth separation; never alters the entity's native pose/physics.
                pose.translate(0, LooseItemRenderLayers.offset(entity), 0);
                pose.mulPose(com.mojang.math.Axis.YP.rotation(entity.getRotationAngle(partialTick)));
                if ("flat_sprite".equals(profile.renderKind())) {
                    // Generated item sprites are vertical quads in NONE context. Lay that plane
                    // onto XZ and size it from profile world-fit values, not the tiny GROUND transform.
                    applyFlatPose(pose, profile);
                    net.minecraft.client.Minecraft.getInstance().getItemRenderer().renderStatic(stack,
                            net.minecraft.world.item.ItemDisplayContext.NONE, light,
                            net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, pose, buffers,
                            entity.level(), entity.getId());
                } else if ("block_model".equals(profile.renderKind())) {
                    pose.translate(0, profile.modelFloorOffset(), 0);
                    pose.scale(profile.modelScale(), profile.modelScale(), profile.modelScale());
                    net.minecraft.client.Minecraft.getInstance().getItemRenderer().renderStatic(stack,
                            net.minecraft.world.item.ItemDisplayContext.NONE, light,
                            net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, pose, buffers,
                            entity.level(), entity.getId());
                } else {
                pose.translate(profile.visual().spriteOffsetX(), profile.modelFloorOffset(),
                        profile.visual().spriteOffsetY());
                pose.scale(profile.modelScale(), profile.modelScale(), profile.modelScale());
                net.minecraft.client.Minecraft.getInstance().getItemRenderer().renderStatic(stack,
                        "grenade".equals(profile.renderKind()) ? net.minecraft.world.item.ItemDisplayContext.NONE
                                : net.minecraft.world.item.ItemDisplayContext.FIXED, light,
                        net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, pose, buffers,
                        entity.level(), entity.getId());
                }
            } finally { pose.popPose(); }
        }
        super.render(entity, entityYaw, partialTick, pose, buffers, light);
    }

    @Override public ResourceLocation getTextureLocation(LooseItemEntity entity) {
        return net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS;
    }
}

