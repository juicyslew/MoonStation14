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
        renderGarments(plan.garments(), getParentModel(), entity.bodyShape(), pose, buffer, light);
    }

    static void renderGarments(java.util.List<EquipmentRenderPlan.Garment> garments,
                              PlayerModel<?> model,
                              com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterBodyShape shape,
                              PoseStack pose, MultiBufferSource buffer, int light) {
        boolean slim = shape == com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterBodyShape.SLIM;
        for (var garment : garments) {
            boolean authored = AuthoredWearableRenderer.canRender(garment.wornModel(), shape,
                    garment.wornModel().flatMap(w -> AuthoredWearableMeshes.get(w.mesh())).isPresent())
                    && AuthoredWearableRenderer.render(garment, model, pose, buffer, light);
            if (!authored) {
                // Authored rendering may switch the shared MultiBufferSource's active builder.
                // Acquire the placeholder consumer only after that attempt, for this garment.
                var placeholderConsumer = buffer.getBuffer(RenderType.entityCutoutNoCull(EquipmentPlaceholderGeometry.WHITE_TEXTURE));
                EquipmentPlaceholderGeometry.render(garment, model, slim, pose, placeholderConsumer, light);
            }
        }
    }
}

