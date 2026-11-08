package com.juicyslew.moonstation14.ms14.loose_objects.client;

import com.juicyslew.moonstation14.item.ModItems;
import com.juicyslew.moonstation14.ms14.loose_objects.LooseObjectProfile;
import com.juicyslew.moonstation14.ms14.botany.ProduceWorldAppearance;
import com.juicyslew.moonstation14.ms14.loose_objects.appearance.JugItemAppearance;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.OverlayTexture;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Shared item-specific world presentation. Callers own entity/weapon placement and rotation. */
public final class WorldItemPresentationRenderer {
    private WorldItemPresentationRenderer() {}

    public static void render(ItemStack stack, LooseObjectProfile profile, PoseStack pose,
                              MultiBufferSource buffers, int light, Level level, int seed) {
        render(stack, profile, pose, buffers, light, level, seed, 1f);
    }

    /** Alpha applies to rendered block-atlas vertices only; it never mutates the source stack. */
    public static void render(ItemStack stack, LooseObjectProfile profile, PoseStack pose,
                              MultiBufferSource buffers, int light, Level level, int seed, float alpha) {
        if (stack.isEmpty()) return;
        int vertexAlpha = alphaByte(alpha);
        if (vertexAlpha == 0) return;
        MultiBufferSource presentationBuffers = vertexAlpha == 255 ? buffers : new MultiBufferSource() {
            @Override public VertexConsumer getBuffer(RenderType type) {
                return new AlphaVertexConsumer(buffers.getBuffer(RenderType.entityTranslucent(TextureAtlas.LOCATION_BLOCKS)), vertexAlpha);
            }
        };
        if ("jug".equals(profile.renderKind()) && stack.is(ModItems.JUG.get())) {
            var reagent = stack.get(com.juicyslew.moonstation14.component.ModDataComponents.REAGENT.get());
            var minecraft = Minecraft.getInstance();
            int fill = JugItemAppearance.fillTier(reagent, JugItemAppearance.capacityCents(level));
            BakedModel model = minecraft.getModelManager().getModel(
                    net.minecraft.client.resources.model.ModelResourceLocation.standalone(
                            ResourceLocation.fromNamespaceAndPath(com.juicyslew.moonstation14.MoonStation14.MOD_ID,
                                    "block/jug_" + fill)));
            int color = JugItemAppearance.color(reagent, level);
            float red = ((color >> 16) & 255) / 255f;
            float green = ((color >> 8) & 255) / 255f;
            float blue = (color & 255) / 255f;
            minecraft.getBlockRenderer().getModelRenderer().renderModel(pose.last(), vertexAlpha == 255
                            ? buffers.getBuffer(RenderType.cutout())
                            : presentationBuffers.getBuffer(RenderType.entityTranslucent(TextureAtlas.LOCATION_BLOCKS)),
                    null, model, red, green, blue, light, OverlayTexture.NO_OVERLAY);
            return;
        }

        float appearanceScale = ProduceWorldAppearance.scale(stack);
        if ("flat_sprite".equals(profile.renderKind())) {
            LooseItemRenderer.applyFlatPose(pose, profile, appearanceScale);
            Minecraft.getInstance().getItemRenderer().renderStatic(stack, ItemDisplayContext.NONE, light,
                    OverlayTexture.NO_OVERLAY, pose, presentationBuffers, level, seed);
        } else if ("block_model".equals(profile.renderKind())) {
            pose.translate(0, profile.modelFloorOffset(), 0);
            pose.scale(profile.modelScale() * appearanceScale, profile.modelScale() * appearanceScale,
                    profile.modelScale() * appearanceScale);
            Minecraft.getInstance().getItemRenderer().renderStatic(stack, ItemDisplayContext.NONE, light,
                    OverlayTexture.NO_OVERLAY, pose, presentationBuffers, level, seed);
        } else {
            pose.translate(profile.visual().spriteOffsetX(), profile.modelFloorOffset(), profile.visual().spriteOffsetY());
            pose.scale(profile.modelScale() * appearanceScale, profile.modelScale() * appearanceScale,
                    profile.modelScale() * appearanceScale);
            Minecraft.getInstance().getItemRenderer().renderStatic(stack,
                    "grenade".equals(profile.renderKind()) ? ItemDisplayContext.NONE : ItemDisplayContext.FIXED,
                    light, OverlayTexture.NO_OVERLAY, pose, presentationBuffers, level, seed);
        }
    }

    public static int alphaByte(float alpha) {
        if (Float.isNaN(alpha)) return 0;
        return Math.round(Math.max(0f, Math.min(1f, alpha)) * 255f);
    }

    private static final class AlphaVertexConsumer implements VertexConsumer {
        private final VertexConsumer delegate;
        private final int alpha;
        private AlphaVertexConsumer(VertexConsumer delegate, int alpha) { this.delegate = delegate; this.alpha = alpha; }
        @Override public VertexConsumer addVertex(float x, float y, float z) { delegate.addVertex(x, y, z); return this; }
        @Override public VertexConsumer setColor(int red, int green, int blue, int sourceAlpha) {
            delegate.setColor(red, green, blue, (sourceAlpha * alpha + 127) / 255); return this;
        }
        @Override public VertexConsumer setUv(float u, float v) { delegate.setUv(u, v); return this; }
        @Override public VertexConsumer setUv1(int u, int v) { delegate.setUv1(u, v); return this; }
        @Override public VertexConsumer setUv2(int u, int v) { delegate.setUv2(u, v); return this; }
        @Override public VertexConsumer setNormal(float x, float y, float z) { delegate.setNormal(x, y, z); return this; }
    }
}
