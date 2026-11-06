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
import com.juicyslew.moonstation14.ms14.equipment.client.BodyAppearanceItemLayer;
import com.juicyslew.moonstation14.ms14.equipment.client.BodyAppearanceClothingLayer;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Pose;
import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.ms14.standing.PostureSystem;
import com.juicyslew.moonstation14.ms14.standing.PronePoseMath;
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
        addLayer(new BodyAppearanceItemLayer(this, context.getItemInHandRenderer()));
        addLayer(new BodyAppearanceClothingLayer(this, context.getItemInHandRenderer()));
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
        var mc = net.minecraft.client.Minecraft.getInstance();
        boolean ownedCameraBody = com.juicyslew.moonstation14.ms14.player_body_control.client.GhostControlClient
                .ownedCharacterForPresentation() == entity && mc.getCameraEntity() == entity;
        if (!com.juicyslew.moonstation14.ms14.equipment.client.BodyWorldRenderPolicy.allowOrdinaryPass(
                ownedCameraBody, mc.getCameraEntity() == entity, mc.options.getCameraType().isFirstPerson())) return;
        PlayerModel<PlayerCharacterHarnessEntity> previousModel = model;
        model = entity.bodyShape() == PlayerCharacterBodyShape.SLIM ? slimModel : wideModel;
        PlayerModel<PlayerCharacterHarnessEntity> active = model;
        boolean[] visible = {active.head.visible, active.hat.visible, active.leftArm.visible, active.rightArm.visible,
                active.leftSleeve.visible, active.rightSleeve.visible};
        float headingOffset = ownedCameraBody
                ? com.juicyslew.moonstation14.ms14.player_body_control.client.GhostControlClient
                        .presentationYawOffset(entity, partialTick) : 0f;
        try {
            com.juicyslew.moonstation14.ms14.player_body_control.client.SupportedBodyCameraClient.withModelYaw(
                    new com.juicyslew.moonstation14.ms14.player_body_control.client.SupportedBodyCameraClient.ModelYawAccess() {
                        public float yaw(){return entity.getYRot();} public void yaw(float value){entity.setYRot(value);}
                        public float oldYaw(){return entity.yRotO;} public void oldYaw(float value){entity.yRotO=value;}
                        public float bodyYaw(){return entity.yBodyRot;} public void bodyYaw(float value){entity.yBodyRot=value;}
                        public float oldBodyYaw(){return entity.yBodyRotO;} public void oldBodyYaw(float value){entity.yBodyRotO=value;}
                        public float headYaw(){return entity.yHeadRot;} public void headYaw(float value){entity.yHeadRot=value;}
                        public float oldHeadYaw(){return entity.yHeadRotO;} public void oldHeadYaw(float value){entity.yHeadRotO=value;}
                    }, headingOffset,
                    () -> super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight));
        } finally {
            active.head.visible = visible[0]; active.hat.visible = visible[1];
            active.leftArm.visible = visible[2]; active.rightArm.visible = visible[3];
            active.leftSleeve.visible = visible[4]; active.rightSleeve.visible = visible[5];
            model = previousModel;
        }
    }

    @Override
    protected void setupRotations(PlayerCharacterHarnessEntity entity, PoseStack poseStack, float bob,
                                  float yBodyRot, float partialTick, float scale) {
        if (PostureSystem.isDownPosture(entity)) {
            var posture = entity.getExistingDataOrNull(ModDataAttachments.POSTURE.get());
            float presentationOffset=posture==null?0f:
                    com.juicyslew.moonstation14.ms14.player_body_control.client.GhostControlClient
                            .presentationYawOffset(entity,partialTick);
            poseStack.mulPose(Axis.YP.rotationDegrees(
                    com.juicyslew.moonstation14.ms14.player_body_control.client.SupportedBodyCameraClient
                            .modelRootRotationDegrees(yBodyRot,posture==null?0f:posture.data().movementFacingYaw(),
                                    posture!=null,presentationOffset)));
            PronePoseMath.applySharedRootPose(poseStack, entity.getBbWidth(), 1.0F,
                    posture != null && posture.data().faceUp());
            return;
        }
        // LivingEntityRenderer only tips when deathTime > 0. That timer is not synced on spawn,
        // and zero health alone does not mean the server accepted die(). Keep the confirmed pose
        // at the vanilla flip angle independently of that transient timer.
        if (entity.deathTime == 0 && !entity.shouldRenderCorpsePose()) {
            applyInertialOrientation(entity, poseStack, partialTick);
            super.setupRotations(entity, poseStack, bob, yBodyRot, partialTick, scale);
            return;
        }

        if (isShaking(entity))
            yBodyRot += (float) (Math.cos((double) entity.tickCount * 3.25) * Math.PI * 0.4F);
        if (!entity.hasPose(Pose.SLEEPING))
            poseStack.mulPose(Axis.YP.rotationDegrees(
                    com.juicyslew.moonstation14.ms14.player_body_control.client.SupportedBodyCameraClient
                            .modelRootRotationDegrees(yBodyRot)));

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

    private void applyInertialOrientation(PlayerCharacterHarnessEntity entity, PoseStack poseStack, float partialTick) {
        poseStack.mulPose(com.juicyslew.moonstation14.ms14.player_body_control.client.AngularRecoveryPresentation
                .orientation(entity, partialTick));
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
