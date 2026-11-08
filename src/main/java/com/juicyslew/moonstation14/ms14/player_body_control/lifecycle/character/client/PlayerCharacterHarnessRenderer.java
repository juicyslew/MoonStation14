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
    private boolean ownedGunArmsPass;
    public static boolean isOwnedGunArmsPass() {
        var camera = net.minecraft.client.Minecraft.getInstance().getCameraEntity();
        if (camera == null) return false;
        if (!(camera instanceof PlayerCharacterHarnessEntity harnessCamera)) return false;
        var renderer = net.minecraft.client.Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(harnessCamera);
        return renderer instanceof PlayerCharacterHarnessRenderer harness && harness.ownedGunArmsPass;
    }

    private PlayerCharacterHarnessRenderer(EntityRendererProvider.Context context) {
        super(context, new ArticulatedHarnessModel<>(context.bakeLayer(ModelLayers.PLAYER), false), 0.5f);
        wideModel = model;
        slimModel = new ArticulatedHarnessModel<>(context.bakeLayer(ModelLayers.PLAYER_SLIM), true);
        addLayer(new BodyAppearanceItemLayer(this, context.getItemInHandRenderer(),
                context.bakeLayer(com.juicyslew.moonstation14.ms14.mob.mouse.client.MouseModel.LAYER_LOCATION), context));
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
        if (!ownedGunArmsPass && !com.juicyslew.moonstation14.ms14.equipment.client.BodyWorldRenderPolicy.allowOrdinaryPass(
                ownedCameraBody, mc.getCameraEntity() == entity, mc.options.getCameraType().isFirstPerson())) return;
        PlayerModel<PlayerCharacterHarnessEntity> previousModel = model;
        model = entity.bodyShape() == PlayerCharacterBodyShape.SLIM ? slimModel : wideModel;
        PlayerModel<PlayerCharacterHarnessEntity> active = model;
        boolean[] visible = captureVisibility(active);
        try {
            if (ownedGunArmsPass) setItemsOnlyVisibility(active);
            super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
        } finally {
            restoreVisibility(active, visible);
            model = previousModel;
        }
    }

    /** Runs the native renderer for held items only, preserving native animation and muzzle capture. */
    public void renderOwnedGunArms(PlayerCharacterHarnessEntity entity, PoseStack poseStack,
                                   MultiBufferSource buffer, int packedLight, float partialTick) {
        if (ownedGunArmsPass) return;
        ownedGunArmsPass = true;
        try { render(entity, entity.getYRot(), partialTick, poseStack, buffer, packedLight); }
        finally { ownedGunArmsPass = false; }
    }

    @SubscribeEvent
    public static void renderOwnedGunArms(net.neoforged.neoforge.client.event.RenderLevelStageEvent event) {
        if (event.getStage() != net.neoforged.neoforge.client.event.RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;
        var mc = net.minecraft.client.Minecraft.getInstance();
        var body = com.juicyslew.moonstation14.ms14.player_body_control.client.GhostControlClient.ownedCharacterForPresentation();
        if (!(body instanceof PlayerCharacterHarnessEntity harnessBody) || mc.getCameraEntity() != harnessBody) return;
        var snapshot = com.juicyslew.moonstation14.ms14.hands.network.BodyHandNetworking.clientCache()
                .get(harnessBody.level().dimension().location(), harnessBody.getUUID(), harnessBody.getId()).orElse(null);
        if (snapshot == null || snapshot.poseContext() == null) return;
        var active = snapshot.hands().stream().filter(h -> h.id().equalsIgnoreCase(snapshot.poseContext().activeHand()))
                .findFirst().orElse(null);
        if (active == null || active.item() == null || active.item().stack().isEmpty()) return;
        var itemId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(active.item().stack().getItem());
        boolean activeGun = itemId != null && com.juicyslew.moonstation14.ms14.combat.ModGunProfiles.clientCatalog().get(itemId) != null;
        if (!com.juicyslew.moonstation14.ms14.equipment.client.BodyWorldRenderPolicy.allowOwnedGunArmsPass(
                true, mc.options.getCameraType().isFirstPerson(),
                com.juicyslew.moonstation14.ms14.player_body_control.client.GhostControlClient.committedCharacterEpoch() > 0,
                activeGun)) return;
        var renderer = mc.getEntityRenderDispatcher().getRenderer(harnessBody);
        if (!(renderer instanceof PlayerCharacterHarnessRenderer harness)) return;
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        var camera = mc.gameRenderer.getMainCamera().getPosition();
        var pose = new PoseStack();
        pose.pushPose();
        try {
            pose.translate(net.minecraft.util.Mth.lerp(partial, harnessBody.xOld, harnessBody.getX()) - camera.x,
                    net.minecraft.util.Mth.lerp(partial, harnessBody.yOld, harnessBody.getY()) - camera.y,
                    net.minecraft.util.Mth.lerp(partial, harnessBody.zOld, harnessBody.getZ()) - camera.z);
            harness.renderOwnedGunArms(harnessBody, pose, mc.renderBuffers().bufferSource(),
                    mc.getEntityRenderDispatcher().getPackedLightCoords(harnessBody, partial), partial);
        } finally { pose.popPose(); }
    }

    static boolean[] captureVisibility(PlayerModel<?> model) {
        return new boolean[]{model.head.visible, model.hat.visible, model.body.visible, model.jacket.visible,
                model.leftLeg.visible, model.rightLeg.visible, model.leftPants.visible, model.rightPants.visible,
                model.leftArm.visible, model.rightArm.visible, model.leftSleeve.visible, model.rightSleeve.visible};
    }
    static void setItemsOnlyVisibility(PlayerModel<?> model) {
        model.head.visible = model.hat.visible = model.body.visible = model.jacket.visible = false;
        model.leftLeg.visible = model.rightLeg.visible = model.leftPants.visible = model.rightPants.visible = false;
        model.leftArm.visible = model.rightArm.visible = model.leftSleeve.visible = model.rightSleeve.visible = false;
    }
    static void restoreVisibility(PlayerModel<?> model, boolean[] v) {
        model.head.visible=v[0]; model.hat.visible=v[1]; model.body.visible=v[2]; model.jacket.visible=v[3];
        model.leftLeg.visible=v[4]; model.rightLeg.visible=v[5]; model.leftPants.visible=v[6]; model.rightPants.visible=v[7];
        model.leftArm.visible=v[8]; model.rightArm.visible=v[9]; model.leftSleeve.visible=v[10]; model.rightSleeve.visible=v[11];
    }

    @Override
    protected void setupRotations(PlayerCharacterHarnessEntity entity, PoseStack poseStack, float bob,
                                  float yBodyRot, float partialTick, float scale) {
        var effectivePosture = com.juicyslew.moonstation14.ms14.combat.client.GunProneAimPresentation.effectivePosture(entity);
        if (effectivePosture != null && effectivePosture.down()) {
            var posture = effectivePosture;
            poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - posture.movementFacingYaw()));
            PronePoseMath.applySharedRootPose(poseStack, entity.getBbWidth(), 1.0F,
                    posture.faceUp());
            return;
        }
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
        if (ownedGunArmsPass) return false;
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

    public static void register(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(PlayerCharacterHarnessRegistration.getEntityType(),
                PlayerCharacterHarnessRenderer::new);
    }
}
