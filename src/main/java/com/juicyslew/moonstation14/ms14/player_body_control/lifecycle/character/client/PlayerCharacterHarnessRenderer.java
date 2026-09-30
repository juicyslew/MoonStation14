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
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Pose;
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
    protected void setupRotations(PlayerCharacterHarnessEntity entity, PoseStack poseStack, float bob,
                                  float yBodyRot, float partialTick, float scale) {
        // LivingEntityRenderer only tips when deathTime > 0. That timer is not synced on spawn,
        // and zero health alone does not mean the server accepted die(). Keep the confirmed pose
        // at the vanilla flip angle independently of that transient timer.
        if (entity.deathTime == 0 && !entity.shouldRenderCorpsePose()) {
            super.setupRotations(entity, poseStack, bob, yBodyRot, partialTick, scale);
            return;
        }

        if (isShaking(entity))
            yBodyRot += (float) (Math.cos((double) entity.tickCount * 3.25) * Math.PI * 0.4F);
        if (!entity.hasPose(Pose.SLEEPING))
            poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - yBodyRot));

        if (entity.shouldRenderCorpsePose()) {
            poseStack.mulPose(Axis.ZP.rotationDegrees(getFlipDegrees(entity)));
        } else if (entity.isAutoSpinAttack()) {
            poseStack.mulPose(Axis.XP.rotationDegrees(-90.0F - entity.getXRot()));
            poseStack.mulPose(Axis.YP.rotationDegrees((entity.tickCount + partialTick) * -75.0F));
        } else if (entity.hasPose(Pose.SLEEPING)) {
            Direction direction = entity.getBedOrientation();
            float rotation = direction != null ? switch (direction) {
                case SOUTH -> 90.0F;
                case WEST -> 0.0F;
                case NORTH -> 270.0F;
                case EAST -> 180.0F;
                default -> 0.0F;
            } : yBodyRot;
            poseStack.mulPose(Axis.YP.rotationDegrees(rotation));
            poseStack.mulPose(Axis.ZP.rotationDegrees(getFlipDegrees(entity)));
            poseStack.mulPose(Axis.YP.rotationDegrees(270.0F));
        } else if (isEntityUpsideDown(entity)) {
            poseStack.translate(0.0F, (entity.getBbHeight() + 0.1F) / scale, 0.0F);
            poseStack.mulPose(Axis.ZP.rotationDegrees(180.0F));
        }
    }

    @Override
    protected boolean shouldShowName(PlayerCharacterHarnessEntity entity) {
        return entity.hasOfflineBadge() || super.shouldShowName(entity);
    }

    @Override
    protected void renderNameTag(PlayerCharacterHarnessEntity entity, Component displayName,
                                 PoseStack poseStack, MultiBufferSource buffer, int packedLight, float partialTick) {
        if (super.shouldShowName(entity))
            super.renderNameTag(entity, displayName, poseStack, buffer, packedLight, partialTick);
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
