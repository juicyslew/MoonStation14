package com.juicyslew.moonstation14.entities.client;

import com.juicyslew.moonstation14.entities.TeslaEnergyBallEntity;
import com.juicyslew.moonstation14.ms14.singularity.client.SingularityBillboardBasis;
import com.juicyslew.moonstation14.ms14.ui.model.animation.SpriteSheet;
import com.juicyslew.moonstation14.ms14.tesla.TeslaVisualResources;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

/** Animated fullbright billboard using the original SS14 Tesla animation sheets. */
public final class TeslaEnergyBallRenderer extends EntityRenderer<TeslaEnergyBallEntity> {
    private static final ResourceLocation MAIN = TeslaVisualResources.MAIN;
    private static final ResourceLocation MINI = TeslaVisualResources.MINI;
    private static final SpriteSheet MAIN_SHEET = TeslaVisualResources.MAIN_SHEET;
    private static final SpriteSheet MINI_SHEET = TeslaVisualResources.MINI_SHEET;

    public TeslaEnergyBallRenderer(EntityRendererProvider.Context context) { super(context); }

    @Override public void render(TeslaEnergyBallEntity entity, float yaw, float partialTick, PoseStack pose,
                                 MultiBufferSource buffers, int light) {
        SpriteSheet sheet = entity.isMini() ? MINI_SHEET : MAIN_SHEET;
        ResourceLocation texture = entity.isMini() ? MINI : MAIN;
        long elapsed = (long) ((entity.level().getGameTime() + partialTick) * 50.0);
        int frame = sheet.frameAt(0, elapsed);
        float minU = (float) sheet.u(0, frame) / sheet.width();
        float minV = (float) sheet.v(0, frame) / sheet.height();
        float maxU = minU + (float) sheet.frameWidth() / sheet.width();
        float maxV = minV + (float) sheet.frameHeight() / sheet.height();
        pose.pushPose();
        try {
            pose.mulPose(SingularityBillboardBasis.between(entity.getPosition(partialTick),
                    entityRenderDispatcher.camera.getPosition()).rotation());
            float halfExtent = (float) entity.visualRadius();
            pose.scale(halfExtent, halfExtent, halfExtent);
            ProjectileBillboard.renderCrossed(pose, buffers, RenderType.entityTranslucent(texture), 15728880,
                    minU, minV, maxU, maxV);
        } finally { pose.popPose(); }
        super.render(entity, yaw, partialTick, pose, buffers, light);
    }

    @Override public ResourceLocation getTextureLocation(TeslaEnergyBallEntity entity) {
        return entity.isMini() ? MINI : MAIN;
    }
}
