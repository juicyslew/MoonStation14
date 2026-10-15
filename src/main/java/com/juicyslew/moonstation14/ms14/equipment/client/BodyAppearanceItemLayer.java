package com.juicyslew.moonstation14.ms14.equipment.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.juicyslew.moonstation14.ms14.hands.network.BodyAppearanceSnapshot;
import com.juicyslew.moonstation14.ms14.hands.network.BodyHandNetworking;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessEntity;

/** Draws the body's public hand projection; never reads the entity/carrier inventory. */
public final class BodyAppearanceItemLayer extends RenderLayer<PlayerCharacterHarnessEntity, PlayerModel<PlayerCharacterHarnessEntity>> {
    private final ItemInHandRenderer items;
    private final com.juicyslew.moonstation14.ms14.mob.mouse.client.MouseModel<net.minecraft.world.entity.LivingEntity> mouseModel;

    public BodyAppearanceItemLayer(RenderLayerParent<PlayerCharacterHarnessEntity, PlayerModel<PlayerCharacterHarnessEntity>> parent,
                                   ItemInHandRenderer items, net.minecraft.client.model.geom.ModelPart mouseRoot) {
        super(parent);
        this.items = items;
        this.mouseModel = new com.juicyslew.moonstation14.ms14.mob.mouse.client.MouseModel<>(
                mouseRoot);
    }

    @Override public void render(PoseStack pose, MultiBufferSource buffer, int light,
                                 PlayerCharacterHarnessEntity entity, float limbSwing, float limbSwingAmount,
                                 float partialTicks, float ageInTicks, float netHeadYaw, float headPitch) {
        ResourceLocation dimension = entity.level().dimension().location();
        BodyAppearanceSnapshot snapshot = BodyHandNetworking.clientCache()
                .get(dimension, entity.getUUID(), entity.getId()).orElse(null);
        if (snapshot == null) return;
        var plan = EquipmentRenderPlan.create(snapshot,
                com.juicyslew.moonstation14.ms14.equipment.ModEquipmentItems.clientCatalog());
        for (var hand : plan.hands()) {
            HumanoidArm arm = hand.id().equalsIgnoreCase("left") ? HumanoidArm.LEFT : HumanoidArm.RIGHT;
            if (com.juicyslew.moonstation14.ms14.hands.PortableBodyAppearanceResolver.fromStack(hand.stack()) != null)
                renderPortableMouse(hand.stack(), arm, pose, buffer, light);
            else renderHand(entity, hand.stack(), arm, pose, buffer, light);
        }
    }

    private void renderPortableMouse(ItemStack stack, HumanoidArm arm, PoseStack pose, MultiBufferSource buffer, int light) {
        var view = com.juicyslew.moonstation14.ms14.hands.PortableBodyAppearanceResolver.fromStack(stack);
        com.juicyslew.moonstation14.ms14.loose_objects.LooseObjectProfile profile;
        try { profile = com.juicyslew.moonstation14.ms14.loose_objects.LooseObjectProfile.resolveForStack(stack, true); }
        catch (RuntimeException unresolvedProfile) { return; }
        if (view == null || profile == null) return;
        var presentation = com.juicyslew.moonstation14.ms14.mob.mouse.MouseAppearanceTextures.heldPresentation(view);
        pose.pushPose();
        try {
            getParentModel().translateToHand(arm, pose);
            pose.mulPose(Axis.XP.rotationDegrees(-90));
            pose.mulPose(Axis.YP.rotationDegrees(180));
            pose.translate((arm == HumanoidArm.LEFT ? -1 : 1) / 16.0F, .125F, -.625F);
            pose.scale(.16F * profile.visual().modelScale(), -.16F * profile.visual().modelScale(),
                    .16F * profile.visual().modelScale());
            mouseModel.applyPresentation(presentation.pose(), presentation.dead(),
                    0, 0, 0, -1, 1);
            mouseModel.renderToBuffer(pose, buffer.getBuffer(net.minecraft.client.renderer.RenderType.entityCutoutNoCull(presentation.texture())),
                    light, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, 0xffffffff);
        } finally { pose.popPose(); }
    }

    private void renderHand(PlayerCharacterHarnessEntity entity, ItemStack stack, HumanoidArm arm,
                            PoseStack pose, MultiBufferSource buffer, int light) {
        if (stack.isEmpty()) return;
        pose.pushPose();
        try {
            getParentModel().translateToHand(arm, pose);
            pose.mulPose(Axis.XP.rotationDegrees(-90.0F));
            pose.mulPose(Axis.YP.rotationDegrees(180.0F));
            boolean left = arm == HumanoidArm.LEFT;
            pose.translate((left ? -1.0F : 1.0F) / 16.0F, 0.125F, -0.625F);
            items.renderItem(entity, stack, left ? ItemDisplayContext.THIRD_PERSON_LEFT_HAND
                    : ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, left, pose, buffer, light);
        } finally { pose.popPose(); }
    }
}
