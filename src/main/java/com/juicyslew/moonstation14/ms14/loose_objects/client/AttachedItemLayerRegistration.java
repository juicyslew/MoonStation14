package com.juicyslew.moonstation14.ms14.loose_objects.client;

import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** Installs the shared attachment presentation on every registered living renderer. */
@EventBusSubscriber(modid = com.juicyslew.moonstation14.MoonStation14.MOD_ID,
        bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class AttachedItemLayerRegistration {
    private AttachedItemLayerRegistration() {}

    @SubscribeEvent
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static void addLayers(EntityRenderersEvent.AddLayers event) {
        for (EntityType<?> type : net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE) {
            var renderer = event.getRenderer(type);
            if (renderer instanceof LivingEntityRenderer livingRenderer)
                livingRenderer.addLayer(new AttachedThrownItemLayer(livingRenderer));
        }
    }
}
