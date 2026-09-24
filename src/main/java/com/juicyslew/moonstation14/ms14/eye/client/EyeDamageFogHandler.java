package com.juicyslew.moonstation14.ms14.eye.client;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.ms14.eye.EyeDamageVision;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ViewportEvent;

/** Client-only threshold presentation for the synchronized camera entity. */
@EventBusSubscriber(modid = MoonStation14.MOD_ID, value = Dist.CLIENT)
@net.neoforged.api.distmarker.OnlyIn(Dist.CLIENT)
public final class EyeDamageFogHandler {
    private EyeDamageFogHandler() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onRenderFog(ViewportEvent.RenderFog event) {
        Entity cameraEntity = event.getCamera().getEntity();
        LivingEntity camera = cameraEntity instanceof LivingEntity living ? living : null;
        if (camera == null) {
            return;
        }
        var attachment = camera.getExistingDataOrNull(ModDataAttachments.EYE_DAMAGE.get());
        boolean blind = attachment != null && attachment.isBlind();
        EyeDamageVision.FogProjection projection = EyeDamageVision.projectFog(
                event.getNearPlaneDistance(), event.getFarPlaneDistance(), blind, event.isCanceled());
        if (!projection.changed()) {
            return;
        }
        event.setNearPlaneDistance(projection.planes().nearPlane());
        event.setFarPlaneDistance(projection.planes().farPlane());
        if (projection.canceled() && !event.isCanceled()) {
            event.setCanceled(true);
        }
    }
}
