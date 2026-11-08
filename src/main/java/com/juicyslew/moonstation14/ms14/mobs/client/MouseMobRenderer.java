package com.juicyslew.moonstation14.ms14.mobs.client;

import com.juicyslew.moonstation14.entities.MouseMob;
import com.juicyslew.moonstation14.ms14.mob.mouse.client.MouseModel;
import com.juicyslew.moonstation14.ms14.mob.mouse.MouseAppearanceTextures;
import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.ms14.character.components.MobStateComponent;
import com.juicyslew.moonstation14.ms14.mobs.MobStateAttachment;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.minecraft.resources.ResourceLocation;

/** Authored R2 model binding. The default Mouse prototype is the white variant. */
public final class MouseMobRenderer extends MobRenderer<MouseMob, MouseModel<MouseMob>> {
    public MouseMobRenderer(EntityRendererProvider.Context context) {
        super(context, new MouseModel<>(context.bakeLayer(MouseModel.LAYER_LOCATION)), 0.16F);
    }

    public static void registerLayer(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(MouseModel.LAYER_LOCATION, MouseModel::createBodyLayer);
    }

    @Override public ResourceLocation getTextureLocation(MouseMob mob) {
        return MouseAppearanceTextures.texture(mob.appearance(), isDead(mob));
    }

    private static boolean isDead(MouseMob mob) {
        MobStateAttachment state = mob.getExistingDataOrNull(ModDataAttachments.MOB_STATE.get());
        return state != null && state.data().state() == MobStateComponent.State.DEAD;
    }

    @Override protected float getFlipDegrees(MouseMob mob) {
        // Preserve normal yaw/facing setup while suppressing vanilla corpse rotation.
        return 0.0F;
    }
}
