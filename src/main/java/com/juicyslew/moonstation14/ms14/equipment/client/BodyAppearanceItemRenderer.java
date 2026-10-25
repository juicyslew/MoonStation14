package com.juicyslew.moonstation14.ms14.equipment.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.juicyslew.moonstation14.ms14.hands.network.BodyAppearanceSnapshot;
import com.juicyslew.moonstation14.ms14.hands.network.BodyHandNetworking;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/** Shared public-snapshot hand display for humanoid and native animal renderers. */
public final class BodyAppearanceItemRenderer {
    @FunctionalInterface public interface HandAnchor {
        /** Returns false for models without an authored hand anchor. */
        boolean translate(LivingEntity entity, HumanoidArm arm, PoseStack pose);
    }
    private final ItemInHandRenderer items;
    private final com.juicyslew.moonstation14.ms14.mob.mouse.client.MouseModel<LivingEntity> mouseModel;
    private final EntityRendererProvider.Context context;
    private final java.util.Map<String, HierarchicalModel<? extends LivingEntity>> animalModels = new java.util.HashMap<>();

    public BodyAppearanceItemRenderer(ItemInHandRenderer items, net.minecraft.client.model.geom.ModelPart mouseRoot,
                                      EntityRendererProvider.Context context) {
        this.items = items;
        this.context = context;
        mouseModel = new com.juicyslew.moonstation14.ms14.mob.mouse.client.MouseModel<>(mouseRoot);
    }

    public void render(LivingEntity entity, PoseStack pose, MultiBufferSource buffer, int light, HandAnchor anchor) {
        ResourceLocation dimension = entity.level().dimension().location();
        BodyAppearanceSnapshot snapshot = BodyHandNetworking.clientCache()
                .get(dimension, entity.getUUID(), entity.getId()).orElse(null);
        if (snapshot == null) return;
        var plan = EquipmentRenderPlan.create(snapshot,
                com.juicyslew.moonstation14.ms14.equipment.ModEquipmentItems.clientCatalog());
        for (var hand : plan.hands()) {
            HumanoidArm arm = hand.id().equalsIgnoreCase("left") ? HumanoidArm.LEFT : HumanoidArm.RIGHT;
            pose.pushPose();
            try {
                if (!anchor.translate(entity, arm, pose)) continue;
                if (com.juicyslew.moonstation14.ms14.hands.PortableBodyAppearanceResolver.fromStack(hand.stack()) != null)
                    renderPortableBody(hand.stack(), arm, pose, buffer, light);
                else renderItem(entity, hand.stack(), arm, pose, buffer, light);
            } finally { pose.popPose(); }
        }
    }

    private void renderPortableBody(ItemStack stack, HumanoidArm arm, PoseStack pose, MultiBufferSource buffer, int light) {
        var view = com.juicyslew.moonstation14.ms14.hands.PortableBodyAppearanceResolver.fromStack(stack);
        com.juicyslew.moonstation14.ms14.loose_objects.LooseObjectProfile profile;
        try { profile = com.juicyslew.moonstation14.ms14.loose_objects.LooseObjectProfile.resolveForStack(stack, true); }
        catch (RuntimeException unresolvedProfile) { return; }
        if (view == null || profile == null) return;
        boolean mouse = usesMouseAdapter(view);
        var animalAdapter = mouse ? null : com.juicyslew.moonstation14.ms14.mobs.client.AnimalModelAdapters.resolve(view.visualKey());
        var mousePresentation = mouse ? com.juicyslew.moonstation14.ms14.mob.mouse.MouseAppearanceTextures.heldPresentation(view) : null;
        var texture = mouse ? mousePresentation.texture() : animalAdapter.portableTexture(view);
        pose.mulPose(Axis.XP.rotationDegrees(-90));
        pose.mulPose(Axis.YP.rotationDegrees(180));
        pose.translate((arm == HumanoidArm.LEFT ? -1 : 1) / 16.0F, .125F, -.625F);
        if (mouse) {
            pose.scale(.16F * profile.visual().modelScale(), -.16F * profile.visual().modelScale(),
                    .16F * profile.visual().modelScale());
            mouseModel.applyPresentation(mousePresentation.pose(), mousePresentation.dead(), 0, 0, 0, -1, 1);
            mouseModel.renderToBuffer(pose, buffer.getBuffer(RenderType.entityCutoutNoCull(texture)), light,
                    OverlayTexture.NO_OVERLAY, 0xffffffff);
        } else {
            var model = animalModels.computeIfAbsent(view.visualKey(), key -> animalAdapter.bake(context));
            float modelScale = profile.visual().modelScale() * animalAdapter.presentationScale();
            pose.scale(modelScale, -modelScale, modelScale);
            animalAdapter.applyPortable(model, view, 0);
            model.renderToBuffer(pose, buffer.getBuffer(RenderType.entityCutoutNoCull(texture)), light,
                    OverlayTexture.NO_OVERLAY, 0xffffffff);
        }
    }

    public static boolean usesMouseAdapter(com.juicyslew.moonstation14.ms14.hands.PortableBodyAppearance view) {
        return view.coat() != com.juicyslew.moonstation14.ms14.hands.PortableBodyAppearance.Coat.NONE
                && view.visualKey().isEmpty();
    }

    private void renderItem(LivingEntity entity, ItemStack stack, HumanoidArm arm,
                           PoseStack pose, MultiBufferSource buffer, int light) {
        if (stack.isEmpty()) return;
        boolean left = arm == HumanoidArm.LEFT;
        pose.mulPose(Axis.XP.rotationDegrees(-90.0F));
        pose.mulPose(Axis.YP.rotationDegrees(180.0F));
        pose.translate((left ? -1.0F : 1.0F) / 16.0F, 0.125F, -0.625F);
        items.renderItem(entity, stack, left ? ItemDisplayContext.THIRD_PERSON_LEFT_HAND
                : ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, left, pose, buffer, light);
    }
}
