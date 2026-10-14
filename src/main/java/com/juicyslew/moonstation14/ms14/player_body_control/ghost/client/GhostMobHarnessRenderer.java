package com.juicyslew.moonstation14.ms14.player_body_control.ghost.client;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.ms14.player_body_control.ghost.GhostMobHarnessEntity;
import com.juicyslew.moonstation14.ms14.player_body_control.ghost.GhostMobHarnessRegistration;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** SS14-inspired translucent sprite presentation for the ghost movement harness. */
@EventBusSubscriber(modid = MoonStation14.MOD_ID, value = Dist.CLIENT)
public final class GhostMobHarnessRenderer extends EntityRenderer<GhostMobHarnessEntity> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(MoonStation14.MOD_ID,
            "textures/entity/ghost/animated.png");

    private GhostMobHarnessRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(GhostMobHarnessEntity entity) {
        return TEXTURE;
    }

    @Override
    public void render(GhostMobHarnessEntity entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getCameraEntity() == entity
                && minecraft.options.getCameraType() == CameraType.FIRST_PERSON) return;
        poseStack.pushPose();
        poseStack.translate(0, entity.getEyeHeight(), 0);
        poseStack.mulPose(entityRenderDispatcher.cameraOrientation());
        poseStack.scale(GhostSpriteGeometry.SIZE, GhostSpriteGeometry.SIZE, GhostSpriteGeometry.SIZE);
        VertexConsumer vertices = buffer.getBuffer(RenderType.entityTranslucent(TEXTURE));
        var matrix = poseStack.last().pose();
        // RSI animated is four directional 32px cells, one frame per direction.
        // The 2x2 sheet has no delays, so each authored direction is static.
        float relativeYaw = Mth.wrapDegrees(entityYaw - minecraft.gameRenderer.getMainCamera().getYRot());
        int direction = GhostSpriteDirection.fromRelativeYaw(relativeYaw);
        float u = (direction & 1) * 0.5F, v = (direction >> 1) * 0.5F;
        int light = 0xF000F0;
        float half = GhostSpriteGeometry.HALF_SIZE;
        vertices.addVertex(matrix, -half, -half, 0).setColor(255, 255, 255, 136).setUv(u, v + .5F).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(0, 0, 1);
        vertices.addVertex(matrix, half, -half, 0).setColor(255, 255, 255, 136).setUv(u + .5F, v + .5F).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(0, 0, 1);
        vertices.addVertex(matrix, half, half, 0).setColor(255, 255, 255, 136).setUv(u + .5F, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(0, 0, 1);
        vertices.addVertex(matrix, -half, half, 0).setColor(255, 255, 255, 136).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(0, 0, 1);
        poseStack.popPose();
    }

    @Override
    protected boolean shouldShowName(GhostMobHarnessEntity entity) {
        return false;
    }

    @Override
    protected void renderNameTag(GhostMobHarnessEntity entity, Component displayName,
                                 PoseStack poseStack, MultiBufferSource buffer, int packedLight, float partialTick) {
        // Ghost identity is revealed through presentation, never an overhead tag.
    }

    @SubscribeEvent
    public static void register(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(GhostMobHarnessRegistration.getEntityType(), GhostMobHarnessRenderer::new);
    }
}

