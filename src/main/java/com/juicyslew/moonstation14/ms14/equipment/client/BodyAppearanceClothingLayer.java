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
        boolean slim = entity.bodyShape() == com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterBodyShape.SLIM;
        var model = getParentModel();
        var placeholderConsumer = buffer.getBuffer(RenderType.entityCutoutNoCull(EquipmentPlaceholderGeometry.WHITE_TEXTURE));
        for (var garment : plan.garments()) {
            boolean authored = AuthoredWearableRenderer.canRender(garment.wornModel(), entity.bodyShape(),
                    garment.wornModel().flatMap(w -> AuthoredWearableMeshes.get(w.mesh())).isPresent())
                    && AuthoredWearableRenderer.render(garment, model, pose, buffer, light);
            if (!authored) for (var anchor : garment.anchors()) {
                EquipmentPlaceholderGeometry.render(garment, anchor, model, slim, pose, placeholderConsumer, light);
            }
        }
    }
}
