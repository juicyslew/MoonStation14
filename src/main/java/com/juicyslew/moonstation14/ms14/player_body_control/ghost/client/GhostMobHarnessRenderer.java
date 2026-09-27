package com.juicyslew.moonstation14.ms14.player_body_control.ghost.client;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.ms14.player_body_control.ghost.GhostMobHarnessEntity;
import com.juicyslew.moonstation14.ms14.player_body_control.ghost.GhostMobHarnessRegistration;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.SlimeModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** Visible vanilla slime-shaped debug proxy for the ghost movement harness. */
@EventBusSubscriber(modid = MoonStation14.MOD_ID, value = Dist.CLIENT)
public final class GhostMobHarnessRenderer extends MobRenderer<GhostMobHarnessEntity, SlimeModel<GhostMobHarnessEntity>> {
    private static final ResourceLocation TEXTURE = ResourceLocation.withDefaultNamespace("textures/entity/slime/slime.png");

    private GhostMobHarnessRenderer(EntityRendererProvider.Context context) {
        super(context, new SlimeModel<>(context.bakeLayer(ModelLayers.SLIME)), 0.0f);
    }

    @Override
    public ResourceLocation getTextureLocation(GhostMobHarnessEntity entity) {
        return TEXTURE;
    }

    @Override
    public void render(GhostMobHarnessEntity entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getCameraEntity() == entity
                && minecraft.options.getCameraType() == CameraType.FIRST_PERSON) return;
        super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
    }

    @SubscribeEvent
    public static void register(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(GhostMobHarnessRegistration.getEntityType(), GhostMobHarnessRenderer::new);
    }
}
