package com.juicyslew.moonstation14.ms14.equipment.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.juicyslew.moonstation14.ms14.hands.network.BodyHandNetworking;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessEntity;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;

/** Draws the resolved body snapshot as authored wearables or shared placeholder geometry. */
public final class BodyAppearanceClothingLayer extends RenderLayer<PlayerCharacterHarnessEntity, PlayerModel<PlayerCharacterHarnessEntity>> {
    public BodyAppearanceClothingLayer(RenderLayerParent<PlayerCharacterHarnessEntity, PlayerModel<PlayerCharacterHarnessEntity>> parent,
                                       net.minecraft.client.renderer.ItemInHandRenderer ignored) { super(parent); }

    @Override public void render(PoseStack pose, MultiBufferSource buffer, int light, PlayerCharacterHarnessEntity entity,
                                 float limbSwing, float limbSwingAmount, float partialTicks, float ageInTicks,
                                 float netHeadYaw, float headPitch) {
        var snapshot = BodyHandNetworking.clientCache().get(entity.level().dimension().location(), entity.getUUID(), entity.getId()).orElse(null);
        if (snapshot == null) return;
        var plan = EquipmentRenderPlan.create(snapshot, com.juicyslew.moonstation14.ms14.equipment.ModEquipmentItems.clientCatalog());
        if (com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.client.PlayerCharacterHarnessRenderer
                .isOwnedGunArmsPass()) {
            renderArmGarments(plan.garments(), getParentModel(), entity.bodyShape(), pose, buffer, light,
                    (entity.level().getGameTime() + partialTicks) / 20.0);
            return;
        }
        renderGarments(plan.garments(), getParentModel(), entity.bodyShape(), pose, buffer, light,
                (entity.level().getGameTime() + partialTicks) / 20.0);
    }

    private static void renderArmGarments(java.util.List<EquipmentRenderPlan.Garment> garments, PlayerModel<?> model,
            com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterBodyShape shape,
            PoseStack pose, MultiBufferSource buffer, int light, double elapsed) {
        var allowed = java.util.EnumSet.of(EquipmentRenderPlan.Anchor.LEFT_ARM, EquipmentRenderPlan.Anchor.RIGHT_ARM);
        boolean slim = shape == com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterBodyShape.SLIM;
        for (var garment : armGarments(garments)) {
            if (garment.renderItemModel() && !garment.stack().isEmpty()) { renderItemModel(garment, model, pose, buffer, light); continue; }
            boolean loaded = garment.wornModel().map(w -> AuthoredWearableMeshes.get(
                    AuthoredWearableRenderer.selectedMesh(w, elapsed)).isPresent()).orElse(false);
            boolean authored = AuthoredWearableRenderer.canRender(garment.wornModel(), shape, loaded)
                    && AuthoredWearableRenderer.render(garment, model, pose, buffer, light, elapsed, allowed);
            if (!authored) EquipmentPlaceholderGeometry.render(garment, model, slim,
                    pose, buffer.getBuffer(RenderType.entityCutoutNoCull(EquipmentPlaceholderGeometry.WHITE_TEXTURE)), light);
        }
    }

    private static java.util.List<EquipmentRenderPlan.Garment> armGarments(java.util.List<EquipmentRenderPlan.Garment> garments) {
        return garments.stream().map(g -> {
            var anchors = g.anchors().stream().filter(a -> a == EquipmentRenderPlan.Anchor.LEFT_ARM
                    || a == EquipmentRenderPlan.Anchor.RIGHT_ARM).toList();
            return anchors.isEmpty() ? null : new EquipmentRenderPlan.Garment(g.slot(), g.item(), g.region(),
                    g.color(), anchors, g.layerOrder(), g.wornModel(), g.stack(), g.renderItemModel());
        }).filter(java.util.Objects::nonNull).toList();
    }

    static void renderGarments(java.util.List<EquipmentRenderPlan.Garment> garments,
                              PlayerModel<?> model,
                              com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterBodyShape shape,
                              PoseStack pose, MultiBufferSource buffer, int light) {
        renderGarments(garments, model, shape, pose, buffer, light, 0);
    }
    static void renderGarments(java.util.List<EquipmentRenderPlan.Garment> garments, PlayerModel<?> model,
                              com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterBodyShape shape,
                              PoseStack pose, MultiBufferSource buffer, int light, double elapsedSeconds) {
        boolean slim = shape == com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterBodyShape.SLIM;
        for (var garment : garments) {
            if (garment.renderItemModel() && !garment.stack().isEmpty()) {
                renderItemModel(garment, model, pose, buffer, light);
                continue;
            }
            var selectedMeshLoaded = garment.wornModel().filter(w -> AuthoredWearableMeshes.get(AuthoredWearableRenderer.selectedMesh(w, elapsedSeconds)).isPresent()).isPresent();
            boolean authored = AuthoredWearableRenderer.canRender(garment.wornModel(), shape, selectedMeshLoaded)
                    && AuthoredWearableRenderer.render(garment, model, pose, buffer, light, elapsedSeconds);
            if (!authored) {
                // Authored rendering may switch the shared MultiBufferSource's active builder.
                // Acquire the placeholder consumer only after that attempt, for this garment.
                var placeholderConsumer = buffer.getBuffer(RenderType.entityCutoutNoCull(EquipmentPlaceholderGeometry.WHITE_TEXTURE));
                EquipmentPlaceholderGeometry.render(garment, model, slim, pose, placeholderConsumer, light);
            }
        }
    }

    private static void renderItemModel(EquipmentRenderPlan.Garment garment, PlayerModel<?> model,
                                        PoseStack pose, MultiBufferSource buffer, int light) {
        pose.pushPose();
        // BACK and WAIST use the existing body/waist anchors, in the same articulated model space.
        model.body.translateAndRotate(pose);
        if (garment.anchors().contains(EquipmentRenderPlan.Anchor.BACK)) pose.translate(0.0, 0.05, 0.16);
        else pose.translate(0.0, -0.12, 0.09);
        pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(90));
        pose.scale(0.42f, 0.42f, 0.42f);
        net.minecraft.client.Minecraft.getInstance().getItemRenderer().renderStatic(garment.stack(),
                net.minecraft.world.item.ItemDisplayContext.FIXED, light, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY,
                pose, buffer, net.minecraft.client.Minecraft.getInstance().level, 0);
        pose.popPose();
    }
}
