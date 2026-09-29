package com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.client;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterAppearance;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterBodyShape;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessEntity;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessRegistration;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** Vanilla wide player presentation for the custom persistent character Mob. */
@EventBusSubscriber(modid = MoonStation14.MOD_ID, value = Dist.CLIENT)
public final class PlayerCharacterHarnessRenderer
        extends MobRenderer<PlayerCharacterHarnessEntity, PlayerModel<PlayerCharacterHarnessEntity>> {
    private static final ResourceLocation STEVE = ResourceLocation.withDefaultNamespace(
            "textures/entity/player/wide/steve.png");
    private static final ResourceLocation ALEX = ResourceLocation.withDefaultNamespace(
            "textures/entity/player/wide/alex.png");
    private static final ResourceLocation STEVE_SLIM = ResourceLocation.withDefaultNamespace(
            "textures/entity/player/slim/steve.png");
    private static final ResourceLocation ALEX_SLIM = ResourceLocation.withDefaultNamespace(
            "textures/entity/player/slim/alex.png");
    private final PlayerModel<PlayerCharacterHarnessEntity> wideModel;
    private final PlayerModel<PlayerCharacterHarnessEntity> slimModel;

    private PlayerCharacterHarnessRenderer(EntityRendererProvider.Context context) {
        super(context, new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER), false), 0.5f);
        wideModel = model;
        slimModel = new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER_SLIM), true);
    }

    @Override
    public ResourceLocation getTextureLocation(PlayerCharacterHarnessEntity entity) {
        boolean alex = entity.appearance() == PlayerCharacterAppearance.ALEX;
        if (entity.bodyShape() == PlayerCharacterBodyShape.SLIM) return alex ? ALEX_SLIM : STEVE_SLIM;
        return alex ? ALEX : STEVE;
    }

    @Override
    public void render(PlayerCharacterHarnessEntity entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        PlayerModel<PlayerCharacterHarnessEntity> previousModel = model;
        model = entity.bodyShape() == PlayerCharacterBodyShape.SLIM ? slimModel : wideModel;
        try {
            super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
        } finally {
            model = previousModel;
        }
    }

    @Override
    protected boolean shouldShowName(PlayerCharacterHarnessEntity entity) {
        // Only the status badge may use the vanilla name-tag rendering path.
        return entity.hasOfflineBadge();
    }

    @Override
    protected void renderNameTag(PlayerCharacterHarnessEntity entity, Component displayName,
                                 PoseStack poseStack, MultiBufferSource buffer, int packedLight, float partialTick) {
        if (entity.hasOfflineBadge()) {
            poseStack.pushPose();
            poseStack.translate(0.0D, 0.25D, 0.0D);
            super.renderNameTag(entity, Component.literal("Offline"), poseStack, buffer, packedLight, partialTick);
            poseStack.popPose();
        }
    }

    @SubscribeEvent
    public static void register(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(PlayerCharacterHarnessRegistration.getEntityType(),
                PlayerCharacterHarnessRenderer::new);
    }
}
