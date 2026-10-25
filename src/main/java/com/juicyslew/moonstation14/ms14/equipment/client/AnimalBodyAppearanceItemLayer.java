package com.juicyslew.moonstation14.ms14.equipment.client;

import com.juicyslew.moonstation14.entities.AnimalMob;
import com.juicyslew.moonstation14.ms14.mobs.client.AnimalMobRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.world.entity.HumanoidArm;

/** Native animal hand projection; models without authored hand anchors intentionally draw no hands.
 * Human garment meshes are not fitted onto animal rigs; animal gear requires an authored animal adapter.
 */
public final class AnimalBodyAppearanceItemLayer extends RenderLayer<AnimalMob,
        net.minecraft.client.model.HierarchicalModel<AnimalMob>> {
    private final AnimalMobRenderer renderer;
    private final BodyAppearanceItemRenderer appearanceItems;

    public AnimalBodyAppearanceItemLayer(AnimalMobRenderer renderer, EntityRendererProvider.Context context) {
        super(renderer);
        this.renderer = renderer;
        appearanceItems = new BodyAppearanceItemRenderer(context.getItemInHandRenderer(),
                context.bakeLayer(com.juicyslew.moonstation14.ms14.mob.mouse.client.MouseModel.LAYER_LOCATION), context);
    }

    @Override public void render(PoseStack pose, MultiBufferSource buffer, int light, AnimalMob entity,
                                 float limbSwing, float limbSwingAmount, float partialTicks,
                                 float ageInTicks, float netHeadYaw, float headPitch) {
        appearanceItems.render(entity, pose, buffer, light,
                (body, arm, stack) -> renderer.translateHeldHand(arm == HumanoidArm.LEFT, stack));
    }
}
