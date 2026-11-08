package com.juicyslew.moonstation14.ms14.radiation.client;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.block.ModBlockEntities;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;

@EventBusSubscriber(modid = MoonStation14.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class RadiationCollectorClientRegistration {
    private RadiationCollectorClientRegistration() { }

    @SubscribeEvent public static void registerRenderer(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlockEntities.RADIATION_COLLECTOR.get(), RadiationCollectorBlockEntityRenderer::new);
    }

    @SubscribeEvent public static void registerModels(ModelEvent.RegisterAdditional event) {
        for (ModelResourceLocation model : RadiationCollectorBlockEntityRenderer.additionalModels()) event.register(model);
    }
}
