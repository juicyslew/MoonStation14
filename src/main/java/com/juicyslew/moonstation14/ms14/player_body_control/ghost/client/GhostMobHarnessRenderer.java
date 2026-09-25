package com.juicyslew.moonstation14.ms14.player_body_control.ghost.client;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.ms14.player_body_control.ghost.GhostMobHarnessEntity;
import com.juicyslew.moonstation14.ms14.player_body_control.ghost.GhostMobHarnessRegistration;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/**
 * Invisible placeholder. Ghost visual/owner-camera visibility is not established yet;
 * this renderer does not provide player movement control.
 */
@EventBusSubscriber(modid = MoonStation14.MOD_ID, value = Dist.CLIENT)
public final class GhostMobHarnessRenderer extends EntityRenderer<GhostMobHarnessEntity> {
    private GhostMobHarnessRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(GhostMobHarnessEntity entity) {
        // The inherited renderer draws no model; there is intentionally no skin or texture asset.
        return ResourceLocation.fromNamespaceAndPath(MoonStation14.MOD_ID, "textures/entity/ghost_mob_harness.png");
    }

    @SubscribeEvent
    public static void register(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(GhostMobHarnessRegistration.getEntityType(), GhostMobHarnessRenderer::new);
    }
}
