package com.juicyslew.moonstation14.ms14.equipment.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.world.entity.HumanoidArm;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessEntity;

/** Draws the body's public hand projection; never reads the entity/carrier inventory. */
public final class BodyAppearanceItemLayer extends RenderLayer<PlayerCharacterHarnessEntity, PlayerModel<PlayerCharacterHarnessEntity>> {
    private final BodyAppearanceItemRenderer appearanceItems;

    public BodyAppearanceItemLayer(RenderLayerParent<PlayerCharacterHarnessEntity, PlayerModel<PlayerCharacterHarnessEntity>> parent,
                                   ItemInHandRenderer items, net.minecraft.client.model.geom.ModelPart mouseRoot,
                                   net.minecraft.client.renderer.entity.EntityRendererProvider.Context context) {
        super(parent);
        appearanceItems = new BodyAppearanceItemRenderer(items, mouseRoot, context);
    }

    @Override public void render(PoseStack pose, MultiBufferSource buffer, int light,
                                 PlayerCharacterHarnessEntity entity, float limbSwing, float limbSwingAmount,
                                 float partialTicks, float ageInTicks, float netHeadYaw, float headPitch) {
        appearanceItems.render(entity, pose, buffer, light, (ignored, arm, stack) -> {
            getParentModel().translateToHand(arm, stack);
            return true;
        });
    }
}
