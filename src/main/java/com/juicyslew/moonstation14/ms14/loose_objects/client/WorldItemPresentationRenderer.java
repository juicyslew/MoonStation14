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
    private static final Runnable NOOP_BEFORE_DRAW = () -> {};
    private WorldItemPresentationRenderer() {}

    public static void render(ItemStack stack, LooseObjectProfile profile, PoseStack pose,
                              MultiBufferSource buffers, int light, Level level, int seed) {
        render(stack, profile, pose, buffers, light, level, seed, 1f);
    }

    /** Alpha applies to rendered block-atlas vertices only; it never mutates the source stack. */
    public static void render(ItemStack stack, LooseObjectProfile profile, PoseStack pose,
                              MultiBufferSource buffers, int light, Level level, int seed, float alpha) {
        render(stack, profile, pose, buffers, light, level, seed, alpha, 0xFFFFFF);
    }

    public static int blendRed(int baseRgb, float strength) {
        float t = Math.max(0f, Math.min(1f, strength));
        int r = Math.round(((baseRgb >> 16) & 255) * (1-t) + 255*t);
        int g = Math.round(((baseRgb >> 8) & 255) * (1-t));
        int b = Math.round((baseRgb & 255) * (1-t));
        return (r << 16) | (g << 8) | b;
    }

    public static void render(ItemStack stack, LooseObjectProfile profile, PoseStack pose,
                               MultiBufferSource buffers, int light, Level level, int seed, float alpha, int tintRgb) {
        render(stack, profile, pose, buffers, light, level, seed, alpha, tintRgb, NOOP_BEFORE_DRAW);
    }

    /** Invokes the observer immediately before the shared pipeline submits its model draw. */
    public static void render(ItemStack stack, LooseObjectProfile profile, PoseStack pose,
                              MultiBufferSource buffers, int light, Level level, int seed, float alpha, int tintRgb,
                              Runnable beforeDraw) {
        render(stack, profile, pose, buffers, light, level, seed, alpha, tintRgb, false, null, beforeDraw, RenderType.cutout());
    }

    /** Narrow material override for the loose-entity jug, whose historical sheet is the block cutout sheet. */
    public static void render(ItemStack stack, LooseObjectProfile profile, PoseStack pose,
                              MultiBufferSource buffers, int light, Level level, int seed, float alpha, int tintRgb,
                              Runnable beforeDraw, RenderType jugRenderType) {
        render(stack, profile, pose, buffers, light, level, seed, alpha, tintRgb, false, null, beforeDraw, jugRenderType);
    }

    /** Render in a caller-provided camera-facing plane (used only for copied first-person melee weapons). */
    public static void renderCameraFacing(ItemStack stack, LooseObjectProfile profile, PoseStack pose,
                                           MultiBufferSource buffers, int light, Level level, int seed, float alpha) {
        render(stack, profile, pose, buffers, light, level, seed, alpha, 0xFFFFFF, true, null, NOOP_BEFORE_DRAW, RenderType.cutout());
    }
    public static void renderCameraFacing(ItemStack stack, LooseObjectProfile profile, PoseStack pose,
            MultiBufferSource buffers, int light, Level level, int seed, float alpha, int tintRgb) {
        renderCameraFacing(stack, profile, pose, buffers, light, level, seed, alpha, tintRgb, NOOP_BEFORE_DRAW);
    }
    public static void renderCameraFacing(ItemStack stack, LooseObjectProfile profile, PoseStack pose,
            MultiBufferSource buffers, int light, Level level, int seed, float alpha, int tintRgb,
            Runnable beforeDraw) {
        render(stack, profile, pose, buffers, light, level, seed, alpha, tintRgb, true, null, beforeDraw, RenderType.cutout());
    }
    public static void renderCameraFacing(ItemStack stack, LooseObjectProfile profile, PoseStack pose,
            MultiBufferSource buffers, int light, Level level, int seed, float alpha, int tintRgb,
            Runnable beforeDraw, RenderType jugRenderType) {
        render(stack, profile, pose, buffers, light, level, seed, alpha, tintRgb, true, null, beforeDraw, jugRenderType);
    }

    /** Same camera-facing item pipeline, returning the number of submitted vertices. */
    public static int renderCameraFacingCounted(ItemStack stack, LooseObjectProfile profile, PoseStack pose,
                                                 MultiBufferSource buffers, int light, Level level, int seed, float alpha) {
        int[] count={0};
        render(stack, profile, pose, buffers, light, level, seed, alpha, 0xFFFFFF, true, count, NOOP_BEFORE_DRAW, RenderType.cutout());
        return count[0];
    }

    private static void render(ItemStack stack, LooseObjectProfile profile, PoseStack pose,
                               MultiBufferSource buffers, int light, Level level, int seed, float alpha, int tintRgb, boolean cameraFacing) {
        render(stack,profile,pose,buffers,light,level,seed,alpha,tintRgb,cameraFacing,null,NOOP_BEFORE_DRAW,RenderType.cutout());
    }

    private static void render(ItemStack stack, LooseObjectProfile profile, PoseStack pose,
                               MultiBufferSource buffers, int light, Level level, int seed, float alpha, int tintRgb,
                               boolean cameraFacing, Runnable beforeDraw) {
        render(stack, profile, pose, buffers, light, level, seed, alpha, tintRgb, cameraFacing, null, beforeDraw, RenderType.cutout());
    }

    private static void render(ItemStack stack, LooseObjectProfile profile, PoseStack pose,
                               MultiBufferSource buffers, int light, Level level, int seed, float alpha, int tintRgb,
                               boolean cameraFacing, int[] count, Runnable beforeDraw, RenderType jugRenderType) {
        if (stack.isEmpty()) return;
        int vertexAlpha = alphaByte(alpha);
        if (vertexAlpha == 0) return;
        MultiBufferSource presentationBuffers = presentationBuffers(buffers, vertexAlpha, tintRgb, count);
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
            beforeDraw.run();
            minecraft.getBlockRenderer().getModelRenderer().renderModel(pose.last(),
                     jugVertexConsumer(buffers, vertexAlpha, tintRgb, count, jugRenderType),
                    null, model, red, green, blue, light, OverlayTexture.NO_OVERLAY);
            return;
        }
        renderNonJug(stack, profile, pose, presentationBuffers, light, level, seed, cameraFacing, beforeDraw);
    }

    private static void renderNonJug(ItemStack stack, LooseObjectProfile profile, PoseStack pose,
                                     MultiBufferSource presentationBuffers, int light, Level level, int seed,
                                     boolean cameraFacing, Runnable beforeDraw) {
        float appearanceScale = ProduceWorldAppearance.scale(stack);
        if ("flat_sprite".equals(profile.renderKind())) {
            if (cameraFacing) LooseItemRenderer.applyCameraFacingFlatPose(pose, profile, appearanceScale);
            else LooseItemRenderer.applyFlatPose(pose, profile, appearanceScale);
            beforeDraw.run();
            Minecraft.getInstance().getItemRenderer().renderStatic(stack, ItemDisplayContext.NONE, light,
                    OverlayTexture.NO_OVERLAY, pose, presentationBuffers, level, seed);
        } else if ("block_model".equals(profile.renderKind())) {
            pose.translate(0, profile.modelFloorOffset(), 0);
            pose.scale(profile.modelScale() * appearanceScale, profile.modelScale() * appearanceScale,
                    profile.modelScale() * appearanceScale);
            beforeDraw.run();
            Minecraft.getInstance().getItemRenderer().renderStatic(stack, ItemDisplayContext.NONE, light,
                    OverlayTexture.NO_OVERLAY, pose, presentationBuffers, level, seed);
        } else {
            pose.translate(profile.visual().spriteOffsetX(), profile.modelFloorOffset(), profile.visual().spriteOffsetY());
            pose.scale(profile.modelScale() * appearanceScale, profile.modelScale() * appearanceScale,
                    profile.modelScale() * appearanceScale);
            beforeDraw.run();
            Minecraft.getInstance().getItemRenderer().renderStatic(stack,
                    "grenade".equals(profile.renderKind()) ? ItemDisplayContext.NONE : ItemDisplayContext.FIXED,
                    light, OverlayTexture.NO_OVERLAY, pose, presentationBuffers, level, seed);
        }
    }

    static MultiBufferSource presentationBuffers(MultiBufferSource buffers, int vertexAlpha, int tintRgb, int[] count) {
        if (vertexAlpha == 255 && tintRgb == 0xFFFFFF && count == null) return buffers;
        return new MultiBufferSource() {
            @Override public VertexConsumer getBuffer(RenderType type) {
                return new AlphaVertexConsumer(buffers.getBuffer(vertexAlpha == 255 && tintRgb == 0xFFFFFF
                        ? type : RenderType.entityTranslucent(TextureAtlas.LOCATION_BLOCKS)), vertexAlpha, tintRgb, count);
            }
        };
    }

    static VertexConsumer jugVertexConsumer(MultiBufferSource buffers, int alpha, int tint, int[] count) {
        return jugVertexConsumer(buffers, alpha, tint, count, RenderType.cutout());
    }
    static VertexConsumer jugVertexConsumer(MultiBufferSource buffers, int alpha, int tint, int[] count, RenderType jugRenderType) {
        return presentationBuffers(buffers, alpha, tint, count).getBuffer(alpha == 255 && tint == 0xFFFFFF
                ? jugRenderType : RenderType.entityTranslucent(TextureAtlas.LOCATION_BLOCKS));
    }

    public static int alphaByte(float alpha) {
        if (Float.isNaN(alpha)) return 0;
        return Math.round(Math.max(0f, Math.min(1f, alpha)) * 255f);
    }

    private static final class AlphaVertexConsumer implements VertexConsumer {
        private final VertexConsumer delegate;
        private final int alpha;
        private final int tint;
        private final int[] count;
        private AlphaVertexConsumer(VertexConsumer delegate, int alpha, int tint, int[] count) { this.delegate = delegate; this.alpha = alpha; this.tint = tint; this.count=count; }
        @Override public VertexConsumer addVertex(float x, float y, float z) { delegate.addVertex(x, y, z); if(count!=null) count[0]++; return this; }
        @Override public VertexConsumer setColor(int red, int green, int blue, int sourceAlpha) {
            delegate.setColor((red * (tint >> 16 & 255) + 127) / 255, (green * (tint >> 8 & 255) + 127) / 255,
                    (blue * (tint & 255) + 127) / 255, (sourceAlpha * alpha + 127) / 255); return this;
        }
        @Override public VertexConsumer setUv(float u, float v) { delegate.setUv(u, v); return this; }
        @Override public VertexConsumer setUv1(int u, int v) { delegate.setUv1(u, v); return this; }
        @Override public VertexConsumer setUv2(int u, int v) { delegate.setUv2(u, v); return this; }
        @Override public VertexConsumer setNormal(float x, float y, float z) { delegate.setNormal(x, y, z); return this; }
    }
}
