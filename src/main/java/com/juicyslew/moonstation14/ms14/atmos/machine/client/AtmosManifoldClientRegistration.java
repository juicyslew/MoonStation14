package com.juicyslew.moonstation14.ms14.atmos.machine.client;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.block.ModBlockEntities;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;

@EventBusSubscriber(modid = MoonStation14.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class AtmosManifoldClientRegistration {
    private AtmosManifoldClientRegistration() { }

    @SubscribeEvent public static void registerRenderer(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlockEntities.ATMOS_MACHINE.get(), AtmosManifoldRenderer::new);
    }

    @SubscribeEvent public static void registerModels(ModelEvent.RegisterAdditional event) {
        AtmosManifoldRenderer.additionalModels().forEach(event::register);
    }
}
